package com.prayerwallpaper.app.logic

import kotlin.math.max
import kotlin.math.min

/** The two looks of the High contrast card. */
enum class CardLook { DARK, LIGHT }

/**
 * The colours of one card: its [background], the [text] on it, the solid [highlight] behind the current row and the
 * [highlightText] on that (which also colours the dot marker). The edge and the dividers come from these: the edge is the
 * opposite colour at [AccessLayout.BORDER_ALPHA] so the card shows on any background, and a divider is the text colour at
 * [AccessLayout.DIVIDER_ALPHA].
 */
class CardColors(val background: Int, val text: Int, val highlight: Int, val highlightText: Int, private val edge: Int) {
    val border: Int get() = withAlpha(edge, AccessLayout.BORDER_ALPHA)
    val divider: Int get() = withAlpha(text, AccessLayout.DIVIDER_ALPHA)
}

object Cards {
    /** Black card, white text, a solid yellow highlight with black text. */
    val DARK = CardColors(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD60A.toInt(), 0xFF000000.toInt(), edge = 0xFFFFFFFF.toInt())

    /** White card, near-black text, a solid deep blue highlight with white text. */
    val LIGHT = CardColors(0xFFFFFFFF.toInt(), 0xFF111111.toInt(), 0xFF0842A0.toInt(), 0xFFFFFFFF.toInt(), edge = 0xFF000000.toInt())

    fun of(look: CardLook): CardColors = if (look == CardLook.DARK) DARK else LIGHT
}

private fun withAlpha(color: Int, alpha: Float): Int = ((alpha * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

/**
 * Which card to draw. A forced style is used as it is. Auto looks at the background behind the card (its mean
 * [backgroundLuminance], 0 to 1) and picks the card that stands out from it: the light card on a dark background and the
 * dark card on a bright one, switching at the same luminance as the adaptive text colour does.
 */
fun chooseCard(style: CardStyle, backgroundLuminance: Float): CardLook = when (style) {
    CardStyle.DARK -> CardLook.DARK
    CardStyle.LIGHT -> CardLook.LIGHT
    CardStyle.AUTO -> if (prefersDarkText(backgroundLuminance)) CardLook.DARK else CardLook.LIGHT
}

/** The WCAG contrast ratio between two opaque colours, 1 (none) to 21 (black on white). */
fun contrastRatio(a: Int, b: Int): Float {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}

/**
 * A dim of at least this much is suggested in the preview when the background behind a Large text list is mixed, light in
 * places and dark in others, so the list's pill and text sit on a patchy ground. The user's own dim setting is never
 * changed by it.
 */
const val SUGGESTED_DIM = 0.20f

/** A background is mixed when each of "bright" and "dark" covers at least this share of the pixels behind the list. */
const val MIXED_MIN_SHARE = 0.2f

/** Whether the pixels in [x0, x1) by [y0, y1) of a [width]-wide ARGB array are a mix of bright and dark (see [MIXED_MIN_SHARE]). */
fun isMixedBackground(pixels: IntArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int): Boolean {
    var bright = 0
    var count = 0
    for (y in y0 until y1) for (x in x0 until x1) {
        if (prefersDarkText(relativeLuminance(pixels[y * width + x]))) bright++
        count++
    }
    if (count == 0) return false
    // The tolerance keeps a share that is exactly the minimum (20 of 100) from falling just under it in floating point.
    val share = bright.toFloat() / count
    return share + 1e-5f >= MIXED_MIN_SHARE && 1f - share + 1e-5f >= MIXED_MIN_SHARE
}
