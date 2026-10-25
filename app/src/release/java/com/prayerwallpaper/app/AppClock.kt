package com.prayerwallpaper.app

import java.time.Clock

/**
 * The clock the wallpaper and screens read. In release builds this is just the system clock. It has
 * no zone of its own: readers call `withZone` with the zone of the place they show (see
 * [com.prayerwallpaper.app.data.Timetable]). The debug source set has a version of this object with a
 * fake clock; nothing of that exists here.
 */
object AppClock {
    val clock: Clock = Clock.systemUTC()

    fun addChangeListener(listener: () -> Unit) = Unit

    fun removeChangeListener(listener: () -> Unit) = Unit
}
