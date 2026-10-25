package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import com.prayerwallpaper.app.data.WhenMissing
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One entry of today's list, in the place's zone. [at] is the instant, [label] the name as the list shows it (Jumaat on a Friday). */
data class WidgetEntry(val prayer: Prayer, val label: String, val date: LocalDate, val time: java.time.LocalTime, val at: Instant)

/** The next prayer to count down to. [tomorrow] is true once today's Isyak has started and the next one is tomorrow's Subuh. */
data class WidgetNext(val entry: WidgetEntry, val tomorrow: Boolean)

/**
 * What the widgets show right now, worked out from the clock alone. [Missing] is no timetable for today: the widgets say so and
 * never show a stale list. [Ready.next] is null only on the last day of the cached data, after Isyak, when tomorrow is not known.
 */
sealed interface WidgetState {
    data class Missing(val reason: WhenMissing) : WidgetState

    data class Ready(
        val date: LocalDate,
        /** Today's six entries, in order. The list never changes before midnight, whatever the time. */
        val entries: List<WidgetEntry>,
        /** The prayer that is on now. Before Subuh that is Isyak (of last night), so the Isyak row is the one marked. */
        val current: Prayer,
        /** True before today's Subuh, when [current] is last night's Isyak. */
        val beforeSubuh: Boolean,
        val next: WidgetNext?,
        /** Tomorrow's entries when known, for a window that runs past Isyak (the large text widgets). */
        val tomorrowEntries: List<WidgetEntry>,
        val hijri: HijriDate?,
        /** False from a minute after Syuruk until Zohor: sunrise is not a prayer, so nothing is marked then. [current] stays Syuruk for the lists. */
        val pillOnCurrent: Boolean = true,
    ) : WidgetState {
        /**
         * The row to mark: the prayer that is on now (Zohor at 2:10 pm, while Asar is the one counted down to), as on the lock screen.
         * Null in the gap after Syuruk, when nothing is marked.
         */
        fun marked(): Prayer? = current.takeIf { pillOnCurrent }
    }
}

private fun entriesOf(day: PrayerDay, zone: ZoneId, names: NameSet): List<WidgetEntry> =
    Prayer.entries.map { WidgetEntry(it, day.label(it, names), day.date, day[it], day.date.atTime(day[it]).atZone(zone).toInstant()) }

/**
 * The state at [now]. [days] is what the timetable has around today, in [zone]. [hijri] is the Hijri date the app decided to show
 * (already null when it is off, not certain, or not Singapore).
 *
 * A prayer that starts exactly now has started: it is the current one. Before Subuh the current one is Isyak of last night, which
 * needs no data from yesterday because the Isyak row of today's list is the one marked. After today's Isyak the next prayer is
 * tomorrow's Subuh if tomorrow is known.
 */
fun widgetState(
    days: Map<LocalDate, PrayerDay>, now: Instant, zone: ZoneId, names: NameSet, hijri: HijriDate?, missing: WhenMissing,
): WidgetState {
    val today = now.atZone(zone).toLocalDate()
    val day = days[today] ?: return WidgetState.Missing(missing)
    val entries = entriesOf(day, zone, names)
    val tomorrow = days[today.plusDays(1)]?.let { entriesOf(it, zone, names) }.orEmpty()
    val current = entries.lastOrNull { it.at <= now }?.prayer ?: Prayer.ISYAK
    val syurukOver = current == Prayer.SYURUK && now >= entries.first { it.prayer == Prayer.SYURUK }.at.plus(SYURUK_PILL_DURATION)
    val next = entries.firstOrNull { it.at > now }?.let { WidgetNext(it, tomorrow = false) }
        ?: tomorrow.firstOrNull()?.let { WidgetNext(it, tomorrow = true) }
    return WidgetState.Ready(today, entries, current, entries.first().at > now, next, tomorrow, hijri, pillOnCurrent = !syurukOver)
}

/** The widgets are refreshed this long after a boundary by the safety alarm, if nothing refreshed them sooner. */
val WIDGET_SAFETY_DELAY: Duration = Duration.ofMinutes(2)

/** The moments in a day's list at which the picture changes: each prayer's start, and a minute after Syuruk's, when its pill goes. */
private fun changeTimes(entries: List<WidgetEntry>): List<Instant> =
    entries.flatMap { if (it.prayer == Prayer.SYURUK) listOf(it.at, it.at.plus(SYURUK_PILL_DURATION)) else listOf(it.at) }

/**
 * The next moment the widgets' picture changes: the next entry time (today's, or tomorrow's Subuh once today's are all past), the minute
 * after Syuruk's start, or the next midnight in [zone], whichever is first. Everything else the widgets show moves on its own (the countdown ticks inside
 * the launcher). Always after [now].
 */
fun nextWidgetBoundary(days: Map<LocalDate, PrayerDay>, now: Instant, zone: ZoneId): Instant {
    val today = now.atZone(zone).toLocalDate()
    val midnight = nextMidnight(now, zone)
    val nextEntry = listOf(today, today.plusDays(1)).asSequence()
        .mapNotNull { days[it] }
        .flatMap { changeTimes(entriesOf(it, zone, NameSet.MALAY)).asSequence() }
        .firstOrNull { it > now }
    return earliest(nextEntry, midnight)!!
}

/**
 * The latest boundary at or before [now]: the last entry time that has passed today, or this morning's midnight. A widget last
 * updated before this is showing the picture of an earlier moment.
 */
fun lastWidgetBoundary(days: Map<LocalDate, PrayerDay>, now: Instant, zone: ZoneId): Instant {
    val today = now.atZone(zone).toLocalDate()
    val midnight = today.atStartOfDay(zone).toInstant()
    val lastEntry = days[today]?.let { changeTimes(entriesOf(it, zone, NameSet.MALAY)) }?.lastOrNull { it <= now }
    return listOfNotNull(midnight, lastEntry).max()
}

/**
 * The base for a countdown `Chronometer`: the elapsed-realtime moment at which it reads zero. [elapsedNow] is
 * `SystemClock.elapsedRealtime()` read at the same moment as [now], so the launcher counts down on its own with no more
 * updates from this app. The clock the launcher counts with never jumps when the phone's wall clock is changed.
 */
fun chronometerBase(now: Instant, target: Instant, elapsedNow: Long): Long = elapsedNow + Duration.between(now, target).toMillis()
