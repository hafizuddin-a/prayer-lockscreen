package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import java.time.Duration
import java.time.Instant

/**
 * The line under the list: the next prayer and how long until it, for example `Asar in 2h 14m`. [name] is the prayer's
 * name as it is written on its own day's list (Jumaat on a Friday, English names if chosen). [minutes] is the time left, rounded up, so the
 * line never says `0m` while the prayer has not started: the last minute reads `1m` and then the list moves on.
 */
data class Countdown(val name: String, val minutes: Long) {
    val text: String get() = "$name in ${formatRemaining(minutes)}"
}

/** `2h 14m`, `2h` on the hour, `14m` under an hour. */
fun formatRemaining(minutes: Long): String {
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "${rest}m"
        rest == 0L -> "${hours}h"
        else -> "${hours}h ${rest}m"
    }
}

/**
 * The countdown to the next prayer, as at [now]: the prayer to come, not the one the pill is on. Null when there is nothing to count
 * to: no timetable for today, or the very end of the cached data (no next prayer), or the prayer is already due.
 */
fun PrayerSnapshot.countdown(now: Instant, names: NameSet): Countdown? {
    val next = next ?: return null
    val millis = Duration.between(now, next.at).toMillis()
    if (millis <= 0) return null
    return Countdown(next.day.label(next.prayer, names), (millis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE)
}

private const val MILLIS_PER_MINUTE = 60_000L
