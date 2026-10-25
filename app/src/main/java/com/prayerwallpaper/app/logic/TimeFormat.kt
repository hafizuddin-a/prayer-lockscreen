package com.prayerwallpaper.app.logic

import java.time.LocalTime

/** A time split for drawing: `6:16` in full size and a small `am` or `pm` after it. The suffix is empty in 24-hour time. */
data class ClockText(val time: String, val suffix: String)

/** [is24Hour] follows the phone's clock setting: `13:10`, or `1:10` with a small `pm`. */
fun formatClock(time: LocalTime, is24Hour: Boolean = false): ClockText {
    val minute = time.minute.toString().padStart(2, '0')
    if (is24Hour) return ClockText("${time.hour.toString().padStart(2, '0')}:$minute", "")
    val hour = time.hour % 12
    return ClockText("${if (hour == 0) 12 else hour}:$minute", if (time.hour < 12) "am" else "pm")
}
