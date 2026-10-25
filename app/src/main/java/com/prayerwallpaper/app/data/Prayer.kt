package com.prayerwallpaper.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Which names the list shows: the Malay ones MUIS uses, or English/Arabic ones. */
enum class NameSet { MALAY, ENGLISH }

/**
 * The six timetable entries in daily order. [column] is the column name in the data.gov.sg
 * datasets, which is the MUIS (Malay) name in lower case; it is not what the list shows, see [label].
 */
enum class Prayer(val column: String, private val malay: String, private val english: String) {
    SUBUH("subuh", "Subuh", "Fajr"),
    SYURUK("syuruk", "Syuruk", "Sunrise"),
    ZOHOR("zohor", "Zohor", "Dhuhr"),
    ASAR("asar", "Asar", "Asr"),
    MAGHRIB("maghrib", "Maghrib", "Maghrib"),
    ISYAK("isyak", "Isyak", "Isha");

    /**
     * The name to draw in the list. On a Friday the noon prayer is the Friday prayer, so Zohor reads Jumaat (Malay) and
     * Dhuhr reads Jumuah (English). Its time is the same: only the name changes.
     */
    fun label(names: NameSet, friday: Boolean = false): String = when {
        friday && this == ZOHOR -> if (names == NameSet.MALAY) FRIDAY_MALAY else FRIDAY_ENGLISH
        names == NameSet.MALAY -> malay
        else -> english
    }

    companion object {
        const val FRIDAY_MALAY = "Jumaat"
        const val FRIDAY_ENGLISH = "Jumuah"
    }

    /** Zohor onwards fall after midday (Zohor can be 12:xx). Used to read 12-hour data. */
    val isAfternoon: Boolean get() = ordinal >= ZOHOR.ordinal
}

/** One day's timetable. [times] is in [Prayer] order and strictly increasing. */
data class PrayerDay(val date: LocalDate, val times: List<LocalTime>) {
    init {
        require(times.size == Prayer.entries.size) { "Expected ${Prayer.entries.size} times" }
        require(times.zipWithNext().all { (a, b) -> a < b }) { "Times must be increasing: $times" }
    }

    operator fun get(prayer: Prayer): LocalTime = times[prayer.ordinal]

    /** A Friday in the place's own calendar, which is what the date here is. */
    val isFriday: Boolean get() = date.dayOfWeek == DayOfWeek.FRIDAY

    /** The name of [prayer] on this day: the Friday prayer's name on a Friday. */
    fun label(prayer: Prayer, names: NameSet): String = prayer.label(names, isFriday)
}
