package com.prayerwallpaper.app.logic

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One boundary and when the widgets were first redrawn for it. [plannedAt] is the boundary (a prayer time, or midnight), [updatedAt] the
 * moment the widgets were next updated, whatever caused it ([reason]: the boundary alarm, the safety alarm, the wallpaper becoming
 * visible, the app opening...). [lateness] is how long the widgets showed the picture of the moment before.
 */
data class WidgetUpdateEntry(val plannedAt: Instant, val updatedAt: Instant, val reason: String) {
    val lateness: Duration get() = Duration.between(plannedAt, updatedAt).coerceAtLeast(Duration.ZERO)
    val latenessSeconds: Long get() = lateness.seconds
}

/** The reasons whose updates say nothing about how well alarms work: the phone was off or the app replaced, not an alarm that was late. */
val WIDGET_REASONS_NOT_COUNTED = setOf("boot", "app updated", "time changed", "time zone changed")

/** The numbers over the last 14 days: how many boundaries, and the median and worst lateness in seconds (null when there are none). */
data class WidgetSummary(val count: Int, val medianSeconds: Long?, val worstSeconds: Long?)

/**
 * The record behind the widget health row. It is made from the updates themselves and so says what the user saw, however the update
 * came: the first update after a boundary is the one that counts for it. [lastUpdate] is when any update last ran.
 */
data class WidgetHistory(val lastUpdate: Instant? = null, val entries: List<WidgetUpdateEntry> = emptyList(), val envKey: String? = null) {
    /**
     * The history after an update at [now]. [lastBoundary] is the latest boundary at or before [now]. If the widgets were last
     * updated before it, this update is the first for that boundary and is recorded, once. The first update ever is not (there was no
     * earlier picture), and an update with the same boundary as the last record is not recorded twice. [envKey] says what the update was
     * laid out for (font scale, language, clock style): when it changes, the widgets are laid out again.
     */
    fun afterUpdate(now: Instant, lastBoundary: Instant, reason: String, envKey: String? = this.envKey): WidgetHistory {
        val previous = lastUpdate
        val firstForBoundary = previous != null && previous < lastBoundary && entries.none { it.plannedAt == lastBoundary }
        val added = if (firstForBoundary) entries + WidgetUpdateEntry(lastBoundary, now, reason) else entries
        val cutoff = now.minus(Duration.ofDays(WIDGET_HISTORY_DAYS))
        return WidgetHistory(now, added.filter { it.plannedAt >= cutoff }, envKey)
    }

    fun summary(): WidgetSummary {
        val counted = entries.filter { it.reason !in WIDGET_REASONS_NOT_COUNTED }.map { it.latenessSeconds }.sorted()
        if (counted.isEmpty()) return WidgetSummary(0, null, null)
        val median = if (counted.size % 2 == 1) counted[counted.size / 2] else (counted[counted.size / 2 - 1] + counted[counted.size / 2]) / 2
        return WidgetSummary(counted.size, median, counted.last())
    }
}

const val WIDGET_HISTORY_DAYS = 14L

private val CLOCK_SECONDS = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH)

/**
 * The widget history as plain text for the share sheet: [header] lines, the summary, then one line per boundary of the last 14 days,
 * planned time against the time the widgets were redrawn. No personal data: only times and the reason for each update.
 */
fun widgetShareText(header: List<String>, history: WidgetHistory, nextBoundary: Instant?, zone: ZoneId, now: Instant): String {
    val s = history.summary()
    val lateness = if (s.medianSeconds == null) "no lateness recorded" else "median ${formatLateness(s.medianSeconds)} late, worst ${formatLateness(s.worstSeconds ?: 0)}"
    return buildString {
        appendLine("Prayer widgets: update history")
        header.forEach { appendLine(it) }
        appendLine("Last $WIDGET_HISTORY_DAYS days up to ${formatHealthTime(now, zone)} (${zone.id})")
        appendLine("Last update: ${history.lastUpdate?.let { formatHealthTime(it, zone) } ?: "never"}; next boundary: ${nextBoundary?.let { formatHealthTime(it, zone) } ?: "none"}")
        appendLine("Summary: ${s.count} boundaries; $lateness")
        appendLine()
        appendLine("Boundary | redrawn | late | because")
        if (history.entries.isEmpty()) appendLine("(nothing recorded yet)")
        for (e in history.entries) {
            val counted = if (e.reason in WIDGET_REASONS_NOT_COUNTED) " (not counted)" else ""
            appendLine("${formatHealthTime(e.plannedAt, zone)} | ${e.updatedAt.atZone(zone).format(CLOCK_SECONDS)} | +${formatLateness(e.latenessSeconds)} | ${e.reason}$counted")
        }
    }
}
