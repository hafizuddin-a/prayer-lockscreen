package com.prayerwallpaper.app.data

import com.prayerwallpaper.app.logic.AdhanCalculator
import com.prayerwallpaper.app.logic.CalcRequest
import com.prayerwallpaper.app.logic.PrayerCalculator
import com.prayerwallpaper.app.logic.PrayerSchedule
import com.prayerwallpaper.app.logic.PrayerSnapshot
import com.prayerwallpaper.app.logic.SINGAPORE
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

/** Which message the wallpaper shows when there is no list for today. */
enum class WhenMissing { LOAD, UPDATE, CANNOT_CALCULATE }

/**
 * Where the list's times come from. The wallpaper, the preview and the debug screen all read this
 * instead of a particular source, so a place with an official timetable and a place whose times are
 * calculated look the same to them. Every date is in [zone], the zone of the place the times are for.
 */
interface Timetable {
    val zone: ZoneId

    /** What to tell the user when [days] has nothing for today. */
    val whenMissing: WhenMissing

    /** The days around [today] (in [zone]) that the schedule may look at. A missing date means no timetable for it. */
    fun days(today: LocalDate): Map<LocalDate, PrayerDay>
}

/** The cached MUIS timetable, Singapore only. */
class MuisTimetable(private val data: PrayerData) : Timetable {
    override val zone: ZoneId = SINGAPORE
    override val whenMissing: WhenMissing get() = if (data.isEmpty) WhenMissing.LOAD else WhenMissing.UPDATE
    override fun days(today: LocalDate): Map<LocalDate, PrayerDay> = data.days()
}

/**
 * Times calculated on the phone for one place, under the choices in [request]. Nothing is downloaded
 * and nothing is stored: the same request always gives the same days, so recent ones are just kept
 * in memory to save working them out on every redraw.
 */
class CalculatedTimetable(
    private val request: CalcRequest,
    private val calculator: PrayerCalculator = AdhanCalculator,
) : Timetable {
    override val zone: ZoneId = request.zone

    // Today and tomorrow, which is all the schedule looks at. A null result is remembered too.
    private val memo = object : LinkedHashMap<LocalDate, PrayerDay?>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, PrayerDay?>) = size > MEMO_DAYS
    }

    override val whenMissing: WhenMissing = WhenMissing.CANNOT_CALCULATE

    @Synchronized
    override fun days(today: LocalDate): Map<LocalDate, PrayerDay> =
        listOf(today, today.plusDays(1)).mapNotNull { date -> day(date)?.let { date to it } }.toMap()

    private fun day(date: LocalDate): PrayerDay? {
        if (memo.containsKey(date)) return memo[date]
        return calculator.calculate(request, date).also { memo[date] = it }
    }

    private companion object {
        const val MEMO_DAYS = 6
    }
}

/** What to show right now. [clock] can be in any zone: it is read in the timetable's own. */
fun Timetable.snapshot(clock: Clock): PrayerSnapshot {
    val local = clock.withZone(zone)
    return PrayerSchedule(days(LocalDate.now(local)), local).snapshot()
}
