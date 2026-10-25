package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HijriStatusTest {
    private val until = LocalDate.parse("2028-01-27")

    private fun status(daysLeft: Long, usesMuis: Boolean = true) = hijriStatus(usesMuis, until, until.minusDays(daysLeft))

    @Test fun `plenty of time left gives the date with no warning`() {
        val s = status(daysLeft = 479)
        assertEquals("Hijri dates available until 27 Jan 2028.", s.message)
        assertFalse(s.warn)
    }

    @Test fun `60 days left is still fine, 59 warns`() {
        assertFalse(status(60).warn)
        assertTrue(status(59).warn)
        assertEquals(60, HIJRI_WARN_DAYS)
    }

    @Test fun `a warning says how many days are left and what to do`() {
        val s = status(daysLeft = 12)
        assertEquals("Hijri dates available until 27 Jan 2028. Only 12 days left: update the app for the next calendar.", s.message)
        assertTrue(s.warn)
    }

    @Test fun `one day left is said in the singular`() {
        assertTrue(status(1).message.contains("Only 1 day left"))
    }

    @Test fun `on the last certain date it still shows, with a warning`() {
        val s = status(0)
        assertTrue(s.warn)
        assertTrue(s.message.contains("Only 0 days left"))
    }

    @Test fun `once the table has run out the line is hidden and the screen says so`() {
        val s = status(daysLeft = -1)
        assertEquals("Hijri dates ran out after 27 Jan 2028. Update the app for the next calendar.", s.message)
        assertTrue(s.warn)
    }

    @Test fun `an empty table is a warning, not a date`() {
        val s = hijriStatus(true, null, LocalDate.parse("2026-10-05"))
        assertTrue(s.warn)
        assertTrue(s.message.contains("Couldn't read the Hijri calendar"))
    }

    @Test fun `outside Singapore it explains why there is no Hijri date and does not warn about running out`() {
        val s = status(daysLeft = 5, usesMuis = false)
        assertFalse(s.warn)
        assertTrue(s.message.contains("only shown for Singapore"))
    }

    @Test fun `the date is in English whatever the phone language is`() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ms-MY"))
            assertTrue(status(479).message.contains("27 Jan 2028"))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
