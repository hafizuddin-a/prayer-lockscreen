package com.prayerwallpaper.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The app's own look: black and white, the same on every phone (no Material You colours, no light variant). Everything is a
 * neutral grey between the two. The values are plain ARGB ints so a unit test can check the contrast of every text and background
 * pair that is drawn together. State is never shown by colour: a word and an icon always say it too.
 */
object Mono {
    /** The page behind everything. Also the window background, so there is no flash before the first frame. */
    const val BACKGROUND = 0xFF000000.toInt()

    /** Cards and rows. */
    const val CARD = 0xFF171717.toInt()

    /** Things that sit on a card: icon discs, dialogs. */
    const val RAISED = 0xFF262626.toInt()

    /** The off switch track and other quiet fills. Decoration only. */
    const val TRACK = 0xFF3A3A3A.toInt()

    /** The empty part of a slider: at least 3:1 against a card, so the whole length of the slider can be seen. */
    const val SLIDER_TRACK = 0xFF6E6E6E.toInt()

    const val TEXT = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xFFB8B8B8.toInt()

    /** Buttons, the selected choice, the switch that is on: white, with black text on it. */
    const val ACCENT = 0xFFFFFFFF.toInt()
    const val ON_ACCENT = 0xFF000000.toInt()

    /** Done and working. Shown with a word and a tick, never as colour alone. */
    const val SUCCESS = 0xFFFFFFFF.toInt()
    const val ON_SUCCESS = 0xFF000000.toInt()

    /** Problems. Shown with a word and a warning icon, never as colour alone. */
    const val ERROR = 0xFFFFFFFF.toInt()
    const val ON_ERROR = 0xFF000000.toInt()

    /** Borders of outlined buttons and fields: at least 3:1 against the card, so a control's edge can be seen. */
    const val OUTLINE = 0xFF8C8C8C.toInt()

    /** Links and the calm secondary buttons. */
    const val SECONDARY = 0xFFFFFFFF.toInt()
    const val ON_SECONDARY = 0xFF000000.toInt()
}

private fun c(argb: Int) = Color(argb)

val MonoColors = darkColorScheme(
    primary = c(Mono.ACCENT), onPrimary = c(Mono.ON_ACCENT),
    primaryContainer = c(Mono.RAISED), onPrimaryContainer = c(Mono.TEXT),
    secondary = c(Mono.SECONDARY), onSecondary = c(Mono.ON_SECONDARY),
    secondaryContainer = c(Mono.SLIDER_TRACK), onSecondaryContainer = c(Mono.TEXT),
    tertiary = c(Mono.SUCCESS), onTertiary = c(Mono.ON_SUCCESS),
    tertiaryContainer = c(Mono.RAISED), onTertiaryContainer = c(Mono.TEXT),
    background = c(Mono.BACKGROUND), onBackground = c(Mono.TEXT),
    surface = c(Mono.BACKGROUND), onSurface = c(Mono.TEXT),
    surfaceVariant = c(Mono.CARD), onSurfaceVariant = c(Mono.TEXT_SECONDARY),
    surfaceTint = c(Mono.ACCENT),
    surfaceContainerLowest = c(Mono.BACKGROUND), surfaceContainerLow = c(Mono.CARD), surfaceContainer = c(Mono.CARD),
    surfaceContainerHigh = c(Mono.RAISED), surfaceContainerHighest = c(Mono.TRACK),
    inverseSurface = c(Mono.TEXT), inverseOnSurface = c(Mono.BACKGROUND), inversePrimary = c(Mono.ON_ACCENT),
    error = c(Mono.ERROR), onError = c(Mono.ON_ERROR),
    errorContainer = c(Mono.ON_ERROR), onErrorContainer = c(Mono.ERROR),
    outline = c(Mono.OUTLINE), outlineVariant = c(Mono.SLIDER_TRACK),
    scrim = Color.Black,
)

/**
 * One deliberate scale on the phone's default font (no font file). Sizes are in sp so Android's font size setting scales them;
 * body text starts at 16 and the small text used for descriptions is 14, never smaller.
 */
val BrandTypography = Typography().let { base ->
    base.copy(
        headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold),
        headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
        bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
        bodySmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
        labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    )
}

val BrandShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PrayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonoColors, typography = BrandTypography, shapes = BrandShapes, content = content)
}
