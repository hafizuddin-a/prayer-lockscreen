package com.prayerwallpaper.app.logic

/** The width, in dp, of [text] drawn at [sizeDp], bold or not. The Android side measures with a Paint; the tests use a stand-in. */
fun interface TextMeasure {
    fun widthDp(text: String, sizeDp: Float, bold: Boolean): Float
}

/** The numbers every widget layout works from. Sizes are in sp at font scale 1, and a line is [LINE] times its text size tall. */
object WidgetMetrics {
    /** Nothing that tells the user something is ever smaller than this. If it does not fit at this size it is dropped. */
    const val MIN_SP = 11f
    const val LINE = 1.2f
    const val PAD_H = 12f
    const val PAD_V = 6f
    const val GAP = 2f
    const val ICON_DP = 14f
    const val ICON_GAP_DP = 4f
    const val MAIN_GAP_DP = 8f

    // The next prayer widget at font scale 1: name 20, time 17, and the Hijri date and the countdown, both 11 and not bold.
    const val NEXT_NAME = 20f
    const val NEXT_TIME = 17f
    const val NEXT_TOP = 11f

    /** The widest a countdown reads before the next prayer: `in 20:00:00`. */
    const val COUNTDOWN_WORST = "in 20:00:00"

    /** A widget grows its text up to this many times when it has the room (2x1 up to 4x2). */
    const val NEXT_MAX_GROW = 2.2f

    // Today's times (4x2) at font scale 1: row text 14, Hijri 12, footer 12, icons 18 dp.
    const val TODAY_TEXT = 14f
    const val TODAY_HEADER = 12f
    const val TODAY_FOOTER = 12f
    const val TODAY_ICON_DP = 18f
    const val TODAY_PAD_H = 12f
    const val TODAY_PAD_V = 8f
    const val TODAY_COL_GAP = 8f
    /** The room a row's own padding, icon gap, time gap and (on a card) dot take besides the words. */
    const val ROW_CHROME_DP = 4f + 6f + 4f + 4f
    const val ROW_DOT_DP = 8f
    const val ROW_MIN_PAD = 4f
    const val TODAY_MAX_GROW = 1.3f

    // All six in a row (4x1) at font scale 1: icon 18 dp, name 11, time 11.
    const val STRIP_TEXT = 11f
    const val STRIP_ICON_DP = 18f
    const val STRIP_PAD_H = 6f
    const val STRIP_PAD_V = 4f
    /** Margin and padding around one column's words. */
    const val STRIP_COL_CHROME_DP = 4f + 2f
    const val STRIP_MAX_GROW = 1.3f

    /** About what a 4-cell wide widget measures on a phone: the width the six-in-a-row picker and settings previews are laid out for, which must hold all six. */
    const val STRIP_USUAL_WIDTH_DP = 344f
}

/** Whether [text] fits on the top row, beside its icon, in a widget [widthDp] wide at [sizeSp] ([fontScale] applied). */
fun topRowFits(widthDp: Float, text: String, sizeSp: Float, fontScale: Float, measure: TextMeasure): Boolean =
    WidgetMetrics.ICON_DP + WidgetMetrics.ICON_GAP_DP + measure.widthDp(text, sizeSp * fontScale, false) <= widthDp - 2 * WidgetMetrics.PAD_H

/**
 * What the next prayer widget draws. Text sizes are in sp. A top row or countdown that does not fit is dropped (the Hijri row
 * first), never squeezed below [WidgetMetrics.MIN_SP]. [stacked] puts the time under the name, for a widget too narrow for both on one
 * line.
 */
data class NextPlan(
    val showTop: Boolean,
    val showCountdown: Boolean,
    val stacked: Boolean,
    /** False only in a widget too small for both the name and the time at the smallest size: then the name alone. */
    val showTime: Boolean,
    val nameSp: Float,
    val timeSp: Float,
    val topSp: Float,
    val countdownSp: Float,
)

/** The name and time may shrink to this share of their usual size before anything is dropped to make room. */
const val NEXT_KEEP_SHARE = 0.75f

/**
 * Chooses the sizes for the next prayer widget in [widthDp] by [heightDp]. [fontScale] is the phone's, [textMultiplier] the
 * extra step from [widgetTextMultiplier]. [top] is the top row's text (the Hijri date, "Next prayer" or "Tomorrow"), or null when
 * there is none. The biggest text that fits wins, up to [WidgetMetrics.NEXT_MAX_GROW] times the usual, with everything shown. When
 * that does not fit, the name and time give up to a quarter of their size first; then the top row goes, then the countdown; then the
 * name and time shrink on to the floor; then the time goes under the name. Nothing is ever below [WidgetMetrics.MIN_SP].
 */
fun planNext(
    widthDp: Float, heightDp: Float, fontScale: Float, textMultiplier: Float, bold: Boolean,
    name: String, time: String, top: String?, measure: TextMeasure,
): NextPlan {
    val m = WidgetMetrics
    val availW = widthDp - 2 * m.PAD_H
    val availH = heightDp - 2 * m.PAD_V
    fun dp(sp: Float) = sp * fontScale
    fun line(sp: Float) = dp(sp) * m.LINE

    fun plan(k: Float, withTop: Boolean, withCountdown: Boolean, stacked: Boolean): NextPlan? {
        val nameSp = maxOf(m.MIN_SP, m.NEXT_NAME * textMultiplier * k)
        val timeSp = maxOf(m.MIN_SP, m.NEXT_TIME * textMultiplier * k)
        val topSp = maxOf(m.MIN_SP, m.NEXT_TOP * textMultiplier * minOf(k, 1.4f))
        // The countdown is the same size as the Hijri date line, so the two read as one quiet pair under and over the name.
        val cdSp = topSp
        var need = if (stacked) line(nameSp) + line(timeSp) else line(maxOf(nameSp, timeSp))
        if (withTop) need += line(topSp) + m.GAP
        if (withCountdown) need += line(cdSp) + m.GAP
        if (need > availH) return null
        val nameW = measure.widthDp(name, dp(nameSp), true)
        val timeW = measure.widthDp(time, dp(timeSp), true)
        if (stacked) {
            if (maxOf(nameW, timeW) > availW) return null
        } else if (nameW + m.MAIN_GAP_DP + timeW > availW) {
            return null
        }
        if (withTop && top != null && m.ICON_DP + m.ICON_GAP_DP + measure.widthDp(top, dp(topSp), false) > availW) return null
        if (withCountdown && measure.widthDp(m.COUNTDOWN_WORST, dp(cdSp), false) > availW) return null
        return NextPlan(withTop && top != null, withCountdown, stacked, true, nameSp, timeSp, topSp, cdSp)
    }

    fun search(from: Float, to: Float, withTop: Boolean, withCountdown: Boolean, stacked: Boolean): NextPlan? {
        var k = from
        while (k >= to - 1e-3f) {
            plan(k, withTop && top != null, withCountdown, stacked)?.let { return it }
            k -= 0.05f
        }
        return null
    }

    for ((withTop, withCountdown) in listOf(true to true, false to true, false to false)) {
        search(m.NEXT_MAX_GROW, NEXT_KEEP_SHARE, withTop, withCountdown, stacked = false)?.let { return it }
    }
    search(NEXT_KEEP_SHARE, 0.5f, withTop = false, withCountdown = false, stacked = false)?.let { return it }
    search(1f, 0.5f, withTop = false, withCountdown = false, stacked = true)?.let { return it }
    // Even stacked at the floor does not fit (a huge font in a tiny widget): the name alone, at the smallest size.
    return NextPlan(false, false, true, false, m.MIN_SP, m.MIN_SP, m.MIN_SP, m.MIN_SP)
}

/**
 * What today's times widget draws. [rows] is how many entries show (six, or the current one and the next three or fewer in a single
 * column). Text sizes are in sp, the icon in dp.
 */
data class TodayPlan(
    val singleColumn: Boolean,
    val rows: Int,
    val textSp: Float,
    val showIcon: Boolean,
    val iconDp: Float,
    val showHeader: Boolean,
    val showFooter: Boolean,
    val headerSp: Float,
    val footerSp: Float,
)

/**
 * Chooses the layout of today's times widget in [widthDp] by [heightDp]. [header] is the Hijri date (null for none), [footerWorst]
 * the longest the footer line gets (for example `Next: Maghrib tomorrow in 20:00:00`), or null for none. [wantSingle] asks for one column (the Large and Extra large steps). [rowWords] gives the
 * (name, time) pairs of the rows that [rows] of them show, so the widest of those, not of all six, is what has to fit.
 *
 * In order of what is given up last: the layout (two columns, then one), the number of rows (six, then four, then fewer), the
 * footer then the Hijri line, and the text size, which grows up to [WidgetMetrics.TODAY_MAX_GROW] and shrinks to a floor. A row is
 * never clipped. Nothing is below [WidgetMetrics.MIN_SP].
 */
fun planToday(
    widthDp: Float, heightDp: Float, fontScale: Float, textMultiplier: Float, wantSingle: Boolean, dot: Boolean,
    header: String?, footerWorst: String?, rowWords: (rows: Int) -> List<Pair<String, String>>, measure: TextMeasure,
): TodayPlan {
    val m = WidgetMetrics
    val availW = widthDp - 2 * m.TODAY_PAD_H
    val availH = heightDp - 2 * m.TODAY_PAD_V
    fun dp(sp: Float) = sp * fontScale
    fun line(sp: Float) = dp(sp) * m.LINE

    fun fitsWidth(k: Float, single: Boolean, rows: Int, withDot: Boolean, icon: Boolean): Boolean {
        val textSp = maxOf(m.MIN_SP, m.TODAY_TEXT * textMultiplier * k)
        val iconDp = maxOf(m.TODAY_ICON_DP, dp(textSp) * 1.1f)
        val columnW = if (single) availW else (availW - m.TODAY_COL_GAP) / 2
        val words = rowWords(rows)
        val nameW = words.maxOf { measure.widthDp(it.first, dp(textSp), true) }
        val timeW = words.maxOf { measure.widthDp(it.second, dp(textSp), true) }
        return m.ROW_CHROME_DP + (if (withDot) m.ROW_DOT_DP else 0f) + (if (icon) iconDp else 0f) + nameW + timeW <= columnW
    }

    fun attempt(k: Float, single: Boolean, rows: Int, withHeader: Boolean, withFooter: Boolean, icon: Boolean): TodayPlan? {
        val textSp = maxOf(m.MIN_SP, m.TODAY_TEXT * textMultiplier * k)
        val iconDp = maxOf(m.TODAY_ICON_DP, dp(textSp) * 1.1f)
        val headerSp = maxOf(m.MIN_SP, m.TODAY_HEADER * textMultiplier * minOf(k, 1.2f))
        val footerSp = maxOf(m.MIN_SP, m.TODAY_FOOTER * textMultiplier * minOf(k, 1.2f))
        val columnW = if (single) availW else (availW - m.TODAY_COL_GAP) / 2
        val words = rowWords(rows)
        val nameW = words.maxOf { measure.widthDp(it.first, dp(textSp), true) }
        val timeW = words.maxOf { measure.widthDp(it.second, dp(textSp), true) }
        if (m.ROW_CHROME_DP + (if (dot) m.ROW_DOT_DP else 0f) + (if (icon) iconDp else 0f) + nameW + timeW > columnW) return null
        val headerH = if (withHeader) line(headerSp) + 2f else 0f
        val footerH = if (withFooter) line(footerSp) + 2f else 0f
        val perColumn = if (single) rows else (rows + 1) / 2
        val rowMin = maxOf(if (icon) iconDp else 0f, line(textSp)) + m.ROW_MIN_PAD
        if (perColumn * rowMin + headerH + footerH > availH) return null
        if (withHeader && header != null && measure.widthDp(header, dp(headerSp), true) > availW) return null
        if (withFooter && footerWorst != null && measure.widthDp(footerWorst, dp(footerSp), false) > availW) return null
        return TodayPlan(single, rows, textSp, icon, iconDp, withHeader && header != null, withFooter && footerWorst != null, headerSp, footerSp)
    }

    /**
     * The first of the usual things to give up that fits, in order: the footer, then the Hijri line. For it, the usual text size if that
     * fits (or down to [to] if only a smaller size does), and then, only when everything chosen fits with room over, bigger text.
     */
    fun search(single: Boolean, rows: Int, to: Float): TodayPlan? {
        for ((withHeader, withFooter) in listOf(true to true, true to false, false to false)) {
            val h = withHeader && header != null
            val f = withFooter && footerWorst != null
            // The icon stays unless the words do not fit the width with it even at the smallest size tried: then it goes, before a row is lost.
            // (Not enough height is no reason to drop it: the footer and the Hijri line go for that, as the spec says.)
            fun find(icon: Boolean): TodayPlan? {
                var k = 1f
                while (k >= to - 1e-3f) {
                    attempt(k, single, rows, h, f, icon)?.let { return it }
                    k -= 0.05f
                }
                return null
            }
            fun widthFailsWithIcon(): Boolean = !fitsWidth(to, single, rows, dot, icon = true)
            var base = find(icon = true) ?: if (widthFailsWithIcon()) find(icon = false) else null
            if (base == null) continue
            if (base.textSp >= m.TODAY_TEXT * textMultiplier - 1e-3f && base.showIcon) {
                var grow = m.TODAY_MAX_GROW
                while (grow > 1f + 1e-3f) {
                    attempt(grow, single, rows, h, f, icon = true)?.let { return it }
                    grow -= 0.05f
                }
            }
            return base
        }
        return null
    }

    val layouts = if (wantSingle) listOf(true) else listOf(false, true)
    fun rowChoices(single: Boolean) = if (single) listOf(6, 4, 3, 2, 1) else listOf(6)
    for (single in layouts) for (rows in rowChoices(single)) search(single, rows, 0.8f)?.let { return it }
    for (single in layouts) for (rows in rowChoices(single)) search(single, rows, 0.5f)?.let { return it }
    return TodayPlan(true, 1, m.MIN_SP, false, m.TODAY_ICON_DP, false, false, m.MIN_SP, m.MIN_SP)
}

/**
 * What the six-in-a-row widget draws: [columns] is six, or three (the current entry and the next two). [showName] is false only in a widget
 * too short for two lines of text at the smallest size, which then shows the times alone (the current one marked).
 */
data class StripPlan(val columns: Int, val textSp: Float, val showIcon: Boolean, val iconDp: Float, val showName: Boolean = true)

/**
 * Chooses the strip's columns and text for [widthDp] by [heightDp]. [three] asks for three columns (the Large and Extra large
 * steps); six columns that cannot hold the words at 11 sp fall back to three rather than clip. [labels] and [times] are the words
 * the columns will hold. The icon is dropped before the text is made smaller than it should be.
 */
fun planStrip(
    widthDp: Float, heightDp: Float, fontScale: Float, textMultiplier: Float, three: Boolean,
    labels: List<String>, times: List<String>, measure: TextMeasure,
): StripPlan {
    val m = WidgetMetrics
    val availW = widthDp - 2 * m.STRIP_PAD_H
    val availH = heightDp - 2 * m.STRIP_PAD_V
    fun dp(sp: Float) = sp * fontScale

    fun attempt(k: Float, columns: Int, icon: Boolean): StripPlan? {
        val textSp = maxOf(m.MIN_SP, m.STRIP_TEXT * textMultiplier * k)
        val columnW = availW / columns - m.STRIP_COL_CHROME_DP
        if (labels.maxOf { measure.widthDp(it, dp(textSp), true) } > columnW) return null
        if (times.maxOf { measure.widthDp(it, dp(textSp), true) } > columnW) return null
        val iconDp = m.STRIP_ICON_DP
        val need = 2 * dp(textSp) * m.LINE + (if (icon) iconDp + 2f else 0f)
        if (need > availH) return null
        return StripPlan(columns, textSp, icon, iconDp)
    }

    fun search(columns: Int, from: Float, to: Float): StripPlan? {
        for (icon in listOf(true, false)) {
            var k = from
            while (k >= to - 1e-3f) {
                attempt(k, columns, icon)?.let { return it }
                k -= 0.05f
            }
        }
        return null
    }

    // Dropping the icon is cheaper than a smaller name and time, so each size is tried with and without it before the next size down.
    fun searchSizes(columns: Int): StripPlan? {
        var k = m.STRIP_MAX_GROW
        while (k >= 1f - 1e-3f) {
            for (icon in listOf(true, false)) attempt(k, columns, icon)?.let { return it }
            k -= 0.05f
        }
        return null
    }

    if (!three) searchSizes(6)?.let { return it }
    searchSizes(3)?.let { return it }
    search(3, 1f, 0.5f)?.let { return it }
    // Two lines do not fit even at the floor (a huge font in a one-row widget): the times alone, in one line.
    var k = 1f
    while (k >= 0.5f) {
        val textSp = maxOf(m.MIN_SP, m.STRIP_TEXT * textMultiplier * k)
        val columnW = availW / 3 - m.STRIP_COL_CHROME_DP
        if (dp(textSp) * m.LINE <= availH && times.maxOf { measure.widthDp(it, dp(textSp), true) } <= columnW) return StripPlan(3, textSp, false, m.STRIP_ICON_DP, showName = false)
        k -= 0.05f
    }
    return StripPlan(3, m.MIN_SP, false, m.STRIP_ICON_DP, showName = false)
}
