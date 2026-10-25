package com.prayerwallpaper.app.wallpaper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListLayoutTest {
    @Test fun `list height is three rows of the shorter side`() {
        // 1080 x 2424 portrait: three rows of 5.3 percent of 1080 is 171.7 px of 2424.
        assertEquals(0.0708f, ListLayout.heightFraction(1080, 2424), 0.001f)
    }

    @Test fun `the lowest allowed top still keeps the whole list on screen`() {
        for ((w, h) in listOf(1080 to 2424, 720 to 1560, 1440 to 3200, 1080 to 1920)) {
            val top = ListLayout.maxTop(w, h)
            assertTrue("$w x $h: bottom ${top + ListLayout.heightFraction(w, h)}", top + ListLayout.heightFraction(w, h) < 1f)
            assertTrue(top <= 0.85f)
        }
    }

    @Test fun `the default position is inside the allowed range`() {
        assertTrue(ListLayout.DEFAULT_TOP < ListLayout.maxTop(1080, 2424))
    }

    @Test fun `the Hijri line is a row of its own half a row above the list, the list's text size, and fully opaque`() {
        assertEquals(ListLayout.ROW_PITCH / 2, ListLayout.HIJRI_GAP, 1e-6f)
        assertEquals(1.0f, ListLayout.HIJRI_SCALE, 1e-6f)
        assertEquals(1.0f, ListLayout.HIJRI_ALPHA, 1e-6f)
        assertEquals(ListLayout.HIJRI_GAP + ListLayout.ROW_PITCH, ListLayout.HIJRI_BLOCK, 1e-6f)
    }

    @Test fun `the moon sits the same distance from the date as a prayer icon from its name`() {
        assertEquals(ListLayout.NAME_OFFSET - ListLayout.PILL_PADDING - ListLayout.ICON_SIZE, ListLayout.HIJRI_ICON_GAP, 1e-6f)
        assertTrue(ListLayout.HIJRI_ICON_GAP > 0f)
    }

    @Test fun `the Hijri line does not change where the list is allowed to go`() {
        // The list's own limits are untouched by the line above it.
        assertEquals(1f - ListLayout.heightFraction(1080, 1000) - 0.03f, ListLayout.maxTop(1080, 1000), 1e-6f)
        assertEquals(3 * ListLayout.ROW_PITCH * 1080 / 2424, ListLayout.heightFraction(1080, 2424), 1e-6f)
    }

    @Test fun `the Hijri line may be a little wider than the list, and an event may shrink it only so far`() {
        assertTrue(ListLayout.HIJRI_MAX_WIDTH > ListLayout.LIST_WIDTH)
        assertTrue(ListLayout.HIJRI_MAX_WIDTH <= 1f)
        assertEquals(0.70f, ListLayout.HIJRI_EVENT_MIN_SCALE, 1e-6f)
        assertEquals(0.70f, ListLayout.HIJRI_DATE_MIN_SCALE, 1e-6f)
    }

    @Test fun `the countdown is a row of its own half a row under the list, a little smaller than the list text`() {
        assertEquals(ListLayout.ROW_PITCH / 2, ListLayout.COUNTDOWN_GAP, 1e-6f)
        assertEquals(ListLayout.COUNTDOWN_GAP + ListLayout.ROW_PITCH, ListLayout.COUNTDOWN_BLOCK, 1e-6f)
        assertTrue(ListLayout.COUNTDOWN_SCALE in 0.7f..1f)
        assertTrue(ListLayout.COUNTDOWN_ALPHA in 0.5f..1f)
        // The same distance below the list as the Hijri line is above it, so the two lines balance it.
        assertEquals(ListLayout.HIJRI_BLOCK, ListLayout.COUNTDOWN_BLOCK, 1e-6f)
    }

    @Test fun `the countdown reaches below the list by its block, as a share of the height`() {
        assertEquals(ListLayout.COUNTDOWN_BLOCK * 1080 / 2424, ListLayout.countdownFraction(1080, 2424), 1e-6f)
    }

    @Test fun `without the countdown the list's limits are exactly what they were`() {
        for ((w, h) in listOf(1080 to 2424, 720 to 1560, 1440 to 3200, 1080 to 1920, 1840 to 2208)) {
            assertEquals(ListLayout.maxTop(w, h), ListLayout.maxTop(w, h, countdown = false), 0f)
        }
    }

    @Test fun `with the countdown the list and the line under it both stay on screen`() {
        for ((w, h) in listOf(1080 to 2424, 720 to 1560, 1440 to 3200, 1080 to 1920, 1840 to 2208, 1080 to 1200)) {
            val top = ListLayout.maxTop(w, h, countdown = true)
            val bottom = top + ListLayout.heightFraction(w, h) + ListLayout.countdownFraction(w, h)
            assertTrue("$w x $h: bottom $bottom", bottom < 1f)
            assertTrue(top <= ListLayout.maxTop(w, h))
        }
    }

    @Test fun `on a tall phone the countdown does not lower the limit at all`() {
        // The 0.85 cap is what holds the list on a 20:9 screen, with or without the line.
        assertEquals(0.85f, ListLayout.maxTop(1080, 2424, countdown = true), 1e-6f)
        assertEquals(0.85f, ListLayout.maxTop(900, 1950, countdown = true), 1e-6f)
    }

    @Test fun `on a nearly square screen the countdown does lower the limit`() {
        assertTrue(ListLayout.maxTop(1840, 2208, countdown = true) < ListLayout.maxTop(1840, 2208))
    }
}
