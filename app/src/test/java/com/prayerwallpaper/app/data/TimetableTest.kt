package com.prayerwallpaper.app.data

import com.prayerwallpaper.app.logic.AsrRule
import com.prayerwallpaper.app.logic.CalcMethod
import com.prayerwallpaper.app.logic.CalcRequest
import com.prayerwallpaper.app.logic.PrayerCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class TimetableTest {
    private fun request(zone: String) = CalcRequest(51.5, -0.12, ZoneId.of(zone), CalcMethod.MUSLIM_WORLD_LEAGUE, AsrRule.STANDARD, List(6) { 0 })

    private val standardDay = listOf("05:00", "06:30", "12:30", "15:45", "18:20", "19:40").map(LocalTime::parse)

    /** Gives every date the same six times unless told otherwise, and counts how often it was asked. */
    private class FakeCalculator(private val times: List<LocalTime>, private val missing: Set<LocalDate> = emptySet()) : PrayerCalculator {
        var calls = 0
        override fun calculate(request: CalcRequest, date: LocalDate): PrayerDay? {
            calls++
            return if (date in missing) null else PrayerDay(date, times)
        }
    }

    private fun clockAt(utc: String) = Clock.fixed(Instant.parse(utc), ZoneOffset.UTC)

    @Test fun `a calculated timetable gives today and tomorrow in the place's zone`() {
        val table = CalculatedTimetable(request("Europe/London"), FakeCalculator(standardDay))
        val today = LocalDate.parse("2026-10-05")
        assertEquals(setOf(today, today.plusDays(1)), table.days(today).keys)
        assertEquals(ZoneId.of("Europe/London"), table.zone)
    }

    @Test fun `the same date is only worked out once`() {
        val calculator = FakeCalculator(standardDay)
        val table = CalculatedTimetable(request("Europe/London"), calculator)
        val today = LocalDate.parse("2026-10-05")
        repeat(5) { table.days(today) }
        assertEquals(2, calculator.calls) // today and tomorrow
        table.days(today.plusDays(1)) // tomorrow is already known, the day after is new
        assertEquals(3, calculator.calls)
    }

    @Test fun `a date that cannot be calculated is left out and not asked about again`() {
        val today = LocalDate.parse("2026-06-21")
        val calculator = FakeCalculator(standardDay, missing = setOf(today))
        val table = CalculatedTimetable(request("Europe/Oslo"), calculator)
        assertEquals(setOf(today.plusDays(1)), table.days(today).keys)
        table.days(today)
        assertEquals(2, calculator.calls)
        assertEquals(WhenMissing.CANNOT_CALCULATE, table.whenMissing)
    }

    @Test fun `memory is bounded`() {
        val calculator = FakeCalculator(standardDay)
        val table = CalculatedTimetable(request("Europe/London"), calculator)
        val start = LocalDate.parse("2026-01-01")
        for (i in 0 until 40L) table.days(start.plusDays(i))
        val before = calculator.calls
        table.days(start) // long forgotten, so worked out again
        assertTrue(calculator.calls > before)
    }

    @Test fun `the place's zone decides which date it is`() {
        // 23:30 UTC on 5 Oct is already 6 Oct in Singapore but still 6 Oct 00:30 in London (BST), and 19:30 on 5 Oct in New York.
        val instant = "2026-10-05T23:30:00Z"
        val seen = mutableListOf<LocalDate>()
        val calculator = object : PrayerCalculator {
            override fun calculate(request: CalcRequest, date: LocalDate): PrayerDay? {
                seen += date
                return PrayerDay(date, listOf("05:00", "06:30", "12:30", "15:45", "18:20", "19:40").map(LocalTime::parse))
            }
        }
        for ((zone, expected) in listOf("Asia/Singapore" to "2026-10-06", "Europe/London" to "2026-10-06", "America/New_York" to "2026-10-05")) {
            seen.clear()
            CalculatedTimetable(request(zone), calculator).snapshot(clockAt(instant))
            assertEquals(zone, LocalDate.parse(expected), seen.first())
        }
    }

    @Test fun `a snapshot marks the prayer that is on now, in the place's own time`() {
        val table = CalculatedTimetable(request("Europe/London"), FakeCalculator(standardDay))
        // 12:00 UTC is 13:00 BST in London: Zohor (12:30) has started and is marked, and Asar (15:45) is next.
        val snapshot = table.snapshot(clockAt("2026-10-05T12:00:00Z"))
        assertEquals(Prayer.ZOHOR, snapshot.highlight)
        assertEquals(Prayer.ASAR, snapshot.next?.prayer)
        assertEquals(LocalDate.parse("2026-10-05"), snapshot.day?.date)
        // The same instant in Singapore is 20:00, after Isyak (19:40): still today's list with Isyak marked, and tomorrow's Subuh is next.
        val singapore = CalculatedTimetable(request("Asia/Singapore"), FakeCalculator(standardDay)).snapshot(clockAt("2026-10-05T12:00:00Z"))
        assertEquals(Prayer.ISYAK, singapore.highlight)
        assertEquals(Prayer.SUBUH, singapore.next?.prayer)
        assertEquals(LocalDate.parse("2026-10-05"), singapore.day?.date)
        assertEquals(LocalDate.parse("2026-10-06"), singapore.next?.day?.date)
    }

    @Test fun `the next change is the right instant for the place's zone`() {
        val instant = "2026-10-05T12:00:00Z"
        // Asar 15:45 BST is 14:45 UTC.
        assertEquals(
            Instant.parse("2026-10-05T14:45:00Z"),
            CalculatedTimetable(request("Europe/London"), FakeCalculator(standardDay)).snapshot(clockAt(instant)).nextChange,
        )
        // After Isyak the picture next changes at midnight: 00:00 on 6 Oct in Singapore (UTC+8) is 16:00 UTC on 5 Oct. Tomorrow's Subuh,
        // 05:00 there, is 21:00 UTC, which is when the countdown is counting to.
        val singapore = CalculatedTimetable(request("Asia/Singapore"), FakeCalculator(standardDay)).snapshot(clockAt(instant))
        assertEquals(Instant.parse("2026-10-05T16:00:00Z"), singapore.nextChange)
        assertEquals(Instant.parse("2026-10-05T21:00:00Z"), singapore.next?.at)
    }

    @Test fun `with no days at all the snapshot is empty`() {
        val today = LocalDate.parse("2026-10-05")
        val table = CalculatedTimetable(request("Europe/London"), FakeCalculator(standardDay, missing = setOf(today, today.plusDays(1))))
        val snapshot = table.snapshot(clockAt("2026-10-05T10:00:00Z"))
        assertNull(snapshot.day)
        assertNull(snapshot.highlight)
    }

    @Test fun `the MUIS timetable is Singapore time and asks for a download or an update`() {
        assertEquals(ZoneId.of("Asia/Singapore"), MuisTimetable(PrayerData()).zone)
        assertEquals(WhenMissing.LOAD, MuisTimetable(PrayerData()).whenMissing)
        val day = PrayerDay(LocalDate.parse("2026-10-05"), standardDay)
        val data = PrayerData(listOf(YearData(2026, "d_test", Instant.parse("2026-10-01T00:00:00Z"), listOf(day))))
        val table = MuisTimetable(data)
        assertEquals(WhenMissing.UPDATE, table.whenMissing)
        assertEquals(day, table.days(LocalDate.parse("2026-10-05"))[LocalDate.parse("2026-10-05")])
    }
}
