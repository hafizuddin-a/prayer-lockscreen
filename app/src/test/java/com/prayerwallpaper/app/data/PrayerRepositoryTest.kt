package com.prayerwallpaper.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PrayerRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sg = ZoneId.of("Asia/Singapore")
    private fun clockAt(iso: String) = Clock.fixed(Instant.parse(iso), sg)
    private val times = listOf("05:44", "07:08", "13:10", "16:34", "19:11", "20:25").map(LocalTime::parse)

    private fun year(year: Int, id: String, fetchedAt: String, count: Int = 365): YearData {
        val start = LocalDate.of(year, 1, 1)
        return YearData(year, id, Instant.parse(fetchedAt), (0 until count).map { PrayerDay(start.plusDays(it.toLong()), times) })
    }

    /** Builds the JSON the API returns for [count] days starting 1 Jan [yearNum]; `next` is always present, like the real API. */
    private fun datasetPage(yearNum: Int, count: Int, offset: Int, limit: Int): String {
        val start = LocalDate.of(yearNum, 1, 1)
        val rows = (offset until minOf(offset + limit, count)).joinToString(",") {
            """{"_id":${it + 1},"Date":"${start.plusDays(it.toLong())}","Day":"x","Subuh":"05:44","Syuruk":"07:08","Zohor":"13:10","Asar":"16:34","Maghrib":"19:11","Isyak":"20:25"}"""
        }
        return """{"success":true,"result":{"fields":[{"type":"text","id":"Date"},{"type":"int4","id":"_id"}],"records":[$rows],"_links":{"next":"/x"},"total":$count}}"""
    }

    private class FakeHttp(val handler: (String) -> String) : HttpGet {
        val requests = mutableListOf<String>()
        override fun get(url: String): String { requests += url; return handler(url) }
    }

    private fun newRepo(http: HttpGet, now: String, file: File = tmp.newFile().also { it.delete() }) =
        PrayerRepository(PrayerStore(file), PrayerApi(http), clockAt(now)) to file

    // --- RefreshPolicy ---

    @Test fun `policy fetches the current year when the cache is empty`() {
        val now = Instant.parse("2026-10-04T00:00:00Z")
        assertEquals(listOf(2026), RefreshPolicy.yearsToFetch(PrayerData(), LocalDate.of(2026, 10, 4), now))
    }

    @Test fun `policy leaves a fresh cache alone and refreshes a week-old one`() {
        val data = PrayerData(listOf(year(2026, "d", "2026-09-30T00:00:00Z")))
        val today = LocalDate.of(2026, 10, 4)
        assertEquals(emptyList<Int>(), RefreshPolicy.yearsToFetch(data, today, Instant.parse("2026-10-06T00:00:00Z")))
        assertEquals(listOf(2026), RefreshPolicy.yearsToFetch(data, today, Instant.parse("2026-10-07T00:00:00Z")))
    }

    @Test fun `in December policy also asks for next year every run until it arrives`() {
        val data = PrayerData(listOf(year(2026, "d", "2026-12-01T00:00:00Z")))
        val today = LocalDate.of(2026, 12, 3)
        assertEquals(listOf(2027), RefreshPolicy.yearsToFetch(data, today, Instant.parse("2026-12-03T00:00:00Z")))
    }

    @Test fun `in January a year fetched in December is not fetched again`() {
        val data = PrayerData(listOf(year(2026, "a", "2026-12-20T00:00:00Z"), year(2027, "b", "2026-12-30T00:00:00Z")))
        assertEquals(emptyList<Int>(), RefreshPolicy.yearsToFetch(data, LocalDate.of(2027, 1, 2), Instant.parse("2027-01-02T00:00:00Z")))
    }

    @Test fun `in January with no cached year the policy fetches it daily`() {
        val data = PrayerData(listOf(year(2026, "a", "2026-12-20T00:00:00Z")))
        assertEquals(listOf(2027), RefreshPolicy.yearsToFetch(data, LocalDate.of(2027, 1, 2), Instant.parse("2027-01-02T00:00:00Z")))
    }

    // --- Store ---

    @Test fun `cache round trips and survives a new store instance`() {
        val file = tmp.newFile()
        val data = PrayerData(listOf(year(2026, "d_x", "2026-10-04T01:02:03Z", count = 5)))
        PrayerStore(file).write(data)
        val read = PrayerStore(file).read()
        assertEquals(data.years, read.years)
        assertEquals(times, read.day(LocalDate.of(2026, 1, 3))!!.times)
    }

    @Test fun `missing or corrupt cache reads as empty`() {
        assertTrue(PrayerStore(File(tmp.root, "none.json")).read().isEmpty)
        val bad = tmp.newFile().apply { writeText("{not json") }
        assertTrue(PrayerStore(bad).read().isEmpty)
        val truncated = tmp.newFile().apply { writeText("""{"version":1,"years":[{"year":2026""") }
        assertTrue(PrayerStore(truncated).read().isEmpty)
    }

    // --- Repository ---

    @Test fun `first refresh downloads the known 2026 dataset in one request and caches it`() {
        val http = FakeHttp { datasetPage(2026, 365, offset = 0, limit = 400) }
        val (repo, file) = newRepo(http, "2026-10-04T04:00:00Z")
        val result = repo.refresh(force = false)
        assertEquals(listOf(2026), result.fetchedYears)
        assertEquals(1, http.requests.size)
        assertTrue(http.requests[0].startsWith("https://data.gov.sg/api/action/datastore_search?resource_id=d_d441e7242e78efc566024dd5b0d9829c&limit=400"))
        assertEquals(365, PrayerStore(file).read().years.single().days.size)
    }

    @Test fun `paging stops at total even though the api always reports a next link`() {
        val http = FakeHttp { url ->
            val offset = Regex("offset=(\\d+)").find(url)!!.groupValues[1].toInt()
            datasetPage(2026, 365, offset, limit = 150) // server returns 150 per page whatever we ask
        }
        val (repo, _) = newRepo(http, "2026-10-04T04:00:00Z")
        repo.refresh(force = false)
        assertEquals(3, http.requests.size) // offsets 0, 150, 300
        assertEquals(365, repo.data().years.single().days.size)
    }

    @Test fun `a fresh cache makes no request and a rate limit leaves the cache intact`() {
        val http = FakeHttp { throw HttpStatusException(429, null) }
        val (repo, file) = newRepo(http, "2026-10-04T04:00:00Z")
        PrayerStore(file).write(PrayerData(listOf(year(2026, "d", "2026-10-03T00:00:00Z"))))
        assertEquals(emptyList<Int>(), repo.refresh(force = false).fetchedYears)
        assertEquals(0, http.requests.size)

        try {
            repo.refresh(force = true)
            fail("expected 429")
        } catch (e: HttpStatusException) {
            assertEquals(429, e.code)
        }
        assertEquals(365, PrayerStore(file).read().years.single().days.size)
    }

    @Test fun `a network error does not touch the cache`() {
        val http = FakeHttp { throw IOException("offline") }
        val (repo, file) = newRepo(http, "2026-10-04T04:00:00Z")
        PrayerStore(file).write(PrayerData(listOf(year(2026, "d", "2026-09-01T00:00:00Z"))))
        try {
            repo.refresh(force = false)
            fail("expected IOException")
        } catch (_: IOException) {
        }
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), PrayerStore(file).read().years.single().fetchedAt)
    }

    @Test fun `December discovers next year's dataset through the collection and skips known ones`() {
        val collection = """{"code":0,"data":{"collectionMetadata":{"childDatasets":[
            "d_dddc19f6c90edd7cff6b57494630ad29","d_e81ea2337599b674c4f645c1af93e0dc","d_d441e7242e78efc566024dd5b0d9829c",
            "d_a6a206cba471fe04b62dd886ef5eaf22","d_new2027"]}}}"""
        val http = FakeHttp { url ->
            when {
                url.contains("collections/2312") -> collection
                url.contains("d_new2027") -> datasetPage(2027, 365, offset = 0, limit = 400)
                else -> datasetPage(2026, 365, offset = 0, limit = 400)
            }
        }
        val (repo, file) = newRepo(http, "2026-12-03T04:00:00Z")
        PrayerStore(file).write(PrayerData(listOf(year(2026, "d_d441e7242e78efc566024dd5b0d9829c", "2026-12-01T00:00:00Z"))))

        val result = repo.refresh(force = false)

        assertEquals(listOf(2027), result.fetchedYears)
        assertEquals(2, http.requests.size) // collection metadata + the one unknown dataset, nothing else
        assertEquals(listOf(2026, 2027), repo.data().years.map { it.year })
        assertEquals("d_new2027", repo.data().years.last().datasetId)
    }

    @Test fun `December with no new dataset published yet fetches nothing and does not fail`() {
        val collection = """{"data":{"collectionMetadata":{"childDatasets":["d_dddc19f6c90edd7cff6b57494630ad29","d_d441e7242e78efc566024dd5b0d9829c"]}}}"""
        val http = FakeHttp { collection }
        val (repo, file) = newRepo(http, "2026-12-03T04:00:00Z")
        PrayerStore(file).write(PrayerData(listOf(year(2026, "d_d441e7242e78efc566024dd5b0d9829c", "2026-12-01T00:00:00Z"))))
        assertEquals(emptyList<Int>(), repo.refresh(force = false).fetchedYears)
        assertEquals(1, http.requests.size)
    }

    @Test fun `old years are dropped but last year is kept for the January morning`() {
        val http = FakeHttp { datasetPage(2026, 365, offset = 0, limit = 400) }
        val (repo, file) = newRepo(http, "2026-10-04T04:00:00Z")
        PrayerStore(file).write(PrayerData(listOf(year(2024, "a", "2025-01-01T00:00:00Z"), year(2025, "b", "2025-12-01T00:00:00Z"))))
        repo.refresh(force = false)
        assertEquals(listOf(2025, 2026), repo.data().years.map { it.year })
    }
}
