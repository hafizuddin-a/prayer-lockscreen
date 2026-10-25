package com.prayerwallpaper.app.data

import com.prayerwallpaper.app.logic.HIJRI_MONTH_NAMES
import com.prayerwallpaper.app.logic.HijriCalendar
import com.prayerwallpaper.app.logic.HijriDate
import com.prayerwallpaper.app.logic.HijriMonthStart
import com.prayerwallpaper.app.logic.IslamicEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Checks the real Hijri month table, `data/hijri_months.csv`, and the asset generated from it. These run in the normal
 * test task, so a mistake in the table fails the build. Unit tests run with the module (`app/`) as the working directory.
 */
class HijriDataTest {
    private class CsvRow(val line: Int, val year: Int, val month: Int, val name: String, val start: LocalDate, val weekday: String)

    private val csvFile = File("../data/hijri_months.csv")
    private val assetFile = File("src/main/assets/hijri_months.json")
    private val eventsCsv = File("../data/islamic_events.csv")
    private val eventsAsset = File("src/main/assets/islamic_events.json")

    private val rows: List<CsvRow> by lazy {
        csvFile.readLines().drop(1).withIndex().filter { it.value.isNotBlank() }.map { (i, line) ->
            val f = line.split(',')
            CsvRow(i + 2, f[0].toInt(), f[1].toInt(), f[2], LocalDate.parse(f[3]), f[4])
        }
    }

    @Test fun `the table is there with the agreed columns`() {
        assertTrue("missing ${csvFile.absolutePath}", csvFile.exists())
        assertEquals("hijri_year,month_number,month_name,gregorian_start,weekday", csvFile.readLines().first())
        assertTrue("only ${rows.size} rows", rows.size >= 12)
        assertTrue(csvFile.readLines().drop(1).filter { it.isNotBlank() }.all { it.split(',').size == 5 })
    }

    @Test fun `rows are in ascending date order`() {
        rows.zipWithNext().forEach { (a, b) -> assertTrue("line ${b.line} is not after line ${a.line}", b.start > a.start) }
    }

    @Test fun `month numbers are consecutive and the Hijri year goes up only after month 12`() {
        rows.zipWithNext().forEach { (a, b) ->
            val expected = if (a.month == 12) (a.year + 1) to 1 else a.year to (a.month + 1)
            assertEquals("line ${b.line}", expected, b.year to b.month)
        }
    }

    @Test fun `every gap between month starts is 29 or 30 days`() {
        rows.zipWithNext().forEach { (a, b) ->
            val gap = ChronoUnit.DAYS.between(a.start, b.start)
            assertTrue("line ${b.line}: $gap days after line ${a.line}", gap == 29L || gap == 30L)
        }
    }

    @Test fun `the weekday column matches the actual weekday`() {
        rows.forEach { assertEquals("line ${it.line} ${it.start}", it.start.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH), it.weekday) }
    }

    @Test fun `month names are the MUIS spellings and match the month number`() {
        rows.forEach {
            assertTrue("line ${it.line}: month ${it.month}", it.month in 1..12)
            assertEquals("line ${it.line}", HIJRI_MONTH_NAMES[it.month - 1], it.name)
        }
        assertEquals(12, HIJRI_MONTH_NAMES.toSet().size)
    }

    @Test fun `the generated asset is exactly the table, so the generator was run after the last edit`() {
        assertTrue("missing ${assetFile.absolutePath}: run tools/make-hijri.py", assetFile.exists())
        val calendar = HijriCalendar.parse(assetFile.readText())
        val fromCsv = rows.map { HijriMonthStart(it.year, it.month, it.name, it.start) }
        // Compare through behaviour: every start date and the day before it, and a month ahead of each.
        val expected = HijriCalendar(fromCsv)
        for (r in fromCsv) {
            for (offset in listOf(-1L, 0L, 1L, 15L, 28L)) {
                val d = r.start.plusDays(offset)
                assertEquals("$d", expected.on(d), calendar.on(d))
            }
        }
        assertEquals(expected.lastKnownDate, calendar.lastKnownDate)
    }

    @Test fun `the Islamic events printed beside the table in the MUIS PDFs fall where the table puts them`() {
        // From the Key Islamic dates 2026 and 2027 PDFs (Islamic Events lists): Hijri day, month number, Hijri year, date.
        // These are independent of the month start column, so they catch a month start typed wrongly.
        class Event(val name: String, val day: Int, val month: Int, val year: Int, val date: String, val weekday: DayOfWeek)
        val events = listOf(
            Event("Nisfu Syaaban", 15, 8, 1447, "2026-02-03", DayOfWeek.TUESDAY),
            Event("Beginning of Fasting", 1, 9, 1447, "2026-02-19", DayOfWeek.THURSDAY),
            Event("Nuzul Quran", 17, 9, 1447, "2026-03-07", DayOfWeek.SATURDAY),
            Event("Hari Raya Aidilfitri", 1, 10, 1447, "2026-03-21", DayOfWeek.SATURDAY),
            Event("Hari Raya Aidiladha", 10, 12, 1447, "2026-05-27", DayOfWeek.WEDNESDAY),
            Event("Islamic New Year", 1, 1, 1448, "2026-06-17", DayOfWeek.WEDNESDAY),
            Event("Asyura Day", 10, 1, 1448, "2026-06-26", DayOfWeek.FRIDAY),
            Event("Prophet Muhammad\u2019s Birthday", 12, 3, 1448, "2026-08-25", DayOfWeek.TUESDAY),
            Event("Israk Mikraj", 27, 7, 1448, "2027-01-06", DayOfWeek.WEDNESDAY),
            Event("Nisfu Syaaban", 15, 8, 1448, "2027-01-24", DayOfWeek.SUNDAY),
            Event("Beginning of Fasting", 1, 9, 1448, "2027-02-08", DayOfWeek.MONDAY),
            Event("Nuzul Quran", 17, 9, 1448, "2027-02-24", DayOfWeek.WEDNESDAY),
            Event("Hari Raya Aidilfitri", 1, 10, 1448, "2027-03-10", DayOfWeek.WEDNESDAY),
            Event("Hari Raya Aidiladha", 10, 12, 1448, "2027-05-17", DayOfWeek.MONDAY),
            Event("Islamic New Year", 1, 1, 1449, "2027-06-06", DayOfWeek.SUNDAY),
            Event("Asyura Day", 10, 1, 1449, "2027-06-15", DayOfWeek.TUESDAY),
            Event("Prophet Muhammad\u2019s Birthday", 12, 3, 1449, "2027-08-15", DayOfWeek.SUNDAY),
        )
        val calendar = HijriCalendar.parse(assetFile.readText())
        val named = IslamicEvents.parse(eventsAsset.readText())
        for (e in events) {
            val date = LocalDate.parse(e.date)
            assertEquals("${e.name} weekday", e.weekday, date.dayOfWeek)
            val hijri = calendar.on(date)
            assertEquals(e.name, HijriDate(e.day, e.month, HIJRI_MONTH_NAMES[e.month - 1], e.year), hijri)
            // And the app names that day as the PDF does.
            assertEquals("${e.name} on ${e.date}", e.name, named.on(hijri!!))
        }
    }

    @Test fun `Israk Mikraj 1447 is before the first month start we have, so it is unknown rather than guessed`() {
        // The 2026 PDF gives 27 Rejab 1447 as 17 January 2026, but 1 Rejab 1447 is not in the table.
        assertNull(HijriCalendar.parse(assetFile.readText()).on(LocalDate.parse("2026-01-17")))
    }

    @Test fun `the table covers 2026 from 20 January and runs to the end of 2027`() {
        val calendar = HijriCalendar.parse(assetFile.readText())
        assertEquals(HijriDate(1, 8, "Syaaban", 1447), calendar.on(LocalDate.parse("2026-01-20")))
        assertNull(calendar.on(LocalDate.parse("2026-01-19")))
        assertEquals(HijriDate(1, 8, "Syaaban", 1449), calendar.on(LocalDate.parse("2027-12-30")))
        assertEquals(LocalDate.parse("2028-01-27"), calendar.lastKnownDate)
        assertNull(calendar.on(LocalDate.parse("2028-01-28")))
    }

    @Test fun `Ramadhan is spelled as the user chose, in the table and the allowed list`() {
        assertEquals("Ramadhan", HIJRI_MONTH_NAMES[8])
        assertEquals(setOf("Ramadhan"), rows.filter { it.month == 9 }.map { it.name }.toSet())
        assertEquals("9 Ramadhan 1447H", HijriCalendar.parse(assetFile.readText()).on(LocalDate.parse("2026-02-27"))!!.text)
    }

    @Test fun `a few dates worked out by hand`() {
        val calendar = HijriCalendar.parse(assetFile.readText())
        // 13 September 2026 is 1 Rabiulakhir 1448, so 5 October is day 23.
        assertEquals("23 Rabiulakhir 1448H", calendar.on(LocalDate.parse("2026-10-05"))!!.text)
        // 12 October is 1 Jamadilawal, so the day before is the last day of Rabiulakhir, a 29 day month.
        assertEquals("29 Rabiulakhir 1448H", calendar.on(LocalDate.parse("2026-10-11"))!!.text)
        assertEquals("1 Jamadilawal 1448H", calendar.on(LocalDate.parse("2026-10-12"))!!.text)
    }

    // The events file: (month, day, name), the same every year, from the Islamic Events tables in the two PDFs.
    private class EventRow(val line: Int, val month: Int, val day: Int, val name: String)

    private val eventRows: List<EventRow> by lazy {
        eventsCsv.readLines().drop(1).withIndex().filter { it.value.isNotBlank() }.map { (i, line) ->
            val f = line.split(',')
            EventRow(i + 2, f[0].toInt(), f[1].toInt(), f[2])
        }
    }

    @Test fun `the events table is there with the agreed columns and the nine events from the PDFs`() {
        assertTrue("missing ${eventsCsv.absolutePath}", eventsCsv.exists())
        assertEquals("hijri_month,hijri_day,event_name", eventsCsv.readLines().first())
        assertEquals(
            listOf(
                Triple(1, 1, "Islamic New Year"), Triple(1, 10, "Asyura Day"), Triple(3, 12, "Prophet Muhammad\u2019s Birthday"),
                Triple(7, 27, "Israk Mikraj"), Triple(8, 15, "Nisfu Syaaban"), Triple(9, 1, "Beginning of Fasting"),
                Triple(9, 17, "Nuzul Quran"), Triple(10, 1, "Hari Raya Aidilfitri"), Triple(12, 10, "Hari Raya Aidiladha"),
            ),
            eventRows.map { Triple(it.month, it.day, it.name) },
        )
    }

    @Test fun `event rows are valid, in order and not repeated`() {
        eventRows.forEach {
            assertTrue("line ${it.line}", it.month in 1..12 && it.day in 1..30)
            assertTrue("line ${it.line}: blank or padded name", it.name.isNotBlank() && it.name == it.name.trim() && !it.name.contains("  "))
        }
        eventRows.zipWithNext().forEach { (a, b) -> assertTrue("line ${b.line} is out of order", b.month * 100 + b.day > a.month * 100 + a.day) }
        assertEquals(eventRows.size, eventRows.map { it.month to it.day }.toSet().size)
    }

    @Test fun `the generated events asset is exactly the events table`() {
        assertTrue("missing ${eventsAsset.absolutePath}: run tools/make-hijri.py", eventsAsset.exists())
        val events = IslamicEvents.parse(eventsAsset.readText())
        for (row in eventRows) {
            assertEquals("line ${row.line}", row.name, events.on(HijriDate(row.day, row.month, HIJRI_MONTH_NAMES[row.month - 1], 1448)))
        }
        // and nothing else: every other day of every month is ordinary
        val known = eventRows.map { it.month to it.day }.toSet()
        for (month in 1..12) for (day in 1..30) {
            if ((month to day) !in known) assertNull("$day $month", events.on(HijriDate(day, month, HIJRI_MONTH_NAMES[month - 1], 1448)))
        }
    }

    @Test fun `across the whole table exactly the expected days carry an event, and no others`() {
        val calendar = HijriCalendar.parse(assetFile.readText())
        val events = IslamicEvents.parse(eventsAsset.readText())
        val found = mutableListOf<Pair<String, String>>()
        var day = LocalDate.parse("2026-01-20")
        while (day <= calendar.lastKnownDate!!) {
            calendar.on(day)?.let { hijri -> events.on(hijri)?.let { found += day.toString() to it } }
            day = day.plusDays(1)
        }
        val fromPdfs = listOf(
            "2026-02-03" to "Nisfu Syaaban", "2026-02-19" to "Beginning of Fasting", "2026-03-07" to "Nuzul Quran",
            "2026-03-21" to "Hari Raya Aidilfitri", "2026-05-27" to "Hari Raya Aidiladha", "2026-06-17" to "Islamic New Year",
            "2026-06-26" to "Asyura Day", "2026-08-25" to "Prophet Muhammad\u2019s Birthday", "2027-01-06" to "Israk Mikraj",
            "2027-01-24" to "Nisfu Syaaban", "2027-02-08" to "Beginning of Fasting", "2027-02-24" to "Nuzul Quran",
            "2027-03-10" to "Hari Raya Aidilfitri", "2027-05-17" to "Hari Raya Aidiladha", "2027-06-06" to "Islamic New Year",
            "2027-06-15" to "Asyura Day", "2027-08-15" to "Prophet Muhammad\u2019s Birthday",
        )
        // Two more fall inside the table's certain range: 27 Rejab 1449 on 26 December 2027 and 15 Syaaban 1449 on 13 January
        // 2028. They are not in these two PDFs (the 2028 one will list them) but they are the same yearly events on their
        // fixed Hijri dates, and the month start table puts those dates there.
        val expected = (fromPdfs + ("2027-12-26" to "Israk Mikraj") + ("2028-01-13" to "Nisfu Syaaban")).sortedBy { it.first }
        assertEquals(expected, found)
    }

    @Test fun `the longest and shortest names the layout has to cope with`() {
        assertEquals("Prophet Muhammad\u2019s Birthday", eventRows.maxBy { it.name.length }.name)
        assertEquals(27, eventRows.maxOf { it.name.length })
        assertEquals("Asyura Day", eventRows.minBy { it.name.length }.name)
    }
}
