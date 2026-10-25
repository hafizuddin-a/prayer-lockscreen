package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

class CountdownTest {
    // Real MUIS times, rounded, for Friday 2 October and Saturday 3 October 2026.
    private fun day(date: LocalDate) = PrayerDay(date, listOf("05:34", "06:51", "12:54", "16:04", "18:56", "20:05").map(LocalTime::parse))
    private val days = listOf(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3)).associateWith(::day)

    private fun at(date: String, time: String): Instant = ZonedDateTime.parse("${date}T$time+08:00[Asia/Singapore]").toInstant()

    private fun countdown(date: String, time: String, names: NameSet = NameSet.MALAY): Countdown? {
        val now = at(date, time)
        return PrayerSchedule(days, Clock.fixed(now, SINGAPORE)).snapshot().countdown(now, names)
    }

    private fun text(date: String, time: String, names: NameSet = NameSet.MALAY) = countdown(date, time, names)?.text

    // --- the words ---

    @Test fun `hours and minutes`() {
        assertEquals("Asar in 2h 14m", text("2026-10-01", "13:50:00")) // Asar 16:04
    }

    @Test fun `under an hour there are only minutes`() {
        assertEquals("Asar in 14m", text("2026-10-01", "15:50:00"))
        assertEquals("Asar in 59m", text("2026-10-01", "15:05:00"))
    }

    @Test fun `on the hour there are only hours`() {
        assertEquals("Asar in 2h", text("2026-10-01", "14:04:00"))
        assertEquals("Asar in 1h", text("2026-10-01", "15:04:00"))
    }

    @Test fun `the last minute reads 1m, never 0m`() {
        assertEquals("Asar in 1m", text("2026-10-01", "16:03:00"))
        assertEquals("Asar in 1m", text("2026-10-01", "16:03:59"))
    }

    @Test fun `a part minute is rounded up, so the line stays true until the prayer starts`() {
        assertEquals("Asar in 15m", text("2026-10-01", "15:49:01")) // 14 min 59 s left
        assertEquals("Asar in 15m", text("2026-10-01", "15:49:00")) // exactly 15 min
        assertEquals("Asar in 14m", text("2026-10-01", "15:50:00")) // exactly 14 min
        assertEquals("Asar in 2h 14m", text("2026-10-01", "13:50:30")) // 2 h 13 min 30 s
    }

    @Test fun `at the moment a prayer starts the line is already counting to the next one`() {
        assertEquals("Maghrib in 2h 52m", text("2026-10-01", "16:04:00"))
        assertEquals("Maghrib in 2h 52m", text("2026-10-01", "16:04:00.4"))
    }

    // --- which prayer ---

    @Test fun `it counts to the next prayer, Syuruk included, while the pill is on the one that is on now`() {
        assertEquals("Syuruk in 17m", text("2026-10-01", "06:34:00"))
    }

    @Test fun `after Isyak it counts to tomorrow's Subuh`() {
        assertEquals("Subuh in 8h 29m", text("2026-10-01", "21:05:00"))
        assertEquals("Subuh in 30m", text("2026-10-02", "05:04:00"))
    }

    @Test fun `on a Friday the noon prayer is named Jumaat, or Jumuah in English`() {
        assertEquals("Jumaat in 2h 5m", text("2026-10-02", "10:49:00"))
        assertEquals("Jumuah in 2h 5m", text("2026-10-02", "10:49:00", NameSet.ENGLISH))
        assertEquals("Zohor in 2h 5m", text("2026-10-01", "10:49:00"))
        assertEquals("Dhuhr in 2h 5m", text("2026-10-01", "10:49:00", NameSet.ENGLISH))
    }

    @Test fun `English names are used when chosen`() {
        assertEquals("Asr in 14m", text("2026-10-01", "15:50:00", NameSet.ENGLISH))
        assertEquals("Fajr in 8h 29m", text("2026-10-01", "21:05:00", NameSet.ENGLISH))
    }

    @Test fun `the numbers on the line are the minutes, not a prayer's clock time`() {
        assertEquals(134L, countdown("2026-10-01", "13:50:00")!!.minutes)
        assertEquals("Asar", countdown("2026-10-01", "13:50:00")!!.name)
    }

    // --- when there is nothing to count to ---

    @Test fun `no timetable for today gives no countdown`() {
        val now = at("2026-11-20", "10:00:00")
        assertNull(PrayerSchedule(days, Clock.fixed(now, SINGAPORE)).snapshot().countdown(now, NameSet.MALAY))
    }

    @Test fun `the end of the cached data, with no next prayer, gives no countdown`() {
        // 3 October is the last day: after Isyak there is no tomorrow to count to.
        val now = at("2026-10-03", "21:00:00")
        val snapshot = PrayerSchedule(days, Clock.fixed(now, SINGAPORE)).snapshot()
        assertEquals(Prayer.ISYAK, snapshot.highlight)
        assertNull(snapshot.next)
        assertNull(snapshot.countdown(now, NameSet.MALAY))
    }

    @Test fun `a prayer that is already due gives no countdown`() {
        val day = days.getValue(LocalDate.of(2026, 10, 1))
        val snapshot = PrayerSnapshot(day, Prayer.ZOHOR, at("2026-10-01", "16:04:00"), NextPrayer(day, Prayer.ASAR, at("2026-10-01", "16:04:00")))
        assertNull(snapshot.countdown(at("2026-10-01", "16:04:00"), NameSet.MALAY))
        assertNull(snapshot.countdown(at("2026-10-01", "16:05:00"), NameSet.MALAY))
        assertNotNull(snapshot.countdown(at("2026-10-01", "16:03:59"), NameSet.MALAY))
    }

    // --- formatting on its own ---

    @Test fun `formatting`() {
        assertEquals("1m", formatRemaining(1))
        assertEquals("59m", formatRemaining(59))
        assertEquals("1h", formatRemaining(60))
        assertEquals("1h 1m", formatRemaining(61))
        assertEquals("10h 25m", formatRemaining(625))
        assertEquals("20h", formatRemaining(1200))
    }

    // --- the once-a-minute tick ---

    @Test fun `the next minute start is the next whole minute, strictly after now`() {
        assertEquals(Instant.parse("2026-10-01T05:01:00Z"), nextMinuteStart(Instant.parse("2026-10-01T05:00:00Z")))
        assertEquals(Instant.parse("2026-10-01T05:01:00Z"), nextMinuteStart(Instant.parse("2026-10-01T05:00:59.999Z")))
        assertEquals(Instant.parse("2026-10-01T05:01:00Z"), nextMinuteStart(Instant.parse("2026-10-01T05:00:30Z")))
        assertEquals(Instant.parse("2026-10-01T05:02:00Z"), nextMinuteStart(Instant.parse("2026-10-01T05:01:00Z")))
    }

    @Test fun `the tick lands on a minute that shows a whole number of minutes left`() {
        // Asar is 16:04:00. A tick just after 15:50:00 reads 14m, and the next one 13m.
        val tick = redrawDelayMs(nextMinuteStart(at("2026-10-01", "15:49:30")), at("2026-10-01", "15:49:30"))!!
        assertEquals(30_000 + REDRAW_SLACK_MS, tick)
        val after = at("2026-10-01", "15:49:30").plusMillis(tick)
        assertEquals("Asar in 14m", PrayerSchedule(days, Clock.fixed(after, SINGAPORE)).snapshot().countdown(after, NameSet.MALAY)?.text)
    }

    @Test fun `the tick never waits longer than a minute and a bit`() {
        for (second in listOf("00", "01", "30", "59")) {
            val now = at("2026-10-01", "15:49:$second")
            val delay = redrawDelayMs(nextMinuteStart(now), now)!!
            assertTrue("$second: $delay", delay in REDRAW_SLACK_MS..(60_000 + REDRAW_SLACK_MS))
        }
    }

    @Test fun `a prayer boundary sooner than the next minute still wins`() {
        val now = at("2026-10-01", "16:03:30")
        val boundary = at("2026-10-01", "16:04:00")
        assertEquals(boundary, earliest(boundary, nextMinuteStart(now))) // the same instant, a whole minute
        val closer = Instant.parse("2026-10-01T08:03:40Z") // 16:03:40 in Singapore, 10 s away
        assertEquals(closer, earliest(closer, nextMinuteStart(now)))
    }
}
