package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The highlight (the pill) is the prayer that is on now, like the home screen widgets: it moves on the moment a prayer starts. The next
 * prayer, for the countdown line, is a separate answer. The list is always the calendar day's.
 */
class PrayerScheduleTest {
    private fun day(date: LocalDate, vararg times: String) = PrayerDay(date, times.map(LocalTime::parse))

    // Real MUIS times, rounded, for 1 and 2 January 2026.
    private val jan1 = day(LocalDate.of(2026, 1, 1), "05:44", "07:08", "13:10", "16:34", "19:11", "20:26")
    private val jan2 = day(LocalDate.of(2026, 1, 2), "05:45", "07:08", "13:11", "16:35", "19:11", "20:26")
    private val all = listOf(jan1, jan2).associateBy { it.date }

    private fun clockAt(date: String, time: String): Clock =
        Clock.fixed(ZonedDateTime.parse("${date}T$time:00+08:00[Asia/Singapore]").toInstant(), SINGAPORE)

    private fun snapshot(time: String, date: String = "2026-01-01", days: Map<LocalDate, PrayerDay> = all, highlightSyuruk: Boolean = true) =
        PrayerSchedule(days, clockAt(date, time), highlightSyuruk).snapshot()

    private fun at(date: String, time: String): Instant = clockAt(date, time).instant()

    @Test fun `before Subuh the pill is on last night's Isyak and Subuh is next, with no need for yesterday's data`() {
        val s = snapshot("04:00")
        assertEquals(jan1, s.day)
        assertEquals(Prayer.ISYAK, s.highlight)
        assertEquals(NextPrayer(jan1, Prayer.SUBUH, at("2026-01-01", "05:44")), s.next)
        assertEquals(at("2026-01-01", "05:44"), s.nextChange)
    }

    @Test fun `the pill moves on the moment a prayer starts`() {
        assertEquals(Prayer.ISYAK, snapshot("05:43").highlight)
        assertEquals(Prayer.SUBUH, snapshot("05:44").highlight) // Subuh has started, so it is the one that is on now
        assertEquals(Prayer.SYURUK, snapshot("05:44").next?.prayer)
        assertEquals(at("2026-01-01", "07:08"), snapshot("05:44").nextChange)
        assertEquals(Prayer.ZOHOR, snapshot("13:10").highlight)
        assertEquals(Prayer.ASAR, snapshot("13:10").next?.prayer)
    }

    @Test fun `between prayers the one that started last is marked and the following one is next`() {
        val s = snapshot("10:00")
        assertNull(s.highlight) // 10:00 is between Syuruk's minute and Zohor: sunrise is not a prayer, so nothing is marked
        assertEquals(Prayer.ZOHOR, s.next?.prayer)
        assertEquals(Prayer.ASAR, snapshot("17:00").highlight.also { assertEquals(Prayer.MAGHRIB, snapshot("17:00").next?.prayer) })
        assertEquals(Prayer.MAGHRIB, snapshot("19:30").highlight)
        assertEquals(Prayer.ISYAK, snapshot("19:30").next?.prayer)
        assertEquals(at("2026-01-01", "20:26"), snapshot("19:30").nextChange)
    }

    @Test fun `once Isyak has started the list is still today's, Isyak stays marked and tomorrow's Subuh is next`() {
        for (time in listOf("20:26", "22:00", "23:59")) {
            val s = snapshot(time)
            assertEquals(jan1, s.day)
            assertEquals(Prayer.ISYAK, s.highlight)
            assertEquals(NextPrayer(jan2, Prayer.SUBUH, at("2026-01-02", "05:45")), s.next)
            // Nothing changes until midnight, when the list becomes the new day's.
            assertEquals(at("2026-01-02", "00:00"), s.nextChange)
        }
    }

    @Test fun `at midnight the list becomes the new day's and the pill is still on Isyak until Subuh`() {
        val s = snapshot("00:00", date = "2026-01-02")
        assertEquals(jan2, s.day)
        assertEquals(Prayer.ISYAK, s.highlight)
        assertEquals(Prayer.SUBUH, s.next?.prayer)
        assertEquals(at("2026-01-02", "05:45"), s.nextChange)
    }

    @Test fun `at the end of the cached data there is no next prayer after Isyak, but the pill is still on Isyak`() {
        val s = snapshot("22:00", days = mapOf(jan1.date to jan1))
        assertEquals(jan1, s.day)
        assertEquals(Prayer.ISYAK, s.highlight)
        assertNull(s.next)
        assertEquals(at("2026-01-02", "00:00"), s.nextChange)
    }

    @Test fun `no timetable for today gives an empty snapshot`() {
        val s = snapshot("12:00", days = mapOf(jan2.date to jan2))
        assertNull(s.day)
        assertNull(s.highlight)
        assertNull(s.next)
        assertNull(s.nextChange)
    }

    @Test fun `Syuruk is marked for one minute only, then nothing until Zohor, then Zohor`() {
        // Syuruk is 07:08 on 1 January.
        assertEquals(Prayer.SUBUH, snapshot("07:07").highlight)
        val start = snapshot("07:08")
        assertEquals(Prayer.SYURUK, start.highlight)
        assertEquals(at("2026-01-01", "07:09"), start.nextChange) // the picture changes again when the minute is over
        assertEquals(Prayer.ZOHOR, start.next?.prayer)
        val over = snapshot("07:09")
        assertNull(over.highlight)
        assertEquals(at("2026-01-01", "13:10"), over.nextChange)
        for (time in listOf("07:30", "10:00", "13:09")) assertNull(time, snapshot(time).highlight)
        assertEquals(Prayer.ZOHOR, snapshot("13:10").highlight)
    }

    @Test fun `the countdown still counts to the next prayer while nothing is marked`() {
        val s = snapshot("10:00")
        assertNull(s.highlight)
        assertEquals(Prayer.ZOHOR, s.next?.prayer)
        assertEquals(at("2026-01-01", "13:10"), s.next?.at)
    }

    @Test fun `Syuruk can be switched off and is then never marked, Subuh staying marked until Zohor`() {
        val off = snapshot("06:00", highlightSyuruk = false)
        assertEquals(Prayer.SUBUH, off.highlight)
        assertEquals(Prayer.ZOHOR, off.next?.prayer)
        assertEquals(at("2026-01-01", "13:10"), off.nextChange)
        assertEquals(Prayer.SUBUH, snapshot("07:08", highlightSyuruk = false).highlight)
        assertEquals(Prayer.SUBUH, snapshot("10:00", highlightSyuruk = false).highlight)
        assertEquals(Prayer.SUBUH, snapshot("06:00", highlightSyuruk = true).highlight)
    }

    @Test fun `uses Singapore time whatever the device zone is`() {
        // 20:00 UTC on 31 Dec is 04:00 on 1 Jan in Singapore.
        val clock = Clock.fixed(Instant.parse("2025-12-31T20:00:00Z"), SINGAPORE)
        val s = PrayerSchedule(all, clock).snapshot()
        assertEquals(jan1, s.day)
        assertEquals(Prayer.ISYAK, s.highlight)
        assertEquals(Prayer.SUBUH, s.next?.prayer)
    }
}
