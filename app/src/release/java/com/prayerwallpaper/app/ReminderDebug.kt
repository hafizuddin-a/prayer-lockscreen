package com.prayerwallpaper.app

import android.content.Context
import com.prayerwallpaper.app.data.PrayerDay
import com.prayerwallpaper.app.logic.AlarmMethod
import java.time.LocalDate
import java.time.ZoneId

/** Release builds have no reminder test switches: the real prayer times and the app's own choice of alarm method always apply. */
object ReminderDebug {
    @Suppress("UNUSED_PARAMETER")
    fun install(context: Context) = Unit

    fun method(): AlarmMethod? = null

    @Suppress("UNUSED_PARAMETER")
    fun days(zone: ZoneId): Map<LocalDate, PrayerDay>? = null
}
