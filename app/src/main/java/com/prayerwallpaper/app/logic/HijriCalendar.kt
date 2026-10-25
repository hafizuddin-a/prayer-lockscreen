package com.prayerwallpaper.app.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * The default for the "Change the date at Maghrib" setting. In the Islamic calendar a day starts at sunset, so after Maghrib
 * the date is already the next day's: the evening before 1 Ramadhan is already 1 Ramadhan. The MUIS calendar lists the
 * Gregorian date on which each month's first (daytime) day falls, so the date is that of the civil day until Maghrib and
 * of the next civil day from Maghrib on. Set it false, or turn the setting off, to move on at midnight instead.
 */
const val HIJRI_DAY_ROLLS_AT_MAGHRIB = true

/** The MUIS spellings, in month order (month number 1 to 12). */
val HIJRI_MONTH_NAMES = listOf(
    "Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir",
    "Rejab", "Syaaban", "Ramadhan", "Syawal", "Zulkaedah", "Zulhijjah",
)

/** The first day of one Hijri month in Singapore, as MUIS publishes it. */
data class HijriMonthStart(val year: Int, val month: Int, val name: String, val start: LocalDate)

/** A date in the MUIS Hijri calendar. [text] is what the wallpaper shows, for example `12 Rabiulakhir 1448H`. */
data class HijriDate(val day: Int, val month: Int, val monthName: String, val year: Int) {
    val text: String get() = "$day $monthName ${year}H"
}

/**
 * The Hijri date in Singapore's own (MUIS) calendar, worked out from a table of month start dates copied from the
 * Muis Calendar. Singapore decides each month by imkan ar-rukyah, so a calculated calendar can be a day off; this
 * class never calculates or estimates anything. If the table does not give a certain answer it gives null, and
 * nothing is drawn.
 *
 * A month is 29 or 30 days long, found from the next month's start. The last month in the table has no next start,
 * so its first 29 days are certain and day 30 is not.
 */
class HijriCalendar(
    months: List<HijriMonthStart>,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val months = months.sortedBy { it.start }

    val isEmpty: Boolean get() = months.isEmpty()

    /**
     * The last date the table gives a certain answer for: the last month start plus 28 days, which is day 29 of that
     * month. Day 30 is not certain, so it is not counted. Null for an empty table.
     */
    val lastKnownDate: LocalDate? get() = months.lastOrNull()?.start?.plusDays(LAST_MONTH_CERTAIN_DAYS - 1L)

    /** The Hijri date for [date] in Singapore, or null when the table does not say for certain. */
    fun on(date: LocalDate): HijriDate? {
        val index = months.indexOfLast { it.start <= date }
        if (index < 0) return null // before the first month start we have
        val month = months[index]
        val day = ChronoUnit.DAYS.between(month.start, date).toInt() + 1
        // A month has at most 30 days. With no next start to say how long this one is, only 29 are certain.
        val certainDays = if (months.getOrNull(index + 1) == null) LAST_MONTH_CERTAIN_DAYS else MAX_MONTH_DAYS
        if (day > certainDays) return null
        return HijriDate(day, month.month, month.name, month.year)
    }

    /**
     * The Hijri date now, with the date taken in Singapore. With [atMaghrib] the date moves on at Maghrib: from
     * [maghrib] (today's Maghrib in Singapore) on, it is tomorrow's. Without a [maghrib] time that case cannot be told
     * apart from the evening, so the answer is null (no line) rather than a guess that may be a day out. With [atMaghrib]
     * false the date moves on at midnight and [maghrib] is ignored.
     */
    fun today(atMaghrib: Boolean = HIJRI_DAY_ROLLS_AT_MAGHRIB, maghrib: LocalTime? = null): HijriDate? {
        val now = ZonedDateTime.now(clock.withZone(SINGAPORE))
        var date = now.toLocalDate()
        if (atMaghrib) {
            if (maghrib == null) return null
            if (now.toLocalTime() >= maghrib) date = date.plusDays(1)
        }
        return on(date)
    }

    /** Whole days from today (Singapore) to [lastKnownDate], negative once it has passed. Null for an empty table. */
    fun daysLeft(): Long? = lastKnownDate?.let { ChronoUnit.DAYS.between(LocalDate.now(clock.withZone(SINGAPORE)), it) }

    companion object {
        private const val LAST_MONTH_CERTAIN_DAYS = 29
        private const val MAX_MONTH_DAYS = 30

        /** Reads the asset that `tools/make-hijri.py` generates. Throws on anything that is not a valid file. */
        fun parse(json: String, clock: Clock = Clock.systemUTC()): HijriCalendar {
            val file = Json { ignoreUnknownKeys = true }.decodeFromString<HijriFile>(json)
            val months = file.months.map {
                require(it.month in 1..12) { "Month ${it.month} is not 1 to 12" }
                HijriMonthStart(it.year, it.month, it.name, LocalDate.parse(it.start))
            }
            return HijriCalendar(months, clock)
        }
    }
}

@Serializable
private class HijriFile(val months: List<HijriRow>)

@Serializable
private class HijriRow(val year: Int, val month: Int, val name: String, val start: String)
