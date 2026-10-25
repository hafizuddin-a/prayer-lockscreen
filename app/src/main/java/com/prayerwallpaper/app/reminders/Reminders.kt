package com.prayerwallpaper.app.reminders

import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import com.prayerwallpaper.app.data.Timetable
import com.prayerwallpaper.app.logic.AlarmMethod
import com.prayerwallpaper.app.logic.HistoryEntry
import com.prayerwallpaper.app.logic.NextReminder
import com.prayerwallpaper.app.logic.ReminderAlarm
import com.prayerwallpaper.app.logic.ReminderHistory
import com.prayerwallpaper.app.logic.ReminderKind
import com.prayerwallpaper.app.logic.ReminderOutcome
import com.prayerwallpaper.app.logic.isStale
import com.prayerwallpaper.app.logic.minutesBefore
import com.prayerwallpaper.app.logic.nextReminder
import com.prayerwallpaper.app.logic.reminderContent
import com.prayerwallpaper.app.logic.spokenTime
import com.prayerwallpaper.app.logic.staleLimitFor
import com.prayerwallpaper.app.settings.AppSettings
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What [Reminders.ensureScheduled] found and did, for the health screen and for scripted checks. */
data class ScheduleState(val next: NextReminder, val method: AlarmMethod?, val set: Boolean)

/**
 * The reminder scheduler. Only the next alarm is ever set, as a chain: each alarm that arrives sets the one after it. Every way
 * the chain could break (a reboot, a time change, a permission change, an app update) calls [ensureScheduled], which puts the next
 * alarm back. One lock covers it all, so the alarm receiver, the daily job and the settings screen never plan at the same time.
 *
 * All times come from [clock], which is the real clock: an alarm is a real moment, so the debug fake clock does not apply here.
 * The times are those of the chosen place, read in its own zone ([Timetable.zone], Asia/Singapore for the MUIS timetable).
 */
class Reminders(
    private val settings: () -> AppSettings,
    private val timetable: (AppSettings) -> Timetable,
    private val store: ReminderHistoryStore,
    private val gateway: AlarmGateway,
    private val notifier: ReminderNotifier = object : ReminderNotifier {
        override fun canPost(silent: Boolean) = true
        override fun post(id: Int, content: com.prayerwallpaper.app.logic.ReminderContent, silent: Boolean) = Unit
    },
    private val clock: Clock = Clock.systemUTC(),
    /** Whether the phone shows 24-hour time, so a reminder's time reads the way the list's does. */
    private val is24Hour: () -> Boolean = { false },
    /** Asks the wallpaper to redraw, in case the highlight moved. Called after each alarm. */
    private val redraw: () -> Unit = {},
    /** Debug builds can replace the prayer times with a fast schedule and force an alarm method. Null in release. */
    private val debugDays: (ZoneId) -> Map<LocalDate, PrayerDay>? = { null },
    private val debugMethod: () -> AlarmMethod? = { null },
    /** A line for the developer's log, which the app only turns on in debug builds. */
    private val log: (String) -> Unit = {},
) {
    @Volatile private var lastThrottled: Instant = Instant.MIN

    @Synchronized
    fun history(): ReminderHistory = store.read()

    /** Which method the next alarm would use now: exact when Android allows it, otherwise inexact. A debug switch can force one. */
    fun method(): AlarmMethod = debugMethod() ?: if (gateway.canScheduleExact()) AlarmMethod.EXACT else AlarmMethod.INEXACT

    /** Whether Android lets the app set exact alarms now (and the build includes them). */
    fun canScheduleExact(): Boolean = gateway.canScheduleExact()

    /** What the next alarm is and how it is set, read without changing anything: for the health screen. */
    data class Peek(val next: NextReminder, val method: AlarmMethod?, val pendingIntentExists: Boolean)

    @Synchronized
    fun peek(): Peek {
        val current = settings()
        if (!current.remindersOn) return Peek(NextReminder.Off, null, false)
        val now = clock.instant()
        val table = timetable(current)
        val days = debugDays(table.zone) ?: table.days(LocalDate.now(clock.withZone(table.zone)))
        val next = nextReminder(days, now, table.zone, current.reminderPrayers, current.preReminderMinutes)
        if (next !is NextReminder.Due) return Peek(next, null, false)
        return Peek(next, store.read().find(next.alarm.key)?.method ?: method(), gateway.exists(next.alarm))
    }

    /** Whether the pending intent of [alarm] exists. It does not prove Android still holds the alarm; see [AlarmGateway.exists]. */
    fun exists(alarm: ReminderAlarm): Boolean = gateway.exists(alarm)

    /**
     * Makes sure the next alarm is set, for [reason] (only for the log). First marks as missed any alarm that is long overdue, then
     * works out the next one and sets it unless it is already set the same way. With [force] it is set again regardless, which is the
     * only way to be sure after something may have cleared Android's alarms: the pending intent can outlive the alarm.
     * Turned off, or nothing to plan: everything still waiting is cancelled.
     */
    @Synchronized
    fun ensureScheduled(reason: String, force: Boolean = true): ScheduleState {
        val current = settings()
        val now = clock.instant()
        var plans = store.read().markMissed(now).prune(now)

        if (!current.remindersOn) {
            cancelFuture(plans, now)
            store.write(plans.withoutFuturePending(now))
            return ScheduleState(NextReminder.Off, null, false)
        }

        val table = timetable(current)
        val today = LocalDate.now(clock.withZone(table.zone))
        val days = debugDays(table.zone) ?: table.days(today)
        val next = nextReminder(days, now, table.zone, current.reminderPrayers, current.preReminderMinutes)
        if (next !is NextReminder.Due) {
            cancelFuture(plans, now)
            store.write(plans.withoutFuturePending(now))
            debug("ensureScheduled($reason): nothing to plan ($next)")
            return ScheduleState(next, null, false)
        }

        val alarm = next.alarm
        val method = method()
        val existing = plans.find(alarm.key)
        val same = existing != null && existing.outcome == ReminderOutcome.PENDING && existing.method == method && gateway.exists(alarm)
        if (same && !force) {
            store.write(plans)
            return ScheduleState(next, method, false)
        }
        // The alarms planned earlier but not due yet are replaced by this one.
        cancelFuture(plans, now, except = alarm)
        val used = gateway.schedule(alarm, method)
        plans = plans.planned(HistoryEntry(alarm.at, alarm.kind, alarm.prayer, used, prayerAtOrNull = alarm.prayerAt), now)
        store.write(plans)
        debug("ensureScheduled($reason): ${alarm.kind} ${alarm.prayer ?: "-"} at ${alarm.at} by $used")
        return ScheduleState(next, used, true)
    }

    /**
     * [ensureScheduled] without forcing, at most once in [THROTTLE]: for the things that happen often, like the screen turning on.
     * Does nothing while reminders are off.
     */
    fun ensureScheduledThrottled(reason: String) {
        if (!settings().remindersOn) return
        val now = clock.instant()
        synchronized(this) {
            if (Duration.between(lastThrottled, now) < THROTTLE) return
            lastThrottled = now
        }
        ensureScheduled(reason, force = false)
    }

    /**
     * Schedules a real alarm [after] from now through the same path as every other, for the health screen's test button. It is not part
     * of the chain, works whether or not reminders are on, and when it arrives it shows a notification like any reminder.
     */
    @Synchronized
    fun scheduleTest(after: Duration = Duration.ofMinutes(1)): ReminderAlarm {
        val now = clock.instant()
        val at = now.plus(after)
        val zone = timetable(settings()).zone
        val alarm = ReminderAlarm(ReminderKind.TEST, null, at.atZone(zone).toLocalDate(), at)
        val used = gateway.schedule(alarm, method())
        store.update { it.planned(HistoryEntry(at, ReminderKind.TEST, null, used, prayerAtOrNull = at), now) }
        debug("test reminder at $at by $used")
        return alarm
    }

    /**
     * An alarm arrived. Records when, and how late it was; shows it unless it came too late (more than the stale limit for the way it
     * was set) or notifications are off; sets the next alarm and asks the wallpaper to redraw. Does nothing if reminders were turned
     * off meanwhile (their alarms were cancelled with them); a test arrives either way.
     */
    @Synchronized
    fun onAlarm(plannedAt: Instant, kind: ReminderKind, prayer: Prayer?) {
        val firedAt = clock.instant()
        val alarm = ReminderAlarm(kind, prayer, LocalDate.MIN, plannedAt)
        val current = settings()
        val entry = store.read().find(alarm.key) ?: HistoryEntry(plannedAt, kind, prayer, method(), prayerAtOrNull = plannedAt)
        val lateness = Duration.between(plannedAt, firedAt).coerceAtLeast(Duration.ZERO)
        if (!current.remindersOn && kind != ReminderKind.TEST) {
            debug("alarm ${alarm.key} arrived with reminders off: ignored")
            return
        }
        val outcome = when {
            isStale(lateness, staleLimitFor(entry.method)) -> ReminderOutcome.STALE
            !notifier.canPost(current.silentReminders) -> ReminderOutcome.BLOCKED
            else -> {
                notifier.post(alarm.key.hashCode(), contentFor(entry, current), current.silentReminders)
                ReminderOutcome.FIRED
            }
        }
        store.update { it.delivered(entry, firedAt, outcome) }
        gateway.cancel(alarm) // it has done its job: release its pending intent
        debug("alarm ${alarm.key} ${outcome.name.lowercase()}, ${lateness.seconds} s late (limit ${staleLimitFor(entry.method).toMinutes()} min)")
        ensureScheduled("alarm", force = false)
        redraw()
    }

    /** Cancels every alarm and forgets the plans still waiting, the test included. Reminders turned off, or "Reset reminders". */
    @Synchronized
    fun cancelAll() {
        val now = clock.instant()
        val plans = store.read()
        cancelFuture(plans, now, includeOverdue = true, includeTests = true)
        store.write(plans.withoutFuturePending(now, includeTests = true))
    }

    private fun contentFor(entry: HistoryEntry, current: AppSettings): com.prayerwallpaper.app.logic.ReminderContent {
        val zone = timetable(current).zone
        val prayerTime = entry.prayerAt.atZone(zone)
        val label = entry.prayer?.label(current.effectiveNames, friday = prayerTime.dayOfWeek == DayOfWeek.FRIDAY).orEmpty()
        return reminderContent(entry.kind, label, minutesBefore(entry.plannedAt, entry.prayerAt), spokenTime(prayerTime.toLocalTime(), is24Hour()))
    }

    private fun cancelFuture(
        plans: ReminderHistory, now: Instant, except: ReminderAlarm? = null, includeOverdue: Boolean = false, includeTests: Boolean = false,
    ) {
        for (entry in plans.entries) {
            if (entry.outcome != ReminderOutcome.PENDING || entry.key == except?.key) continue
            if (entry.kind == ReminderKind.TEST && !includeTests) continue
            if (entry.plannedAt <= now && !includeOverdue) continue
            gateway.cancel(ReminderAlarm(entry.kind, entry.prayer, LocalDate.MIN, entry.plannedAt))
        }
    }

    private fun debug(message: String) = log(message)

    companion object {
        /** The engine and the settings screen ask often; the schedule is checked at most this often on their account. */
        val THROTTLE: Duration = Duration.ofMinutes(30)
    }
}
