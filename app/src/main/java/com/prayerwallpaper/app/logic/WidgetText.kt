package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.WhenMissing
import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.time.LocalTime
import java.util.Locale

/** A time as the widgets write it: the digits and, in 12-hour time, the am or pm marker (empty in 24-hour time). */
data class WidgetTime(val time: String, val marker: String) {
    /** The whole time as one string, for a text view or for a screen reader. */
    val text: String get() = if (marker.isEmpty()) time else "$time $marker"
}

/**
 * [time] in the phone's own style. The digits and the am or pm words come from the [locale] (Arabic digits and markers in an
 * Arabic phone), never joined by hand from English pieces. English markers are written in lower case like the lock screen
 * (`pm`). With [compact] the marker is its first letter only (`4:15 p`), which is how the six-in-a-row widget fits its columns.
 * [is24Hour] follows the phone's clock setting: `13:10`, hours always two digits like the lock screen.
 */
fun formatWidgetTime(time: LocalTime, is24Hour: Boolean, locale: Locale, compact: Boolean = false): WidgetTime {
    val digits = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false }
    val twoDigits = NumberFormat.getIntegerInstance(locale).apply { isGroupingUsed = false; minimumIntegerDigits = 2 }
    val minute = twoDigits.format(time.minute.toLong())
    if (is24Hour) return WidgetTime("${twoDigits.format(time.hour.toLong())}:$minute", "")
    val hour = time.hour % 12
    val markers = DateFormatSymbols.getInstance(locale).amPmStrings
    val full = markers[if (time.hour < 12) 0 else 1].lowercase(locale)
    val marker = if (compact) full.codePointAt(0).let { String(Character.toChars(it)) } else full
    return WidgetTime("${digits.format((if (hour == 0) 12 else hour).toLong())}:$minute", marker)
}

/** How much of the Hijri date a widget writes. */
enum class HijriForm { NONE, SHORT, FULL }

/** From this system font scale up the secondary lines (the Hijri date first) are dropped to keep the main text readable. */
const val SECONDARY_DROP_FONT_SCALE = 1.3f

/** `24 Rabiulakhir`. */
fun HijriDate.shortText(): String = "$day $monthName"

/**
 * How the Hijri date is written on the next prayer widget. The full form (`24 Rabiulakhir 1448H`) when it fits the width
 * ([fullFits]), which is a 3-cell widget or wider on a usual launcher, and the short one (`24 Rabiulakhir`) on a 2-cell widget.
 * Whether a launcher's cells are wide or narrow, this follows the room the widget really has. Nothing when the date is unknown or
 * switched off ([date] null), never an estimate; and nothing at a large font scale, where the name and time need the room.
 */
fun hijriFormFor(date: HijriDate?, fullFits: Boolean, fontScale: Float): HijriForm = when {
    date == null -> HijriForm.NONE
    fontScale >= SECONDARY_DROP_FONT_SCALE -> HijriForm.NONE
    fullFits -> HijriForm.FULL
    else -> HijriForm.SHORT
}

fun HijriDate.text(form: HijriForm): String? = when (form) {
    HijriForm.NONE -> null
    HijriForm.SHORT -> shortText()
    HijriForm.FULL -> text
}

/** The words on the widgets. English like the rest of the app, kept here so the tests can read them. */
object WidgetWords {
    const val NEXT_PRAYER = "Next prayer"
    const val TOMORROW = "Tomorrow"
    const val LOAD = "Open the app to load prayer times"
    const val UPDATE = "Prayer times need updating"
    const val CANNOT_CALCULATE = "Can't calculate times here today"

    fun missing(reason: WhenMissing): String = when (reason) {
        WhenMissing.LOAD -> LOAD
        WhenMissing.UPDATE -> UPDATE
        WhenMissing.CANNOT_CALCULATE -> CANNOT_CALCULATE
    }

    /** A countdown format for a Chronometer, `in %s`. The name is made safe for a format string. */
    const val IN_FORMAT = "in %s"

    /** `Asar in %s`, or `Subuh tomorrow in %s`. */
    fun nextFooterFormat(name: String, tomorrow: Boolean): String =
        "${name.replace("%", "%%")}${if (tomorrow) " tomorrow" else ""} in %s"
}

/**
 * What a screen reader says. The live countdown is left out on purpose: a description is fixed text and the countdown would be
 * wrong a moment later.
 */
object WidgetSpoken {
    fun next(next: WidgetNext, time: WidgetTime, hijri: String? = null): String =
        "Next prayer: ${next.entry.label}${if (next.tomorrow) " tomorrow" else ""} at ${time.text}." + (hijri?.let { " $it." } ?: "")

    fun row(label: String, time: WidgetTime, current: Boolean): String = "$label, ${time.text}" + if (current) ", current prayer" else ""

    fun footer(next: WidgetNext, time: WidgetTime): String = "Next prayer: ${next.entry.label}${if (next.tomorrow) " tomorrow" else ""} at ${time.text}"
}

/**
 * The entries a single-column list shows, starting with the current one and running on in time, into tomorrow's list when
 * today's ends. [count] entries when that many exist; when the data runs out (the last day, after Isyak) the window slides back
 * so it still has [count] entries, and the current one is still in it. All six, in today's order, when [count] is six or more.
 *
 * Before Subuh the current one is Isyak of last night, and the window runs Isyak, Subuh, Syuruk of today.
 */
fun windowFromCurrent(state: WidgetState.Ready, count: Int): List<WidgetEntry> {
    if (count >= state.entries.size) return state.entries
    val line: List<WidgetEntry> = if (state.beforeSubuh) {
        listOf(state.entries.last()) + state.entries.dropLast(1)
    } else {
        state.entries.dropWhile { it.prayer != state.current } + state.tomorrowEntries
    }
    // Not enough after the current one: the last [count] entries of the day, which still contain the current one.
    return if (line.size >= count) line.take(count) else state.entries.takeLast(count)
}
