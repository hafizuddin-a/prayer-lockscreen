package com.prayerwallpaper.app.logic

import kotlin.math.min

/**
 * The Large and Extra large layout: one column of six rows, with the Hijri date above and the countdown below. Every size
 * is a fraction of the shorter screen side (the same "unit" the standard layout uses, which in portrait is the width),
 * so it scales across devices. These are the starting values from the accessibility spec, tuned against its mockups.
 */
object AccessLayout {
    /** Large sits between the standard list's 0.040 and Extra large, which is the size Large used to be (0.075). */
    const val LARGE_TEXT = 0.058f
    const val EXTRA_LARGE_TEXT = 0.075f

    /** A row is this many times its text size tall, and may shrink to [MIN_ROW_FACTOR] to make the block fit. */
    const val ROW_FACTOR = 1.9f
    const val MIN_ROW_FACTOR = 1.5f

    /** The block is this wide, centred. The rows, the Hijri date and the countdown all sit inside it. */
    const val BLOCK_WIDTH = 0.88f

    /** The Hijri and countdown lines: their text as a share of the row text, and a line's height as a multiple of its own text. */
    const val LINE_SCALE = 0.85f
    const val LINE_ROW_FACTOR = 1.5f

    /** Space between the Hijri date or the countdown and the nearest row, as a share of a row's height. */
    const val LINE_GAP = 0.25f

    /** The am or pm is as big as the time, with this much of the text size between them. */
    const val SUFFIX_SCALE = 1f
    const val SUFFIX_GAP = 0.25f

    /**
     * The block must stay inside this much of the screen's height at the top and at the bottom. The spec started the bottom
     * at 0.06; Android's own unlock icon sits at about 87 to 90 percent of the height, and an Extra large block that ends
     * at 94 percent drew the countdown over it, so the bottom is 0.14.
     */
    const val TOP_SAFE = 0.03f
    const val BOTTOM_SAFE = 0.14f

    const val ROWS = 6

    /**
     * The High contrast card: its inner padding and corner radius, the edge and the dividers as fractions of the shorter side
     * (about 1.5 dp and 1 dp on a typical phone), and their opacity.
     */
    const val CARD_PADDING = 0.03f
    const val CARD_RADIUS = 0.05f
    const val CARD_BORDER = 0.0035f
    const val DIVIDER = 0.0026f
    const val BORDER_ALPHA = 0.40f
    const val DIVIDER_ALPHA = 0.25f

    fun textFraction(size: TextSize): Float = when (size) {
        TextSize.LARGE -> LARGE_TEXT
        TextSize.EXTRA_LARGE -> EXTRA_LARGE_TEXT
        TextSize.STANDARD -> error("Standard uses the standard layout")
    }
}

/**
 * Where everything goes, in pixels. [size] is the text size actually used: when the block cannot fit at the size asked for
 * it is the next one down, and when even that cannot fit it is [TextSize.STANDARD], which means "use the standard layout".
 * [reduced] says the size or the row height was cut to make it fit, so the preview can say so. The other fields are not
 * meaningful for [TextSize.STANDARD].
 *
 * [left] and [width] are the whole block (the card, when there is one) and [contentLeft] and [contentWidth] what is inside
 * its [padding]. [columns] is 1 for the big layouts and 2 for the standard list inside a card.
 *
 * [text] is the row text size and [pitch] a row's height. [dateRowTop], [eventRowTop] and [countdownRowTop] are the tops of
 * the lines, each [lineRow] tall with [lineText] text, and are null for a line that is not showing. [top] and [bottom]
 * bound the whole block including [padding] (the high contrast card's inner padding). [minListTop] and [maxListTop] are
 * the lowest and highest the list may be placed, for the drag and the slider.
 */
class BlockPlan(
    val size: TextSize,
    val reduced: Boolean,
    val text: Float,
    val pitch: Float,
    val left: Float,
    val width: Float,
    val contentLeft: Float,
    val contentWidth: Float,
    val columns: Int,
    val listTop: Float,
    val lineText: Float,
    val lineRow: Float,
    val dateRowTop: Float?,
    val eventRowTop: Float?,
    val countdownRowTop: Float?,
    val padding: Float,
    val top: Float,
    val bottom: Float,
    val minListTop: Float,
    val maxListTop: Float,
)

/**
 * Plans the Large or Extra large block for a [width] by [height] screen. [requestedTop] is where the user put the top of the
 * list, as a fraction of the height. [hijriLines] is how many lines the Hijri date takes now (0 for none, 1 for the date, 2
 * with an event name on a line of its own under it) and [fitHijriLines] how many to leave room for when deciding whether
 * everything fits, so the text size does not change from one day to the next. [padding] is the card's inner padding in pixels
 * (0 without the card).
 *
 * The fit rule: first the position is clamped so the block ends above the bottom safe margin (and starts below the top one).
 * If the block is too tall even then, the rows shrink to [AccessLayout.MIN_ROW_FACTOR] times the text size, then the text
 * drops a step, and so on until it fits: a row is never clipped.
 */
fun planBlock(
    width: Int, height: Int, requested: TextSize, requestedTop: Float, hijriLines: Int, fitHijriLines: Int, countdown: Boolean,
    padding: Float = 0f,
): BlockPlan {
    val unit = min(width, height).toFloat()
    val h = height.toFloat()
    val limitTop = AccessLayout.TOP_SAFE * h
    val limitBottom = (1f - AccessLayout.BOTTOM_SAFE) * h
    val blockWidth = AccessLayout.BLOCK_WIDTH * unit

    val steps = listOf(TextSize.EXTRA_LARGE, TextSize.LARGE).filter { it.ordinal <= requested.ordinal }
    for ((stepIndex, size) in steps.withIndex()) {
        for ((factorIndex, factor) in listOf(AccessLayout.ROW_FACTOR, AccessLayout.MIN_ROW_FACTOR).withIndex()) {
            val text = AccessLayout.textFraction(size) * unit
            val pitch = factor * text
            val lineText = AccessLayout.LINE_SCALE * text
            val lineRow = AccessLayout.LINE_ROW_FACTOR * lineText
            val gap = AccessLayout.LINE_GAP * pitch
            val list = AccessLayout.ROWS * pitch

            fun above(lines: Int) = padding + if (lines > 0) lines * lineRow + gap else 0f
            val below = padding + if (countdown) gap + lineRow else 0f

            if (above(fitHijriLines) + list + below > limitBottom - limitTop) continue

            val lowestTop = limitTop + above(hijriLines)
            val maxListTop = maxOf(limitBottom - list - below, lowestTop)
            val listTop = (requestedTop * h).coerceIn(lowestTop, maxListTop)
            val eventRowTop = if (hijriLines == 2) listTop - gap - lineRow else null
            val dateRowTop = when (hijriLines) {
                0 -> null
                1 -> listTop - gap - lineRow
                else -> listTop - gap - 2 * lineRow
            }
            return BlockPlan(
                size = size, reduced = stepIndex > 0 || factorIndex > 0, text = text, pitch = pitch,
                left = (width - blockWidth) / 2, width = blockWidth, contentLeft = (width - blockWidth) / 2 + padding,
                contentWidth = blockWidth - 2 * padding, columns = 1, listTop = listTop,
                lineText = lineText, lineRow = lineRow, dateRowTop = dateRowTop, eventRowTop = eventRowTop,
                countdownRowTop = if (countdown) listTop + list + gap else null,
                padding = padding, top = listTop - above(hijriLines), bottom = listTop + list + below,
                minListTop = limitTop + above(fitHijriLines), maxListTop = maxListTop,
            )
        }
    }
    return BlockPlan(
        size = TextSize.STANDARD, reduced = requested != TextSize.STANDARD, text = 0f, pitch = 0f, left = 0f, width = 0f,
        contentLeft = 0f, contentWidth = 0f, columns = 2, listTop = requestedTop * h, lineText = 0f, lineRow = 0f, dateRowTop = null, eventRowTop = null, countdownRowTop = null,
        padding = padding, top = requestedTop * h, bottom = requestedTop * h, minListTop = limitTop, maxListTop = limitBottom,
    )
}
