package com.prayerwallpaper.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the real bundled list, the one the app ships, not a sample. Unit tests run with the module as the working directory. */
class CityAssetTest {
    private val asset = File("src/main/assets/${CityIndex.ASSET_NAME}")
    private val index: CityIndex by lazy { asset.inputStream().use { CityIndex.parse(it) } }

    @Test fun `the list is there and big`() {
        assertTrue("missing ${asset.absolutePath}", asset.exists())
        assertTrue("asset is ${asset.length()} bytes", asset.length() in 1_500_000..4_000_000)
        assertTrue("only ${index.size} cities", index.size > 30_000)
    }

    @Test fun `every row parses, with a time zone this JVM knows`() {
        assertEquals(0, index.skipped)
    }

    @Test fun `Singapore is one place, on the official timetable`() {
        val hits = index.search("singapore")
        assertEquals(1, hits.count { it.country == "SG" })
        val singapore = hits.first { it.country == "SG" }
        assertEquals(Place.SINGAPORE.id, singapore.id)
        assertTrue(singapore.usesMuis)
        // None of its dozens of neighbourhoods are listed separately.
        assertTrue(index.search("tampines").none { it.country == "SG" })
        assertTrue(index.search("jurong").none { it.country == "SG" })
    }

    @Test fun `the holy cities are found under the names people use`() {
        assertEquals("Makkah", index.search("mecca").first().name)
        assertEquals("Makkah", index.search("makkah").first().name)
        assertEquals("Madinah", index.search("medina").first().name)
        assertEquals("SA", index.search("medina").first().country)
    }

    @Test fun `well known cities are found by name`() {
        for ((query, country) in listOf(
            "london" to "GB", "new york" to "US", "karachi" to "PK", "jakarta" to "ID", "kuala lumpur" to "MY", "dubai" to "AE",
            "istanbul" to "TR", "cairo" to "EG", "sydney" to "AU", "toronto" to "CA", "cologne" to "DE", "bombay" to "IN",
            "bangalore" to "IN", "sao paulo" to "BR", "cape town" to "ZA", "kuwait" to "KW", "doha" to "QA", "dhaka" to "BD",
        )) {
            assertEquals(query, country, index.search(query).first().country)
        }
    }

    @Test fun `the biggest city comes first`() {
        assertEquals("GB", index.search("birmingham").first().country) // 984,000 against Alabama's 212,000
        assertEquals("US", index.search("new york").first().country)
    }

    @Test fun `nearest finds the right city from a known point`() {
        assertEquals("Singapore", index.nearest(1.30, 103.80)!!.place.name)
        assertEquals("Makkah", index.nearest(21.42, 39.82)!!.place.name)
        assertEquals("London", index.nearest(51.51, -0.13)!!.place.name)
        assertEquals("Jakarta", index.nearest(-6.2, 106.85)!!.place.name)
        assertTrue(index.nearest(0.0, -30.0)!!.kilometres > 500) // mid-Atlantic: far from everything
    }

    @Test fun `search is fast enough to run on every keystroke`() {
        index.search("a") // warm up
        val start = System.nanoTime()
        repeat(20) { index.search("san") }
        val perSearchMs = (System.nanoTime() - start) / 20 / 1e6
        assertTrue("a search took $perSearchMs ms", perSearchMs < 50)
        val nearestStart = System.nanoTime()
        index.nearest(48.85, 2.35)
        assertTrue("nearest took ${(System.nanoTime() - nearestStart) / 1e6} ms", (System.nanoTime() - nearestStart) / 1e6 < 200)
    }

    @Test fun `every place has a usable zone and sane coordinates`() {
        for (place in index.search("a", limit = 1000) + index.search("s", limit = 1000)) {
            place.zone // would throw if it were unknown
            assertTrue(place.latitude in -90.0..90.0 && place.longitude in -180.0..180.0)
        }
    }
}
