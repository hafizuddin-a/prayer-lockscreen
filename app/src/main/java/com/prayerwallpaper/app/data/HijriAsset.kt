package com.prayerwallpaper.app.data

import android.content.Context
import com.prayerwallpaper.app.logic.HijriCalendar
import com.prayerwallpaper.app.logic.IslamicEvents
import java.time.Clock

const val HIJRI_ASSET_NAME = "hijri_months.json"

/**
 * The Hijri calendar from the bundled asset. A missing or damaged asset gives an empty calendar, which answers
 * "unknown" for every date, so the wallpaper draws no Hijri line instead of crashing the process it shares.
 */
fun loadHijriCalendar(context: Context, clock: Clock): HijriCalendar =
    try {
        HijriCalendar.parse(context.assets.open(HIJRI_ASSET_NAME).bufferedReader().use { it.readText() }, clock)
    } catch (_: Exception) {
        HijriCalendar(emptyList(), clock)
    }

const val EVENTS_ASSET_NAME = "islamic_events.json"

/** The Islamic events from the bundled asset. A missing or damaged asset gives none, and the date is shown on its own. */
fun loadIslamicEvents(context: Context): IslamicEvents =
    try {
        IslamicEvents.parse(context.assets.open(EVENTS_ASSET_NAME).bufferedReader().use { it.readText() })
    } catch (_: Exception) {
        IslamicEvents(emptyList())
    }
