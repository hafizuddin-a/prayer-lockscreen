package com.prayerwallpaper.app.data

import android.content.Context
import java.io.InputStream
import java.text.Normalizer
import java.time.ZoneId
import java.time.zone.ZoneRulesException
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** The city nearest a point, and how far away it is. */
data class Nearest(val place: Place, val kilometres: Double)

/**
 * The bundled city list, searchable by name and by position. Built from GeoNames `cities15000`
 * (cities of 15,000 people or more, CC BY 4.0) by `tools/make-cities.py`, one tab-separated line per
 * city, biggest first:
 *
 *     geonameid  name  asciiname  country  admin1  latitude  longitude  timezone  population  aliases
 *
 * admin1 is only kept for the United States (a state code: Springfield, MO and Springfield, IL). Elsewhere
 * GeoNames uses numbers, or codes that mean little to a reader. aliases is optional: other names people search by,
 * separated by semicolons ("Mecca" for Makkah). The order of the lines is the ranking: a search lists
 * the biggest matching city first.
 */
class CityIndex private constructor(
    private val places: List<Place>,
    private val haystacks: List<String>,
    /** Lines that had the fields of a city but were rejected (a bad number, or a time zone this phone does not know). */
    val skipped: Int,
) {
    val size: Int get() = places.size

    /**
     * Cities whose name starts with [query], then those with a word that does, each biggest first.
     * Accents and case do not matter: "sao paulo", "São Paulo" and "SAO PAULO" are the same search.
     */
    fun search(query: String, limit: Int = DEFAULT_LIMIT): List<Place> {
        val q = normalise(query)
        if (q.isEmpty()) return emptyList()
        val found = ArrayList<Place>(limit)
        // Each haystack is "\nname\nname..." in normalised form, so "\n" + q means some name starts with q, and " " + q a later word does.
        for (needle in arrayOf("\n$q", " $q")) {
            for (i in places.indices) {
                if (found.size >= limit) return found
                if (haystacks[i].contains(needle) && places[i] !in found) found += places[i]
            }
        }
        return found
    }

    /** The closest city to a point, by great-circle distance. Null only for an empty list. */
    fun nearest(latitude: Double, longitude: Double): Nearest? {
        var best: Place? = null
        var bestKm = Double.MAX_VALUE
        for (place in places) {
            val km = distanceKm(latitude, longitude, place.latitude, place.longitude)
            if (km < bestKm) {
                bestKm = km
                best = place
            }
        }
        return best?.let { Nearest(it, bestKm) }
    }

    fun byId(id: Long): Place? = places.firstOrNull { it.id == id }

    companion object {
        private const val DEFAULT_LIMIT = 30
        private const val EARTH_RADIUS_KM = 6371.0088
        const val ASSET_NAME = "cities.tsv"

        /** Letters that Unicode does not take apart into a base letter and an accent. */
        private val straight = mapOf('ø' to "o", 'đ' to "d", 'ł' to "l", 'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'þ' to "th", 'ı' to "i", 'ð' to "d")

        /** Apostrophes are dropped, not turned into a space: "Ha'il" and "Hail" are the same word. */
        private const val APOSTROPHES = "'\u2019\u02BC\u02BB`\u00B4"

        /** Lower case, accents removed, apostrophes dropped, anything else that is not a letter or digit a single space. */
        fun normalise(text: String): String {
            val decomposed = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            val out = StringBuilder(decomposed.length)
            var pendingSpace = false
            for (c in decomposed) {
                if (Character.getType(c) == Character.NON_SPACING_MARK.toInt() || c in APOSTROPHES) continue
                val piece = straight[c] ?: if (c.isLetterOrDigit()) c.toString() else null
                if (piece == null) {
                    pendingSpace = true
                    continue
                }
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(piece)
            }
            return out.toString()
        }

        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dLat = p2 - p1
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) + cos(p1) * cos(p2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
        }

        /** Lines that are short, have a bad number or a time zone this phone does not know are skipped, never guessed at. */
        fun parse(lines: Sequence<String>): CityIndex {
            val places = ArrayList<Place>()
            val haystacks = ArrayList<String>()
            var skipped = 0
            val zones = HashMap<String, Boolean>()
            for (line in lines) {
                val f = line.split('\t')
                if (f.size < 8) continue
                val id = f[0].toLongOrNull()
                val lat = f[5].toDoubleOrNull()
                val lon = f[6].toDoubleOrNull()
                if (id == null || lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0 || f[3].length != 2) {
                    skipped++
                    continue
                }
                val zoneKnown = zones.getOrPut(f[7]) {
                    try { ZoneId.of(f[7]); true } catch (_: ZoneRulesException) { false } catch (_: java.time.DateTimeException) { false }
                }
                if (!zoneKnown) {
                    skipped++
                    continue
                }
                // Only US state codes are worth showing. Elsewhere GeoNames gives numbers, or codes (England's "ENG",
                // a Swiss canton "SG") that read as noise or even as another country.
                val region = f[4].takeIf { f[3] == "US" && it.length == 2 && it.all(Char::isLetter) }.orEmpty()
                places += Place(id, f[1], region, f[3], lat, lon, f[7])
                val names = LinkedHashSet<String>()
                for (name in listOf(f[2], f[1]) + f.getOrElse(9) { "" }.split(';')) normalise(name).takeIf { it.isNotEmpty() }?.let(names::add)
                haystacks += names.joinToString(separator = "\n", prefix = "\n")
            }
            return CityIndex(places, haystacks, skipped)
        }

        fun parse(stream: InputStream): CityIndex = stream.bufferedReader(Charsets.UTF_8).use { parse(it.lineSequence()) }

        /** Reads the list from the app's assets. About 34,000 lines, so do it off the main thread. Throws IOException if it is missing. */
        fun fromAssets(context: Context): CityIndex = context.assets.open(ASSET_NAME).use { parse(it) }
    }
}
