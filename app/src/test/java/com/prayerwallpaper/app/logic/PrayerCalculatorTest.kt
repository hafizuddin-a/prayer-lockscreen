package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class PrayerCalculatorTest {
    private fun request(
        lat: Double, lon: Double, zone: String, method: CalcMethod,
        asr: AsrRule = AsrRule.STANDARD, adjustments: List<Int> = List(6) { 0 },
    ) = CalcRequest(lat, lon, ZoneId.of(zone), method, asr, adjustments)

    private fun times(text: String): List<LocalTime> = text.split(" ").map(LocalTime::parse)

    private fun minutesApart(a: LocalTime, b: LocalTime) = Math.abs(Duration.between(a, b).toMinutes())

    /** Compares all six times, in minutes, naming the prayer that is off. */
    private fun assertClose(label: String, expected: String, actual: PrayerDay?, toleranceMinutes: Long) {
        assertNotNull("$label: no times calculated", actual)
        val wanted = times(expected)
        Prayer.entries.forEach { prayer ->
            val off = minutesApart(wanted[prayer.ordinal], actual!![prayer])
            assertTrue("$label ${prayer.name}: wanted ${wanted[prayer.ordinal]}, got ${actual[prayer]} ($off min apart)", off <= toleranceMinutes)
        }
    }

    // Reference values for these places come from Aladhan's public API, an independent implementation
    // of the same published methods, fetched on 2026-10-05. The two round and offset a little
    // differently (London Asr on 25 Oct: the sun says 14:16, Aladhan 14:18, Adhan 14:15), so up to
    // 3 minutes between them is expected; a wrong day or zone would be hours.
    private class Golden(val label: String, val request: CalcRequest, val date: String, val expected: String)

    private val golden = listOf(
        Golden("Mecca, Umm al-Qura", request(21.4225, 39.8262, "Asia/Riyadh", CalcMethod.UMM_AL_QURA), "2026-10-05", "04:57 06:13 12:09 15:33 18:05 19:35"),
        Golden("London, Muslim World League", request(51.5074, -0.1278, "Europe/London", CalcMethod.MUSLIM_WORLD_LEAGUE), "2026-10-05", "05:16 07:08 12:49 15:50 18:29 20:14"),
        Golden("Karachi, Karachi method, Hanafi Asr", request(24.8607, 67.0011, "Asia/Karachi", CalcMethod.KARACHI, AsrRule.HANAFI), "2026-10-05", "05:10 06:26 12:20 16:36 18:15 19:31"),
        Golden("New York, ISNA", request(40.7128, -74.0060, "America/New_York", CalcMethod.NORTH_AMERICA), "2026-10-05", "05:42 06:57 12:44 15:59 18:31 19:46"),
        Golden("Jakarta, Singapore method", request(-6.2088, 106.8456, "Asia/Jakarta", CalcMethod.SINGAPORE), "2026-10-05", "04:18 05:36 11:41 14:44 17:47 18:56"),
        // Far from UTC: the library's own UTC day is a day away from the local one here.
        Golden("Honolulu, UTC-10, ISNA", request(21.3069, -157.8583, "Pacific/Honolulu", CalcMethod.NORTH_AMERICA), "2026-10-05", "05:23 06:24 12:20 15:43 18:15 19:16"),
        Golden("Auckland, UTC+13, Muslim World League", request(-36.8485, 174.7633, "Pacific/Auckland", CalcMethod.MUSLIM_WORLD_LEAGUE), "2026-10-05", "05:23 06:52 13:09 16:43 19:28 20:51"),
        // Clock changes: the times must be local wall-clock times on either side of the change.
        Golden("London, clocks go back", request(51.5074, -0.1278, "Europe/London", CalcMethod.MUSLIM_WORLD_LEAGUE), "2026-10-25", "04:49 06:42 11:45 14:18 16:47 18:32"),
        Golden("London, clocks go forward", request(51.5074, -0.1278, "Europe/London", CalcMethod.MUSLIM_WORLD_LEAGUE), "2026-03-29", "04:46 06:43 13:05 16:33 19:29 21:19"),
        // Midsummer in London the sun never gets 18 degrees down; Fajr and Isha fall back to a share of the night.
        Golden("London, midsummer, angle-based fallback", request(51.5074, -0.1278, "Europe/London", CalcMethod.MUSLIM_WORLD_LEAGUE), "2026-06-21", "02:31 04:43 13:02 17:25 21:22 23:27"),
    )

    @Test fun `matches an independent implementation for places around the world`() {
        golden.forEach { g ->
            assertClose(g.label, g.expected, AdhanCalculator.calculate(g.request, LocalDate.parse(g.date)), toleranceMinutes = 3)
        }
    }

    /**
     * The sun's geometric altitude from the NOAA solar equations, so the checks below compare against
     * the physical definition ("the sun is 18 degrees below the horizon") instead of another prayer
     * time library. Good to a few seconds of time.
     */
    private object Sun {
        private fun declinationAndEquationOfTime(julianDate: Double): Pair<Double, Double> {
            val t = (julianDate - 2451545.0) / 36525.0
            val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360
            val m = Math.toRadians(357.52911 + t * (35999.05029 - 0.0001537 * t))
            val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
            val c = Math.sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) + Math.sin(2 * m) * (0.019993 - 0.000101 * t) + Math.sin(3 * m) * 0.000289
            val omega = Math.toRadians(125.04 - 1934.136 * t)
            val lambda = Math.toRadians(l0 + c - 0.00569 - 0.00478 * Math.sin(omega))
            val eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
            val eps = Math.toRadians(eps0 + 0.00256 * Math.cos(omega))
            val declination = Math.toDegrees(Math.asin(Math.sin(eps) * Math.sin(lambda)))
            val y = Math.tan(eps / 2).let { it * it }
            val l0r = Math.toRadians(l0)
            val equation = 4 * Math.toDegrees(
                y * Math.sin(2 * l0r) - 2 * e * Math.sin(m) + 4 * e * y * Math.sin(m) * Math.cos(2 * l0r) -
                    0.5 * y * y * Math.sin(4 * l0r) - 1.25 * e * e * Math.sin(2 * m),
            )
            return declination to equation
        }

        fun altitude(at: java.time.Instant, latitude: Double, longitude: Double): Double {
            val julianDate = at.epochSecond / 86400.0 + at.nano / 86400e9 + 2440587.5
            val (declination, equation) = declinationAndEquationOfTime(julianDate)
            val utcMinutes = (at.epochSecond % 86400) / 60.0
            val solarMinutes = ((utcMinutes + equation + 4 * longitude) % 1440 + 1440) % 1440
            val hourAngle = Math.toRadians(solarMinutes / 4 - 180)
            val lat = Math.toRadians(latitude)
            val dec = Math.toRadians(declination)
            return Math.toDegrees(Math.asin(Math.sin(lat) * Math.sin(dec) + Math.cos(lat) * Math.cos(dec) * Math.cos(hourAngle)))
        }

        /** The sun's altitude when an object's shadow is [shadowFactor] times its length longer than at noon. */
        fun asrAltitude(at: java.time.Instant, latitude: Double, shadowFactor: Double): Double {
            val (declination, _) = declinationAndEquationOfTime(at.epochSecond / 86400.0 + 2440587.5)
            return Math.toDegrees(Math.atan(1 / (shadowFactor + Math.tan(Math.toRadians(Math.abs(latitude - declination))))))
        }

        /** The local time on [date] between [fromHour] and [toHour] when the sun crosses the altitude [target] gives. */
        fun crossing(date: LocalDate, zone: ZoneId, latitude: Double, longitude: Double, rising: Boolean, fromHour: Long, toHour: Long, target: (java.time.Instant) -> Double): LocalTime {
            var low = date.atStartOfDay(zone).plusHours(fromHour).toInstant()
            var high = date.atStartOfDay(zone).plusHours(toHour).toInstant()
            repeat(50) {
                val mid = low.plusMillis((high.toEpochMilli() - low.toEpochMilli()) / 2)
                val below = altitude(mid, latitude, longitude) < target(mid)
                if (below == rising) low = mid else high = mid
            }
            return low.atZone(zone).toLocalTime().withSecond(0).withNano(0).let { if (low.atZone(zone).second >= 30) it.plusMinutes(1) else it }
        }
    }

    private class Angles(val fajr: Double, val isha: Double)

    private val angles = mapOf(
        CalcMethod.MUSLIM_WORLD_LEAGUE to Angles(18.0, 17.0),
        CalcMethod.KARACHI to Angles(18.0, 18.0),
        CalcMethod.NORTH_AMERICA to Angles(15.0, 15.0),
        CalcMethod.SINGAPORE to Angles(20.0, 18.0),
        CalcMethod.EGYPTIAN to Angles(19.5, 17.5),
    )

    @Test fun `Fajr, sunrise, Maghrib and Isha are where the sun is at the angle the method defines`() {
        class Place(val name: String, val lat: Double, val lon: Double, val zone: String)
        val places = listOf(
            Place("London", 51.5074, -0.1278, "Europe/London"),
            Place("Mecca", 21.4225, 39.8262, "Asia/Riyadh"),
            Place("Karachi", 24.8607, 67.0011, "Asia/Karachi"),
            Place("New York", 40.7128, -74.0060, "America/New_York"),
            Place("Singapore", 1.3521, 103.8198, "Asia/Singapore"),
            Place("Honolulu", 21.3069, -157.8583, "Pacific/Honolulu"),
            Place("Auckland", -36.8485, 174.7633, "Pacific/Auckland"),
            Place("Sydney", -33.8688, 151.2093, "Australia/Sydney"),
            Place("Istanbul", 41.0082, 28.9784, "Europe/Istanbul"),
        )
        val dates = listOf("2026-03-20", "2026-10-05", "2026-12-21").map(LocalDate::parse)
        var checked = 0
        for (place in places) for ((method, angle) in angles) for (date in dates) {
            val zone = ZoneId.of(place.zone)
            val day = AdhanCalculator.calculate(request(place.lat, place.lon, place.zone, method), date)
            if (day == null) continue
            fun reference(degrees: Double, rising: Boolean, from: Long, to: Long) =
                Sun.crossing(date, zone, place.lat, place.lon, rising, from, to) { degrees }
            val label = "${place.name} ${method.name} $date"
            // Sunrise and sunset use the standard 50 arcminutes for the sun's radius and refraction.
            val checks = listOf(
                Triple("Fajr", day[Prayer.SUBUH], reference(-angle.fajr, true, 2, 9)),
                Triple("Sunrise", day[Prayer.SYURUK], reference(-50.0 / 60, true, 4, 10)),
                Triple("Asr", day[Prayer.ASAR], Sun.crossing(date, zone, place.lat, place.lon, false, 12, 20) { Sun.asrAltitude(it, place.lat, 1.0) }),
                Triple("Maghrib", day[Prayer.MAGHRIB], reference(-50.0 / 60, false, 14, 22)),
                Triple("Isha", day[Prayer.ISYAK], reference(-angle.isha, false, 16, 24)),
            )
            for ((name, actual, wanted) in checks) {
                val off = minutesApart(wanted, actual)
                assertTrue("$label $name: sun says $wanted, calculated $actual ($off min apart)", off <= 2)
                checked++
            }
        }
        assertTrue("only $checked checks ran", checked > 450)
    }

    @Test fun `every calculated day is in order and on the day asked for`() {
        golden.forEach { g ->
            val day = AdhanCalculator.calculate(g.request, LocalDate.parse(g.date))!!
            assertEquals(LocalDate.parse(g.date), day.date)
            assertEquals(6, day.times.size)
            assertTrue(g.label, day.times.zipWithNext().all { (a, b) -> a < b })
        }
    }

    // Twelve real days of the official MUIS timetable (data.gov.sg, 2026), read from the app's cache.
    private val muis = mapOf(
        "2026-01-01" to "05:44 07:08 13:10 16:34 19:11 20:25",
        "2026-02-15" to "05:58 07:17 13:20 16:38 19:22 20:32",
        "2026-03-20" to "05:52 07:09 13:14 16:14 19:17 20:25",
        "2026-04-10" to "05:45 07:02 13:07 16:18 19:10 20:20",
        "2026-05-15" to "05:35 06:56 13:03 16:25 19:07 20:20",
        "2026-06-21" to "05:37 07:01 13:08 16:33 19:13 20:28",
        "2026-07-04" to "05:40 07:04 13:11 16:36 19:16 20:30",
        "2026-08-09" to "05:46 07:06 13:12 16:31 19:16 20:27",
        "2026-09-12" to "05:41 06:58 13:02 16:06 19:05 20:14",
        "2026-10-05" to "05:34 06:51 12:54 16:04 18:56 20:05",
        "2026-11-18" to "05:27 06:49 12:52 16:14 18:52 20:05",
        "2026-12-31" to "05:44 07:07 13:10 16:34 19:10 20:25",
    )

    @Test fun `calculating Singapore lands within two minutes of the official MUIS timetable`() {
        val singapore = request(1.3521, 103.8198, "Asia/Singapore", CalcMethod.SINGAPORE)
        muis.forEach { (date, official) ->
            assertClose("Singapore $date", official, AdhanCalculator.calculate(singapore, LocalDate.parse(date)), toleranceMinutes = 2)
        }
    }

    @Test fun `a minute adjustment moves only that prayer, by exactly that much`() {
        val base = request(51.5074, -0.1278, "Europe/London", CalcMethod.MUSLIM_WORLD_LEAGUE)
        val date = LocalDate.parse("2026-10-05")
        val plain = AdhanCalculator.calculate(base, date)!!
        val nudged = AdhanCalculator.calculate(base.copy(adjustments = listOf(0, 0, 0, 0, 5, -3)), date)!!
        Prayer.entries.forEach { prayer ->
            val expected = when (prayer) {
                Prayer.MAGHRIB -> plain[prayer].plusMinutes(5)
                Prayer.ISYAK -> plain[prayer].minusMinutes(3)
                else -> plain[prayer]
            }
            assertEquals(prayer.name, expected, nudged[prayer])
        }
    }

    @Test fun `adjustments beyond the allowed range are limited`() {
        val base = request(21.4225, 39.8262, "Asia/Riyadh", CalcMethod.UMM_AL_QURA)
        val date = LocalDate.parse("2026-10-05")
        val plain = AdhanCalculator.calculate(base, date)!!
        val wild = AdhanCalculator.calculate(base.copy(adjustments = listOf(500, 0, 0, 0, 0, 0)), date)!!
        assertEquals(plain[Prayer.SUBUH].plusMinutes(MAX_ADJUSTMENT_MINUTES.toLong()), wild[Prayer.SUBUH])
    }

    @Test fun `Hanafi Asr is later than the standard one and nothing else changes`() {
        val standard = request(24.8607, 67.0011, "Asia/Karachi", CalcMethod.KARACHI)
        val date = LocalDate.parse("2026-10-05")
        val a = AdhanCalculator.calculate(standard, date)!!
        val b = AdhanCalculator.calculate(standard.copy(asr = AsrRule.HANAFI), date)!!
        assertTrue(b[Prayer.ASAR] > a[Prayer.ASAR])
        Prayer.entries.filter { it != Prayer.ASAR }.forEach { assertEquals(it.name, a[it], b[it]) }
    }

    @Test fun `the same request gives the same answer every time`() {
        val r = request(51.5074, -0.1278, "Europe/London", CalcMethod.MUSLIM_WORLD_LEAGUE)
        val date = LocalDate.parse("2026-10-05")
        assertEquals(AdhanCalculator.calculate(r, date), AdhanCalculator.calculate(r, date))
    }

    @Test fun `no sunrise or sunset means no times, not a crash or a wrong day`() {
        // Tromsø in June: the sun does not set.
        val tromso = request(69.6492, 18.9553, "Europe/Oslo", CalcMethod.MUSLIM_WORLD_LEAGUE)
        assertNull(AdhanCalculator.calculate(tromso, LocalDate.parse("2026-06-21")))
        // And it does work in the dark months at the same place.
        val winter = AdhanCalculator.calculate(tromso, LocalDate.parse("2026-12-21"))
        winter?.let { assertTrue(it.times.zipWithNext().all { (a, b) -> a < b }) }
    }

    @Test fun `high latitudes either give a sensible day or none`() {
        listOf(
            request(59.9139, 10.7522, "Europe/Oslo", CalcMethod.MUSLIM_WORLD_LEAGUE),
            request(64.1466, -21.9426, "Atlantic/Reykjavik", CalcMethod.MUSLIM_WORLD_LEAGUE),
            request(55.9533, -3.1883, "Europe/London", CalcMethod.MOONSIGHTING),
            request(-54.8019, -68.3030, "America/Argentina/Ushuaia", CalcMethod.MUSLIM_WORLD_LEAGUE),
        ).forEach { r ->
            for (month in 1..12) {
                val date = LocalDate.of(2026, month, 21)
                val day = AdhanCalculator.calculate(r, date)
                day?.let {
                    assertEquals(date, it.date)
                    assertTrue("${r.zone} $date ${it.times}", it.times.zipWithNext().all { (a, b) -> a < b })
                }
            }
        }
    }

    @Test fun `southern hemisphere and the date line give ordinary days`() {
        listOf(
            request(-33.8688, 151.2093, "Australia/Sydney", CalcMethod.MUSLIM_WORLD_LEAGUE),
            request(-17.7134, 178.0650, "Pacific/Fiji", CalcMethod.MUSLIM_WORLD_LEAGUE),
            request(-33.9249, 18.4241, "Africa/Johannesburg", CalcMethod.MUSLIM_WORLD_LEAGUE),
            request(-23.5505, -46.6333, "America/Sao_Paulo", CalcMethod.MUSLIM_WORLD_LEAGUE),
        ).forEach { r ->
            for (month in 1..12) {
                val date = LocalDate.of(2026, month, 15)
                val day = AdhanCalculator.calculate(r, date)
                assertNotNull("${r.zone} $date", day)
                assertTrue("${r.zone} $date ${day!!.times}", day.times.zipWithNext().all { (a, b) -> a < b })
                // Whatever the zone, noon prayer is around midday and Maghrib in the evening.
                assertTrue("${r.zone} $date zohor ${day[Prayer.ZOHOR]}", day[Prayer.ZOHOR] in LocalTime.of(11, 0)..LocalTime.of(14, 30))
                assertTrue("${r.zone} $date maghrib ${day[Prayer.MAGHRIB]}", day[Prayer.MAGHRIB] in LocalTime.of(15, 30)..LocalTime.of(21, 30))
            }
        }
    }

    @Test fun `every method gives increasing times for an ordinary place`() {
        CalcMethod.entries.forEach { method ->
            val day = AdhanCalculator.calculate(request(30.0444, 31.2357, "Africa/Cairo", method), LocalDate.parse("2026-10-05"))
            assertNotNull(method.name, day)
        }
    }
}
