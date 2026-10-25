package com.prayerwallpaper.app.logic

import java.time.ZoneId

/**
 * The calculation methods on offer for places without an official timetable. [title] is what the
 * settings list shows; [detail] says what the method is based on.
 */
enum class CalcMethod(val title: String, val detail: String) {
    MUSLIM_WORLD_LEAGUE("Muslim World League", "Fajr 18°, Isha 17°. A common default worldwide."),
    EGYPTIAN("Egyptian General Authority of Survey", "Fajr 19.5°, Isha 17.5°."),
    KARACHI("University of Islamic Sciences, Karachi", "Fajr 18°, Isha 18°."),
    UMM_AL_QURA("Umm al-Qura, Makkah", "Fajr 18.5°, Isha 90 minutes after Maghrib."),
    DUBAI("Dubai", "Fajr 18.2°, Isha 18.2°."),
    KUWAIT("Kuwait", "Fajr 18°, Isha 17.5°."),
    QATAR("Qatar", "Fajr 18°, Isha 90 minutes after Maghrib."),
    SINGAPORE("Singapore, Malaysia, Indonesia", "Fajr 20°, Isha 18°."),
    TURKEY("Turkey (Diyanet)", "Fajr 18°, Isha 17°, with Diyanet's minute offsets."),
    NORTH_AMERICA("North America (ISNA)", "Fajr 15°, Isha 15°."),
    MOONSIGHTING("Moonsighting Committee", "Fajr 18°, Isha 18°, with seasonal adjustment."),
}

/** Which shadow length ends Zohor and starts Asar. */
enum class AsrRule(val title: String, val detail: String) {
    STANDARD("Standard", "Shafi'i, Maliki and Hanbali: the shadow is as long as the object."),
    HANAFI("Hanafi", "The shadow is twice as long as the object, so Asar is later."),
}

/** How far the user may nudge one prayer, in minutes, either way. */
const val MAX_ADJUSTMENT_MINUTES = 30

/**
 * Everything the calculation depends on, as a value. Two equal requests give the same six times on
 * the same date, so a result can be kept under this key. [adjustments] has one entry per
 * [com.prayerwallpaper.app.data.Prayer], in order, in minutes.
 */
data class CalcRequest(
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
    val method: CalcMethod,
    val asr: AsrRule,
    val adjustments: List<Int>,
) {
    init {
        require(adjustments.size == 6) { "One adjustment per prayer" }
    }
}
