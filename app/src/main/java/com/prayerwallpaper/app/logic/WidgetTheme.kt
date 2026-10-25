package com.prayerwallpaper.app.logic

/** The two looks of the home screen widgets: translucent glass over the wallpaper, or a solid card. */
enum class WidgetStyle { GLASS, CARD }

/**
 * The glass's tint: a white one (text follows the wallpaper, light on a dark one and dark on a bright one) or a dark one (a black tint
 * with light text, like a dark mode glass; dark text on a dark tint could not be read).
 */
enum class GlassTint { LIGHT, DARK }

/**
 * The colours one widget is drawn in, all ARGB. [fill] and [border] are the background and its hairline edge (the fill's alpha is
 * the glass opacity), [pill] and [pillText] the marker behind the current entry, and [dot] says to add the filled dot marker, so colour
 * is never the only cue for the current entry.
 */
class WidgetTheme(
    val text: Int,
    val dimText: Int,
    val icon: Int,
    val fill: Int,
    val border: Int,
    val pill: Int,
    val pillText: Int,
    val pillIcon: Int,
    val dot: Boolean,
)

/** The glass hairline is this opaque, and the fill defaults to [DEFAULT_GLASS_OPACITY] white. */
const val GLASS_BORDER_ALPHA = 0.20f
const val DEFAULT_GLASS_OPACITY = 0.12f

/** A dark tint needs more strength than a white one to read as glass at all, so it has its own default. */
const val DEFAULT_DARK_GLASS_OPACITY = 0.55f

/** A dim line of text (the Hijri date, the countdown) is this opaque. */
const val DIM_TEXT_ALPHA = 0.78f

private fun withAlpha(color: Int, alpha: Float): Int = ((alpha * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

/**
 * Glass: a white (or, with [tint] dark, black) fill at [opacity] and a hairline edge, with the text tinted to suit the wallpaper behind the widget exactly as the
 * lock screen's text is ([behind] is the wallpaper's mean colour and [luminance] its brightness), and a translucent pill.
 */
fun glassTheme(opacity: Float, behind: Int, luminance: Float, tint: GlassTint = GlassTint.LIGHT): WidgetTheme {
    // A dark tint always has light text; a white tint follows the wallpaper.
    val colors = textColorsFor(behind, dark = tint == GlassTint.LIGHT && prefersDarkText(luminance))
    val fillColor = if (tint == GlassTint.DARK) 0x000000 else 0xFFFFFF
    return WidgetTheme(
        text = colors.text, dimText = withAlpha(colors.text, DIM_TEXT_ALPHA), icon = colors.icon,
        fill = withAlpha(fillColor, opacity.coerceIn(0f, 1f)), border = withAlpha(0xFFFFFF, GLASS_BORDER_ALPHA),
        pill = colors.pill, pillText = colors.text, pillIcon = colors.icon, dot = false,
    )
}

/** A solid card in the colours of the lock screen's high contrast card, with the filled dot marker. */
fun cardTheme(look: CardLook): WidgetTheme {
    val card = Cards.of(look)
    return WidgetTheme(
        text = card.text, dimText = card.text, icon = card.text, fill = card.background, border = card.border,
        pill = card.highlight, pillText = card.highlightText, pillIcon = card.highlightText, dot = true,
    )
}

/**
 * How the widgets are drawn once the accessibility settings are applied. With "follow the lock screen" on, the text size, bold text
 * and high contrast are the lock screen's (high contrast means the card style); otherwise the widgets are standard.
 */
class WidgetLook(
    val style: WidgetStyle,
    val card: CardStyle,
    val opacity: Float,
    val textSize: TextSize,
    val bold: Boolean,
    val tint: GlassTint = GlassTint.LIGHT,
)

fun widgetLook(
    style: WidgetStyle, card: CardStyle, opacity: Float, follow: Boolean, access: AccessOptions, tint: GlassTint = GlassTint.LIGHT,
): WidgetLook =
    if (follow) {
        WidgetLook(if (access.highContrast) WidgetStyle.CARD else style, card, opacity, access.textSize, access.bold, tint)
    } else {
        WidgetLook(style, card, opacity, TextSize.STANDARD, bold = false, tint = tint)
    }

/** The theme for [look] over a wallpaper of mean colour [behind] and brightness [luminance]. */
fun WidgetLook.theme(behind: Int, luminance: Float): WidgetTheme = when (style) {
    WidgetStyle.GLASS -> glassTheme(opacity, behind, luminance, tint)
    WidgetStyle.CARD -> cardTheme(chooseCard(card, luminance))
}

/**
 * The contrast of the widget's text against what is really behind it: the wallpaper's mean colour with the white glass fill laid
 * over it at [opacity]. It falls when the opacity is so low that a busy wallpaper shows through, and when a light wallpaper and the
 * white fill leave no room for light text. Used for the preview's warning.
 */
fun glassContrast(opacity: Float, behind: Int, luminance: Float, tint: GlassTint = GlassTint.LIGHT): Float {
    val theme = glassTheme(opacity, behind, luminance, tint)
    val effective = mixColors(behind, if (tint == GlassTint.DARK) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), opacity.coerceIn(0f, 1f))
    return contrastRatio(theme.text and 0xFFFFFF or (0xFF shl 24), effective)
}

/** Glass text below this contrast against what is behind it is flagged in the settings preview. */
const val GLASS_MIN_CONTRAST = 4.5f

/**
 * How much bigger the text is for the chosen step, as a multiple of the usual size. The phone's own font scale already makes
 * `sp` text bigger, so the step only adds what the font scale does not: a Large step at font scale 1.3 adds nothing.
 */
fun widgetTextMultiplier(size: TextSize, fontScale: Float): Float {
    val step = when (size) {
        TextSize.STANDARD -> 1f
        TextSize.LARGE -> WIDGET_LARGE_SCALE
        TextSize.EXTRA_LARGE -> WIDGET_EXTRA_LARGE_SCALE
    }
    return maxOf(fontScale, step) / fontScale
}

const val WIDGET_LARGE_SCALE = 1.2f
const val WIDGET_EXTRA_LARGE_SCALE = 1.5f
