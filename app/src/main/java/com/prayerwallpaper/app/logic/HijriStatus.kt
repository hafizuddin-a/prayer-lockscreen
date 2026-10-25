package com.prayerwallpaper.app.logic

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What the settings screen says about how far the Hijri month table reaches. [warn] makes it a visible warning. */
class HijriStatus(val message: String, val warn: Boolean)

/** Below this many days left the settings screen warns, so there is time to ship the next MUIS calendar. */
const val HIJRI_WARN_DAYS = 60

private val untilFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * The settings screen's line about the Hijri table. [lastKnown] is the last date the table gives a certain answer for
 * (null for an empty table) and [today] is today in Singapore. No notifications: this is only ever shown on screen.
 */
fun hijriStatus(usesMuis: Boolean, lastKnown: LocalDate?, today: LocalDate): HijriStatus {
    if (!usesMuis) {
        return HijriStatus("Hijri dates are only shown for Singapore.", warn = false)
    }
    if (lastKnown == null) {
        return HijriStatus("Couldn't read the Hijri calendar. Reinstall the app to restore it.", warn = true)
    }
    val until = untilFormat.format(lastKnown)
    val daysLeft = ChronoUnit.DAYS.between(today, lastKnown)
    return when {
        daysLeft < 0 -> HijriStatus("Hijri dates ran out after $until. Update the app for the next calendar.", warn = true)
        daysLeft < HIJRI_WARN_DAYS -> HijriStatus("Hijri dates available until $until. Only $daysLeft ${if (daysLeft == 1L) "day" else "days"} left: update the app for the next calendar.", warn = true)
        else -> HijriStatus("Hijri dates available until $until.", warn = false)
    }
}
