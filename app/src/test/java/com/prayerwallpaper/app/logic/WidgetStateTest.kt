package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import com.prayerwallpaper.app.data.WhenMissing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class WidgetStateTest {
    private val sg = ZoneId.of("Asia/Singapore")

    // Real MUIS times, rounded: Thursday 1 October, Friday 2 October and Saturday 3 October 2026.
    private fun day(date: LocalDate, times: List<String> = listOf("05:34", "06:51", "12:54", "16:04", "18:56", "20:05")) =
        PrayerDay(date, times.map(LocalTime::parse))

    private val thu = LocalDate.of(2026, 10, 1)
    private val days = mapOf(thu to day(thu), thu.plusDays(1) to day(thu.plusDays(1), listOf("05:34", "06:50", "12:54", "16:03", "18:56", "20:04")), thu.plusDays(2) to day(thu.plusDays(2)))

    private fun at(date: String, time: String): Instant = ZonedDateTime.parse("${date}T$time+08:00[Asia/Singapore]").toInstant()

    private val hijri = HijriDate(24, 4, "Rabiulakhir", 1448)

    private fun state(date: String, time: String, data: Map<LocalDate, PrayerDay> = days, names: NameSet = NameSet.MALAY, h: HijriDate? = hijri) =
        widgetState(data, at(date, time), sg, names, h, WhenMissing.LOAD)

    private fun ready(date: String, time: String, data: Map<LocalDate, PrayerDay> = days, names: NameSet = NameSet.MALAY) =
        state(date, time, data, names) as WidgetState.Ready

    // --- the current and the next entry at each time of the day ---

    @Test fun `each prayer is current from its start and the next one follows`() {
        val expected = listOf(
            "05:34:00" to (Prayer.SUBUH to Prayer.SYURUK), "06:51:00" to (Prayer.SYURUK to Prayer.ZOHOR), "12:54:00" to (Prayer.ZOHOR to Prayer.ASAR),
            "16:04:00" to (Prayer.ASAR to Prayer.MAGHRIB), "18:56:00" to (Prayer.MAGHRIB to Prayer.ISYAK),
        )
        for ((time, pair) in expected) {
            val s = ready("2026-10-01", time)
            assertEquals(time, pair.first, s.current)
            assertEquals(time, pair.second, s.next!!.entry.prayer)
            assertEquals(time, false, s.next.tomorrow)
        }
    }

    @Test fun `one second before a boundary the earlier prayer is still current`() {
        val s = ready("2026-10-01", "12:53:59")
        assertEquals(Prayer.SYURUK, s.current)
        assertEquals(Prayer.ZOHOR, s.next!!.entry.prayer)
    }

    @Test fun `exactly at a boundary the prayer has started`() {
        val s = ready("2026-10-01", "16:04:00")
        assertEquals(Prayer.ASAR, s.current)
        assertEquals(Prayer.MAGHRIB, s.next!!.entry.prayer)
    }

    @Test fun `before Subuh the current entry is Isyak and the next is today's Subuh`() {
        val s = ready("2026-10-01", "04:00:00")
        assertEquals(Prayer.ISYAK, s.current)
        assertTrue(s.beforeSubuh)
        assertEquals(Prayer.SUBUH, s.next!!.entry.prayer)
        assertEquals(false, s.next.tomorrow)
        assertEquals(at("2026-10-01", "05:34:00"), s.next.entry.at)
    }

    @Test fun `just after midnight the list is the new date's and Isyak is still the current entry`() {
        val s = ready("2026-10-02", "00:00:00")
        assertEquals(LocalDate.of(2026, 10, 2), s.date)
        assertEquals(Prayer.ISYAK, s.current)
        assertEquals(LocalDate.of(2026, 10, 2), s.next!!.entry.date)
    }

    @Test fun `after Isyak the next prayer is tomorrow's Subuh and it is flagged`() {
        val s = ready("2026-10-01", "21:00:00")
        assertEquals(Prayer.ISYAK, s.current)
        assertEquals(false, s.beforeSubuh)
        val next = s.next!!
        assertTrue(next.tomorrow)
        assertEquals(Prayer.SUBUH, next.entry.prayer)
        assertEquals(at("2026-10-02", "05:34:00"), next.entry.at)
    }

    @Test fun `the list does not change after Isyak, only at midnight`() {
        val evening = ready("2026-10-01", "23:59:59")
        assertEquals(LocalDate.of(2026, 10, 1), evening.date)
        assertEquals(day(thu).times, evening.entries.map { it.time })
        val morning = ready("2026-10-02", "00:00:00")
        assertEquals(days.getValue(thu.plusDays(1)).times, morning.entries.map { it.time })
    }

    // --- the end of the data and the empty states ---

    @Test fun `on the last day of the data after Isyak there is no next prayer, but the list stays`() {
        val s = ready("2026-10-03", "21:00:00")
        assertNull(s.next)
        assertEquals(Prayer.ISYAK, s.current)
        assertEquals(6, s.entries.size)
        assertTrue(s.tomorrowEntries.isEmpty())
    }

    @Test fun `no timetable for today is Missing with the reason the timetable gave`() {
        assertEquals(WidgetState.Missing(WhenMissing.UPDATE), widgetState(days, at("2026-11-20", "10:00:00"), sg, NameSet.MALAY, hijri, WhenMissing.UPDATE))
        assertEquals(WidgetState.Missing(WhenMissing.LOAD), widgetState(emptyMap(), at("2026-10-01", "10:00:00"), sg, NameSet.MALAY, null, WhenMissing.LOAD))
        assertEquals(WidgetState.Missing(WhenMissing.CANNOT_CALCULATE), widgetState(emptyMap(), at("2026-10-01", "10:00:00"), sg, NameSet.MALAY, null, WhenMissing.CANNOT_CALCULATE))
    }

    @Test fun `the Hijri date is carried when known and absent when not`() {
        assertEquals(hijri, ready("2026-10-01", "10:00:00").hijri)
        assertNull((state("2026-10-01", "10:00:00", h = null) as WidgetState.Ready).hijri)
    }

    // --- names ---

    @Test fun `Friday's noon prayer is Jumaat, or Jumuah in English, and only the label changes`() {
        val fri = ready("2026-10-02", "10:00:00")
        assertEquals("Jumaat", fri.entries.first { it.prayer == Prayer.ZOHOR }.label)
        assertEquals(LocalTime.of(12, 54), fri.entries.first { it.prayer == Prayer.ZOHOR }.time)
        assertEquals("Jumuah", ready("2026-10-02", "10:00:00", names = NameSet.ENGLISH).entries[2].label)
        assertEquals("Zohor", ready("2026-10-01", "10:00:00").entries[2].label)
        assertEquals("Fajr", ready("2026-10-01", "10:00:00", names = NameSet.ENGLISH).entries[0].label)
    }

    // --- which row is marked ---

    @Test fun `the prayer that is on now is marked, while the next one is counted down to`() {
        val s = ready("2026-10-01", "14:00:00")
        assertEquals(Prayer.ZOHOR, s.marked())
        assertEquals(Prayer.ASAR, s.next?.entry?.prayer)
        assertEquals(Prayer.ISYAK, ready("2026-10-01", "21:00:00").marked()) // after Isyak the next one is tomorrow's, so Isyak stays marked
    }

    @Test fun `Syuruk is marked for one minute only, then nothing is marked until Zohor`() {
        // Syuruk is 06:51 on 1 October.
        assertEquals(Prayer.SUBUH, ready("2026-10-01", "06:50:59").marked())
        assertEquals(Prayer.SYURUK, ready("2026-10-01", "06:51:00").marked())
        assertEquals(Prayer.SYURUK, ready("2026-10-01", "06:51:59").marked())
        for (time in listOf("06:52:00", "08:00:00", "10:00:00", "12:53:59")) assertNull(time, ready("2026-10-01", time).marked())
        assertEquals(Prayer.ZOHOR, ready("2026-10-01", "12:54:00").marked())
    }

    @Test fun `while nothing is marked Syuruk is still the current entry and Zohor the next, so the list and countdown are unchanged`() {
        val s = ready("2026-10-01", "10:00:00")
        assertEquals(Prayer.SYURUK, s.current)
        assertEquals(Prayer.ZOHOR, s.next?.entry?.prayer)
        assertNull(s.marked())
    }

    // --- the boundary the widgets must refresh at ---

    @Test fun `a widget redraws a minute after Syuruk, when its mark goes`() {
        assertEquals(at("2026-10-01", "06:51:00"), nextWidgetBoundary(days, at("2026-10-01", "06:00:00"), sg))
        assertEquals(at("2026-10-01", "06:52:00"), nextWidgetBoundary(days, at("2026-10-01", "06:51:00"), sg))
        assertEquals(at("2026-10-01", "12:54:00"), nextWidgetBoundary(days, at("2026-10-01", "06:52:00"), sg))
        assertEquals(at("2026-10-01", "06:51:00"), lastWidgetBoundary(days, at("2026-10-01", "06:51:30"), sg))
        assertEquals(at("2026-10-01", "06:52:00"), lastWidgetBoundary(days, at("2026-10-01", "06:52:00"), sg))
        assertEquals(at("2026-10-01", "06:52:00"), lastWidgetBoundary(days, at("2026-10-01", "10:00:00"), sg))
    }

    @Test fun `the next boundary is the next entry time, strictly after now`() {
        assertEquals(at("2026-10-01", "12:54:00"), nextWidgetBoundary(days, at("2026-10-01", "10:00:00"), sg))
        assertEquals(at("2026-10-01", "16:04:00"), nextWidgetBoundary(days, at("2026-10-01", "12:54:00"), sg)) // exactly on one: the one after
        assertEquals(at("2026-10-01", "12:54:00"), nextWidgetBoundary(days, at("2026-10-01", "12:53:59.999"), sg))
    }

    @Test fun `after Isyak the next boundary is midnight, which comes before tomorrow's Subuh`() {
        assertEquals(at("2026-10-02", "00:00:00"), nextWidgetBoundary(days, at("2026-10-01", "21:00:00"), sg))
        assertEquals(at("2026-10-02", "00:00:00"), nextWidgetBoundary(days, at("2026-10-01", "20:05:00"), sg))
    }

    @Test fun `after midnight and before Subuh the next boundary is Subuh`() {
        assertEquals(at("2026-10-02", "05:34:00"), nextWidgetBoundary(days, at("2026-10-02", "00:00:00"), sg))
        assertEquals(at("2026-10-02", "05:34:00"), nextWidgetBoundary(days, at("2026-10-02", "03:00:00"), sg))
    }

    @Test fun `with no timetable the next boundary is still midnight`() {
        assertEquals(at("2026-11-21", "00:00:00"), nextWidgetBoundary(days, at("2026-11-20", "10:00:00"), sg))
        assertEquals(at("2026-10-04", "00:00:00"), nextWidgetBoundary(days, at("2026-10-03", "21:00:00"), sg))
    }

    @Test fun `the last boundary is the latest entry that has passed, or this morning's midnight`() {
        assertEquals(at("2026-10-01", "00:00:00"), lastWidgetBoundary(days, at("2026-10-01", "04:00:00"), sg))
        assertEquals(at("2026-10-01", "05:34:00"), lastWidgetBoundary(days, at("2026-10-01", "05:34:00"), sg))
        assertEquals(at("2026-10-01", "12:54:00"), lastWidgetBoundary(days, at("2026-10-01", "14:00:00"), sg))
        assertEquals(at("2026-10-01", "20:05:00"), lastWidgetBoundary(days, at("2026-10-01", "23:59:59"), sg))
        assertEquals(at("2026-11-20", "00:00:00"), lastWidgetBoundary(days, at("2026-11-20", "10:00:00"), sg))
    }

    @Test fun `the safety alarm is two minutes after the boundary`() {
        assertEquals(java.time.Duration.ofMinutes(2), WIDGET_SAFETY_DELAY)
    }

    @Test fun `boundaries are in the place's zone, not Singapore's`() {
        val ny = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 10, 1)
        val nyDays = mapOf(date to day(date, listOf("05:40", "06:55", "12:50", "15:55", "18:30", "19:45")), date.plusDays(1) to day(date.plusDays(1)))
        val now = ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, ny).toInstant()
        assertEquals(ZonedDateTime.of(2026, 10, 1, 12, 50, 0, 0, ny).toInstant(), nextWidgetBoundary(nyDays, now, ny))
        assertEquals(ZonedDateTime.of(2026, 10, 2, 0, 0, 0, 0, ny).toInstant(), nextWidgetBoundary(nyDays, ZonedDateTime.of(2026, 10, 1, 22, 0, 0, 0, ny).toInstant(), ny))
    }

    @Test fun `midnight on a day the clocks change is the real start of the next day`() {
        val london = ZoneId.of("Europe/London")
        val last = LocalDate.of(2026, 10, 24) // clocks go back on 25 October 2026
        val data = mapOf(last to day(last), last.plusDays(1) to day(last.plusDays(1)))
        val now = ZonedDateTime.of(2026, 10, 24, 22, 0, 0, 0, london).toInstant()
        assertEquals(ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, london).toInstant(), nextWidgetBoundary(data, now, london))
    }

    // --- the chronometer ---

    @Test fun `the chronometer base is the elapsed-realtime moment of the target`() {
        val now = at("2026-10-01", "14:00:00")
        val target = at("2026-10-01", "16:04:00")
        assertEquals(1_000_000L + 2 * 3_600_000 + 4 * 60_000, chronometerBase(now, target, 1_000_000L))
        assertEquals(1_000_000L, chronometerBase(now, now, 1_000_000L))
        assertEquals(1_000_000L - 5_000, chronometerBase(now, now.minusSeconds(5), 1_000_000L)) // already past: negative remaining
    }

    // --- a printed table for the checkpoint ---

    @Test fun `print the state through a day`() {
        println("PHASE A STATE TABLE (Singapore, Thursday 1 October 2026; the lock screen would mark the NEXT prayer)")
        println("%-9s | %-8s | %-8s | %-22s | %s".format("time", "current", "marked", "next (countdown to)", "next boundary"))
        for (time in listOf("00:00:00", "04:00:00", "05:33:59", "05:34:00", "06:51:00", "10:00:00", "12:54:00", "14:00:00", "16:04:00", "18:55:59", "18:56:00", "20:05:00", "21:00:00", "23:59:59")) {
            val s = ready("2026-10-01", time)
            val next = s.next?.let { "${it.entry.label}${if (it.tomorrow) " tomorrow" else ""} ${it.entry.time}" } ?: "none"
            val b = nextWidgetBoundary(days, at("2026-10-01", time), sg).atZone(sg)
            println("%-9s | %-8s | %-8s | %-22s | %s".format(time, s.current.label(NameSet.MALAY), s.marked()?.label(NameSet.MALAY), next, "${b.toLocalDate()} ${b.toLocalTime()}"))
        }
        assertNotNull(state("2026-10-01", "10:00:00"))
    }
}
