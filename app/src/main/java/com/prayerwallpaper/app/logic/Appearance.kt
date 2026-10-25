package com.prayerwallpaper.app.logic

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Packs 0..255 channels into an opaque ARGB int. Pure maths, so it also runs in plain JVM tests. */
fun argb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

/** Mixes [from] (at 0) towards [to] (at 1), channel by channel. Result is opaque. */
fun mixColors(from: Int, to: Int, t: Float): Int {
    fun channel(shift: Int): Int {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * t).roundToInt().coerceIn(0, 255)
    }
    return argb(channel(16), channel(8), channel(0))
}

/**
 * Two-colour gradients follow the curve of the reference: a quick fade through the top and a slow
 * one below it, which keeps the lower two thirds dark enough for light text. [WEIGHT_OF_TOP] is how
 * much of the top colour remains at each of [POSITIONS].
 */
object GradientCurve {
    val POSITIONS = floatArrayOf(0f, 0.2f, 0.45f, 1f)
    private val WEIGHT_OF_TOP = floatArrayOf(1f, 0.55f, 0.22f, 0f)

    fun colors(top: Int, bottom: Int): IntArray = IntArray(POSITIONS.size) { mixColors(bottom, top, WEIGHT_OF_TOP[it]) }
}

/** WCAG relative luminance of an sRGB colour, 0 (black) to 1 (white). */
fun relativeLuminance(color: Int): Float {
    fun linear(shift: Int): Double {
        val c = ((color shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return (0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)).toFloat()
}

/**
 * Light text below this luminance behind the list, dark text above it. Pure contrast parity is
 * about 0.18; this sits a little higher because soft light text still reads well on mid tones.
 */
const val DARK_TEXT_ABOVE_LUMINANCE = 0.35f

fun prefersDarkText(backgroundLuminance: Float): Boolean = backgroundLuminance > DARK_TEXT_ABOVE_LUMINANCE

/** Mean luminance of the pixels in [x0, x1) by [y0, y1) of a [width]-wide ARGB pixel array. */
fun meanLuminance(pixels: IntArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int): Float {
    var sum = 0.0
    var count = 0
    for (y in y0 until y1) {
        for (x in x0 until x1) {
            sum += relativeLuminance(pixels[y * width + x])
            count++
        }
    }
    return if (count == 0) 0f else (sum / count).toFloat()
}

/** Longest and shortest side limits for a stored photo, in pixels. Screens never need more. */
const val PHOTO_MAX_SHORT_SIDE = 1440
const val PHOTO_MAX_LONG_SIDE = 3200

/** Scales ([width], [height]) down, keeping the aspect ratio, so it fits both limits. Never scales up. */
fun scaledPhotoSize(width: Int, height: Int, maxShort: Int = PHOTO_MAX_SHORT_SIDE, maxLong: Int = PHOTO_MAX_LONG_SIDE): Pair<Int, Int> {
    val short = min(width, height)
    val long = max(width, height)
    val scale = min(1f, min(maxShort.toFloat() / short, maxLong.toFloat() / long))
    return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
}

/**
 * The colours "match my wallpaper" paints, lightest at the top like the reference. Duplicates are
 * dropped so a two-colour wallpaper gives a two-colour gradient.
 */
fun matchGradientColors(primary: Int, secondary: Int, tertiary: Int?): IntArray =
    listOfNotNull(primary, secondary, tertiary).distinct().sortedByDescending(::relativeLuminance).toIntArray()

/** Mean colour of the pixels in [x0, x1) by [y0, y1) of a [width]-wide ARGB pixel array. Opaque. */
fun meanColor(pixels: IntArray, width: Int, x0: Int, y0: Int, x1: Int, y1: Int): Int {
    var r = 0L
    var g = 0L
    var b = 0L
    var count = 0
    for (y in y0 until y1) {
        for (x in x0 until x1) {
            val c = pixels[y * width + x]
            r += (c shr 16) and 0xFF
            g += (c shr 8) and 0xFF
            b += c and 0xFF
            count++
        }
    }
    if (count == 0) return argb(0, 0, 0)
    return argb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
}

/** Hue 0..360, saturation 0..1, value 0..1. */
private fun toHsv(color: Int): FloatArray {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return floatArrayOf(if (hue < 0f) hue + 360f else hue, if (max == 0f) 0f else delta / max, max)
}

private fun fromHsv(hue: Float, saturation: Float, value: Float): Int {
    val c = value * saturation
    val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = value - c
    val (r, g, b) = when ((hue / 60f).toInt() % 6) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return argb(((r + m) * 255f).roundToInt(), ((g + m) * 255f).roundToInt(), ((b + m) * 255f).roundToInt())
}

/** Colours for the list: [text], a slightly dimmer [icon], and the highlight [pill] (translucent). */
class TextColors(val text: Int, val icon: Int, val pill: Int)

/**
 * Text that takes its tint from the background [behind] it, so a navy background gives the soft
 * blue-white of the reference and a black one gives plain white. The tint strength follows how
 * colourful the background is. [dark] picks dark text for light backgrounds.
 */
fun textColorsFor(behind: Int, dark: Boolean): TextColors {
    val (hue, saturation) = toHsv(behind).let { it[0] to it[1] }
    return if (dark) {
        val tint = (0.6f * saturation).coerceAtMost(0.5f)
        val text = fromHsv(hue, tint, 0.20f)
        TextColors(text, fromHsv(hue, tint * 1.1f, 0.27f), (0x26 shl 24) or (text and 0xFFFFFF))
    } else {
        val tint = (0.33f * saturation).coerceAtMost(0.27f)
        val text = fromHsv(hue, tint, 0.99f)
        TextColors(text, fromHsv(hue, (tint * 1.07f).coerceAtMost(0.29f), 0.93f), (0x30 shl 24) or (text and 0xFFFFFF))
    }
}

/**
 * A light and a dark text colour, each with a slightly stronger neighbour for the icons. [light] is used on
 * dark wallpapers and [dark] on bright ones, the way a lock screen clock switches.
 */
class TextTones(val light: Int, val lightDim: Int, val dark: Int, val darkDim: Int)

/**
 * The list's colours from a pair of [tones]. [supportsDarkText] is the wallpaper colour hint the system reads to
 * choose between light and dark for its own clock, so the list switches when the clock does. The tones are
 * brightened to survive the lock screen's scrim (see [undoScrim]).
 */
fun tonedTextColors(supportsDarkText: Boolean, tones: TextTones, scrim: Float = KEYGUARD_SCRIM): TextColors {
    val textTone = if (supportsDarkText) tones.dark else tones.light
    val iconTone = if (supportsDarkText) tones.darkDim else tones.lightDim
    // One factor for both, set by the text, so the icons stay a touch dimmer than the text instead of all saturating to white.
    val boost = scrimBoost(textTone, scrim)
    val text = scaleColor(textTone, boost)
    val pillAlpha = if (supportsDarkText) 0x26 else 0x30
    return TextColors(text, scaleColor(iconTone, boost), (pillAlpha shl 24) or (text and 0xFFFFFF))
}

/** Icons are drawn this much dimmer than the text, as in the other colour modes. */
private const val ICON_DIMMING = 0.95f

/**
 * The list's colours when the user picks the text colour: exactly [color], brightened to survive the lock screen's
 * scrim (see [scrimBoost]) so that what shows on the lock screen is as close to the picked colour as the scrim allows.
 * The highlight is the colour, translucent, a little stronger under light text.
 */
fun customTextColors(color: Int, scrim: Float = KEYGUARD_SCRIM): TextColors {
    val boost = scrimBoost(color, scrim)
    val text = scaleColor(color, boost)
    val pillAlpha = if (relativeLuminance(color) > 0.4f) 0x30 else 0x26
    return TextColors(text, scaleColor(color, boost * ICON_DIMMING), (pillAlpha shl 24) or (text and 0xFFFFFF))
}

/**
 * How much black the lock screen lays over the wallpaper, 20% on stock Android 16 (measured). The clock is
 * drawn above it and our list below it, so the list shows at 0.8 times its colour.
 */
const val KEYGUARD_SCRIM = 0.2f

/**
 * How much to brighten [color] so that after a black scrim of [scrim] it looks as it does unscrimmed: 1 / (1 - scrim),
 * but no more than the brightest channel allows (it cannot go past 255).
 */
fun scrimBoost(color: Int, scrim: Float = KEYGUARD_SCRIM): Float {
    val brightest = max((color shr 16) and 0xFF, max((color shr 8) and 0xFF, color and 0xFF))
    return if (brightest == 0) 1f else min(1f / (1f - scrim), 255f / brightest)
}

/** [color] with every channel multiplied by [factor], so the tint is kept. Opaque, and never past 255. */
fun scaleColor(color: Int, factor: Float): Int {
    fun channel(shift: Int) = (((color shr shift) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
    return argb(channel(16), channel(8), channel(0))
}

/** [color] brightened to survive a black scrim of [scrim], as far as the brightest channel allows. */
fun undoScrim(color: Int, scrim: Float = KEYGUARD_SCRIM): Int = scaleColor(color, scrimBoost(color, scrim))

/**
 * How much to shrink the list text so the widest row ([needed]) fits in the room it has
 * ([available]). 1 when it already fits. Never below [floor], so a very wide font still gets
 * readable text, with the renderer's squeeze as the last resort.
 */
fun textFitScale(needed: Float, available: Float, floor: Float = 0.7f): Float =
    if (needed <= available || needed <= 0f) 1f else (available / needed).coerceIn(floor, 1f)
