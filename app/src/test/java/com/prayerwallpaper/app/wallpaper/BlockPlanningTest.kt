package com.prayerwallpaper.app.wallpaper

import com.prayerwallpaper.app.logic.AccessLayout
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.logic.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which layout the accessibility options choose, and that the High contrast card always stays on the screen. */
class BlockPlanningTest {
    private val eps = 0.01f
    private val screens = listOf(720 to 1280, 720 to 1600, 1080 to 2400, 1080 to 2424, 1440 to 3200, 1840 to 2208)
    private val standard = AccessOptions()
    private val contrast = AccessOptions(highContrast = true)
    private fun big(size: TextSize, highContrast: Boolean = false) = AccessOptions(textSize = size, highContrast = highContrast)

    @Test fun `the usual options need no plan at all, so the usual list is drawn as it always was`() {
        assertNull(planFor(1080, 2424, standard, 0.4f, 1, true))
        assertNull(planFor(1080, 2424, AccessOptions(bold = true), 0.4f, 1, true))
    }

    @Test fun `Large text without the card is one column and no padding`() {
        val plan = planFor(1080, 2424, big(TextSize.LARGE), 0.4f, 1, true)!!
        assertEquals(TextSize.LARGE, plan.size)
        assertEquals(1, plan.columns)
        assertEquals(0f, plan.padding, 0f)
        assertEquals(plan.left, plan.contentLeft, 0f)
        assertEquals(plan.width, plan.contentWidth, 0f)
    }

    @Test fun `High contrast on its own keeps the two columns, inside the card`() {
        val plan = planFor(1080, 2424, contrast, 0.4f, 1, true)!!
        assertEquals(TextSize.STANDARD, plan.size)
        assertEquals(2, plan.columns)
        assertEquals(0.03f * 1080, plan.padding, eps)
        // The usual rows, the usual text.
        assertEquals(ListLayout.ROW_PITCH * 1080, plan.pitch, eps)
        assertEquals(ListLayout.TEXT_SIZE * 1080, plan.text, eps)
    }

    @Test fun `Large text with High contrast is one column inside the card`() {
        val plan = planFor(1080, 2424, big(TextSize.LARGE, true), 0.4f, 1, true)!!
        assertEquals(TextSize.LARGE, plan.size)
        assertEquals(1, plan.columns)
        assertEquals(0.03f * 1080, plan.padding, eps)
    }

    @Test fun `the card spans the block width and the content is inside its padding`() {
        for (access in listOf(contrast, big(TextSize.LARGE, true), big(TextSize.EXTRA_LARGE, true))) {
            val plan = planFor(1080, 2424, access, 0.4f, 1, true)!!
            assertEquals(0.88f * 1080, plan.width, eps)
            assertEquals((1080 - plan.width) / 2, plan.left, eps)
            assertEquals(plan.left + plan.padding, plan.contentLeft, eps)
            assertEquals(plan.width - 2 * plan.padding, plan.contentWidth, eps)
        }
    }

    @Test fun `the card's corner radius and padding are 5 and 3 percent of the width`() {
        assertEquals(0.05f, AccessLayout.CARD_RADIUS, 0f)
        assertEquals(0.03f, AccessLayout.CARD_PADDING, 0f)
    }

    @Test fun `the card holds the Hijri date, the rows and the countdown and nothing more`() {
        val plan = planFor(1080, 2424, big(TextSize.LARGE, true), 0.4f, 1, true)!!
        assertEquals(plan.dateRowTop!! - plan.padding, plan.top, eps)
        assertEquals(plan.countdownRowTop!! + plan.lineRow + plan.padding, plan.bottom, eps)
    }

    @Test fun `without the Hijri line or the countdown the card hugs the rows`() {
        val plan = planFor(1080, 2424, big(TextSize.LARGE, true), 0.4f, 0, false)!!
        assertEquals(plan.listTop - plan.padding, plan.top, eps)
        assertEquals(plan.listTop + 6 * plan.pitch + plan.padding, plan.bottom, eps)
    }

    @Test fun `the card never leaves the safe area, for any size, any content and any list position`() {
        val options = listOf(contrast, big(TextSize.LARGE, true), big(TextSize.EXTRA_LARGE, true))
        for ((w, h) in screens) for (access in options) for (hijri in 0..2) for (countdown in listOf(true, false)) for (top in listOf(0f, 0.05f, 0.4f, 0.85f, 1f)) {
            val plan = planFor(w, h, access, top, hijri, countdown)!!
            val where = "$w x $h ${access.textSize} hijri=$hijri countdown=$countdown top=$top"
            assertTrue("$where: top ${plan.top}", plan.top >= AccessLayout.TOP_SAFE * h - eps)
            assertTrue("$where: bottom ${plan.bottom}", plan.bottom <= (1f - AccessLayout.BOTTOM_SAFE) * h + eps)
        }
    }

    @Test fun `the standard list in a card is held between the same margins, so a list parked low is lifted`() {
        val low = planFor(1080, 2424, contrast, 0.85f, 1, true)!!
        assertEquals((1f - AccessLayout.BOTTOM_SAFE) * 2424, low.bottom, eps)
        assertTrue(low.listTop < 0.85f * 2424)
    }

    @Test fun `the standard list in a card stays put when it fits`() {
        assertEquals(0.4f * 2424, planFor(1080, 2424, contrast, 0.4f, 1, true)!!.listTop, eps)
    }

    @Test fun `the standard card plan lays the Hijri line and the countdown out as the usual list does`() {
        val plan = planFor(1080, 2424, contrast, 0.4f, 1, true)!!
        assertEquals(plan.listTop - ListLayout.HIJRI_BLOCK * 1080, plan.dateRowTop!!, eps)
        assertEquals(plan.listTop + 3 * plan.pitch + ListLayout.COUNTDOWN_GAP * 1080, plan.countdownRowTop!!, eps)
    }

    @Test fun `Large text that cannot fit even at Large falls back to the standard list in the card, flagged as reduced`() {
        val plan = planFor(2400, 1080, big(TextSize.EXTRA_LARGE, true), 0.4f, 1, true)!!
        assertEquals(TextSize.STANDARD, plan.size)
        assertEquals(2, plan.columns)
        assertTrue(plan.reduced)
    }

    @Test fun `without the card a phone on its side still gets the big layout, Extra large stepping down to Large`() {
        val plan = planFor(2400, 1080, big(TextSize.EXTRA_LARGE), 0.4f, 1, true)!!
        assertEquals(TextSize.LARGE, plan.size)
        assertTrue(plan.reduced)
    }

    // --- the drag and the slider ---

    @Test fun `the usual list keeps its usual range`() {
        val range = listTopRange(1080, 2424, standard, hijri = true, countdown = true)
        assertEquals(0.05f, range.start, 0f)
        assertEquals(0.85f, range.endInclusive, 0f)
    }

    @Test fun `the range with the card or the big text is held by the fit rule`() {
        for (access in listOf(contrast, big(TextSize.LARGE), big(TextSize.EXTRA_LARGE, true))) {
            val range = listTopRange(1080, 2424, access, hijri = true, countdown = true)
            assertTrue("$access", range.start >= 0f)
            assertTrue("$access", range.endInclusive <= 0.85f)
            assertTrue("$access", range.start < range.endInclusive)
        }
    }

    @Test fun `the block extent reaches over and under the list when there is a card or big text`() {
        assertNull(accessBlockExtent(1080, 2424, 0.4f, standard, 1, true))
        val extent = accessBlockExtent(1080, 2424, 0.4f, big(TextSize.LARGE, true), 1, true)
        assertNotNull(extent)
        assertTrue(extent!!.above > 0f)
        assertTrue(extent.below > 0f)
        assertEquals(6 * 0.058f * 1.9f * 1080, extent.listHeight, eps)
    }

    @Test fun `no range is empty, even on a screen with room for one position`() {
        for ((w, h) in listOf(720 to 900, 720 to 1000, 2400 to 1080)) {
            val range = listTopRange(w, h, big(TextSize.EXTRA_LARGE, true), hijri = true, countdown = true)
            assertFalse("$w x $h", range.start >= range.endInclusive)
        }
    }
}
