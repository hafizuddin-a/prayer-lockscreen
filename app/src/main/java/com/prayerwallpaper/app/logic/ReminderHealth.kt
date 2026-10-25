package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a row of the reminder health screen is doing. Always written out as a word too, never shown by colour alone. */
enum class HealthLevel(val word: String) { OK("OK"), WARNING("Warning"), PROBLEM("Problem") }

/** What a row's button can do. The screen turns each into the documented system screen, or the app's own action. */
enum class HealthFix { NOTIFICATION_SETTINGS, EXACT_ALARM_SETTINGS, BATTERY_SETTINGS, RESET_REMINDERS }

enum class HealthRowId { NOTIFICATIONS, EXACT_ALARMS, NEXT_ALARM, BATTERY, DATA, HISTORY }

/** One row: [detail] is a plain sentence, [fix] a button label's action when there is one, and [affectsStatus] whether it can turn "All good" into "Needs attention". */
data class HealthRow(val id: HealthRowId, val title: String, val level: HealthLevel, val detail: String, val fix: HealthFix? = null, val affectsStatus: Boolean = false)

/** The one line shown next to the Reminders setting. */
data class ReminderStatus(val allGood: Boolean, val text: String)

data class HealthReport(val rows: List<HealthRow>, val status: ReminderStatus)

/** What the health screen asks Android about the data, in plain values, so the rules can be tested. */
data class DataHealth(val usesMuis: Boolean, val fetchedAt: Instant?, val coveredThrough: LocalDate?)

/** The standby bucket numbers of `UsageStatsManager`, which are fixed by Android. */
object StandbyBucket {
    const val ACTIVE = 10
    const val WORKING_SET = 20
    const val FREQUENT = 30
    const val RARE = 40
    const val RESTRICTED = 45

    fun name(bucket: Int?): String = when {
        bucket == null -> "unknown"
        bucket <= ACTIVE -> "Active"
        bucket <= WORKING_SET -> "Working set"
        bucket <= FREQUENT -> "Frequent"
        bucket <= RARE -> "Rare"
        else -> "Restricted"
    }
}

/** Everything the rules need, gathered by the screen. A null from Android (it could not be read) is shown as "unknown", never as fine. */
data class HealthInput(
    val remindersOn: Boolean,
    val notificationsAllowed: Boolean,
    /** The importance of the channel reminders use, or null while the channel does not exist yet. 0 is switched off by the user. */
    val channelImportance: Int?,
    val silentReminders: Boolean,
    /** False in a build made with `-PexactAlarms=false`: then reminders are approximate by design. */
    val exactAlarmsInBuild: Boolean,
    val exactAllowed: Boolean,
    val next: NextReminder,
    val nextMethod: AlarmMethod?,
    /** Whether the pending intent of the next alarm exists. Android cannot say whether the alarm itself is still registered. */
    val nextPendingIntentExists: Boolean,
    val batteryOptimised: Boolean?,
    val backgroundRestricted: Boolean?,
    val standbyBucket: Int?,
    val data: DataHealth,
    val history: ReminderHistory,
    /** The prayer names the app shows, so the next reminder is named as the list names it. */
    val names: NameSet = NameSet.MALAY,
)

/** Alarms missed, and so reported, within this many days of now. */
const val MISSED_LOOKBACK_DAYS = 7L

private val DAY_TIME = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH)

/** How a time reads in the health screen and its share text: always in the place's zone. */
fun formatHealthTime(at: Instant, zone: ZoneId): String = DAY_TIME.format(at.atZone(zone))

/** `5 s`, `2 min 1 s` or `1 h 2 min`. For lateness. */
fun formatLateness(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min" + if (seconds % 60 != 0L) " ${seconds % 60} s" else ""
    else -> "${seconds / 3600} h" + if (seconds % 3600 >= 60) " ${(seconds % 3600) / 60} min" else ""
}

/**
 * Works out the rows of the reminder health screen and the one-line status. The rules: notifications must be allowed and the
 * channel on; exact alarms allowed (or approximate by design in a build without them); the next alarm must be set; background
 * activity must not be restricted; and no alarm may have been missed in the last [MISSED_LOOKBACK_DAYS] days. "All good" needs all of
 * those; otherwise it is "Needs attention" and the first problem is named. Battery optimisation, a low standby bucket, short data
 * and blocked reminders are shown as warnings but do not change the status on their own.
 */
fun healthReport(input: HealthInput, now: Instant, zone: ZoneId, bucketName: (Int?) -> String = StandbyBucket::name): HealthReport {
    val rows = listOf(notificationRow(input), exactRow(input), nextRow(input, zone), batteryRow(input, bucketName), dataRow(input, now, zone), historyRow(input, now))
    val problem = if (!input.remindersOn) null else rows.firstOrNull { it.affectsStatus && it.level != HealthLevel.OK }
    val status = when {
        !input.remindersOn -> ReminderStatus(false, "Off")
        problem == null -> ReminderStatus(true, "All good")
        else -> ReminderStatus(false, "Needs attention: ${problem.headline()}")
    }
    return HealthReport(rows, status)
}

/** The first problem as a short phrase for the status line. */
private fun HealthRow.headline(): String = when (id) {
    HealthRowId.NOTIFICATIONS -> if (level == HealthLevel.PROBLEM) "notifications are off" else "notifications are muted"
    HealthRowId.EXACT_ALARMS -> "exact alarms are not allowed, so reminders can be up to an hour late"
    HealthRowId.NEXT_ALARM -> "the next reminder is not set"
    HealthRowId.BATTERY -> "background activity is restricted"
    HealthRowId.DATA -> "prayer times are missing"
    HealthRowId.HISTORY -> "a reminder never arrived"
}

private fun notificationRow(i: HealthInput): HealthRow {
    val title = "Notifications"
    return when {
        !i.notificationsAllowed ->
            HealthRow(HealthRowId.NOTIFICATIONS, title, HealthLevel.PROBLEM, "Notifications are off, so reminders can't show.", HealthFix.NOTIFICATION_SETTINGS, true)
        i.channelImportance == 0 ->
            HealthRow(HealthRowId.NOTIFICATIONS, title, HealthLevel.PROBLEM, "Reminders are switched off in Android's settings.", HealthFix.NOTIFICATION_SETTINGS, true)
        i.channelImportance != null && i.channelImportance in 1..2 ->
            HealthRow(HealthRowId.NOTIFICATIONS, title, HealthLevel.WARNING, "Reminders are set to low importance, so they have no sound or vibration.", HealthFix.NOTIFICATION_SETTINGS, true)
        else -> HealthRow(HealthRowId.NOTIFICATIONS, title, HealthLevel.OK, "Allowed, and the reminder channel is on.")
    }
}

private fun exactRow(i: HealthInput): HealthRow {
    val title = "Exact alarms"
    return when {
        !i.exactAlarmsInBuild ->
            HealthRow(HealthRowId.EXACT_ALARMS, title, HealthLevel.OK, "Approximate times: reminders can be up to an hour late.")
        i.exactAllowed -> HealthRow(HealthRowId.EXACT_ALARMS, title, HealthLevel.OK, "Allowed: reminders arrive at the exact minute.")
        else -> HealthRow(
            HealthRowId.EXACT_ALARMS, title, HealthLevel.WARNING,
            "Not allowed: reminders can be up to an hour late. Allow it for the exact minute.", HealthFix.EXACT_ALARM_SETTINGS, true,
        )
    }
}

private fun nextRow(i: HealthInput, zone: ZoneId): HealthRow {
    val title = "Next reminder"
    if (!i.remindersOn) return HealthRow(HealthRowId.NEXT_ALARM, title, HealthLevel.OK, "Reminders are off.")
    return when (val next = i.next) {
        is NextReminder.Due -> {
            val a = next.alarm
            val what = when (a.kind) { ReminderKind.AT -> "at the time"; ReminderKind.PRE -> "heads-up"; ReminderKind.TEST -> "test" }
            val method = when (i.nextMethod) { AlarmMethod.EXACT -> "exact"; AlarmMethod.INEXACT -> "approximate"; AlarmMethod.ALARM_CLOCK -> "alarm clock"; null -> "not set" }
            val line = "${a.prayer?.let { "${it.label(i.names)} " }.orEmpty()}${formatHealthTime(a.at, zone)}, $what, $method."
            if (i.nextPendingIntentExists) HealthRow(HealthRowId.NEXT_ALARM, title, HealthLevel.OK, "$line The alarm is set.")
            else HealthRow(HealthRowId.NEXT_ALARM, title, HealthLevel.PROBLEM, "$line It is not set. Reset reminders to set it again.", HealthFix.RESET_REMINDERS, true)
        }
        NextReminder.NoData ->
            HealthRow(HealthRowId.NEXT_ALARM, title, HealthLevel.WARNING, "No times for tomorrow yet. Reminders restart when they arrive.")
        NextReminder.NothingSelected ->
            HealthRow(HealthRowId.NEXT_ALARM, title, HealthLevel.WARNING, "No prayer chosen. Choose at least one.")
        NextReminder.Off -> HealthRow(HealthRowId.NEXT_ALARM, title, HealthLevel.OK, "Reminders are off.")
    }
}

private fun batteryRow(i: HealthInput, bucketName: (Int?) -> String): HealthRow {
    val title = "Battery"
    val optimised = when (i.batteryOptimised) { true -> "battery optimisation is on"; false -> "not battery optimised"; null -> "battery optimisation unknown" }
    val restricted = when (i.backgroundRestricted) { true -> "background activity is restricted"; false -> "background activity is allowed"; null -> "background restriction unknown" }
    val bucket = "standby bucket ${bucketName(i.standbyBucket)}"
    val detail = "${optimised.replaceFirstChar { it.uppercase() }}; $restricted; $bucket."
    return when {
        i.backgroundRestricted == true ->
            HealthRow(HealthRowId.BATTERY, title, HealthLevel.PROBLEM, "$detail Android may stop alarms from reaching the app.", HealthFix.BATTERY_SETTINGS, true)
        i.batteryOptimised == true || (i.standbyBucket ?: 0) >= StandbyBucket.RARE ->
            HealthRow(HealthRowId.BATTERY, title, HealthLevel.WARNING, "$detail Exact alarms still arrive, but battery rules can delay them.", HealthFix.BATTERY_SETTINGS)
        else -> HealthRow(HealthRowId.BATTERY, title, HealthLevel.OK, detail)
    }
}

private fun dataRow(i: HealthInput, now: Instant, zone: ZoneId): HealthRow {
    val title = "Prayer times"
    val d = i.data
    if (!d.usesMuis) return HealthRow(HealthRowId.DATA, title, HealthLevel.OK, "Calculated on this phone. Nothing to download.")
    val fetched = d.fetchedAt?.let { "Last downloaded ${formatHealthTime(it, zone)}" } ?: "Nothing downloaded yet"
    val covered = d.coveredThrough
    val today = now.atZone(zone).toLocalDate()
    return when {
        covered == null -> HealthRow(HealthRowId.DATA, title, HealthLevel.WARNING, "$fetched. Open the app to download the prayer times.")
        covered < today.plusDays(1) -> HealthRow(HealthRowId.DATA, title, HealthLevel.WARNING, "$fetched; they cover up to $covered. Reminders stop when they run out.")
        else -> HealthRow(HealthRowId.DATA, title, HealthLevel.OK, "$fetched; they cover up to $covered.")
    }
}

private fun historyRow(i: HealthInput, now: Instant): HealthRow {
    val title = "History"
    val s = i.history.summary()
    val recentMissed = i.history.entries.count { it.outcome == ReminderOutcome.MISSED && it.plannedAt >= now.minus(Duration.ofDays(MISSED_LOOKBACK_DAYS)) }
    val counts = "${s.fired} shown, ${s.blocked} blocked, ${s.stale} too late, ${s.missed} missed"
    val lateness = if (s.medianLatenessSeconds == null) "" else "; median ${formatLateness(s.medianLatenessSeconds)} late, worst ${formatLateness(s.worstLatenessSeconds ?: 0)}"
    return when {
        recentMissed > 0 -> HealthRow(HealthRowId.HISTORY, title, HealthLevel.PROBLEM, "$recentMissed in the last $MISSED_LOOKBACK_DAYS days never arrived. $counts$lateness.", HealthFix.RESET_REMINDERS, true)
        s.blocked > 0 || s.stale > 0 -> HealthRow(HealthRowId.HISTORY, title, HealthLevel.WARNING, "$counts$lateness.")
        else -> HealthRow(HealthRowId.HISTORY, title, HealthLevel.OK, if (i.history.entries.isEmpty()) "Nothing recorded yet." else "$counts$lateness.")
    }
}

/** What the turn-on flow asks for next. */
enum class PermissionNeed { NOTIFICATIONS, EXACT_ALARMS, NONE }

/**
 * The turn-on flow: notifications first (without them reminders cannot show), then exact alarms (without them reminders still work,
 * only late). [exactAlreadyAsked] is true once the person has been taken to the exact alarm screen in this flow, so a "no" is not
 * asked again. Nothing here ever asks for the battery exemption.
 */
fun nextPermissionNeed(notificationsAllowed: Boolean, exactAllowed: Boolean, exactAlarmsInBuild: Boolean, exactAlreadyAsked: Boolean): PermissionNeed = when {
    !notificationsAllowed -> PermissionNeed.NOTIFICATIONS
    exactAlarmsInBuild && !exactAllowed && !exactAlreadyAsked -> PermissionNeed.EXACT_ALARMS
    else -> PermissionNeed.NONE
}
