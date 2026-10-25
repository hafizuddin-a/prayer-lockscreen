package com.prayerwallpaper.app.wallpaper

import com.prayerwallpaper.app.logic.AccessLayout
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.logic.BlockPlan
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.planBlock
import com.prayerwallpaper.app.settings.AppSettings
import kotlin.math.max
import kotlin.math.min

/**
 * The plan for the block when the accessibility options need one, or null when the usual standard list is drawn as it
 * always was. Large and Extra large get the one-column [planBlock]; High contrast adds the card's padding, and on its own it
 * keeps the standard two-column list inside the card ([planStandardCard]), which is also what a Large text list that cannot
 * fit even at Large falls back to. [hijriLines] is how many lines the Hijri date takes (0 for none, 1 for the date, 2 with
 * an event on a line of its own, which only the big layouts do).
 */
fun planFor(width: Int, height: Int, access: AccessOptions, requestedTop: Float, hijriLines: Int, countdown: Boolean): BlockPlan? {
    val padding = if (access.highContrast) AccessLayout.CARD_PADDING * min(width, height) else 0f
    if (access.textSize != TextSize.STANDARD) {
        val plan = planBlock(width, height, access.textSize, requestedTop, hijriLines, if (hijriLines > 0) 2 else 0, countdown, padding)
        if (plan.size != TextSize.STANDARD) return plan
    }
    return if (access.highContrast) planStandardCard(width, height, requestedTop, hijriLines > 0, countdown, padding, reduced = access.textSize != TextSize.STANDARD) else null
}

/**
 * The standard two-column list inside a High contrast card. The list keeps its usual row height and text size; the card is
 * the block's width, [padding] larger than what it holds on every side, and the block is held between the same safe margins
 * as the big layouts so the card never runs off the screen or over the unlock icon.
 */
fun planStandardCard(width: Int, height: Int, requestedTop: Float, hijri: Boolean, countdown: Boolean, padding: Float, reduced: Boolean = false): BlockPlan {
    val unit = min(width, height).toFloat()
    val h = height.toFloat()
    val pitch = ListLayout.ROW_PITCH * unit
    val list = 3 * pitch
    val above = padding + if (hijri) ListLayout.HIJRI_BLOCK * unit else 0f
    val below = padding + if (countdown) ListLayout.COUNTDOWN_BLOCK * unit else 0f
    val lowest = AccessLayout.TOP_SAFE * h + above
    val highest = max((1f - AccessLayout.BOTTOM_SAFE) * h - list - below, lowest)
    val listTop = (requestedTop * h).coerceIn(lowest, highest)
    val blockWidth = AccessLayout.BLOCK_WIDTH * unit
    val left = (width - blockWidth) / 2
    return BlockPlan(
        size = TextSize.STANDARD, reduced = reduced, text = ListLayout.TEXT_SIZE * unit, pitch = pitch,
        left = left, width = blockWidth, contentLeft = left + padding, contentWidth = blockWidth - 2 * padding, columns = 2,
        listTop = listTop, lineText = 0f, lineRow = pitch,
        dateRowTop = if (hijri) listTop - ListLayout.HIJRI_BLOCK * unit else null, eventRowTop = null,
        countdownRowTop = if (countdown) listTop + list + ListLayout.COUNTDOWN_GAP * unit else null,
        padding = padding, top = listTop - above, bottom = listTop + list + below, minListTop = lowest, maxListTop = highest,
    )
}

/**
 * Where the list may be put, as fractions of the screen height: for the drag in the preview and the position slider. The
 * standard list keeps its usual limits; the big layouts and the card are held by the fit rule, so the block always ends
 * above the bottom safe margin. [hijri] and [countdown] say whether those lines are showing.
 */
fun listTopRange(width: Int, height: Int, access: AccessOptions, hijri: Boolean, countdown: Boolean): ClosedFloatingPointRange<Float> {
    val plan = planFor(width, height, access, 0f, if (hijri) 1 else 0, countdown)
        ?: return AppSettings.MIN_LIST_TOP..ListLayout.maxTop(width, height, countdown)
    // A screen with room for exactly one position still gives a range a slider can draw.
    return (plan.minListTop / height)..maxOf(plan.maxListTop / height, plan.minListTop / height + 0.001f)
}

/**
 * Where the list is really drawn ([BlockExtent.listTop], after the fit rule) and how far the whole block (Hijri line, rows
 * and countdown, and the card around them) reaches above its top and below its bottom, in pixels, for the preview's outline
 * and the area a drag may start in. Null for the usual standard list, which has its own sizes.
 */
class BlockExtent(val listTop: Float, val above: Float, val below: Float, val listHeight: Float)

fun accessBlockExtent(width: Int, height: Int, listTop: Float, access: AccessOptions, hijriLines: Int, countdown: Boolean): BlockExtent? {
    val plan = planFor(width, height, access, listTop, hijriLines, countdown) ?: return null
    val rows = if (plan.size == TextSize.STANDARD) 3 else AccessLayout.ROWS
    val list = rows * plan.pitch
    return BlockExtent(plan.listTop, plan.listTop - plan.top, plan.bottom - plan.listTop - list, list)
}
