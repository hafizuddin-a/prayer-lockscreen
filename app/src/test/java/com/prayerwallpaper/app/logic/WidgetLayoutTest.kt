package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLayoutTest {
    /** A stand-in for the Paint: every character is the same width, a little over half the text size. */
    private val measure = TextMeasure { text, sizeDp, bold -> text.length * sizeDp * (if (bold) 0.58f else 0.52f) }

    private fun plan(w: Float, h: Float, fontScale: Float = 1f, multiplier: Float = 1f, top: String? = "25 Rabiulakhir") =
        planNext(w, h, fontScale, multiplier, bold = false, name = "Zohor", time = "12:54 pm", top = top, measure = measure)

    // --- whether the full Hijri date fits ---

    @Test fun `the full Hijri date fits a wide widget and not a 2 by 1`() {
        val full = "25 Rabiulakhir 1448H"
        assertFalse(topRowFits(146f, full, WidgetMetrics.NEXT_TOP, 1f, measure))
        assertTrue(topRowFits(180f, full, WidgetMetrics.NEXT_TOP, 1f, measure))
        assertTrue(topRowFits(146f, "25 Rabiulakhir", WidgetMetrics.NEXT_TOP, 1f, measure))
        assertFalse(topRowFits(180f, full, WidgetMetrics.NEXT_TOP, 1.5f, measure)) // a bigger font needs more room
    }

    // --- the next prayer plan ---

    @Test fun `a roomy widget keeps every row and grows its text`() {
        val p = plan(320f, 160f)
        assertTrue(p.showTop)
        assertTrue(p.showCountdown)
        assertFalse(p.stacked)
        assertTrue(p.nameSp > WidgetMetrics.NEXT_NAME)
    }

    @Test fun `the usual 2 by 1 keeps all three rows at about the usual size`() {
        val p = plan(146f, 72f)
        assertTrue(p.showTop && p.showCountdown)
        assertFalse(p.stacked)
        assertTrue("${p.nameSp}", p.nameSp >= WidgetMetrics.NEXT_NAME * NEXT_KEEP_SHARE - 0.01f)
    }

    @Test fun `no size is ever below 11 sp`() {
        for (w in listOf(80f, 110f, 146f, 180f, 250f, 320f)) for (h in listOf(40f, 56f, 72f, 110f, 160f)) for (fs in listOf(0.85f, 1f, 1.3f, 1.5f, 2f)) {
            val p = plan(w, h, fs)
            assertTrue("$w x $h at $fs: $p", minOf(p.nameSp, p.timeSp, p.topSp, p.countdownSp) >= WidgetMetrics.MIN_SP)
        }
    }

    @Test fun `the countdown is never bigger than the name or the time`() {
        for (w in listOf(110f, 146f, 180f, 320f)) for (h in listOf(40f, 72f, 160f)) for (fs in listOf(1f, 1.3f, 2f)) {
            val p = plan(w, h, fs)
            if (p.showCountdown) assertTrue("$w x $h at $fs: $p", p.countdownSp <= minOf(p.nameSp, p.timeSp) + 0.001f)
        }
    }

    @Test fun `what is shown always fits the height and the width`() {
        for (w in listOf(110f, 146f, 180f, 250f, 320f)) for (h in listOf(40f, 56f, 72f, 110f, 160f)) for (fs in listOf(1f, 1.3f, 2f)) {
            val p = plan(w, h, fs)
            val dp = { sp: Float -> sp * fs }
            val line = { sp: Float -> dp(sp) * WidgetMetrics.LINE }
            var need = if (!p.showTime) line(p.nameSp) else if (p.stacked) line(p.nameSp) + line(p.timeSp) else line(maxOf(p.nameSp, p.timeSp))
            if (p.showTop) need += line(p.topSp) + WidgetMetrics.GAP
            if (p.showCountdown) need += line(p.countdownSp) + WidgetMetrics.GAP
            val atFloor = p.nameSp <= WidgetMetrics.MIN_SP && p.timeSp <= WidgetMetrics.MIN_SP
            // Only a widget too small for even the smallest text may overflow, and then it shows nothing but the name and time.
            if (need > h - 2 * WidgetMetrics.PAD_V + 0.01f) assertTrue("$w x $h at $fs: $p", atFloor && !p.showTop && !p.showCountdown && !p.showTime)
            val nameW = measure.widthDp("Zohor", dp(p.nameSp), true)
            val timeW = measure.widthDp("12:54 pm", dp(p.timeSp), true)
            val rowW = if (p.stacked) maxOf(nameW, timeW) else nameW + WidgetMetrics.MAIN_GAP_DP + timeW
            if (rowW > w - 2 * WidgetMetrics.PAD_H + 0.01f) assertTrue("$w x $h at $fs: $p", atFloor)
        }
    }

    @Test fun `the top row goes before the countdown`() {
        // Tall enough for the name and the countdown but not for the Hijri row too.
        val p = plan(200f, 62f)
        assertFalse("$p", p.showTop && !p.showCountdown)
    }

    @Test fun `with no top text there is no top row`() {
        assertFalse(plan(320f, 160f, top = null).showTop)
    }

    @Test fun `a big font drops what does not fit rather than shrinking it`() {
        val p = plan(146f, 72f, fontScale = 2f)
        assertFalse(p.showTop)
        assertFalse(p.showCountdown)
    }

    @Test fun `too narrow for the name and time on one line, the time goes under the name`() {
        val p = plan(110f, 72f, fontScale = 2f)
        assertTrue("$p", p.stacked)
    }

    @Test fun `a larger text step makes the main text bigger when there is room`() {
        val usual = plan(250f, 110f, multiplier = 1f)
        val big = plan(250f, 110f, multiplier = 1.5f)
        assertTrue(big.nameSp >= usual.nameSp)
    }

    // --- the accessibility step and the phone's font scale ---

    @Test fun `a step adds only what the font scale does not`() {
        assertEquals(1f, widgetTextMultiplier(TextSize.STANDARD, 1f), 0.0001f)
        assertEquals(1.2f, widgetTextMultiplier(TextSize.LARGE, 1f), 0.0001f)
        assertEquals(1.5f, widgetTextMultiplier(TextSize.EXTRA_LARGE, 1f), 0.0001f)
        assertEquals(1f, widgetTextMultiplier(TextSize.LARGE, 1.3f), 0.0001f)
        assertEquals(1.5f / 1.3f, widgetTextMultiplier(TextSize.EXTRA_LARGE, 1.3f), 0.0001f)
        assertEquals(1f, widgetTextMultiplier(TextSize.STANDARD, 2f), 0.0001f)
    }
}

class WidgetListLayoutTest {
    private val measure = TextMeasure { text, sizeDp, bold -> text.length * sizeDp * (if (bold) 0.58f else 0.52f) }
    private val labels = listOf("Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak")
    private val times = listOf("5:41 am", "6:56 am", "1:00 pm", "4:15 pm", "7:00 pm", "8:11 pm")
    private val compact = listOf("5:41 a", "6:56 a", "1:00 p", "4:15 p", "7:00 p", "8:11 p")
    private val header = "24 Rabiulakhir 1448H"
    private val footer = "Maghrib tomorrow in 20:00:00"

    // The rows of a window start at the current entry, Zohor here; a full list is all six in order.
    private val wordsAll = labels.zip(times)
    private fun rowWords(rows: Int): List<Pair<String, String>> = if (rows >= 6) wordsAll else (wordsAll.drop(2) + wordsAll.take(2)).take(rows)

    private fun today(w: Float, h: Float, fs: Float = 1f, mult: Float = 1f, single: Boolean = false, dot: Boolean = false) =
        planToday(w, h, fs, mult, single, dot, header, footer, ::rowWords, measure)

    private fun strip(w: Float, h: Float, fs: Float = 1f, mult: Float = 1f, three: Boolean = false) =
        planStrip(w, h, fs, mult, three, labels, compact, measure)

    @Test fun `the usual width of the six in a row widget holds all six columns at the usual font`() {
        // The settings preview was laid out for 320 dp, which falls back to three columns; a real 4-cell widget is about 344 dp.
        assertEquals(6, strip(WidgetMetrics.STRIP_USUAL_WIDTH_DP, 72f).columns)
        assertEquals(3, strip(WidgetMetrics.STRIP_USUAL_WIDTH_DP, 72f, three = true).columns)
    }

    // --- today's times ---

    @Test fun `the usual 4 by 2 is two columns of three with the header and footer`() {
        val p = today(320f, 160f)
        assertFalse(p.singleColumn)
        assertEquals(6, p.rows)
        assertTrue(p.showHeader && p.showFooter)
        assertTrue("${p.textSp}", p.textSp >= WidgetMetrics.TODAY_TEXT * 0.8f)
    }

    @Test fun `with little height the footer goes first, then the Hijri line`() {
        val short = today(320f, 118f)
        assertFalse("$short", short.showFooter && !short.showHeader)
        val tiny = today(320f, 88f)
        assertFalse(tiny.showFooter)
    }

    @Test fun `no header text means no header row`() {
        assertFalse(planToday(320f, 160f, 1f, 1f, false, false, null, footer, ::rowWords, measure).showHeader)
        assertFalse(planToday(320f, 160f, 1f, 1f, false, false, header, null, ::rowWords, measure).showFooter)
    }

    @Test fun `too narrow for two columns it becomes one`() {
        val p = today(190f, 200f)
        assertTrue("$p", p.singleColumn)
    }

    @Test fun `large text asks for one column, all six rows when the height allows`() {
        val p = today(320f, 300f, mult = 1.5f, single = true)
        assertTrue(p.singleColumn)
        assertEquals(6, p.rows)
    }

    @Test fun `when six rows do not fit it shows the current entry and the next three`() {
        val p = today(320f, 160f, mult = 1.5f, single = true)
        assertTrue("$p", p.singleColumn)
        assertEquals("$p", 4, p.rows)
    }

    @Test fun `a row is never clipped, whatever the size and font`() {
        for (w in listOf(250f, 320f, 400f)) for (h in listOf(110f, 160f, 300f)) for (fs in listOf(1f, 1.3f, 2f)) for (single in listOf(false, true)) {
            val p = today(w, h, fs, single = single)
            val dp = { sp: Float -> sp * fs }
            val line = { sp: Float -> dp(sp) * WidgetMetrics.LINE }
            val rowMin = maxOf(if (p.showIcon) p.iconDp else 0f, line(p.textSp)) + WidgetMetrics.ROW_MIN_PAD
            val perColumn = if (p.singleColumn) p.rows else (p.rows + 1) / 2
            var need = perColumn * rowMin
            if (p.showHeader) need += line(p.headerSp) + 2
            if (p.showFooter) need += line(p.footerSp) + 2
            val floor = p.textSp <= WidgetMetrics.MIN_SP && p.rows == 1
            assertTrue("$w x $h at $fs single=$single: $p needs $need", need <= h - 2 * WidgetMetrics.TODAY_PAD_V + 0.01f || floor)
            assertTrue(p.textSp >= WidgetMetrics.MIN_SP)
        }
    }

    @Test fun `the dot marker takes room, so a card is no wider than the glass fits`() {
        val glass = today(250f, 160f, dot = false)
        val card = today(250f, 160f, dot = true)
        assertTrue(card.textSp <= glass.textSp + 0.001f || card.singleColumn)
    }

    // --- six in a row ---

    @Test fun `the usual 4 by 1 has six columns, with icons`() {
        val p = strip(320f, 72f)
        assertEquals(6, p.columns)
        assertTrue(p.showIcon)
        assertTrue(p.textSp >= WidgetMetrics.MIN_SP)
    }

    @Test fun `six columns that cannot hold the words fall back to three`() {
        assertEquals(3, strip(200f, 72f).columns)
        assertEquals(6, strip(400f, 72f).columns)
    }

    @Test fun `large text shows three columns`() {
        val p = strip(320f, 72f, mult = 1.5f, three = true)
        assertEquals(3, p.columns)
        assertTrue(p.textSp > WidgetMetrics.STRIP_TEXT)
    }

    @Test fun `the icon goes before the text gets small`() {
        val p = strip(320f, 56f, fs = 1.3f)
        assertTrue("$p", p.textSp >= WidgetMetrics.MIN_SP)
    }

    @Test fun `the strip never overflows its height or its columns`() {
        for (w in listOf(250f, 320f, 400f)) for (h in listOf(40f, 56f, 72f, 110f)) for (fs in listOf(1f, 1.3f, 2f)) for (three in listOf(false, true)) {
            val p = strip(w, h, fs, three = three)
            val textDp = p.textSp * fs
            val need = (if (p.showName) 2 else 1) * textDp * WidgetMetrics.LINE + (if (p.showIcon) p.iconDp + 2 else 0f)
            val floor = p.textSp <= WidgetMetrics.MIN_SP
            assertTrue("$w x $h at $fs: $p needs $need", need <= h - 2 * WidgetMetrics.STRIP_PAD_V + 0.01f || floor)
            val columnW = (w - 2 * WidgetMetrics.STRIP_PAD_H) / p.columns - WidgetMetrics.STRIP_COL_CHROME_DP
            val widest = maxOf(labels.maxOf { measure.widthDp(it, textDp, true) }, compact.maxOf { measure.widthDp(it, textDp, true) })
            assertTrue("$w x $h at $fs: $p widest $widest in $columnW", widest <= columnW + 0.01f || floor)
        }
    }
}

class WidgetPlanEdgeTest {
    @Test fun `a one-row strip at the largest font shows the times alone rather than clip a second line`() {
        val labels = listOf("Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak")
        val times = listOf("5:41 a", "6:56 a", "1:00 p", "4:15 p", "7:00 p", "8:11 p")
        val p = planStrip(250f, 40f, 2f, 1f, false, labels, times, measure)
        assertFalse("$p", p.showName)
        assertFalse(p.showIcon)
    }

    private val measure = TextMeasure { text, sizeDp, bold -> text.length * sizeDp * (if (bold) 0.58f else 0.52f) }

    @Test fun `a widget too small for the name and the time at the smallest size shows the name alone`() {
        val p = planNext(110f, 40f, 2f, 1f, false, "Asar", "4:05 pm", "Next prayer", measure)
        assertFalse(p.showTime)
        assertFalse(p.showTop)
        assertFalse(p.showCountdown)
    }

    @Test fun `the list drops its icon before it loses a row or shrinks`() {
        val words = listOf("Subuh" to "5:33 am", "Syuruk" to "6:50 am", "Zohor" to "12:54 pm", "Asar" to "4:05 pm", "Maghrib" to "6:56 pm", "Isyak" to "8:05 pm")
        val p = planToday(230f, 200f, 2f, 1f, true, false, null, null, { words.take(if (it >= 6) 6 else it) }, measure)
        assertFalse("$p", p.showIcon)
        assertTrue("$p", p.rows >= 4)
    }

    @Test fun `the rows measured are the ones shown, not all six`() {
        val wide = listOf("Maghrib" to "12:54 pm", "Isyak" to "8:05 pm")
        val narrow = listOf("Asar" to "4:05 pm", "Isyak" to "8:05 pm")
        val withWide = planToday(250f, 120f, 1.3f, 1f, true, false, null, null, { wide.take(it.coerceAtMost(2)) }, measure)
        val withNarrow = planToday(250f, 120f, 1.3f, 1f, true, false, null, null, { narrow.take(it.coerceAtMost(2)) }, measure)
        assertTrue(withNarrow.textSp >= withWide.textSp)
    }
}
