package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import com.prayerwallpaper.app.data.WhenMissing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class WidgetTextTest {
    private val sg = ZoneId.of("Asia/Singapore")
    private val en = Locale.ENGLISH
    private val ar = Locale.forLanguageTag("ar-EG")

    // --- the time as the widgets write it ---

    @Test fun `12-hour time has am or pm in lower case`() {
        assertEquals(WidgetTime("5:41", "am"), formatWidgetTime(LocalTime.of(5, 41), false, en))
        assertEquals(WidgetTime("4:15", "pm"), formatWidgetTime(LocalTime.of(16, 15), false, en))
        assertEquals("4:15 pm", formatWidgetTime(LocalTime.of(16, 15), false, en).text)
    }

    @Test fun `noon and midnight read 12`() {
        assertEquals(WidgetTime("12:00", "pm"), formatWidgetTime(LocalTime.NOON, false, en))
        assertEquals(WidgetTime("12:05", "am"), formatWidgetTime(LocalTime.of(0, 5), false, en))
        assertEquals(WidgetTime("1:00", "pm"), formatWidgetTime(LocalTime.of(13, 0), false, en))
    }

    @Test fun `compact time keeps one letter of the marker`() {
        assertEquals(WidgetTime("5:41", "a"), formatWidgetTime(LocalTime.of(5, 41), false, en, compact = true))
        assertEquals("4:15 p", formatWidgetTime(LocalTime.of(16, 15), false, en, compact = true).text)
    }

    @Test fun `24-hour time has two-digit hours and no marker, like the lock screen`() {
        assertEquals(WidgetTime("05:41", ""), formatWidgetTime(LocalTime.of(5, 41), true, en))
        assertEquals("16:15", formatWidgetTime(LocalTime.of(16, 15), true, en, compact = true).text)
        assertEquals("00:05", formatWidgetTime(LocalTime.of(0, 5), true, en).text)
    }

    @Test fun `an Arabic phone gets its own digits and markers, not English ones`() {
        val t = formatWidgetTime(LocalTime.of(16, 15), false, ar)
        val digits = NumberFormat.getIntegerInstance(ar)
        assertEquals("${digits.format(4)}:${digits.format(15)}", t.time)
        assertNotEquals("pm", t.marker)
        assertTrue(t.marker.isNotEmpty())
        assertEquals(1, formatWidgetTime(LocalTime.of(16, 15), false, ar, compact = true).marker.let { it.codePointCount(0, it.length) })
        // The minutes keep two digits in the locale's own zero.
        assertEquals("${digits.format(5)}:${digits.format(0)}${digits.format(5)}", formatWidgetTime(LocalTime.of(5, 5), false, ar).time)
    }

    // --- the Hijri date by widget width and font scale ---

    private val date = HijriDate(24, 4, "Rabiulakhir", 1448)

    @Test fun `a narrow widget gets the short Hijri date, a wide one the full one`() {
        assertEquals(HijriForm.SHORT, hijriFormFor(date, false, 1f))
        assertEquals(HijriForm.FULL, hijriFormFor(date, true, 1f))
        assertEquals("24 Rabiulakhir", date.text(HijriForm.SHORT))
        assertEquals("24 Rabiulakhir 1448H", date.text(HijriForm.FULL))
        assertNull(date.text(HijriForm.NONE))
    }

    @Test fun `no Hijri date means none, at any width`() {
        assertEquals(HijriForm.NONE, hijriFormFor(null, false, 1f))
        assertEquals(HijriForm.NONE, hijriFormFor(null, true, 1f))
    }

    @Test fun `from font scale 1_3 the Hijri date is dropped`() {
        assertEquals(HijriForm.SHORT, hijriFormFor(date, false, 1.29f))
        assertEquals(HijriForm.NONE, hijriFormFor(date, false, 1.3f))
        assertEquals(HijriForm.NONE, hijriFormFor(date, true, 2.0f))
    }

    // --- the words ---

    @Test fun `messages for a missing timetable`() {
        assertEquals("Open the app to load prayer times", WidgetWords.missing(WhenMissing.LOAD))
        assertEquals("Prayer times need updating", WidgetWords.missing(WhenMissing.UPDATE))
        assertEquals("Can't calculate times here today", WidgetWords.missing(WhenMissing.CANNOT_CALCULATE))
    }

    @Test fun `the footer format names the prayer and leaves the countdown to the chronometer`() {
        assertEquals("Asar in %s", WidgetWords.nextFooterFormat("Asar", false))
        assertEquals("Subuh tomorrow in %s", WidgetWords.nextFooterFormat("Subuh", true))
        assertEquals("100%% in %s", WidgetWords.nextFooterFormat("100%", false))
        assertEquals("in %s", WidgetWords.IN_FORMAT)
    }

    // --- what a screen reader says ---

    private val asar = WidgetEntry(Prayer.ASAR, "Asar", LocalDate.of(2026, 10, 1), LocalTime.of(16, 4), Instant.EPOCH)
    private val subuh = WidgetEntry(Prayer.SUBUH, "Subuh", LocalDate.of(2026, 10, 2), LocalTime.of(5, 34), Instant.EPOCH)

    @Test fun `spoken text has the prayer and the time, never the countdown`() {
        assertEquals("Next prayer: Asar at 4:04 pm.", WidgetSpoken.next(WidgetNext(asar, false), WidgetTime("4:04", "pm")))
        assertEquals("Next prayer: Subuh tomorrow at 5:34 am.", WidgetSpoken.next(WidgetNext(subuh, true), WidgetTime("5:34", "am")))
        assertEquals("Next prayer: Asar at 4:04 pm. 24 Rabiulakhir 1448H.", WidgetSpoken.next(WidgetNext(asar, false), WidgetTime("4:04", "pm"), "24 Rabiulakhir 1448H"))
        assertEquals("Zohor, 1:00 pm, current prayer", WidgetSpoken.row("Zohor", WidgetTime("1:00", "pm"), true))
        assertEquals("Asar, 4:04 pm", WidgetSpoken.row("Asar", WidgetTime("4:04", "pm"), false))
        assertEquals("Next prayer: Asar at 4:04 pm", WidgetSpoken.footer(WidgetNext(asar, false), WidgetTime("4:04", "pm")))
    }

    // --- the windows of the large text widgets ---

    private fun day(date: LocalDate) = PrayerDay(date, listOf("05:34", "06:51", "12:54", "16:04", "18:56", "20:05").map(LocalTime::parse))
    private val thu = LocalDate.of(2026, 10, 1)
    private val days = mapOf(thu to day(thu), thu.plusDays(1) to day(thu.plusDays(1)))

    private fun ready(date: String, time: String, data: Map<LocalDate, PrayerDay> = days) =
        widgetState(data, ZonedDateTime.parse("${date}T$time+08:00[Asia/Singapore]").toInstant(), sg, NameSet.MALAY, null, WhenMissing.LOAD) as WidgetState.Ready

    private fun labels(entries: List<WidgetEntry>) = entries.map { it.label }

    @Test fun `three columns start at the current entry and run on`() {
        assertEquals(listOf("Zohor", "Asar", "Maghrib"), labels(windowFromCurrent(ready("2026-10-01", "14:00:00"), 3)))
        assertEquals(listOf("Subuh", "Syuruk", "Zohor"), labels(windowFromCurrent(ready("2026-10-01", "06:00:00"), 3)))
    }

    @Test fun `near the end of the day the window runs into tomorrow's times`() {
        val w = windowFromCurrent(ready("2026-10-01", "19:00:00"), 3) // Maghrib is current
        assertEquals(listOf("Maghrib", "Isyak", "Subuh"), labels(w))
        assertEquals(thu.plusDays(1), w.last().date)
        val late = windowFromCurrent(ready("2026-10-01", "21:00:00"), 3) // Isyak is current
        assertEquals(listOf("Isyak", "Subuh", "Syuruk"), labels(late))
        assertEquals(listOf(thu, thu.plusDays(1), thu.plusDays(1)), late.map { it.date })
    }

    @Test fun `before Subuh the window is Isyak, Subuh, Syuruk of today`() {
        val w = windowFromCurrent(ready("2026-10-01", "04:00:00"), 3)
        assertEquals(listOf("Isyak", "Subuh", "Syuruk"), labels(w))
        assertTrue(w.all { it.date == thu })
    }

    @Test fun `with no tomorrow the window slides back and still holds the current entry`() {
        val last = mapOf(thu to day(thu))
        val w = windowFromCurrent(ready("2026-10-01", "21:00:00", last), 3)
        assertEquals(listOf("Maghrib", "Isyak"), labels(w).takeLast(2))
        assertEquals(3, w.size)
        assertTrue(w.any { it.prayer == Prayer.ISYAK })
        assertEquals(4, windowFromCurrent(ready("2026-10-01", "17:00:00", last), 4).size)
    }

    @Test fun `asking for six or more gives today's six in order`() {
        assertEquals(listOf("Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"), labels(windowFromCurrent(ready("2026-10-01", "14:00:00"), 6)))
    }

    @Test fun `the current entry in a window is the first one`() {
        for (time in listOf("06:00:00", "13:00:00", "17:00:00", "19:30:00", "21:00:00", "04:00:00")) {
            val s = ready("2026-10-01", time)
            assertEquals(time, s.current, windowFromCurrent(s, 4).first().prayer)
        }
    }
}
