package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.PrayerDay
import java.time.Instant
import java.time.LocalTime

/**
 * The wallpaper is a picture, which a screen reader cannot read, so the settings app also gives today's times as plain text:
 * a prayer's [label] (Jumaat on a Friday, English names if chosen), its time as it would be said, and whether it is the prayer
 * that is on now, the one the wallpaper highlights.
 */
data class TodayRow(val label: String, val time: String, val current: Boolean) {
    /** The row as one phrase for a screen reader, for example `Asar, 4:04 pm, current prayer`. */
    val spoken: String get() = if (current) "$label, $time, current prayer" else "$label, $time"
}

/** `4:04 pm`, or `16:04` in 24-hour time: the whole time, with no small suffix, as a person would say it. */
fun spokenTime(time: LocalTime, is24Hour: Boolean): String = formatClock(time, is24Hour).let { if (it.suffix.isEmpty()) it.time else "${it.time} ${it.suffix}" }

/** The six rows of the list, in order, with the prayer that is on now (the one the snapshot highlights) marked. */
fun todayRows(snapshot: PrayerSnapshot, names: NameSet, is24Hour: Boolean): List<TodayRow> {
    val day: PrayerDay = snapshot.day ?: return emptyList()
    return com.prayerwallpaper.app.data.Prayer.entries.map {
        TodayRow(day.label(it, names), spokenTime(day[it], is24Hour), it == snapshot.highlight)
    }
}

/**
 * The text alternative of the preview: which prayer is next, its time, and how long until it. Without a timetable it says
 * so rather than describe a list that is not there.
 */
fun previewDescription(snapshot: PrayerSnapshot, now: Instant, names: NameSet, is24Hour: Boolean): String {
    val next = snapshot.next ?: return "Preview of the lock screen. There are no prayer times to show yet."
    val left = snapshot.countdown(now, names)?.let { ", in ${formatRemaining(it.minutes)}" }.orEmpty()
    return "Preview of the lock screen: ${next.day.label(next.prayer, names)} at ${spokenTime(next.day[next.prayer], is24Hour)} is the next prayer$left."
}
