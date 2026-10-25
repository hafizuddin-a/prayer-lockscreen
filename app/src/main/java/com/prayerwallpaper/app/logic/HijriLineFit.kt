package com.prayerwallpaper.app.logic

/** What goes between the date and the event name. */
const val HIJRI_EVENT_SEPARATOR = " · "

/** Marks a shortened event name. */
const val HIJRI_ELLIPSIS = "…"

/** The text to draw, and the factor to shrink the text size by (1 for none). */
class HijriFit(val scale: Float, val text: String)

/**
 * Makes the Hijri line fit in [available] width. The line is a moon icon ([fixed] wide with its gap), the [date], and
 * when there is an [event], a separator and the event's name. Event names run from a few letters (Asyura Day) to a long
 * one (Prophet Muhammad's Birthday), and a wide font makes everything wider, so in order:
 *
 *  1. If it fits, draw it as it is.
 *  2. Otherwise shrink the whole line, down to [eventFloor] of the usual size.
 *  3. If it still does not fit, keep the date and shorten the event name with an ellipsis, at [eventFloor].
 *  4. If not even one letter of the name fits, draw the date alone.
 *
 * The date is never dropped or cut. A line with no event only shrinks, down to [dateFloor]. [widthOf] gives a string's
 * width at the usual size, so the arithmetic here does not depend on Android and can be tested.
 */
fun fitHijriLine(
    widthOf: (String) -> Float,
    fixed: Float,
    date: String,
    event: String?,
    available: Float,
    eventFloor: Float,
    dateFloor: Float,
): HijriFit {
    fun total(text: String) = fixed + widthOf(text)
    val dateOnly = HijriFit(textFitScale(total(date), available, dateFloor), date)
    if (event == null) return dateOnly

    val full = date + HIJRI_EVENT_SEPARATOR + event
    val need = total(full)
    if (need <= available) return HijriFit(1f, full)
    val scale = available / need
    if (scale >= eventFloor) return HijriFit(scale, full)

    for (letters in event.length - 1 downTo 1) {
        val shortened = date + HIJRI_EVENT_SEPARATOR + event.take(letters).trimEnd() + HIJRI_ELLIPSIS
        if (eventFloor * total(shortened) <= available) return HijriFit(eventFloor, shortened)
    }
    return dateOnly
}

/**
 * Makes an event name that has a line of its own fit in [available] width: as it is, or shrunk down to [floor] of the usual
 * size, or shortened with an ellipsis at [floor]. [widthOf] gives a string's width at the usual size.
 */
fun fitEventName(widthOf: (String) -> Float, event: String, available: Float, floor: Float): HijriFit {
    val need = widthOf(event)
    if (need <= available) return HijriFit(1f, event)
    val scale = available / need
    if (scale >= floor) return HijriFit(scale, event)
    for (letters in event.length - 1 downTo 1) {
        val shortened = event.take(letters).trimEnd() + HIJRI_ELLIPSIS
        if (floor * widthOf(shortened) <= available) return HijriFit(floor, shortened)
    }
    return HijriFit(floor, HIJRI_ELLIPSIS)
}
