package com.prayerwallpaper.app.wallpaper

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.prayerwallpaper.app.logic.MoonPhase

/**
 * A moon in the style of the prayer icons: a 24 unit grid, an outline circle of the same radius and stroke weight, with
 * the lit part filled so the phase reads at a glance. Waxing phases are lit on the right, waning ones on the left. One
 * instance draws all eight phases; it keeps its paints and path so drawing allocates nothing.
 */
class MoonIcon {
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val lit = Path()
    private val disc = RectF()
    private val terminator = RectF()

    /** Draws [phase] in the square at [left], [top] of side [size], in [color]. */
    fun draw(canvas: Canvas, phase: MoonPhase, left: Float, top: Float, size: Float, color: Int) {
        val unit = size / GRID
        val cx = left + GRID / 2 * unit
        val cy = top + GRID / 2 * unit
        val r = RADIUS * unit
        outline.color = color
        outline.strokeWidth = STROKE * unit
        fill.color = color

        when (phase) {
            MoonPhase.NEW -> Unit
            MoonPhase.FULL -> canvas.drawCircle(cx, cy, r, fill)
            else -> {
                buildLit(phase, cx, cy, r, TERMINATOR * unit)
                canvas.drawPath(lit, fill)
            }
        }
        canvas.drawCircle(cx, cy, r, outline)
    }

    /**
     * The lit shape: half the rim on the lit side, then back along the terminator, an ellipse that is as tall as the moon and
     * [bulge] wide each side of the centre. The terminator bulges towards the lit edge for a crescent (a thin sliver) and
     * away from it for a gibbous moon (most of the disc); a quarter is a straight line.
     */
    private fun buildLit(phase: MoonPhase, cx: Float, cy: Float, r: Float, bulge: Float) {
        val waxing = phase == MoonPhase.WAXING_CRESCENT || phase == MoonPhase.FIRST_QUARTER || phase == MoonPhase.WAXING_GIBBOUS
        val crescent = phase == MoonPhase.WAXING_CRESCENT || phase == MoonPhase.WANING_CRESCENT
        val quarter = phase == MoonPhase.FIRST_QUARTER || phase == MoonPhase.LAST_QUARTER
        disc.set(cx - r, cy - r, cx + r, cy + r)
        terminator.set(cx - bulge, cy - r, cx + bulge, cy + r)

        lit.rewind()
        lit.moveTo(cx, cy - r)
        // Angles run clockwise from 3 o'clock: the top is -90, the bottom 90, the right 0 and the left 180.
        lit.arcTo(disc, -90f, if (waxing) 180f else -180f) // down the lit side's rim, to the bottom
        if (quarter) {
            lit.lineTo(cx, cy - r)
        } else {
            // Back up to the top through the right of the terminator when that is the lit side's edge, else the left.
            val viaRight = waxing == crescent
            lit.arcTo(terminator, 90f, if (viaRight) -180f else 180f)
        }
        lit.close()
    }

    private companion object {
        const val GRID = 24f
        const val RADIUS = 8f        // as the prayer icons
        const val STROKE = 1.9f      // as the prayer icons
        const val TERMINATOR = 4.4f
    }
}
