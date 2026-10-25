package com.prayerwallpaper.app.data

import java.time.ZoneId
import java.util.Locale

/**
 * A place the list is for: a city from the bundled list, or the default, Singapore. It is stored
 * whole in the settings, so the wallpaper never has to open the city list to draw.
 *
 * [country] is the ISO 3166-1 alpha-2 code. [region] is a short state or province code where the
 * city list has one (a US state, say), to tell apart cities with the same name; otherwise blank.
 */
data class Place(
    val id: Long,
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val zoneId: String,
) {
    val zone: ZoneId get() = ZoneId.of(zoneId)

    /** Singapore is the one place with an official timetable in the app. Every other place is calculated. */
    val usesMuis: Boolean get() = country == SINGAPORE_COUNTRY

    /** For example "Springfield, MO, United States". */
    fun displayName(): String = listOf(name, region, countryName(country)).filter { it.isNotBlank() }.joinToString(", ")

    companion object {
        const val SINGAPORE_COUNTRY = "SG"

        /** GeoNames id 1880252. The default place, so a phone that never opens the place screen behaves as before. */
        val SINGAPORE = Place(1880252, "Singapore", "", SINGAPORE_COUNTRY, 1.28967, 103.85007, "Asia/Singapore")

        fun countryName(code: String): String =
            if (code.length != 2) code else Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH).ifBlank { code }
    }
}
