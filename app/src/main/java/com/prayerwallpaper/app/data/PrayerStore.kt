package com.prayerwallpaper.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** One year's timetable as fetched from one dataset. */
data class YearData(val year: Int, val datasetId: String, val fetchedAt: Instant, val days: List<PrayerDay>)

/** Everything in the cache. The wallpaper reads this and never touches the network. */
data class PrayerData(val years: List<YearData> = emptyList()) {
    val isEmpty: Boolean get() = years.none { it.days.isNotEmpty() }
    private val byDate: Map<LocalDate, PrayerDay> by lazy { years.flatMap { it.days }.associateBy { it.date } }

    fun day(date: LocalDate): PrayerDay? = byDate[date]
    fun days(): Map<LocalDate, PrayerDay> = byDate
}

@Serializable
private data class CacheFile(val version: Int = 1, val years: List<CachedYear> = emptyList())

@Serializable
private data class CachedYear(val year: Int, val datasetId: String, val fetchedAtEpochMs: Long, val days: List<CachedDay>)

@Serializable
private data class CachedDay(val date: String, val times: List<String>)

/** JSON cache in app-private storage. A missing or damaged file reads as empty, never as stale data. */
class PrayerStore(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    // The wallpaper reads on every screen-on, so only re-parse when the file actually changed.
    private var memo: Triple<Long, Long, PrayerData>? = null

    @Synchronized
    fun read(): PrayerData {
        val stamp = file.lastModified() to file.length()
        memo?.let { (modified, length, data) -> if (modified == stamp.first && length == stamp.second) return data }
        return parse().also { memo = Triple(stamp.first, stamp.second, it) }
    }

    private fun parse(): PrayerData {
        if (!file.exists()) return PrayerData()
        return try {
            val cached = json.decodeFromString<CacheFile>(file.readText())
            PrayerData(cached.years.map { year ->
                YearData(
                    year.year,
                    year.datasetId,
                    Instant.ofEpochMilli(year.fetchedAtEpochMs),
                    year.days.map { PrayerDay(LocalDate.parse(it.date), it.times.map(LocalTime::parse)) },
                )
            })
        } catch (_: Exception) {
            PrayerData()
        }
    }

    /** Writes to a temp file and renames, so a crash mid-write cannot leave a half-written cache. */
    @Synchronized
    fun write(data: PrayerData) {
        val cached = CacheFile(
            years = data.years.map { year ->
                CachedYear(
                    year.year,
                    year.datasetId,
                    year.fetchedAt.toEpochMilli(),
                    year.days.map { day -> CachedDay(day.date.toString(), day.times.map(LocalTime::toString)) },
                )
            },
        )
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(json.encodeToString(cached))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
