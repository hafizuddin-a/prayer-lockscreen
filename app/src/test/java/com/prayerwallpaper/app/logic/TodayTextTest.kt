package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class TodayTextTest {
    private val times = listOf("05:34", "06:51", "12:54", "16:04", "18:56", "20:05").map(LocalTime::parse)
    private val monday = PrayerDay(LocalDate.of(2026, 10, 5), times)
    private val friday = PrayerDay(LocalDate.of(2026, 10, 9), times)
    private val days = listOf(monday, friday).associateBy { it.date }

    private fun at(date: String, time: String): Instant = ZonedDateTime.parse("${date}T$time+08:00[Asia/Singapore]").toInstant()
    private fun snapshot(date: String, time: String) = PrayerSchedule(days, Clock.fixed(at(date, time), SINGAPORE)).snapshot()

    @Test fun `a time is said whole, with am or pm, not as a small suffix`() {
        assertEquals("5:34 am", spokenTime(LocalTime.of(5, 34), false))
        assertEquals("12:54 pm", spokenTime(LocalTime.of(12, 54), false))
        assertEquals("12:05 am", spokenTime(LocalTime.of(0, 5), false))
    }

    @Test fun `in 24 hour time it is just the time`() {
        assertEquals("16:04", spokenTime(LocalTime.of(16, 4), true))
        assertEquals("05:34", spokenTime(LocalTime.of(5, 34), true))
    }

    @Test fun `there are six rows in order and exactly one is the current prayer`() {
        val rows = todayRows(snapshot("2026-10-05", "13:50"), NameSet.MALAY, false)
        assertEquals(listOf("Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"), rows.map { it.label })
        assertEquals(listOf("5:34 am", "6:51 am", "12:54 pm", "4:04 pm", "6:56 pm", "8:05 pm").map { it }.size, rows.size)
        assertEquals(1, rows.count { it.current })
        assertEquals("Zohor", rows.single { it.current }.label) // 13:50: Zohor started at 12:54, Asar is next
    }

    @Test fun `after Isyak the list is still today's with Isyak current, and with no data for tomorrow there is no next prayer`() {
        val snapshot = snapshot("2026-10-05", "21:00")
        assertEquals("Isyak", todayRows(snapshot, NameSet.MALAY, false).single { it.current }.label)
        // Friday is not tomorrow, so tomorrow's Subuh is unknown.
        assertTrue(snapshot.next == null)
    }

    @Test fun `a row is spoken as label, time and whether it is the current prayer`() {
        assertEquals("Asar, 4:04 pm, current prayer", TodayRow("Asar", "4:04 pm", true).spoken)
        assertEquals("Subuh, 5:34 am", TodayRow("Subuh", "5:34 am", false).spoken)
    }

    @Test fun `on a Friday the noon prayer is Jumaat, or Jumuah in English`() {
        assertEquals("Jumaat", todayRows(snapshot("2026-10-09", "10:00"), NameSet.MALAY, false)[2].label)
        assertEquals("Jumuah", todayRows(snapshot("2026-10-09", "10:00"), NameSet.ENGLISH, false)[2].label)
        assertEquals("Dhuhr", todayRows(snapshot("2026-10-05", "10:00"), NameSet.ENGLISH, false)[2].label)
    }

    @Test fun `the preview's text alternative names the next prayer, its time and the time left`() {
        val now = at("2026-10-05", "13:50")
        assertEquals(
            "Preview of the lock screen: Asar at 4:04 pm is the next prayer, in 2h 14m.",
            previewDescription(snapshot("2026-10-05", "13:50"), now, NameSet.MALAY, false),
        )
    }

    @Test fun `the description follows the names and the 24 hour setting`() {
        val now = at("2026-10-09", "10:49")
        assertEquals(
            "Preview of the lock screen: Jumuah at 12:54 is the next prayer, in 2h 5m.",
            previewDescription(snapshot("2026-10-09", "10:49"), now, NameSet.ENGLISH, true),
        )
    }

    @Test fun `without a timetable the description says so`() {
        val none = PrayerSnapshot(null, null, null)
        assertTrue(previewDescription(none, Instant.EPOCH, NameSet.MALAY, false).contains("no prayer times"))
        assertTrue(todayRows(none, NameSet.MALAY, false).isEmpty())
    }

    @Test fun `every prayer is in the six rows`() {
        assertEquals(Prayer.entries.size, todayRows(snapshot("2026-10-05", "13:50"), NameSet.MALAY, false).size)
    }
}
