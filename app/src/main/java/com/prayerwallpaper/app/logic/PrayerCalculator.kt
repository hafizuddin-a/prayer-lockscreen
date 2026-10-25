package com.prayerwallpaper.app.logic

import com.batoulapps.adhan.CalculationMethod
import com.batoulapps.adhan.CalculationParameters
import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.HighLatitudeRule
import com.batoulapps.adhan.Madhab
import com.batoulapps.adhan.PrayerAdjustments
import com.batoulapps.adhan.PrayerTimes
import com.batoulapps.adhan.data.DateComponents
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import java.time.LocalDate
import java.time.LocalTime

/** Works out one day's six times for a place. An interface so tests and previews can supply their own. */
interface PrayerCalculator {
    /**
     * Null when there is no sensible answer for [date]: the sun does not rise or set there that day, or
     * a prayer falls outside the calendar day (which a one-day list cannot show).
     */
    fun calculate(request: CalcRequest, date: LocalDate): PrayerDay?
}

/**
 * Calculates with the Adhan library (MIT, offline, based on Meeus's astronomical algorithms).
 *
 * Adhan returns instants for a date it reads as a UTC day. For places far from UTC (Hawaii, New
 * Zealand) an event on the local date can come out as the neighbouring UTC date, a day's drift
 * away. So the neighbouring dates are tried too and each prayer is taken from the date whose result
 * lands on the local date asked for.
 */
object AdhanCalculator : PrayerCalculator {
    override fun calculate(request: CalcRequest, date: LocalDate): PrayerDay? {
        val parameters = parametersFor(request)
        val coordinates = Coordinates(request.latitude, request.longitude)

        // Results for the same UTC date first, then the neighbours. A date with no sunrise or sunset has no times at all.
        val candidates = listOf(date, date.minusDays(1), date.plusDays(1)).mapNotNull { utcDate ->
            val found = PrayerTimes(coordinates, DateComponents(utcDate.year, utcDate.monthValue, utcDate.dayOfMonth), parameters)
            if (found.fajr == null) null else listOf(found.fajr, found.sunrise, found.dhuhr, found.asr, found.maghrib, found.isha)
        }

        val times = Prayer.entries.map { prayer ->
            candidates.firstNotNullOfOrNull { found ->
                val local = found[prayer.ordinal].toInstant().atZone(request.zone)
                if (local.toLocalDate() == date) LocalTime.of(local.hour, local.minute) else null
            } ?: return null
        }
        if (times.zipWithNext().any { (a, b) -> a >= b }) return null
        return PrayerDay(date, times)
    }

    internal fun parametersFor(request: CalcRequest): CalculationParameters {
        val parameters = when (request.method) {
            CalcMethod.MUSLIM_WORLD_LEAGUE -> CalculationMethod.MUSLIM_WORLD_LEAGUE.getParameters()
            CalcMethod.EGYPTIAN -> CalculationMethod.EGYPTIAN.getParameters()
            CalcMethod.KARACHI -> CalculationMethod.KARACHI.getParameters()
            CalcMethod.UMM_AL_QURA -> CalculationMethod.UMM_AL_QURA.getParameters()
            CalcMethod.DUBAI -> CalculationMethod.DUBAI.getParameters()
            CalcMethod.KUWAIT -> CalculationMethod.KUWAIT.getParameters()
            CalcMethod.QATAR -> CalculationMethod.QATAR.getParameters()
            CalcMethod.SINGAPORE -> CalculationMethod.SINGAPORE.getParameters()
            CalcMethod.NORTH_AMERICA -> CalculationMethod.NORTH_AMERICA.getParameters()
            CalcMethod.MOONSIGHTING -> CalculationMethod.MOON_SIGHTING_COMMITTEE.getParameters()
            // Adhan 1.2.1 has no Turkey preset. These are the values newer Adhan releases use for it.
            CalcMethod.TURKEY -> CalculationParameters(18.0, 17.0, CalculationMethod.OTHER)
                .withMethodAdjustments(PrayerAdjustments(0, -7, 5, 4, 7, 0))
        }
        parameters.madhab = if (request.asr == AsrRule.HANAFI) Madhab.HANAFI else Madhab.SHAFI
        // Where the sun never gets 18 degrees below the horizon, Fajr and Isha fall back to a share of the night
        // that follows the angle (angle / 60). Unlike a fixed share, it never overrides a time the angle does reach.
        parameters.highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE
        val a = request.adjustments.map { it.coerceIn(-MAX_ADJUSTMENT_MINUTES, MAX_ADJUSTMENT_MINUTES) }
        parameters.adjustments = PrayerAdjustments(a[0], a[1], a[2], a[3], a[4], a[5])
        return parameters
    }
}
