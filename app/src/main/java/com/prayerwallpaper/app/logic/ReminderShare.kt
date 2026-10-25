package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CLOCK_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)

/**
 * The reminder history as plain text for the share sheet, so results can be pasted into a chat: [header] lines (the app, the phone,
 * the settings that matter), the summary numbers, then one line for each alarm of the last 14 days, planned time against actual time.
 * It holds no personal data: no account, no location, only what the log holds. Times are in the place's zone ([zone]).
 */
fun historyShareText(header: List<String>, history: ReminderHistory, zone: ZoneId, names: NameSet, now: Instant): String {
    val s = history.summary()
    val lateness = if (s.medianLatenessSeconds == null) "no lateness recorded" else
        "median ${formatLateness(s.medianLatenessSeconds)} late, worst ${formatLateness(s.worstLatenessSeconds ?: 0)}"
    return buildString {
        appendLine("Prayer reminders: history")
        header.forEach { appendLine(it) }
        appendLine("Last ${HISTORY_DAYS} days up to ${formatHealthTime(now, zone)} (${zone.id})")
        appendLine("Summary: ${s.fired} shown, ${s.blocked} blocked, ${s.stale} too late, ${s.missed} missed, ${s.pending} waiting; $lateness")
        appendLine()
        appendLine("Planned | alarm | how it was set | outcome | arrived | late")
        if (history.entries.isEmpty()) appendLine("(nothing recorded yet)")
        for (e in history.entries) {
            val what = when (e.kind) {
                ReminderKind.AT -> "${e.prayer?.label(names) ?: "-"} at the time"
                ReminderKind.PRE -> "${e.prayer?.label(names) ?: "-"} heads-up"
                ReminderKind.TEST -> "test"
            }
            val method = when (e.method) { AlarmMethod.EXACT -> "exact"; AlarmMethod.INEXACT -> "inexact"; AlarmMethod.ALARM_CLOCK -> "alarm clock" }
            val arrived = e.firedAt?.atZone(zone)?.format(CLOCK_SECONDS) ?: "-"
            val late = e.latenessSeconds?.let { "+${formatLateness(it)}" } ?: "-"
            appendLine("${formatHealthTime(e.plannedAt, zone)} | $what | $method | ${e.outcome.name.lowercase()} | $arrived | $late")
        }
    }
}

/** How long ago an alarm was planned, for the screen's list: `3 h ago`, `2 d ago`. */
fun formatAgo(from: Instant, now: Instant): String {
    val d = Duration.between(from, now)
    return when {
        d.isNegative -> "soon"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 48 -> "${d.toHours()} h ago"
        else -> "${d.toDays()} d ago"
    }
}
