package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Place

/** The one line under a tile's title on the home screen: what is set now, in a few words, so most visits need no further tap. */
object HomeSummary {
    fun look(backgroundTitle: String, align: HijriAlign, countdown: Boolean): String {
        val alignment = if (align == HijriAlign.LEFT) "left" else "centred"
        return "$backgroundTitle, $alignment, countdown ${if (countdown) "on" else "off"}"
    }

    /** The place as one short phrase: Singapore is just "Singapore" here, where its full display name says the country twice. */
    fun placeName(place: Place): String =
        listOf(place.name, place.region, Place.countryName(place.country)).filter { it.isNotBlank() }.distinct().joinToString(", ")

    fun location(place: Place, names: NameSet): String {
        val source = if (place.usesMuis) "official MUIS" else "calculated"
        val words = if (names == NameSet.MALAY) "Malay names" else "English names"
        return "${placeName(place)}, $source, $words"
    }

    /** "Off", or "On, 5 prayers" with the health line (`All good`) when it is known. */
    fun reminders(on: Boolean, prayers: Int, health: String?): String {
        if (!on) return "Off"
        val count = when (prayers) {
            0 -> "no prayers chosen"
            1 -> "1 prayer"
            else -> "$prayers prayers"
        }
        // Only the headline of the health line: its detail ("Needs attention: exact alarms are not allowed...") belongs on its own screen.
        val headline = health?.substringBefore(':')?.trim()?.trimEnd('.')
        return if (headline.isNullOrBlank()) "On, $count" else "On, $count, ${headline.replaceFirstChar { it.lowercase() }}"
    }

    /** [placed] is how many are on the home screen, or null while that is not known yet. */
    fun widgets(placed: Int?): String = when (placed) {
        null -> "Add to your home screen"
        0 -> "None added yet"
        else -> "$placed added"
    }

    fun reading(followSystem: Boolean, options: AccessOptions): String {
        if (followSystem) return "Follows your phone"
        val size = when (options.textSize) {
            TextSize.STANDARD -> "Standard text"
            TextSize.LARGE -> "Large text"
            TextSize.EXTRA_LARGE -> "Extra large text"
        }
        return listOfNotNull(size, "high contrast".takeIf { options.highContrast }, "bold".takeIf { options.bold }).joinToString(", ")
    }
}
