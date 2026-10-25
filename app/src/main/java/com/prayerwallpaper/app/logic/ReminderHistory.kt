package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.Prayer
import java.time.Duration
import java.time.Instant

/** How an alarm was handed to Android. */
enum class AlarmMethod { EXACT, INEXACT, ALARM_CLOCK }

/**
 * What became of an alarm. PENDING: planned, not yet due. FIRED: delivered and shown. BLOCKED: delivered but notifications
 * were off. STALE: delivered too late to be worth showing. MISSED: its time passed long ago and it never arrived.
 */
enum class ReminderOutcome { PENDING, FIRED, BLOCKED, STALE, MISSED }

/** One planned alarm and its fate. [firedAt] is when Android actually delivered it; it is null until then, and for MISSED. */
data class HistoryEntry(
    val plannedAt: Instant,
    val kind: ReminderKind,
    val prayer: Prayer?,
    val method: AlarmMethod,
    val outcome: ReminderOutcome = ReminderOutcome.PENDING,
    val firedAt: Instant? = null,
    /** The prayer time itself, which a pre-reminder's notification names. Null in older logs, where it is taken to be [plannedAt]. */
    val prayerAtOrNull: Instant? = null,
) {
    val prayerAt: Instant get() = prayerAtOrNull ?: plannedAt

    val key: String get() = "${plannedAt.toEpochMilli()}/${kind.name}/${prayer?.name ?: "-"}"

    /** Seconds from the planned time to the delivery, never below 0. Null for an alarm that has not been delivered. */
    val latenessSeconds: Long? get() = firedAt?.let { maxOf(0L, Duration.between(plannedAt, it).seconds) }
}

/** The numbers the health screen shows. Lateness covers every alarm that was delivered, shown or not, since it measures Android. */
data class HistorySummary(
    val fired: Int, val blocked: Int, val stale: Int, val missed: Int, val pending: Int,
    val medianLatenessSeconds: Long?, val worstLatenessSeconds: Long?,
)

/** How many days of alarms are kept. */
const val HISTORY_DAYS = 14L

/**
 * The reminder log: every alarm the app planned in the last [HISTORY_DAYS] days, and what became of it. Immutable; each change
 * returns a new log. It holds no personal data, only planned and actual times, and never leaves the phone unless the user shares it.
 */
class ReminderHistory(entries: List<HistoryEntry> = emptyList()) {
    val entries: List<HistoryEntry> = entries.sortedBy { it.plannedAt }

    /**
     * Records [entry] as planned, and drops the earlier plans for alarms that have not come due yet: only the next alarm is ever
     * planned, so a new plan replaces the old one. An alarm that is already overdue stays until it arrives or is marked missed.
     */
    fun planned(entry: HistoryEntry, now: Instant): ReminderHistory =
        ReminderHistory(entries.filterNot { it.key == entry.key || it.isFuturePlan(now) } + entry)

    /** A plan not yet due that the chain may replace. The health screen's test reminder is not part of the chain and is left alone. */
    private fun HistoryEntry.isFuturePlan(now: Instant) = outcome == ReminderOutcome.PENDING && plannedAt > now && kind != ReminderKind.TEST

    /** Forgets every alarm still waiting for its time: reminders were turned off, or nothing can be planned. */
    fun withoutFuturePending(now: Instant, includeTests: Boolean = false): ReminderHistory =
        ReminderHistory(entries.filterNot { it.outcome == ReminderOutcome.PENDING && it.plannedAt > now && (includeTests || it.kind != ReminderKind.TEST) })

    /** Records that the alarm [key] was delivered at [firedAt] and what was done with it. An unknown alarm is added, so nothing is lost. */
    fun delivered(entry: HistoryEntry, firedAt: Instant, outcome: ReminderOutcome): ReminderHistory {
        val updated = entry.copy(outcome = outcome, firedAt = firedAt)
        return ReminderHistory(entries.filterNot { it.key == entry.key } + updated)
    }

    /** The entry for [key], if any. */
    fun find(key: String): HistoryEntry? = entries.firstOrNull { it.key == key }

    /**
     * Marks as missed every alarm still pending whose time passed more than its stale limit ago ([limitFor]: 30 minutes for an exact
     * alarm, 90 for an inexact one): it can no longer arrive usefully.
     */
    fun markMissed(now: Instant, limitFor: (HistoryEntry) -> Duration = { staleLimitFor(it.method) }): ReminderHistory =
        ReminderHistory(entries.map {
            if (it.outcome == ReminderOutcome.PENDING && Duration.between(it.plannedAt, now) > limitFor(it)) it.copy(outcome = ReminderOutcome.MISSED) else it
        })

    /** Drops what is older than [HISTORY_DAYS] days. */
    fun prune(now: Instant): ReminderHistory {
        val oldest = now.minus(Duration.ofDays(HISTORY_DAYS))
        return ReminderHistory(entries.filter { it.plannedAt >= oldest })
    }

    /** The counts, and the median and worst lateness, over everything in the log. */
    fun summary(): HistorySummary {
        fun count(outcome: ReminderOutcome) = entries.count { it.outcome == outcome }
        val lateness = entries.mapNotNull { it.latenessSeconds }.sorted()
        val median = when {
            lateness.isEmpty() -> null
            lateness.size % 2 == 1 -> lateness[lateness.size / 2]
            else -> (lateness[lateness.size / 2 - 1] + lateness[lateness.size / 2] + 1) / 2
        }
        return HistorySummary(
            count(ReminderOutcome.FIRED), count(ReminderOutcome.BLOCKED), count(ReminderOutcome.STALE), count(ReminderOutcome.MISSED),
            count(ReminderOutcome.PENDING), median, lateness.lastOrNull(),
        )
    }
}
