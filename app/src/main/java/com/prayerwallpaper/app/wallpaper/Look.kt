package com.prayerwallpaper.app.wallpaper

import android.graphics.Bitmap
import com.prayerwallpaper.app.logic.GradientCurve
import com.prayerwallpaper.app.logic.PhotoView
import com.prayerwallpaper.app.logic.matchGradientColors
import com.prayerwallpaper.app.settings.AppSettings
import com.prayerwallpaper.app.settings.BackgroundMode
import com.prayerwallpaper.app.settings.PhotoStore
import com.prayerwallpaper.app.settings.SettingsStore
import com.prayerwallpaper.app.settings.TextColorSource

/** What is painted behind the prayer list. */
sealed interface Background {
    /** Vertical, following the reference curve. */
    class Gradient(val top: Int, val bottom: Int) : Background

    class Solid(val color: Int) : Background

    /** Evenly spaced colours from the top down, used by "match my wallpaper". */
    class Stops(val colors: IntArray) : Background

    /** Fills the screen, framed by [view] (zoom and position). */
    class Photo(val bitmap: Bitmap, val view: PhotoView = PhotoView()) : Background
}

/**
 * Everything the renderer needs besides the schedule: the background, how much to dim it, the list
 * position, and where the text colour comes from.
 */
class Look(
    val background: Background, val dim: Float, val listTop: Float,
    val textColors: TextColorSource = TextColorSource.BACKGROUND,
    val customTextColor: Int = AppSettings.DEFAULT_CUSTOM_TEXT,
)

/** Turns stored settings into a [Look]. A mode that is not ready yet falls back to the default gradient. */
class LookProvider(private val settings: SettingsStore, private val photos: PhotoStore) {
    fun current(): Look = of(settings.read())

    fun of(s: AppSettings): Look = Look(background(s), s.dim, s.listTop, s.textColors, s.customTextColor)

    private fun background(s: AppSettings): Background {
        val fallback = Background.Gradient(AppSettings.DEFAULT_GRADIENT_TOP, AppSettings.DEFAULT_GRADIENT_BOTTOM)
        return when (s.mode) {
            BackgroundMode.GRADIENT -> Background.Gradient(s.gradientTop, s.gradientBottom)
            BackgroundMode.SOLID -> Background.Solid(s.solid)
            BackgroundMode.PHOTO -> photos.bitmap(s.photoVersion)?.let { Background.Photo(it, PhotoView(s.photoZoom, s.photoPanX, s.photoPanY)) } ?: fallback
            BackgroundMode.MATCH -> s.captured?.let { c ->
                matchGradientColors(c.primary, c.secondary, c.tertiary).let { colors ->
                    if (colors.size == 1) Background.Solid(colors[0]) else Background.Stops(colors)
                }
            } ?: fallback
        }
    }
}

/** Gradient colours and positions for [background], or null if it is not a gradient. */
internal fun gradientOf(background: Background): Pair<IntArray, FloatArray>? = when (background) {
    is Background.Gradient -> GradientCurve.colors(background.top, background.bottom) to GradientCurve.POSITIONS
    is Background.Stops -> background.colors to FloatArray(background.colors.size) { it / (background.colors.size - 1f) }
    else -> null
}
