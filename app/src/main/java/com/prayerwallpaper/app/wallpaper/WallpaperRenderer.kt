package com.prayerwallpaper.app.wallpaper

import android.app.WallpaperColors
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.format.DateFormat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.R
import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import com.prayerwallpaper.app.data.WhenMissing
import com.prayerwallpaper.app.logic.AccessLayout
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.logic.BlockPlan
import com.prayerwallpaper.app.logic.CardColors
import com.prayerwallpaper.app.logic.Cards
import com.prayerwallpaper.app.logic.Countdown
import com.prayerwallpaper.app.logic.HijriAlign
import com.prayerwallpaper.app.logic.HijriLine
import com.prayerwallpaper.app.logic.PrayerSnapshot
import com.prayerwallpaper.app.logic.TextColors
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.TextTones
import com.prayerwallpaper.app.logic.customTextColors
import com.prayerwallpaper.app.logic.fitEventName
import com.prayerwallpaper.app.logic.fitHijriLine
import com.prayerwallpaper.app.logic.formatClock
import com.prayerwallpaper.app.logic.moonPhaseForHijriDay
import com.prayerwallpaper.app.logic.chooseCard
import com.prayerwallpaper.app.logic.isMixedBackground
import com.prayerwallpaper.app.logic.tonedTextColors
import com.prayerwallpaper.app.settings.TextColorSource
import com.prayerwallpaper.app.logic.photoPlacement
import com.prayerwallpaper.app.logic.meanColor
import com.prayerwallpaper.app.logic.meanLuminance
import com.prayerwallpaper.app.logic.prefersDarkText
import com.prayerwallpaper.app.logic.relativeLuminance
import com.prayerwallpaper.app.logic.textFitScale
import com.prayerwallpaper.app.logic.textColorsFor
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The list's layout. Every size is a fraction of the shorter screen side, so a rotated screen does not blow the list up.
 * [DEFAULT_TOP] is a fraction of the screen height.
 */
object ListLayout {
    const val LIST_WIDTH = 0.80f
    const val COLUMN_GAP = 0.05f
    const val ROW_PITCH = 0.053f
    const val PILL_HEIGHT = 0.056f
    const val PILL_RADIUS = 0.014f
    const val PILL_PADDING = 0.010f
    const val ICON_SIZE = 0.054f
    const val NAME_OFFSET = 0.075f
    const val TEXT_SIZE = 0.040f
    const val SUFFIX_SCALE = 0.64f
    const val SUFFIX_GAP = 0.007f
    const val NAME_TIME_GAP = 0.026f // least space kept between a name and its time
    const val DEFAULT_TOP = 0.40f

    /** Font weights. The highlighted row is marked by the pill alone; raise HIGHLIGHT_ROW_WEIGHT (say 900) to bold it too. */
    const val ROW_WEIGHT = 500
    const val HIGHLIGHT_ROW_WEIGHT = 500
    const val BOLD_WEIGHT = 700

    /**
     * The filled dot at the left edge of the current row in a High contrast card, so the colour is never the only cue. Every
     * other row keeps an empty slot of the same width ([DOT_SLOT]) so the icons and names do not shift. Fractions of one unit
     * like the rest of the row, so they grow with the text.
     */
    const val DOT_DIAMETER = 0.016f
    const val DOT_SLOT = 0.032f
    private const val ROWS = 3

    /**
     * The Hijri date line above the list: a moon icon, then the date, as a row of its own. [HIJRI_SCALE] is its text size as
     * a share of the row text size (1: the same), [HIJRI_GAP] the space between the line's row and the list (half a row),
     * [HIJRI_ALPHA] its opacity, [HIJRI_ICON_GAP] the space between the moon and the text (the same as a prayer icon and its
     * name). All sizes are fractions of the shorter screen side, like the rest of the layout. [HIJRI_BLOCK] is how far the
     * line reaches above the list: its row plus the gap.
     */
    const val HIJRI_SCALE = 1.0f
    const val HIJRI_GAP = ROW_PITCH / 2
    const val HIJRI_ALPHA = 1.0f
    const val HIJRI_ICON_GAP = NAME_OFFSET - PILL_PADDING - ICON_SIZE
    const val HIJRI_BLOCK = HIJRI_GAP + ROW_PITCH

    /**
     * The widest the Hijri line may be, as a share of the shorter screen side: a little wider than the list, since an event
     * name is long. [HIJRI_EVENT_MIN_SCALE] is how far a line with an event may shrink before the name is shortened, and
     * [HIJRI_DATE_MIN_SCALE] how far a date on its own may shrink.
     */
    const val HIJRI_MAX_WIDTH = 0.92f
    const val HIJRI_EVENT_MIN_SCALE = 0.70f

    /** An event name on a line of its own (the big layouts) may shrink further, as its text is still bigger than the usual list's. */
    const val HIJRI_EVENT_LINE_MIN_SCALE = 0.55f
    const val HIJRI_DATE_MIN_SCALE = 0.70f

    /**
     * The countdown line under the list: a row of its own [COUNTDOWN_GAP] (half a row, as above the list) clear of the list's
     * last row, so the list does not move when it is shown. [COUNTDOWN_SCALE] is its text size as a share of the row text
     * size, [COUNTDOWN_ALPHA] its opacity. [COUNTDOWN_BLOCK] is how far it reaches below the list: the gap plus its row. It
     * may be as wide as the Hijri line ([HIJRI_MAX_WIDTH]) and shrinks to [COUNTDOWN_MIN_SCALE] of its size before the
     * renderer squeezes it.
     */
    const val COUNTDOWN_SCALE = 0.85f
    const val COUNTDOWN_GAP = ROW_PITCH / 2
    const val COUNTDOWN_ALPHA = 1.0f
    const val COUNTDOWN_BLOCK = COUNTDOWN_GAP + ROW_PITCH
    const val COUNTDOWN_MIN_SCALE = 0.70f

    /** The three-row list's height as a fraction of the screen height. */
    fun heightFraction(width: Int, height: Int): Float = ROWS * ROW_PITCH * min(width, height) / height

    /** How far the countdown line reaches below the list, as a fraction of the screen height. */
    fun countdownFraction(width: Int, height: Int): Float = COUNTDOWN_BLOCK * min(width, height) / height

    /**
     * Largest top position that still keeps the whole list on screen, as a fraction of the height, and the countdown line
     * under it when [countdown] is true. A tall phone is held by the 0.85 cap either way; a nearly square screen is not.
     */
    fun maxTop(width: Int, height: Int, countdown: Boolean = false): Float =
        (1f - heightFraction(width, height) - (if (countdown) countdownFraction(width, height) else 0f) - 0.03f).coerceAtMost(0.85f)
}

private const val SAMPLE_WIDTH = 48

/** Icon sizes kept at once: the usual list, Large, Extra large, and one spare for another screen. */
private const val MAX_ICON_SETS = 4

/** Draws the background and, on the lock screen, the prayer list. No state beyond paints and icons. */
class WallpaperRenderer(private val context: Context) {
    /** Name left-aligned, time and its small am/pm suffix right-aligned. */
    private class RowPaints(weight: Int) {
        val name = paint(weight, Paint.Align.LEFT)
        val time = paint(weight, Paint.Align.RIGHT)
        val suffix = paint(weight, Paint.Align.RIGHT)

        fun weight(weight: Int) {
            name.setWeight(weight)
            time.setWeight(weight)
            suffix.setWeight(weight)
        }

        fun style(size: Float, color: Int, suffixScale: Float = ListLayout.SUFFIX_SCALE) {
            name.textSize = size
            time.textSize = size
            suffix.textSize = size * suffixScale
            name.color = color
            time.color = color
            suffix.color = color
        }
    }

    private val iconResources = mapOf(
        Prayer.SUBUH to R.drawable.ic_prayer_subuh,
        Prayer.SYURUK to R.drawable.ic_prayer_syuruk,
        Prayer.ZOHOR to R.drawable.ic_prayer_zohor,
        Prayer.ASAR to R.drawable.ic_prayer_asar,
        Prayer.MAGHRIB to R.drawable.ic_prayer_maghrib,
        Prayer.ISYAK to R.drawable.ic_prayer_isyak,
    )

    /**
     * One set of icon drawables for each icon size in use. A vector drawable keeps a cache sized for the biggest it has
     * been drawn at, and reusing one at a smaller size gives slightly thinner strokes until the process restarts, so a
     * list that switches between sizes (Large text, then the usual list) gets its own set for each.
     */
    private val iconSets = LinkedHashMap<Int, Map<Prayer, Drawable>>()

    private fun iconsFor(sizePx: Int): Map<Prayer, Drawable> = iconSets.getOrPut(sizePx) {
        if (iconSets.size >= MAX_ICON_SETS) iconSets.remove(iconSets.keys.first())
        iconResources.mapValues { (_, res) -> context.getDrawable(res)!!.mutate() }
    }

    private val loadMessage = context.getString(R.string.msg_load_times)
    private val updateMessage = context.getString(R.string.msg_update_times)
    private val calculateMessage = context.getString(R.string.msg_cannot_calculate)

    private val backgroundPaint = Paint(Paint.DITHER_FLAG)
    private val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val regular = RowPaints(ListLayout.ROW_WEIGHT)
    private val highlighted = RowPaints(ListLayout.HIGHLIGHT_ROW_WEIGHT)
    private val messagePaint = paint(500, Paint.Align.CENTER)
    private val hijriPaint = paint(ListLayout.ROW_WEIGHT, Paint.Align.LEFT)
    private val countdownPaint = paint(ListLayout.ROW_WEIGHT, Paint.Align.LEFT)
    private val moon = MoonIcon()
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardRect = RectF()

    private val pill = RectF()
    private val photoBounds = RectF()

    private var sample: Bitmap? = null
    private var samplePixels = IntArray(0)
    private var thumbSource: Bitmap? = null
    private var thumb: Bitmap? = null

    /**
     * [missing] picks the message when [snapshot] has no timetable for today. [drawList] is false
     * on the home screen, which gets the background alone. [names] picks the prayer names shown. [hijri] is the
     * Hijri date line to put above the list, with a moon icon for its day of the month and the Islamic event on that day if
     * there is one, or null for none. [countdown] is the line under the list (the next prayer and the time left), or null for
     * none; it needs a timetable for today, so it is not drawn with a message. [hijriAlign] centres both lines or starts
     * them at the list's left edge. [access] is the accessibility options: Large and Extra large draw one column of big
     * rows (see [planBlock]); with the standard options nothing here differs from the usual two-column list.
     */
    fun draw(
        canvas: Canvas, width: Int, height: Int, snapshot: PrayerSnapshot, missing: WhenMissing, drawList: Boolean, look: Look,
        names: NameSet = NameSet.MALAY, hijri: HijriLine? = null, hijriAlign: HijriAlign = HijriAlign.CENTER,
        countdown: Countdown? = null, access: AccessOptions = AccessOptions(),
    ) {
        paintBackground(canvas, width, height, look)
        if (!drawList) return

        val day = snapshot.day
        if (day != null) {
            // Whether the Hijri line takes one line or two (an event on its own line, in the big layouts) is decided now; the
            // room left for it is always two, so the text size does not change from the day with an event to the day without.
            val lines = when {
                hijri == null -> 0
                hijri.event != null -> 2
                else -> 1
            }
            val plan = planFor(width, height, access, look.listTop, lines, countdown != null)
            if (plan != null) {
                drawBlock(canvas, width, height, snapshot, day, plan, look, names, hijri, hijriAlign, countdown, access)
                return
            }
        }
        // The usual list. Bold text is the only accessibility option that reaches it, and only changes the weights.
        setWeights(if (access.bold) ListLayout.BOLD_WEIGHT else ListLayout.ROW_WEIGHT, if (access.bold) ListLayout.BOLD_WEIGHT else ListLayout.HIGHLIGHT_ROW_WEIGHT)

        val unit = min(width, height).toFloat()
        // With the countdown showing, a list parked very low is lifted just enough to keep the line on screen.
        val listTop = if (countdown != null) min(look.listTop, ListLayout.maxTop(width, height, countdown = true)) else look.listTop
        val top = listTop * height
        val pitch = ListLayout.ROW_PITCH * unit
        val listWidth = ListLayout.LIST_WIDTH * unit
        val left = (width - listWidth) / 2
        val theme = themeBehind(look, width, height, left, top, listWidth, 3 * pitch)

        if (day == null) {
            messagePaint.textSize = ListLayout.TEXT_SIZE * unit * 1.05f
            messagePaint.color = theme.text
            val text = when (missing) {
                WhenMissing.LOAD -> loadMessage
                WhenMissing.UPDATE -> updateMessage
                WhenMissing.CANNOT_CALCULATE -> calculateMessage
            }
            canvas.drawText(text, width / 2f, baselineIn(messagePaint, top, pitch), messagePaint)
            hijri?.let { drawHijri(canvas, standardHijriLine(unit, top, left, ListLayout.TEXT_SIZE * unit), it, hijriAlign, width.toFloat(), theme) }
            return
        }

        val gap = ListLayout.COLUMN_GAP * unit
        val columnWidth = (listWidth - gap) / 2
        // The phone's 12/24-hour setting, read on every draw so a change shows the next time the screen wakes.
        val is24Hour = DateFormat.is24HourFormat(context)
        val textSize = fittedTextSize(day, unit, columnWidth, theme.text, is24Hour, names)
        regular.style(textSize, theme.text)
        highlighted.style(textSize, theme.text)
        pillPaint.color = theme.pill
        for ((index, prayer) in Prayer.entries.withIndex()) {
            val columnLeft = left + (index / 3) * (columnWidth + gap)
            val rowTop = top + (index % 3) * pitch
            drawRow(canvas, unit, day.label(prayer, names), prayer, day[prayer], prayer == snapshot.highlight, is24Hour, columnLeft, columnWidth, rowTop, pitch, iconTint = theme.icon)
        }
        hijri?.let { drawHijri(canvas, standardHijriLine(unit, top, left, textSize), it, hijriAlign, width.toFloat(), theme) }
        countdown?.let { drawCountdown(canvas, standardCountdownLine(unit, top + 3 * pitch, left, textSize), it, hijriAlign, width.toFloat(), theme) }
    }

    /**
     * The list when an accessibility option needs a [plan]: the big one-column rows, or the usual two columns inside a High
     * contrast card. The rows are drawn by the same code as the usual ones: everything in it is a fraction of one unit, so a
     * bigger unit gives bigger text, icons and padding.
     */
    private fun drawBlock(
        canvas: Canvas, width: Int, height: Int, snapshot: PrayerSnapshot, day: PrayerDay, plan: BlockPlan, look: Look,
        names: NameSet, hijri: HijriLine?, align: HijriAlign, countdown: Countdown?, access: AccessOptions,
    ) {
        val unit = min(width, height).toFloat()
        val big = plan.size != TextSize.STANDARD
        val rowsPerColumn = if (big) AccessLayout.ROWS else 3
        val rowUnit = if (big) plan.text / ListLayout.TEXT_SIZE else unit

        val card = if (access.highContrast) {
            val luminance = luminanceBehind(look, width, height, plan.left, plan.top, plan.width, plan.bottom - plan.top)
            val chosen = chooseCard(access.cardStyle, luminance)
            if (BuildConfig.DEBUG) android.util.Log.d("PrayerCard", "style=${access.cardStyle} luminance=%.3f -> $chosen".format(luminance))
            Cards.of(chosen)
        } else {
            null
        }
        val theme = if (card != null) {
            TextColors(card.text, card.text, card.highlight)
        } else {
            themeBehind(look, width, height, plan.contentLeft, plan.listTop, plan.contentWidth, rowsPerColumn * plan.pitch).let {
                TextColors(it.text, it.icon, strongPill(it.text))
            }
        }

        val bold = access.bold
        setWeights(if (bold) ListLayout.BOLD_WEIGHT else ListLayout.ROW_WEIGHT, if (bold || big || card != null) ListLayout.BOLD_WEIGHT else ListLayout.HIGHLIGHT_ROW_WEIGHT)
        val is24Hour = DateFormat.is24HourFormat(context)
        val suffixScale = if (big) AccessLayout.SUFFIX_SCALE else ListLayout.SUFFIX_SCALE
        val suffixGap = if (big) AccessLayout.SUFFIX_GAP * plan.text else ListLayout.SUFFIX_GAP * unit
        val gap = ListLayout.COLUMN_GAP * unit
        val columnWidth = if (plan.columns == 1) plan.contentWidth else (plan.contentWidth - gap) / 2
        val dotSlot = if (card != null) ListLayout.DOT_SLOT * rowUnit else 0f
        val textSize = fittedTextSize(day, rowUnit, columnWidth, theme.text, is24Hour, names, suffixScale, suffixGap, dotSlot)
        regular.style(textSize, theme.text, suffixScale)
        highlighted.style(textSize, card?.highlightText ?: theme.text, suffixScale)
        pillPaint.color = theme.pill
        if (card != null) drawCard(canvas, unit, plan, card)

        for ((index, prayer) in Prayer.entries.withIndex()) {
            val current = prayer == snapshot.highlight
            // On the card the current row's icon is in the highlight's text colour, the others in the card's.
            val iconTint = if (card == null) theme.icon else if (current) card.highlightText else card.text
            val columnLeft = plan.contentLeft + (index / rowsPerColumn) * (columnWidth + gap)
            val rowTop = plan.listTop + (index % rowsPerColumn) * plan.pitch
            drawRow(
                canvas, rowUnit, day.label(prayer, names), prayer, day[prayer], current, is24Hour, columnLeft, columnWidth, rowTop, plan.pitch,
                suffixGap, dotSlot, card?.highlightText ?: 0, iconTint,
            )
        }
        if (card != null) drawDividers(canvas, unit, plan, card, snapshot, rowsPerColumn, columnWidth, gap, rowUnit)

        // The moon and the text of the lines start in line with the icons, which the dot's empty slot pushes in on the card.
        val lineLeft = plan.contentLeft + ListLayout.PILL_PADDING * rowUnit + dotSlot
        if (big) {
            // The moon and the gap after it keep the proportions they have beside the standard text.
            val iconSide = ListLayout.ICON_SIZE / ListLayout.TEXT_SIZE * plan.lineText
            val iconGap = ListLayout.HIJRI_ICON_GAP / ListLayout.TEXT_SIZE * plan.lineText
            // Centred, a line may use the whole content width; from the left it runs to where the times end.
            val rightEdge = plan.contentLeft + plan.contentWidth - ListLayout.PILL_PADDING * rowUnit
            hijri?.let {
                val date = LineGeometry(plan.dateRowTop!!, plan.lineRow, plan.lineText, iconSide, iconGap, lineLeft, plan.contentWidth, rightEdge)
                drawHijri(canvas, date, it, align, width.toFloat(), theme, eventRowTop = plan.eventRowTop)
            }
            countdown?.let {
                val line = LineGeometry(plan.countdownRowTop!!, plan.lineRow, plan.lineText, 0f, 0f, lineLeft, plan.contentWidth, rightEdge)
                drawCountdown(canvas, line, it, align, width.toFloat(), theme)
            }
        } else {
            val rightEdge = plan.contentLeft + plan.contentWidth - ListLayout.PILL_PADDING * rowUnit
            hijri?.let { drawHijri(canvas, standardHijriLine(unit, plan.listTop, plan.contentLeft + dotSlot, textSize, plan.contentWidth, rightEdge), it, align, width.toFloat(), theme) }
            countdown?.let { drawCountdown(canvas, standardCountdownLine(unit, plan.listTop + 3 * plan.pitch, plan.contentLeft + dotSlot, textSize, plan.contentWidth, rightEdge), it, align, width.toFloat(), theme) }
        }
    }

    /** The card behind the block: solid, rounded, with an edge in the opposite colour so it shows on any background. */
    private fun drawCard(canvas: Canvas, unit: Float, plan: BlockPlan, card: CardColors) {
        val radius = AccessLayout.CARD_RADIUS * unit
        val stroke = AccessLayout.CARD_BORDER * unit
        cardRect.set(plan.left, plan.top, plan.left + plan.width, plan.bottom)
        cardPaint.style = Paint.Style.FILL
        cardPaint.color = card.background
        canvas.drawRoundRect(cardRect, radius, radius, cardPaint)
        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = stroke
        cardPaint.color = card.border
        cardRect.inset(stroke / 2, stroke / 2)
        canvas.drawRoundRect(cardRect, radius - stroke / 2, radius - stroke / 2, cardPaint)
    }

    /** A thin line between rows, none under the last row of a column and none beside the current row, whose highlight is its own edge. */
    private fun drawDividers(
        canvas: Canvas, unit: Float, plan: BlockPlan, card: CardColors, snapshot: PrayerSnapshot, rowsPerColumn: Int,
        columnWidth: Float, gap: Float, rowUnit: Float,
    ) {
        cardPaint.style = Paint.Style.STROKE
        cardPaint.strokeWidth = AccessLayout.DIVIDER * unit
        cardPaint.color = card.divider
        val inset = ListLayout.PILL_PADDING * rowUnit
        for (column in 0 until plan.columns) {
            val columnLeft = plan.contentLeft + column * (columnWidth + gap)
            for (row in 0 until rowsPerColumn - 1) {
                val above = Prayer.entries[column * rowsPerColumn + row]
                val below = Prayer.entries[column * rowsPerColumn + row + 1]
                if (above == snapshot.highlight || below == snapshot.highlight) continue
                val y = plan.listTop + (row + 1) * plan.pitch
                canvas.drawLine(columnLeft + inset, y, columnLeft + columnWidth - inset, y, cardPaint)
            }
        }
    }

    /** The pill behind the current row when there is no card: a stronger one than the usual, white on light text and black on dark. */
    private fun strongPill(textColor: Int): Int =
        if (relativeLuminance(textColor) > 0.5f) (0x4D shl 24) or 0xFFFFFF else (0x38 shl 24)

    private fun setWeights(regularWeight: Int, highlightWeight: Int) {
        regular.weight(regularWeight)
        highlighted.weight(highlightWeight)
        hijriPaint.setWeight(regularWeight)
        countdownPaint.setWeight(regularWeight)
    }

    /** Where a line sits and how big it is: its row, its text, the moon beside the date, and the room it may use. */
    private class LineGeometry(
        val rowTop: Float, val rowHeight: Float, val textSize: Float, val iconSide: Float, val iconGap: Float,
        val leftStart: Float, val maxWidth: Float, val rightEdge: Float? = null,
    ) {
        /** The room a line has: [maxWidth] centred, or from [leftStart] to the right edge, which is the centred box's unless given. */
        fun available(centered: Boolean, width: Float): Float =
            if (centered) maxWidth else (rightEdge ?: ((width + maxWidth) / 2)) - leftStart
    }

    /** The Hijri line above the standard list: a row of its own [ListLayout.HIJRI_GAP] clear of it, in the list's text size. */
    private fun standardHijriLine(unit: Float, top: Float, listLeft: Float, listTextSize: Float, maxWidth: Float = ListLayout.HIJRI_MAX_WIDTH * unit, rightEdge: Float? = null) = LineGeometry(
        rowTop = top - ListLayout.HIJRI_BLOCK * unit, rowHeight = ListLayout.ROW_PITCH * unit,
        textSize = listTextSize * ListLayout.HIJRI_SCALE, iconSide = ListLayout.ICON_SIZE * unit,
        iconGap = ListLayout.HIJRI_ICON_GAP * unit, leftStart = listLeft + ListLayout.PILL_PADDING * unit,
        maxWidth = maxWidth, rightEdge = rightEdge,
    )

    /** The countdown under the standard list: a row of its own [ListLayout.COUNTDOWN_GAP] below [listBottom]. */
    private fun standardCountdownLine(unit: Float, listBottom: Float, listLeft: Float, listTextSize: Float, maxWidth: Float = ListLayout.HIJRI_MAX_WIDTH * unit, rightEdge: Float? = null) = LineGeometry(
        rowTop = listBottom + ListLayout.COUNTDOWN_GAP * unit, rowHeight = ListLayout.ROW_PITCH * unit,
        textSize = listTextSize * ListLayout.COUNTDOWN_SCALE, iconSide = 0f, iconGap = 0f,
        leftStart = listLeft + ListLayout.PILL_PADDING * unit, maxWidth = maxWidth, rightEdge = rightEdge,
    )

    /**
     * One line as a row of its own: a moon icon for the day of the month, the date, and the event's name after a dot when
     * the day has one. The text is the list's font and colour; the icon is drawn like the prayer icons, in the icon colour.
     * Centred, or from the list's left edge in line with the prayer icons. [fitHijriLine] shrinks it or shortens a long
     * event name to fit. With an [eventRowTop] (the big text sizes) the event goes on a line of its own under the date.
     */
    private fun drawHijri(
        canvas: Canvas, g: LineGeometry, line: HijriLine, align: HijriAlign, width: Float, theme: TextColors, eventRowTop: Float? = null,
    ) {
        hijriPaint.textSize = g.textSize
        hijriPaint.color = ((Color.alpha(theme.text) * ListLayout.HIJRI_ALPHA).roundToInt() shl 24) or (theme.text and 0xFFFFFF)
        val centered = align == HijriAlign.CENTER
        // Centred, the line may use the whole allowance either side of the middle; from the left it runs to the same right edge.
        val available = g.available(centered, width)
        val fit = fitHijriLine(
            { hijriPaint.measureText(it) }, g.iconSide + g.iconGap, line.date.text, if (eventRowTop == null) line.event else null, available,
            ListLayout.HIJRI_EVENT_MIN_SCALE, ListLayout.HIJRI_DATE_MIN_SCALE,
        )
        hijriPaint.textSize *= fit.scale

        val side = g.iconSide * fit.scale
        val gap = g.iconGap * fit.scale
        val textWidth = hijriPaint.measureText(fit.text)
        val start = if (centered) width / 2 - (side + gap + textWidth) / 2 else g.leftStart
        moon.draw(canvas, moonPhaseForHijriDay(line.date.day), start, g.rowTop + g.rowHeight / 2 - side / 2, side, theme.icon)
        canvas.drawText(fit.text, start + side + gap, baselineIn(hijriPaint, g.rowTop, g.rowHeight), hijriPaint)

        val event = line.event
        if (eventRowTop != null && event != null) {
            hijriPaint.textSize = g.textSize
            val eventFit = fitEventName({ hijriPaint.measureText(it) }, event, available, ListLayout.HIJRI_EVENT_LINE_MIN_SCALE)
            hijriPaint.textSize *= eventFit.scale
            val eventWidth = hijriPaint.measureText(eventFit.text)
            // Centred on its own, or from the left edge, under the moon.
            val eventStart = if (centered) width / 2 - eventWidth / 2 else g.leftStart
            canvas.drawText(eventFit.text, eventStart, baselineIn(hijriPaint, eventRowTop, g.rowHeight), hijriPaint)
        }
    }

    /**
     * One line as a row of its own under the list: the next prayer and the time left, in the list's font and colour a
     * little smaller. Centred, or from the list's left edge in line with the prayer icons, the same as the Hijri line
     * above. A font wider than Roboto shrinks it, then squeezes it, rather than let it run off the screen.
     */
    private fun drawCountdown(canvas: Canvas, g: LineGeometry, countdown: Countdown, align: HijriAlign, width: Float, theme: TextColors) {
        val text = countdown.text
        countdownPaint.textSize = g.textSize
        countdownPaint.color = ((Color.alpha(theme.text) * ListLayout.COUNTDOWN_ALPHA).roundToInt() shl 24) or (theme.text and 0xFFFFFF)
        val centered = align == HijriAlign.CENTER
        val available = g.available(centered, width)
        countdownPaint.textSize *= textFitScale(countdownPaint.measureText(text), available, ListLayout.COUNTDOWN_MIN_SCALE)
        val natural = countdownPaint.measureText(text)
        val squeeze = if (natural > available) available / natural else 1f
        val start = if (centered) width / 2 - natural * squeeze / 2 else g.leftStart
        countdownPaint.textScaleX = squeeze
        canvas.drawText(text, start, baselineIn(countdownPaint, g.rowTop, g.rowHeight), countdownPaint)
        countdownPaint.textScaleX = 1f
    }

    /**
     * A small render of the background and its dim scrim, no list. It is what the system reads to
     * theme the clock, and what decides between light and dark text. Valid until the next call.
     */
    fun sample(look: Look, width: Int, height: Int): Bitmap {
        val sampleHeight = max(1, (SAMPLE_WIDTH.toFloat() * height / width).roundToInt())
        val bitmap = sample?.takeIf { it.width == SAMPLE_WIDTH && it.height == sampleHeight }
            ?: createBitmap(SAMPLE_WIDTH, sampleHeight).also { sample = it }
        paintBackground(Canvas(bitmap), SAMPLE_WIDTH, sampleHeight, look, forSample = true)
        return bitmap
    }

    /**
     * Whether the background (dim included) is a mix of bright and dark behind the given box, for the preview's suggestion
     * of a dim. The user's own dim setting is never changed by it.
     */
    fun isMixedBehind(look: Look, width: Int, height: Int, left: Float, top: Float, boxWidth: Float, boxHeight: Float): Boolean {
        val bitmap = sample(look, width, height)
        if (samplePixels.size != bitmap.width * bitmap.height) samplePixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(samplePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val sx = bitmap.width / width.toFloat()
        val sy = bitmap.height / height.toFloat()
        val x0 = floor(left * sx).toInt().coerceIn(0, bitmap.width - 1)
        val x1 = ceil((left + boxWidth) * sx).toInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = floor(top * sy).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = ceil((top + boxHeight) * sy).toInt().coerceIn(y0 + 1, bitmap.height)
        return isMixedBackground(samplePixels, bitmap.width, x0, y0, x1, y1)
    }

    /** The mean luminance of the background, dim included, in the given box: what Auto looks at to pick the card. */
    private fun luminanceBehind(look: Look, width: Int, height: Int, left: Float, top: Float, boxWidth: Float, boxHeight: Float): Float {
        val bitmap = sample(look, width, height)
        if (samplePixels.size != bitmap.width * bitmap.height) samplePixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(samplePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val sx = bitmap.width / width.toFloat()
        val sy = bitmap.height / height.toFloat()
        val x0 = floor(left * sx).toInt().coerceIn(0, bitmap.width - 1)
        val x1 = ceil((left + boxWidth) * sx).toInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = floor(top * sy).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = ceil((top + boxHeight) * sy).toInt().coerceIn(y0 + 1, bitmap.height)
        return meanLuminance(samplePixels, bitmap.width, x0, y0, x1, y1)
    }

    private fun themeBehind(look: Look, width: Int, height: Int, left: Float, top: Float, listWidth: Float, listHeight: Float): TextColors {
        val bitmap = sample(look, width, height)
        when (look.textColors) {
            TextColorSource.CUSTOM -> return customTextColors(look.customTextColor)
            TextColorSource.SYSTEM -> {
                // Light or dark exactly as the system decides it for its own clock: from the colour hints of the whole image.
                val darkText = WallpaperColors.fromBitmap(bitmap).colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
                return tonedTextColors(darkText, systemTones())
            }
            TextColorSource.BACKGROUND -> Unit
        }
        if (samplePixels.size != bitmap.width * bitmap.height) samplePixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(samplePixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val sx = bitmap.width / width.toFloat()
        val sy = bitmap.height / height.toFloat()
        val x0 = floor(left * sx).toInt().coerceIn(0, bitmap.width - 1)
        val x1 = ceil((left + listWidth) * sx).toInt().coerceIn(x0 + 1, bitmap.width)
        val y0 = floor(top * sy).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = ceil((top + listHeight) * sy).toInt().coerceIn(y0 + 1, bitmap.height)
        val dark = prefersDarkText(meanLuminance(samplePixels, bitmap.width, x0, y0, x1, y1))
        return textColorsFor(meanColor(samplePixels, bitmap.width, x0, y0, x1, y1), dark)
    }

    /** The system's Material You tones, read afresh each time because they follow the wallpaper. */
    private fun systemTones() = TextTones(
        light = context.getColor(android.R.color.system_accent1_100),
        lightDim = context.getColor(android.R.color.system_accent1_200),
        dark = context.getColor(android.R.color.system_accent2_600),
        darkDim = context.getColor(android.R.color.system_accent2_700),
    )

    /**
     * The text size to use: the standard size, or a little smaller when the phone's font is wide
     * enough that the widest row (a name, its time and the gap between) would not fit in a column.
     * One size for the whole list, so rows keep their proportions.
     */
    private fun fittedTextSize(
        day: PrayerDay, unit: Float, columnWidth: Float, color: Int, is24Hour: Boolean, names: NameSet,
        suffixScale: Float = ListLayout.SUFFIX_SCALE, suffixGapSize: Float = ListLayout.SUFFIX_GAP * unit, extraLeft: Float = 0f,
    ): Float {
        val base = ListLayout.TEXT_SIZE * unit
        regular.style(base, color, suffixScale)
        highlighted.style(base, color, suffixScale)
        val available = columnWidth - ListLayout.PILL_PADDING * unit - ListLayout.NAME_OFFSET * unit - extraLeft
        var needed = 0f
        for (prayer in Prayer.entries) {
            val clock = formatClock(day[prayer], is24Hour)
            val suffixGap = if (clock.suffix.isEmpty()) 0f else suffixGapSize
            for (paints in arrayOf(regular, highlighted)) {
                needed = max(
                    needed,
                    paints.name.measureText(day.label(prayer, names)) + ListLayout.NAME_TIME_GAP * unit +
                        paints.time.measureText(clock.time) + suffixGap + paints.suffix.measureText(clock.suffix),
                )
            }
        }
        return base * textFitScale(needed, available)
    }

    private fun paintBackground(canvas: Canvas, width: Int, height: Int, look: Look, forSample: Boolean = false) {
        val w = width.toFloat()
        val h = height.toFloat()
        when (val background = look.background) {
            is Background.Solid -> canvas.drawColor(background.color)
            is Background.Photo -> drawPhoto(canvas, w, h, background, forSample)
            else -> {
                val (colors, positions) = gradientOf(background)!!
                backgroundPaint.shader = LinearGradient(0f, 0f, 0f, h, colors, positions, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, backgroundPaint)
                backgroundPaint.shader = null
            }
        }
        if (look.dim > 0f) canvas.drawColor(Color.argb((look.dim * 255).roundToInt(), 0, 0, 0))
    }

    /** Draws the photo framed by its zoom and position. Samples use a pre-shrunk copy so thin detail still averages out. */
    private fun drawPhoto(canvas: Canvas, w: Float, h: Float, photo: Background.Photo, forSample: Boolean) {
        val source = if (forSample) shrunk(photo.bitmap) else photo.bitmap
        val p = photoPlacement(source.width, source.height, w, h, photo.view)
        photoBounds.set(p.left, p.top, p.left + p.width, p.top + p.height)
        canvas.drawBitmap(source, null, photoBounds, photoPaint)
    }

    /** Halves [bitmap] until it is under about four times the sample width. Each exact halving is a box filter. */
    private fun shrunk(bitmap: Bitmap): Bitmap {
        if (thumbSource === bitmap) return thumb!!
        var current = bitmap
        while (current.width > SAMPLE_WIDTH * 4 && current.height > 8) {
            current = current.scale(current.width / 2, current.height / 2)
        }
        thumbSource = bitmap
        thumb = current
        return current
    }

    private fun drawRow(
        canvas: Canvas, unit: Float, name: String, prayer: Prayer, time: LocalTime, current: Boolean, is24Hour: Boolean,
        columnLeft: Float, columnWidth: Float, rowTop: Float, pitch: Float, suffixGap: Float = ListLayout.SUFFIX_GAP * unit,
        dotSlot: Float = 0f, dotColor: Int = 0, iconTint: Int = 0,
    ) {
        val centerY = rowTop + pitch / 2
        val padding = ListLayout.PILL_PADDING * unit
        val columnRight = columnLeft + columnWidth
        if (current) {
            val half = ListLayout.PILL_HEIGHT * unit / 2
            pill.set(columnLeft, centerY - half, columnRight, centerY + half)
            val radius = ListLayout.PILL_RADIUS * unit
            canvas.drawRoundRect(pill, radius, radius, pillPaint)
        }

        if (current && dotSlot > 0f) {
            dotPaint.color = dotColor
            canvas.drawCircle(columnLeft + padding + 0.45f * dotSlot, centerY, ListLayout.DOT_DIAMETER * unit / 2, dotPaint)
        }

        val iconSize = (ListLayout.ICON_SIZE * unit).toInt()
        val iconLeft = (columnLeft + padding + dotSlot).toInt()
        val iconTop = (centerY - iconSize / 2f).toInt()
        iconsFor(iconSize).getValue(prayer).apply {
            setTint(iconTint)
            setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            draw(canvas)
        }

        val paints = if (current) highlighted else regular
        val baseline = baselineIn(paints.name, rowTop, pitch)

        val clock = formatClock(time, is24Hour)
        val right = columnRight - padding
        var timeRight = right
        if (clock.suffix.isNotEmpty()) {
            canvas.drawText(clock.suffix, right, baseline, paints.suffix)
            timeRight = right - paints.suffix.measureText(clock.suffix) - suffixGap
        }
        canvas.drawText(clock.time, timeRight, baseline, paints.time)

        // Other fonts are wider than Roboto: squeeze the name rather than let it run into the time.
        val nameLeft = columnLeft + ListLayout.NAME_OFFSET * unit + dotSlot
        val room = timeRight - paints.time.measureText(clock.time) - ListLayout.NAME_TIME_GAP * unit - nameLeft
        val nameWidth = paints.name.measureText(name)
        paints.name.textScaleX = if (nameWidth > room) room / nameWidth else 1f
        canvas.drawText(name, nameLeft, baseline, paints.name)
        paints.name.textScaleX = 1f
    }

    /** The baseline that centres [paint]'s text vertically in a box starting at [boxTop]. */
    private fun baselineIn(paint: Paint, boxTop: Float, boxHeight: Float): Float {
        val metrics = paint.fontMetrics
        return boxTop + boxHeight / 2 - (metrics.ascent + metrics.descent) / 2
    }
}

private fun Paint.setWeight(weight: Int) {
    if (typeface?.weight != weight) typeface = Typeface.create(Typeface.DEFAULT, weight, false)
}

private fun paint(weight: Int, align: Paint.Align) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = align
    typeface = Typeface.create(Typeface.DEFAULT, weight, false)
}
