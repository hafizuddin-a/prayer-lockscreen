package com.prayerwallpaper.app.logic

/** How big the list's text is. Standard is the usual two-column list; the other two are one column of much bigger rows. */
enum class TextSize { STANDARD, LARGE, EXTRA_LARGE }

/** The colours High contrast draws its card in. AUTO picks light or dark from the background behind the card. */
enum class CardStyle { AUTO, DARK, LIGHT }

/** The accessibility options in force for one draw, after "Follow system settings" has been applied. */
data class AccessOptions(
    val textSize: TextSize = TextSize.STANDARD,
    val highContrast: Boolean = false,
    val cardStyle: CardStyle = CardStyle.AUTO,
    val bold: Boolean = false,
) {
    /** True when nothing changes the usual look. */
    val isStandard: Boolean get() = textSize == TextSize.STANDARD && !highContrast && !bold
}

/**
 * What Android reports about the user's own accessibility settings, from documented public APIs only: the font scale and
 * the bold-text adjustment from the app's `Configuration`, and the contrast level from `UiModeManager.getContrast()`.
 */
data class SystemAccessibility(val fontScale: Float = 1f, val fontWeightAdjustment: Int = 0, val contrast: Float = 0f)

/** A system font scale at or above this follows as Large text, and at or above [EXTRA_LARGE_FONT_SCALE] as Extra large. */
const val LARGE_FONT_SCALE = 1.15f
const val EXTRA_LARGE_FONT_SCALE = 1.5f

/** A system contrast level (0 standard, 0.5 medium, 1 high) at or above this follows as High contrast. */
const val HIGH_CONTRAST_LEVEL = 0.5f

fun SystemAccessibility.textSize(): TextSize = when {
    fontScale >= EXTRA_LARGE_FONT_SCALE -> TextSize.EXTRA_LARGE
    fontScale >= LARGE_FONT_SCALE -> TextSize.LARGE
    else -> TextSize.STANDARD
}

/** The options the system settings stand for. [cardStyle] is the user's own choice: the system has no such setting. */
fun SystemAccessibility.toOptions(cardStyle: CardStyle = CardStyle.AUTO) = AccessOptions(
    textSize = textSize(),
    highContrast = contrast >= HIGH_CONTRAST_LEVEL,
    cardStyle = cardStyle,
    bold = fontWeightAdjustment > 0,
)
