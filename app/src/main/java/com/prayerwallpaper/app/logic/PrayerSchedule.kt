package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Syuruk is sunrise, not a prayer. Its row gets the pill only for [SYURUK_PILL_DURATION] from the moment it starts; after that nothing is
 * marked until Zohor starts, and Zohor is marked then. Set this to false to never mark Syuruk (Subuh then stays marked until Zohor).
 */
const val HIGHLIGHT_SYURUK = true

val SYURUK_PILL_DURATION: Duration = Duration.ofMinutes(1)

val SINGAPORE: ZoneId = ZoneId.of("Asia/Singapore")

/** The prayer that comes next and when it starts. [day] is the day it belongs to, which names it (Jumaat on a Friday). */
data class NextPrayer(val day: PrayerDay, val prayer: Prayer, val at: Instant)

/**
 * What the wallpaper shows. [day] is today's timetable, the list for the whole calendar day. [highlight] is the prayer that is on
 * now, the row that gets the pill (the same rule as the home screen widgets): the latest one that has started, and before today's
 * first prayer the last row, last night's Isyak. It is null from a minute after Syuruk until Zohor, because sunrise is not a prayer. [next] is the prayer to come, for the countdown line: today's next one, or
 * tomorrow's first once today's have all passed. [nextChange] is when the picture next changes: the next prayer's time, or midnight
 * once all of today's have passed. [day] is null when the cache has no timetable for today, and [next] is null only at the very
 * end of the cached data, after Isyak, when tomorrow is not known.
 */
data class PrayerSnapshot(val day: PrayerDay?, val highlight: Prayer?, val nextChange: Instant?, val next: NextPrayer? = null)

class PrayerSchedule(
    private val days: Map<LocalDate, PrayerDay>,
    private val clock: Clock,
    private val highlightSyuruk: Boolean = HIGHLIGHT_SYURUK,
) {
    private class Entry(val prayer: Prayer, val at: Instant)

    fun snapshot(): PrayerSnapshot {
        val now = ZonedDateTime.now(clock)
        val date = now.toLocalDate()
        val today = days[date] ?: return PrayerSnapshot(null, null, null)

        // The pill is on the prayer that is on now. A prayer that starts now has already started. Before the first one of the day it is
        // still last night's last prayer, which needs no data from yesterday because it is the last row of today's list.
        val instant = now.toInstant()
        val entries = entriesOf(today, now.zone)
        val current = entries.lastOrNull { it.at <= instant } ?: entries.last()
        // Sunrise is marked for a minute only, then nothing is until Zohor.
        val pillEnds = if (current.prayer == Prayer.SYURUK) current.at.plus(SYURUK_PILL_DURATION) else null
        val pill = if (pillEnds != null && instant >= pillEnds) null else current.prayer

        // The prayer to come: today's next one, or tomorrow's first once today's have passed (unknown at the end of the data).
        val upcoming = entries.firstOrNull { it.at > instant }
        val next = upcoming?.let { NextPrayer(today, it.prayer, it.at) }
            ?: days[date.plusDays(1)]?.let { tomorrow -> entriesOf(tomorrow, now.zone).first().let { NextPrayer(tomorrow, it.prayer, it.at) } }

        // The picture changes when the next prayer starts, or at midnight, when the list becomes the new day's.
        val nextChange = earliest(upcoming?.at ?: date.plusDays(1).atStartOfDay(now.zone).toInstant(), pillEnds?.takeIf { instant < it })
        return PrayerSnapshot(today, pill, nextChange, next)
    }

    private fun entriesOf(day: PrayerDay, zone: ZoneId): List<Entry> =
        Prayer.entries
            .filter { highlightSyuruk || it != Prayer.SYURUK }
            .map { Entry(it, day.date.atTime(day[it]).atZone(zone).toInstant()) }
}

/** The next 00:00 in [zone] strictly after [now]: when the calendar date, and with it the Hijri date, changes. */
fun nextMidnight(now: Instant, zone: ZoneId): Instant = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

/** The earlier of two optional instants, or the one that exists. */
fun earliest(a: Instant?, b: Instant?): Instant? = when {
    a == null -> b
    b == null -> a
    else -> minOf(a, b)
}

/**
 * The start of the next whole minute after [now], strictly: when the countdown line's minutes next change. Every prayer time
 * is on a whole minute, so redrawing on a whole minute always shows a whole number of minutes left.
 */
fun nextMinuteStart(now: Instant): Instant {
    val minute = java.time.Duration.ofMinutes(1).toMillis()
    return Instant.ofEpochMilli((Math.floorDiv(now.toEpochMilli(), minute) + 1) * minute)
}

/** Slack after a boundary, so the redraw lands once the new state is certainly in force. */
const val REDRAW_SLACK_MS = 500L

/** How long to wait before redrawing for [next], or null when nothing is due. Never negative. */
fun redrawDelayMs(next: Instant?, now: Instant): Long? =
    next?.let { maxOf(java.time.Duration.between(now, it).toMillis(), 0L) + REDRAW_SLACK_MS }
