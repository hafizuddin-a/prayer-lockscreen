package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class ReminderContentTest {
    @Test fun `at the prayer time the title is the prayer and the text is its time`() {
        assertEquals(ReminderContent("Zohor", "1:00 pm"), reminderContent(ReminderKind.AT, "Zohor", 0, "1:00 pm"))
    }

    @Test fun `a heads-up says how long to go and keeps the prayer's time as the text`() {
        assertEquals(ReminderContent("Zohor in 10 minutes", "1:00 pm"), reminderContent(ReminderKind.PRE, "Zohor", 10, "1:00 pm"))
        assertEquals(ReminderContent("Subuh in 5 minutes", "5:33 am"), reminderContent(ReminderKind.PRE, "Subuh", 5, "5:33 am"))
        assertEquals(ReminderContent("Isyak in 15 minutes", "8:05 pm"), reminderContent(ReminderKind.PRE, "Isyak", 15, "8:05 pm"))
    }

    @Test fun `one minute is not plural`() {
        assertEquals("Asar in 1 minute", reminderContent(ReminderKind.PRE, "Asar", 1, "4:04 pm").title)
    }

    @Test fun `the names follow the list, Jumaat on a Friday and English names if chosen`() {
        assertEquals("Jumaat", reminderContent(ReminderKind.AT, "Jumaat", 0, "12:54 pm").title)
        assertEquals("Fajr in 10 minutes", reminderContent(ReminderKind.PRE, "Fajr", 10, "5:33 am").title)
    }

    @Test fun `the time can be a 24 hour time`() {
        assertEquals("16:04", reminderContent(ReminderKind.AT, "Asar", 0, "16:04").text)
    }

    @Test fun `the test says what it is, whatever it is given`() {
        assertEquals(ReminderContent(TEST_TITLE, TEST_TEXT), reminderContent(ReminderKind.TEST, "", 0, ""))
    }

    @Test fun `how far ahead a heads-up is, in whole minutes, never below zero`() {
        val at = Instant.parse("2026-10-05T04:54:00Z")
        assertEquals(10L, minutesBefore(at, at.plusSeconds(600)))
        assertEquals(0L, minutesBefore(at, at))
        assertEquals(0L, minutesBefore(at, at.minusSeconds(60)))
    }
}
