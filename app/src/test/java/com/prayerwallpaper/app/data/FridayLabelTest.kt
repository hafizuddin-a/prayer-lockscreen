package com.prayerwallpaper.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class FridayLabelTest {
    private val times = listOf("05:34", "06:51", "12:54", "16:04", "18:56", "20:05").map(LocalTime::parse)
    private fun day(date: String) = PrayerDay(LocalDate.parse(date), times)

    // 2026-10-02 is a Friday, 2026-10-05 a Monday.
    @Test fun `on a Friday the Malay noon prayer is Jumaat`() {
        assertEquals("Jumaat", day("2026-10-02").label(Prayer.ZOHOR, NameSet.MALAY))
    }

    @Test fun `on a Friday the English noon prayer is Jumuah`() {
        assertEquals("Jumuah", day("2026-10-02").label(Prayer.ZOHOR, NameSet.ENGLISH))
    }

    @Test fun `on any other day it keeps its usual name`() {
        // 3 October 2026 is a Saturday, so these are Saturday to Thursday.
        for (date in listOf("2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07", "2026-10-08")) {
            assertEquals(date, "Zohor", day(date).label(Prayer.ZOHOR, NameSet.MALAY))
            assertEquals(date, "Dhuhr", day(date).label(Prayer.ZOHOR, NameSet.ENGLISH))
        }
    }

    @Test fun `only the noon prayer is renamed`() {
        val friday = day("2026-10-02")
        for (names in NameSet.entries) {
            for (prayer in Prayer.entries.filter { it != Prayer.ZOHOR }) {
                assertEquals("$prayer $names", prayer.label(names), friday.label(prayer, names))
            }
        }
    }

    @Test fun `the time is untouched, only the name changes`() {
        assertEquals(LocalTime.of(12, 54), day("2026-10-02")[Prayer.ZOHOR])
    }

    @Test fun `the dataset column key is still zohor on a Friday`() {
        assertEquals("zohor", Prayer.ZOHOR.column)
    }

    @Test fun `the day knows it is a Friday`() {
        assertTrue(day("2026-10-02").isFriday)
        assertFalse(day("2026-10-05").isFriday)
        assertTrue(day("2026-10-09").isFriday)
    }

    @Test fun `a label with no day given is the usual name`() {
        assertEquals("Zohor", Prayer.ZOHOR.label(NameSet.MALAY))
        assertEquals("Dhuhr", Prayer.ZOHOR.label(NameSet.ENGLISH))
        assertEquals("Jumaat", Prayer.ZOHOR.label(NameSet.MALAY, friday = true))
    }
}
