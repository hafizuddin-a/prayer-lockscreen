package com.prayerwallpaper.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DatasetParserTest {
    private fun parse(raw: String, prayer: Prayer) = DatasetParser.parseTime(raw, prayer)
    private fun record(json: String) = DatasetParser.parseRecord(Json.parseToJsonElement(json).jsonObject)

    @Test fun `parses H colon mm, H dot mm and H space mm`() {
        assertEquals(LocalTime.of(5, 44), parse("5:44", Prayer.SUBUH))
        assertEquals(LocalTime.of(5, 44), parse("05:44", Prayer.SUBUH))
        assertEquals(LocalTime.of(5, 44), parse("5.44", Prayer.SUBUH))
        assertEquals(LocalTime.of(5, 44), parse("5 44", Prayer.SUBUH))
        assertEquals(LocalTime.of(7, 7), parse("07 07", Prayer.SYURUK))
    }

    @Test fun `tolerates stray whitespace including non-breaking spaces`() {
        assertEquals(LocalTime.of(5, 44), parse("  5:44  ", Prayer.SUBUH))
        assertEquals(LocalTime.of(5, 44), parse(" 5 44 ", Prayer.SUBUH))
        assertEquals(LocalTime.of(5, 44), parse("5 : 44", Prayer.SUBUH))
        assertEquals(LocalTime.of(5, 44), parse("05:44:00", Prayer.SUBUH))
    }

    @Test fun `24-hour afternoon times are left alone`() {
        assertEquals(LocalTime.of(13, 10), parse("13:10", Prayer.ZOHOR))
        assertEquals(LocalTime.of(16, 34), parse("16:34", Prayer.ASAR))
        assertEquals(LocalTime.of(20, 25), parse("20:25", Prayer.ISYAK))
        assertEquals(LocalTime.of(12, 56), parse("12:56", Prayer.ZOHOR))
    }

    @Test fun `12-hour data without am or pm is read as pm from Zohor on`() {
        assertEquals(LocalTime.of(13, 10), parse("1 10", Prayer.ZOHOR))
        assertEquals(LocalTime.of(16, 34), parse("4 34", Prayer.ASAR))
        assertEquals(LocalTime.of(16, 34), parse("04:34", Prayer.ASAR))
        assertEquals(LocalTime.of(19, 10), parse("7:10", Prayer.MAGHRIB))
        assertEquals(LocalTime.of(20, 25), parse("8 25", Prayer.ISYAK))
        assertEquals(LocalTime.of(12, 56), parse("12 56", Prayer.ZOHOR))
    }

    @Test fun `rejects garbage and impossible times`() {
        listOf("", "abc", "5", "544", "5:4", "25:00", "5:60", "5-44", "5:44 pm").forEach {
            assertNull("'$it' should not parse", parse(it, Prayer.SUBUH))
        }
    }

    @Test fun `parses a real 2026 row`() {
        val day = record(
            """{"_id":1,"Date":"2026-01-01","Day":"Thurs","Subuh":"05:44","Syuruk":"07:08","Zohor":"13:10","Asar":"16:34","Maghrib":"19:11","Isyak":"20:25"}""",
        )
        assertNotNull(day)
        assertEquals(LocalDate.of(2026, 1, 1), day!!.date)
        assertEquals(
            listOf("05:44", "07:08", "13:10", "16:34", "19:11", "20:25"),
            day.times.map { it.toString() },
        )
    }

    @Test fun `parses a real 2024 row with space separated 12-hour times`() {
        val day = record(
            """{"_id":1,"Date":"2024-01-01","Day":"Mon","Subuh":"5 44","Syuruk":"07 07","Zohor":"1 10","Asar":"4 34","Maghrib":"7 10","Isyak":"8 25"}""",
        )
        assertEquals(
            listOf("05:44", "07:07", "13:10", "16:34", "19:10", "20:25"),
            day!!.times.map { it.toString() },
        )
    }

    @Test fun `parses a real 2025 row`() {
        val day = record(
            """{"_id":1,"Date":"2025-01-01","Day":"Wednesday","Subuh":"5:43","Syuruk":"7:07","Zohor":"1:09","Asar":"4:33","Maghrib":"7:10","Isyak":"8:25"}""",
        )
        assertEquals(
            listOf("05:43", "07:07", "13:09", "16:33", "19:10", "20:25"),
            day!!.times.map { it.toString() },
        )
    }

    @Test fun `column names are matched case-insensitively and extra columns are ignored`() {
        val day = record(
            """{"date":" 2026-01-01 ","SUBUH":"05:44","syuruk":"07:08","Zohor":"13:10","Asar":"16:34","Maghrib":"19:11","Isyak":"20:25","Extra":"x"}""",
        )
        assertNotNull(day)
    }

    @Test fun `rejects rows that are incomplete or out of order instead of guessing`() {
        // Missing Isyak
        assertNull(record("""{"Date":"2026-01-01","Subuh":"05:44","Syuruk":"07:08","Zohor":"13:10","Asar":"16:34","Maghrib":"19:11"}"""))
        // Bad date
        assertNull(record("""{"Date":"01/01/2026","Subuh":"05:44","Syuruk":"07:08","Zohor":"13:10","Asar":"16:34","Maghrib":"19:11","Isyak":"20:25"}"""))
        // Asar earlier than Zohor
        assertNull(record("""{"Date":"2026-01-01","Subuh":"05:44","Syuruk":"07:08","Zohor":"16:34","Asar":"13:10","Maghrib":"19:11","Isyak":"20:25"}"""))
        // Null value
        assertNull(record("""{"Date":"2026-01-01","Subuh":null,"Syuruk":"07:08","Zohor":"13:10","Asar":"16:34","Maghrib":"19:11","Isyak":"20:25"}"""))
    }
}
