package com.prayerwallpaper.app.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Reads timetable rows from data.gov.sg. The datasets are not consistent: 2024 and 2025 use
 * 12-hour times with no am/pm ("1 10", "4:34"), 2026 uses 24-hour ("13:10"). Anything that
 * does not parse into six increasing times is rejected, never guessed.
 */
object DatasetParser {
    private const val BLANK = "\\s\\u00A0" // Java's \s does not match a non-breaking space
    private const val SPACE = "[$BLANK]*"
    // H:mm, H.mm or H mm, optional :ss, stray whitespace (including non-breaking) anywhere.
    private val time = Regex("^$SPACE(\\d{1,2})$SPACE[:.$BLANK]$SPACE(\\d{2})(?::\\d{2})?$SPACE$")

    fun parseTime(raw: String, prayer: Prayer): LocalTime? {
        val match = time.matchEntire(raw) ?: return null
        var hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()
        if (hour > 23 || minute > 59) return null
        // 12-hour data: anything from Zohor on that reads before midday is really pm.
        if (prayer.isAfternoon && hour < 12) hour += 12
        return LocalTime.of(hour, minute)
    }

    /** Returns null when the row is unusable (bad date, bad time, or times out of order). */
    fun parseRecord(record: JsonObject): PrayerDay? {
        val fields = record.entries.associate { (key, value) ->
            key.trim().lowercase() to (value as? JsonPrimitive)?.contentOrNull
        }
        val date = try {
            LocalDate.parse(fields["date"]?.trim() ?: return null)
        } catch (_: DateTimeParseException) {
            return null
        }
        val times = Prayer.entries.map { prayer ->
            parseTime(fields[prayer.column] ?: return null, prayer) ?: return null
        }
        if (times.zipWithNext().any { (a, b) -> a >= b }) return null
        return PrayerDay(date, times)
    }
}
