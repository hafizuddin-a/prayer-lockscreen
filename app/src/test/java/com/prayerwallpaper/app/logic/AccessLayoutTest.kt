package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Large and Extra large block: proportions, the order of its lines, and the rule that it always fits on the screen. */
class AccessLayoutTest {
    private val screens = listOf(720 to 1280, 720 to 1600, 1080 to 2400, 1080 to 2424, 1440 to 3200, 1840 to 2208)
    private val eps = 0.01f

    private fun plan(w: Int, h: Int, size: TextSize, top: Float = 0.40f, hijri: Int = 1, countdown: Boolean = true, padding: Float = 0f) =
        planBlock(w, h, size, top, hijri, if (hijri > 0) 2 else 0, countdown, padding)

    // --- proportions ---

    @Test fun `Large text is about 5_8 percent of the width, Extra large about 7_5 percent`() {
        assertEquals(0.058f * 1080, plan(1080, 2400, TextSize.LARGE).text, eps)
        assertEquals(0.075f * 1080, plan(1080, 2400, TextSize.EXTRA_LARGE).text, eps)
    }

    @Test fun `a row is 1_9 times its text, the block is 88 percent wide and centred`() {
        val p = plan(1080, 2400, TextSize.LARGE)
        assertEquals(1.9f * p.text, p.pitch, eps)
        assertEquals(0.88f * 1080, p.width, eps)
        assertEquals((1080 - p.width) / 2, p.left, eps)
        assertFalse(p.reduced)
        assertEquals(TextSize.LARGE, p.size)
    }

    @Test fun `sizes follow the shorter side, so a taller screen does not make the text bigger`() {
        val short = plan(1080, 2400, TextSize.LARGE)
        val tall = plan(1080, 3200, TextSize.LARGE)
        assertEquals(short.text, tall.text, 0f)
        assertEquals(short.pitch, tall.pitch, 0f)
        assertEquals(short.width, tall.width, 0f)
    }

    @Test fun `the Hijri date and countdown are 85 percent of the row text`() {
        val p = plan(1080, 2400, TextSize.LARGE)
        assertEquals(0.85f * p.text, p.lineText, eps)
    }

    // --- the order of things, top to bottom, with nothing overlapping ---

    @Test fun `from top to bottom there are the date, the event, a gap, six rows, a gap and the countdown`() {
        val p = plan(1080, 2400, TextSize.LARGE, hijri = 2)
        val date = p.dateRowTop!!
        val event = p.eventRowTop!!
        val countdown = p.countdownRowTop!!
        val gap = 0.25f * p.pitch
        assertEquals(date + p.lineRow, event, eps)
        assertEquals(event + p.lineRow + gap, p.listTop, eps)
        assertEquals(p.listTop + 6 * p.pitch + gap, countdown, eps)
        assertEquals(countdown + p.lineRow, p.bottom, eps)
        assertEquals(date, p.top, eps)
    }

    @Test fun `with one Hijri line there is no event row, and with none there is neither`() {
        val one = plan(1080, 2400, TextSize.LARGE, hijri = 1)
        assertNotNull(one.dateRowTop)
        assertNull(one.eventRowTop)
        val none = plan(1080, 2400, TextSize.LARGE, hijri = 0)
        assertNull(none.dateRowTop)
        assertNull(none.eventRowTop)
        assertEquals(none.listTop, none.top, eps)
    }

    @Test fun `without the countdown nothing is reserved under the list`() {
        val p = plan(1080, 2400, TextSize.LARGE, countdown = false)
        assertNull(p.countdownRowTop)
        assertEquals(p.listTop + 6 * p.pitch, p.bottom, eps)
    }

    @Test fun `the card's padding grows the block on every side`() {
        val bare = plan(1080, 2400, TextSize.LARGE)
        val card = plan(1080, 2400, TextSize.LARGE, padding = 32f)
        assertEquals(bare.top - 32f, card.top, eps)
        assertEquals(bare.bottom + 32f, card.bottom, eps)
    }

    // --- the fit rule ---

    @Test fun `every step on every screen stays inside the safe area, wherever the list was put`() {
        for ((w, h) in screens) for (size in listOf(TextSize.LARGE, TextSize.EXTRA_LARGE)) for (hijri in 0..2) for (countdown in listOf(true, false))
            for (top in listOf(0f, 0.05f, 0.40f, 0.85f, 1f)) {
                val p = plan(w, h, size, top, hijri, countdown)
                val where = "$w x $h $size hijri=$hijri countdown=$countdown top=$top"
                if (p.size == TextSize.STANDARD) continue
                assertTrue("$where: block top ${p.top}", p.top >= AccessLayout.TOP_SAFE * h - eps)
                assertTrue("$where: block bottom ${p.bottom}", p.bottom <= (1f - AccessLayout.BOTTOM_SAFE) * h + eps)
            }
    }

    @Test fun `no row is clipped, all six lie inside the block and the block inside the screen`() {
        for ((w, h) in screens) for (size in listOf(TextSize.LARGE, TextSize.EXTRA_LARGE)) for (top in listOf(0f, 0.40f, 1f)) {
            val p = plan(w, h, size, top, hijri = 2)
            if (p.size == TextSize.STANDARD) continue
            val firstRowTop = p.listTop
            val lastRowBottom = p.listTop + 6 * p.pitch
            assertTrue("$w x $h $size top=$top", firstRowTop >= p.top && lastRowBottom <= p.bottom)
            assertTrue("$w x $h $size top=$top", p.top >= 0f && p.bottom <= h)
        }
    }

    @Test fun `the list sits where it was put when that fits`() {
        val p = plan(1080, 2400, TextSize.LARGE, top = 0.40f)
        assertEquals(0.40f * 2400, p.listTop, eps)
    }

    @Test fun `a list put too low is lifted until the block ends above the bottom margin`() {
        val p = plan(1080, 2400, TextSize.LARGE, top = 0.95f)
        assertEquals((1f - AccessLayout.BOTTOM_SAFE) * 2400, p.bottom, eps)
        assertEquals(p.maxListTop, p.listTop, eps)
    }

    @Test fun `a list put too high is lowered until the block starts below the top margin`() {
        val p = plan(1080, 2400, TextSize.LARGE, top = 0f)
        assertEquals(AccessLayout.TOP_SAFE * 2400, p.top, eps)
    }

    @Test fun `the position does not change from a day with an event to a day without`() {
        // The size is decided with room for the event line, and the clamp for the lines actually shown.
        val without = planBlock(1080, 2400, TextSize.EXTRA_LARGE, 0.40f, 1, 2, true)
        val withEvent = planBlock(1080, 2400, TextSize.EXTRA_LARGE, 0.40f, 2, 2, true)
        assertEquals(without.size, withEvent.size)
        assertEquals(without.text, withEvent.text, 0f)
        assertEquals(without.pitch, withEvent.pitch, 0f)
        assertEquals(without.listTop, withEvent.listTop, eps)
    }

    @Test fun `a block too tall at the normal row height first gets shorter rows`() {
        // 720 x 950: Extra large with a two-line date and the countdown does not fit at 1.9 rows but does at 1.5.
        val p = plan(720, 950, TextSize.EXTRA_LARGE, hijri = 2)
        assertEquals(TextSize.EXTRA_LARGE, p.size)
        assertTrue(p.reduced)
        assertEquals(AccessLayout.MIN_ROW_FACTOR * p.text, p.pitch, eps)
    }

    @Test fun `then the text drops a step`() {
        val p = plan(720, 850, TextSize.EXTRA_LARGE, hijri = 2)
        assertEquals(TextSize.LARGE, p.size)
        assertTrue(p.reduced)
        assertEquals(0.058f * 720, p.text, eps)
    }

    @Test fun `when even Large cannot fit the standard layout is used, which always fits`() {
        // A phone on its side with the card's padding: 2400 x 1080.
        val p = plan(2400, 1080, TextSize.EXTRA_LARGE, hijri = 2, padding = AccessLayout.CARD_PADDING * 1080)
        assertEquals(TextSize.STANDARD, p.size)
        assertTrue(p.reduced)
    }

    @Test fun `asking for Standard is not reduced and plans nothing`() {
        val p = plan(1080, 2400, TextSize.STANDARD)
        assertEquals(TextSize.STANDARD, p.size)
        assertFalse(p.reduced)
    }

    @Test fun `a big screen never needs reducing`() {
        for (size in listOf(TextSize.LARGE, TextSize.EXTRA_LARGE)) assertFalse(plan(1440, 3200, size, hijri = 2).reduced)
    }

    @Test fun `the Large text of a tall phone is not reduced at the default position either`() {
        assertFalse(plan(1080, 2424, TextSize.LARGE, hijri = 2).reduced)
        assertFalse(plan(1080, 2424, TextSize.EXTRA_LARGE, hijri = 2).reduced)
    }

    // --- the drag and the slider ---

    @Test fun `the range the list may move in matches the clamp`() {
        val p = plan(1080, 2400, TextSize.EXTRA_LARGE, hijri = 2)
        val low = plan(1080, 2400, TextSize.EXTRA_LARGE, top = 0f, hijri = 2)
        val high = plan(1080, 2400, TextSize.EXTRA_LARGE, top = 1f, hijri = 2)
        assertEquals(low.listTop, p.minListTop, eps)
        assertEquals(high.listTop, p.maxListTop, eps)
        assertTrue(p.minListTop < p.maxListTop)
    }

    @Test fun `the range always leaves room for an event line, so it does not change on an event day`() {
        val date = plan(1080, 2400, TextSize.EXTRA_LARGE, hijri = 1)
        val event = plan(1080, 2400, TextSize.EXTRA_LARGE, hijri = 2)
        assertEquals(event.minListTop, date.minListTop, 0f)
        assertEquals(event.maxListTop, date.maxListTop, 0f)
    }

    @Test fun `the safe margins leave the unlock icon clear`() {
        // Android's own unlock icon is at about 87 to 90 percent of the height.
        assertTrue(1f - AccessLayout.BOTTOM_SAFE <= 0.87f)
        assertTrue(AccessLayout.TOP_SAFE < 0.1f)
    }

    @Test fun `a phone on its side holds six Large rows with shorter rows, and Extra large steps down to Large`() {
        for (size in listOf(TextSize.LARGE, TextSize.EXTRA_LARGE)) {
            val p = plan(2400, 1080, size)
            assertEquals(TextSize.LARGE, p.size)
            assertTrue(p.reduced)
            assertEquals(AccessLayout.MIN_ROW_FACTOR * p.text, p.pitch, eps)
        }
    }

    @Test fun `a phone on its side with the card cannot hold six big rows, so it gets the standard list rather than a clipped one`() {
        for (size in listOf(TextSize.LARGE, TextSize.EXTRA_LARGE)) {
            val p = plan(2400, 1080, size, padding = AccessLayout.CARD_PADDING * 1080)
            assertEquals(TextSize.STANDARD, p.size)
            assertTrue(p.reduced)
        }
    }
}
