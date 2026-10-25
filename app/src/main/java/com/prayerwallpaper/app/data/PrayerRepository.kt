package com.prayerwallpaper.app.data

import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Month

/** Decides which years to download. Pure, so it can be tested without a network or a clock. */
object RefreshPolicy {
    private val STALE_AFTER: Duration = Duration.ofDays(7)

    /** December also wants next year's timetable, which MUIS publishes in late November. */
    fun yearsNeeded(today: LocalDate): List<Int> =
        if (today.month == Month.DECEMBER) listOf(today.year, today.year + 1) else listOf(today.year)

    /**
     * Missing years are fetched on every run. The periodic job runs daily, so that is the daily
     * retry for December and January. Years already cached are only refreshed weekly.
     */
    fun yearsToFetch(data: PrayerData, today: LocalDate, now: Instant): List<Int> =
        yearsNeeded(today).filter { year ->
            val cached = data.years.firstOrNull { it.year == year }
            cached == null || Duration.between(cached.fetchedAt, now) >= STALE_AFTER
        }
}

class RefreshResult(val fetchedYears: List<Int>, val schema: String, val rejectedRows: Int)

class PrayerRepository(
    private val store: PrayerStore,
    private val api: PrayerApi,
    private val clock: Clock,
) {
    fun data(): PrayerData = store.read()

    /**
     * Downloads what [RefreshPolicy] asks for, or every needed year when [force] is set (the
     * "refresh now" button). Throws on network errors, including [HttpStatusException] for 429.
     * The cache is only replaced for years that downloaded successfully.
     */
    @Synchronized
    fun refresh(force: Boolean): RefreshResult {
        val now = clock.instant()
        val today = LocalDate.now(clock)
        var data = store.read()
        val years = if (force) RefreshPolicy.yearsNeeded(today) else RefreshPolicy.yearsToFetch(data, today, now)

        var schema = ""
        var rejected = 0
        val fetched = mutableListOf<Int>()
        for (year in years) {
            val (fetch, days) = fetchYear(year, data) ?: continue
            schema = fetch.schema
            rejected += fetch.rejectedRows
            data = data.withYear(YearData(year, fetch.datasetId, now, days), keepFrom = today.year - 1)
            store.write(data)
            fetched += year
        }
        return RefreshResult(fetched, schema, rejected)
    }

    /** Null when MUIS has not published [year] yet. */
    private fun fetchYear(year: Int, data: PrayerData): Pair<DatasetFetch, List<PrayerDay>>? {
        val knownId = YEAR_DATASETS[year]
        val candidates = if (knownId != null) {
            listOf(knownId)
        } else {
            val seen = YEAR_DATASETS.values.toSet() + OTHER_DATASETS + data.years.map { it.datasetId }
            api.collectionDatasetIds() - seen
        }
        for (id in candidates) {
            val fetch = api.fetchDataset(id)
            val days = fetch.days.filter { it.date.year == year }.sortedBy { it.date }
            if (days.size >= MIN_DAYS_IN_YEAR) return fetch to days
            if (knownId != null) throw IOException("Dataset $id returned only ${days.size} days for $year")
        }
        return null
    }

    private fun PrayerData.withYear(year: YearData, keepFrom: Int) =
        PrayerData((years.filter { it.year != year.year } + year).filter { it.year >= keepFrom }.sortedBy { it.year })

    companion object {
        /** Verified against the live API on 2026-10-04. Newer years are found through the collection. */
        val YEAR_DATASETS = mapOf(2026 to "d_d441e7242e78efc566024dd5b0d9829c")

        /** Known datasets that are not a single year we need: 2024, 2025 and the 2024 to 2026 consolidation. */
        val OTHER_DATASETS = setOf(
            "d_dddc19f6c90edd7cff6b57494630ad29",
            "d_e81ea2337599b674c4f645c1af93e0dc",
            "d_a6a206cba471fe04b62dd886ef5eaf22",
        )

        /** A year with a few bad rows is still worth caching; those days show "no data". */
        private const val MIN_DAYS_IN_YEAR = 300
    }
}
