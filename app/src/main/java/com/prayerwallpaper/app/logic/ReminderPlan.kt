package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What an alarm is for: the prayer time itself, a heads-up before it, or the test the health screen schedules. */
enum class ReminderKind { AT, PRE, TEST }

/**
 * One alarm the app plans. [prayer] is null only for [ReminderKind.TEST]. [date] is the day, in the place's own zone, whose
 * prayer time this is, [at] is when the alarm should go off, and [prayerAt] is the prayer time itself (a pre-reminder is earlier).
 */
data class ReminderAlarm(val kind: ReminderKind, val prayer: Prayer?, val date: LocalDate, val at: Instant, val prayerAt: Instant = at) {
    /** Identifies this alarm among the others, including inside a pending intent, whatever its extras say. */
    val key: String get() = "${at.toEpochMilli()}/${kind.name}/${prayer?.name ?: "-"}"
}

/** The pre-reminder sizes the settings offer, in minutes. 0 is off. */
val PRE_REMINDER_CHOICES = listOf(0, 5, 10, 15)

/** An exact alarm later than this after its planned time is not shown: a reminder for a prayer that passed long ago is worse than none. */
val STALE_LIMIT: Duration = Duration.ofMinutes(30)

/**
 * The same for an inexact alarm, which Android may deliver up to an hour late (measured: a window of 75% of the time to go,
 * capped at one hour), so half an hour would throw away reminders that arrived as promised. A late reminder is shown.
 */
val STALE_LIMIT_INEXACT: Duration = Duration.ofMinutes(90)

/** The stale limit for an alarm that was set by [method]. */
fun staleLimitFor(method: AlarmMethod): Duration = if (method == AlarmMethod.INEXACT) STALE_LIMIT_INEXACT else STALE_LIMIT

/** True when an alarm came [lateness] after its planned time and is past the [limit]. Exactly at the limit is still fresh. */
fun isStale(lateness: Duration, limit: Duration = STALE_LIMIT): Boolean = lateness > limit

/** The answer to "what is the next alarm?". */
sealed interface NextReminder {
    data class Due(val alarm: ReminderAlarm) : NextReminder

    /** There are no times for the days that matter, so nothing can be planned: the end of the cached data. */
    data object NoData : NextReminder

    /** Reminders are on but no prayer is selected. */
    data object NothingSelected : NextReminder

    /** Reminders are switched off, so nothing is planned. */
    data object Off : NextReminder
}

/**
 * The earliest alarm strictly after [now], from the times for today and tomorrow in [zone] (read from [days]). Each selected
 * prayer gives an alarm at its time, and when [preMinutes] is above 0 another that many minutes before it. After today's last
 * alarm the next one is tomorrow's first. If neither day has times, or today's are done and tomorrow's are missing, there is
 * nothing to plan ([NextReminder.NoData]): it never guesses from an older day.
 *
 * Pure and clock-free: [now] is given, so it is tested without a phone.
 */
fun nextReminder(days: Map<LocalDate, PrayerDay>, now: Instant, zone: ZoneId, prayers: Set<Prayer>, preMinutes: Int): NextReminder {
    if (prayers.isEmpty()) return NextReminder.NothingSelected
    val today = now.atZone(zone).toLocalDate()
    val candidates = buildList {
        for (date in listOf(today, today.plusDays(1))) {
            val day = days[date] ?: continue
            for (prayer in Prayer.entries.filter { it in prayers }) {
                val time = day.date.atTime(day[prayer]).atZone(zone).toInstant()
                add(ReminderAlarm(ReminderKind.AT, prayer, date, time))
                if (preMinutes > 0) add(ReminderAlarm(ReminderKind.PRE, prayer, date, time.minusSeconds(preMinutes * 60L), prayerAt = time))
            }
        }
    }
    // Ties (a pre-reminder at the same minute as another prayer) go to the earlier prayer, then to the heads-up.
    val next = candidates.filter { it.at > now }.minWithOrNull(compareBy({ it.at }, { it.prayer?.ordinal }, { it.kind }))
    return if (next != null) NextReminder.Due(next) else NextReminder.NoData
}
