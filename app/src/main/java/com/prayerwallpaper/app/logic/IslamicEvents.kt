package com.prayerwallpaper.app.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A fixed day of a Hijri month that is marked every year, for example 1 Syawal, Hari Raya Aidilfitri. */
data class IslamicEvent(val month: Int, val day: Int, val name: String)

/**
 * The Islamic events MUIS lists in its "Key Islamic dates" calendar. They fall on the same Hijri day and month every year,
 * so an event is found by looking up the day the Hijri calendar says it is: nothing here works out a date. Names are as MUIS
 * prints them, which mixes Malay and English (Hari Raya Aidilfitri, Beginning of Fasting).
 */
class IslamicEvents(events: List<IslamicEvent>) {
    private val byDay: Map<Pair<Int, Int>, String> = events.associate { (it.month to it.day) to it.name }

    val isEmpty: Boolean get() = byDay.isEmpty()

    /** The event on this Hijri date, or null when it is an ordinary day. */
    fun on(date: HijriDate): String? = byDay[date.month to date.day]

    companion object {
        /** Reads the asset that `tools/make-hijri.py` generates. Throws on anything that is not a valid file. */
        fun parse(json: String): IslamicEvents {
            val file = Json { ignoreUnknownKeys = true }.decodeFromString<EventsFile>(json)
            return IslamicEvents(file.events.map {
                require(it.month in 1..12 && it.day in 1..30 && it.name.isNotBlank()) { "Bad event ${it.name}" }
                IslamicEvent(it.month, it.day, it.name)
            })
        }
    }
}

@Serializable
private class EventsFile(val events: List<EventRow>)

@Serializable
private class EventRow(val month: Int, val day: Int, val name: String)

/** What the line above the list says: the Hijri date, and the event on that day if there is one. */
data class HijriLine(val date: HijriDate, val event: String? = null)

/**
 * Where the Hijri date line above the list and the countdown line under it sit: centred, or from the list's left edge, in line
 * with the prayer icons. One choice for both. (The name and the stored key date from when the date was the only line.)
 */
enum class HijriAlign { CENTER, LEFT }
