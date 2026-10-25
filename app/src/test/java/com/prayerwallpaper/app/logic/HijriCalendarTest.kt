package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class HijriCalendarTest {
    private fun row(year: Int, month: Int, start: String) = HijriMonthStart(year, month, HIJRI_MONTH_NAMES[month - 1], LocalDate.parse(start))

    // Real month starts from the MUIS calendar, but the tests only rely on their shape: Zulkaedah has 29 days,
    // Zulhijjah 30, then a new Hijri year; Safar is last, with no next start in the table.
    private val table = listOf(
        row(1447, 11, "2026-04-19"),
        row(1447, 12, "2026-05-18"),
        row(1448, 1, "2026-06-17"),
        row(1448, 2, "2026-07-16"),
    )

    // The clock does not matter for on(), which takes the date itself.
    private fun on(date: String) = HijriCalendar(table).on(LocalDate.parse(date))

    @Test fun `the first day of a month`() {
        assertEquals(HijriDate(1, 11, "Zulkaedah", 1447), on("2026-04-19"))
        assertEquals("1 Zulkaedah 1447H", on("2026-04-19")!!.text)
    }

    @Test fun `the second day of a month`() {
        assertEquals(HijriDate(2, 11, "Zulkaedah", 1447), on("2026-04-20"))
    }

    @Test fun `day 29 of a 29 day month is the last day of it`() {
        assertEquals(HijriDate(29, 11, "Zulkaedah", 1447), on("2026-05-17"))
    }

    @Test fun `day 30 exists when the next month starts on day 31`() {
        // Zulhijjah starts 18 May and Muharram 17 June, 30 days later.
        assertEquals(HijriDate(30, 12, "Zulhijjah", 1447), on("2026-06-16"))
    }

    @Test fun `the month rolls over on the next start`() {
        assertEquals(HijriDate(29, 11, "Zulkaedah", 1447), on("2026-05-17"))
        assertEquals(HijriDate(1, 12, "Zulhijjah", 1447), on("2026-05-18"))
    }

    @Test fun `the Hijri year goes up after Zulhijjah`() {
        assertEquals(HijriDate(30, 12, "Zulhijjah", 1447), on("2026-06-16"))
        assertEquals(HijriDate(1, 1, "Muharram", 1448), on("2026-06-17"))
    }

    @Test fun `with no next month start, day 29 or less is still certain`() {
        assertEquals(HijriDate(1, 2, "Safar", 1448), on("2026-07-16"))
        assertEquals(HijriDate(29, 2, "Safar", 1448), on("2026-08-13"))
    }

    @Test fun `with no next month start, day 30 is unknown`() {
        assertNull(on("2026-08-14"))
        assertNull(on("2026-08-15"))
        assertNull(on("2027-03-01"))
    }

    @Test fun `before the first month start is unknown`() {
        assertNull(on("2026-04-18"))
        assertNull(on("2020-01-01"))
    }

    @Test fun `the last date that is certain is day 29 of the last month`() {
        assertEquals(LocalDate.parse("2026-08-13"), HijriCalendar(table).lastKnownDate)
        assertNotNull(on("2026-08-13"))
        assertNull(on("2026-08-14"))
    }

    @Test fun `an empty table knows nothing`() {
        val empty = HijriCalendar(emptyList(), Clock.systemUTC())
        assertTrue(empty.isEmpty)
        assertNull(empty.on(LocalDate.parse("2026-10-05")))
        assertNull(empty.today())
        assertNull(empty.lastKnownDate)
        assertNull(empty.daysLeft())
    }

    @Test fun `a table out of order is sorted rather than trusted as given`() {
        assertEquals(HijriDate(2, 12, "Zulhijjah", 1447), HijriCalendar(table.reversed()).on(LocalDate.parse("2026-05-19")))
    }

    @Test fun `a month that runs past 30 days because the table is wrong is unknown, never a day 31`() {
        val broken = listOf(row(1447, 11, "2026-04-19"), row(1447, 12, "2026-06-30"))
        assertEquals(HijriDate(30, 11, "Zulkaedah", 1447), HijriCalendar(broken).on(LocalDate.parse("2026-05-18")))
        assertNull(HijriCalendar(broken).on(LocalDate.parse("2026-05-19")))
    }

    // The clock is in UTC here; Singapore is 8 hours ahead, so 16:00 UTC is midnight in Singapore.
    private fun todayAt(utc: String, atMaghrib: Boolean, maghrib: LocalTime? = null) =
        HijriCalendar(table, Clock.fixed(Instant.parse(utc), java.time.ZoneOffset.UTC)).today(atMaghrib, maghrib)

    private val maghrib = LocalTime.of(18, 56)
    private val day29 = HijriDate(29, 11, "Zulkaedah", 1447)
    private val day1 = HijriDate(1, 12, "Zulhijjah", 1447)
    private val day2 = HijriDate(2, 12, "Zulhijjah", 1447)

    @Test fun `changing at midnight, the date moves on at 00 00 in Singapore and ignores Maghrib`() {
        assertEquals(day29, todayAt("2026-05-17T15:59:59Z", atMaghrib = false)) // 23:59:59 on 17 May in Singapore
        assertEquals(day1, todayAt("2026-05-17T16:00:00Z", atMaghrib = false))  // 00:00:00 on 18 May
        assertEquals(day29, todayAt("2026-05-17T11:30:00Z", atMaghrib = false, maghrib = maghrib)) // 19:30, after Maghrib: still the 17th
    }

    @Test fun `the Singapore date is used whatever zone the clock is in`() {
        val newYork = Clock.fixed(Instant.parse("2026-05-17T16:00:00Z"), java.time.ZoneId.of("America/New_York"))
        assertEquals(day1, HijriCalendar(table, newYork).today(atMaghrib = false))
    }

    @Test fun `by default the date changes at Maghrib, as the Islamic day starts at sunset`() {
        assertTrue(HIJRI_DAY_ROLLS_AT_MAGHRIB)
    }

    @Test fun `changing at Maghrib, the date moves on at Maghrib`() {
        assertEquals(day29, todayAt("2026-05-17T10:55:00Z", atMaghrib = true, maghrib = maghrib)) // 18:55 on 17 May: not yet
        assertEquals(day1, todayAt("2026-05-17T10:56:00Z", atMaghrib = true, maghrib = maghrib))  // 18:56: tomorrow's date
        assertEquals(day1, todayAt("2026-05-17T15:59:59Z", atMaghrib = true, maghrib = maghrib))  // 23:59:59: still tomorrow's
        assertEquals(day1, todayAt("2026-05-17T16:00:00Z", atMaghrib = true, maghrib = maghrib))  // midnight: the same date, no jump
        assertEquals(day1, todayAt("2026-05-18T10:55:00Z", atMaghrib = true, maghrib = maghrib))  // 18:55 on 18 May: still day 1
        assertEquals(day2, todayAt("2026-05-18T10:56:00Z", atMaghrib = true, maghrib = maghrib))  // 18:56: day 2
    }

    @Test fun `the evening before a new month already shows the new month, and a new Hijri year`() {
        // 17 June 2026 is 1 Muharram 1448; after Maghrib on 16 June it is already that.
        assertEquals(HijriDate(30, 12, "Zulhijjah", 1447), todayAt("2026-06-16T10:55:00Z", atMaghrib = true, maghrib = maghrib))
        assertEquals(HijriDate(1, 1, "Muharram", 1448), todayAt("2026-06-16T10:56:00Z", atMaghrib = true, maghrib = maghrib))
    }

    @Test fun `changing at Maghrib without today's Maghrib time gives no date, not a guess that may be a day out`() {
        assertNull(todayAt("2026-05-17T14:00:00Z", atMaghrib = true, maghrib = null))
        assertNull(todayAt("2026-05-17T02:00:00Z", atMaghrib = true, maghrib = null)) // even in the morning: we cannot tell it is before Maghrib
    }

    @Test fun `at Maghrib on the last certain day the next day is unknown, so the line goes`() {
        // The table's last month starts 16 July, so day 29 is 13 August. After Maghrib that evening it is day 30: not known.
        assertEquals(HijriDate(29, 2, "Safar", 1448), todayAt("2026-08-13T10:55:00Z", atMaghrib = true, maghrib = maghrib))
        assertNull(todayAt("2026-08-13T10:56:00Z", atMaghrib = true, maghrib = maghrib))
    }

    @Test fun `days left counts to the last certain date`() {
        val atStart = HijriCalendar(table, Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), java.time.ZoneOffset.UTC))
        assertEquals(12L, atStart.daysLeft()) // 1 August to 13 August, in Singapore (08:00 on 1 August)
        val after = HijriCalendar(table, Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), java.time.ZoneOffset.UTC))
        assertEquals(-7L, after.daysLeft())
    }

    @Test fun `a month start file is read, and extra keys are ignored`() {
        val json = """{"source":"x","extra":1,"months":[{"year":1448,"month":4,"name":"Rabiulakhir","start":"2026-09-13","other":true}]}"""
        val cal = HijriCalendar.parse(json, Clock.systemUTC())
        assertEquals(HijriDate(23, 4, "Rabiulakhir", 1448), cal.on(LocalDate.parse("2026-10-05")))
    }

    @Test fun `a damaged month start file is rejected, not half read`() {
        assertThrows(Exception::class.java) { HijriCalendar.parse("not json") }
        assertThrows(Exception::class.java) { HijriCalendar.parse("""{"months":[{"year":1448,"month":13,"name":"X","start":"2026-09-13"}]}""") }
        assertThrows(Exception::class.java) { HijriCalendar.parse("""{"months":[{"year":1448,"month":4,"name":"Rabiulakhir","start":"13 Sep"}]}""") }
    }

    @Test fun `the text is day, month name and year with an H`() {
        assertEquals("12 Rabiulakhir 1448H", HijriDate(12, 4, "Rabiulakhir", 1448).text)
    }
}
