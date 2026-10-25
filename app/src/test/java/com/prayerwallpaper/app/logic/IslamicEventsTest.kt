package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class IslamicEventsTest {
    private val events = IslamicEvents(
        listOf(IslamicEvent(10, 1, "Hari Raya Aidilfitri"), IslamicEvent(1, 10, "Asyura Day"), IslamicEvent(3, 12, "Prophet Muhammad’s Birthday")),
    )

    private fun date(day: Int, month: Int) = HijriDate(day, month, HIJRI_MONTH_NAMES[month - 1], 1448)

    @Test fun `an event is found by the day of the Hijri month`() {
        assertEquals("Hari Raya Aidilfitri", events.on(date(1, 10)))
        assertEquals("Asyura Day", events.on(date(10, 1)))
        assertEquals("Prophet Muhammad’s Birthday", events.on(date(12, 3)))
    }

    @Test fun `it is the same event every Hijri year`() {
        assertEquals("Asyura Day", events.on(HijriDate(10, 1, "Muharram", 1447)))
        assertEquals("Asyura Day", events.on(HijriDate(10, 1, "Muharram", 1449)))
    }

    @Test fun `an ordinary day has no event, and the right day of the wrong month does not match`() {
        assertNull(events.on(date(2, 10)))
        assertNull(events.on(date(1, 9)))
        assertNull(events.on(date(10, 2)))
    }

    @Test fun `an empty list has no events`() {
        assertTrue(IslamicEvents(emptyList()).isEmpty)
        assertNull(IslamicEvents(emptyList()).on(date(1, 10)))
        assertFalse(events.isEmpty)
    }

    @Test fun `the event file is read, with extra keys ignored`() {
        val json = """{"source":"x","extra":1,"events":[{"month":9,"day":17,"name":"Nuzul Quran","other":true}]}"""
        assertEquals("Nuzul Quran", IslamicEvents.parse(json).on(date(17, 9)))
    }

    @Test fun `a damaged event file is rejected`() {
        assertThrows(Exception::class.java) { IslamicEvents.parse("not json") }
        assertThrows(Exception::class.java) { IslamicEvents.parse("""{"events":[{"month":13,"day":1,"name":"X"}]}""") }
        assertThrows(Exception::class.java) { IslamicEvents.parse("""{"events":[{"month":1,"day":31,"name":"X"}]}""") }
        assertThrows(Exception::class.java) { IslamicEvents.parse("""{"events":[{"month":1,"day":1,"name":" "}]}""") }
    }

    @Test fun `a line carries a date and maybe an event`() {
        assertNull(HijriLine(date(5, 4)).event)
        assertEquals("Asyura Day", HijriLine(date(10, 1), "Asyura Day").event)
    }
}
