package com.prayerwallpaper.app.logic

/** The eight pictures of the moon the Hijri date line chooses between. */
enum class MoonPhase { NEW, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS, FULL, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT }

/**
 * The moon picture for a day of the Hijri month. A Hijri month follows the moon: it begins when the new crescent is
 * sighted and the moon is full around the middle, so the day of the month says roughly where the moon is. This is a
 * picture for the eye, not astronomy: the same days show the same phase every month.
 *
 *     1 to 5    waxing crescent      17 to 20  waning gibbous
 *     6 to 9    first quarter        21 to 23  last quarter
 *     10 to 12  waxing gibbous       24 to 27  waning crescent
 *     13 to 16  full                 28 to 30  new (dark) moon
 */
fun moonPhaseForHijriDay(day: Int): MoonPhase {
    require(day in 1..30) { "A Hijri day is 1 to 30, not $day" }
    return when (day) {
        in 1..5 -> MoonPhase.WAXING_CRESCENT
        in 6..9 -> MoonPhase.FIRST_QUARTER
        in 10..12 -> MoonPhase.WAXING_GIBBOUS
        in 13..16 -> MoonPhase.FULL
        in 17..20 -> MoonPhase.WANING_GIBBOUS
        in 21..23 -> MoonPhase.LAST_QUARTER
        in 24..27 -> MoonPhase.WANING_CRESCENT
        else -> MoonPhase.NEW
    }
}
