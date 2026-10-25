package com.prayerwallpaper.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityIndexTest {
    // geonameid, name, asciiname, country, admin1, latitude, longitude, timezone, population. Biggest first, as the real file is.
    private val sample = """
        1796236	Shanghai	Shanghai	CN		31.22222	121.45806	Asia/Shanghai	22315474
        5128581	New York City	New York City	US	NY	40.71427	-74.00597	America/New_York	8175133
        2643743	London	London	GB	ENG	51.50853	-0.12574	Europe/London	7556900
        3448439	São Paulo	Sao Paulo	BR	27	-23.5475	-46.63611	America/Sao_Paulo	10021295
        1880252	Singapore	Singapore	SG	00	1.28967	103.85007	Asia/Singapore	3547809
        1735161	Kuala Lumpur	Kuala Lumpur	MY	14	3.1412	101.68653	Asia/Kuala_Lumpur	1453975
        1642911	Jakarta	Jakarta	ID	04	-6.21462	106.84513	Asia/Jakarta	8540121
        1174872	Karachi	Karachi	PK	05	24.8608	67.0104	Asia/Karachi	11624219
        104515	Mecca	Mecca	SA	14	21.42664	39.82728	Asia/Riyadh	1323624
        109223	Medina	Medina	SA	05	24.46861	39.61417	Asia/Riyadh	1300000
        3133895	Tromsø	Tromso	NO	19	69.6496	18.95508	Europe/Oslo	52436
        4409896	Springfield	Springfield	US	MO	37.21533	-93.29824	America/Chicago	166810
        4250542	Springfield	Springfield	US	IL	39.80172	-89.64371	America/Chicago	116250
        2655603	Birmingham	Birmingham	GB	ENG	52.48142	-1.89983	Europe/London	984333
        4049979	Birmingham	Birmingham	US	AL	33.52066	-86.80249	America/Chicago	212237
        2636482	Stoke-on-Trent	Stoke-on-Trent	GB	ENG	53.00415	-2.18538	Europe/London	372775
        9999991	Nowhere	Nowhere	ZZ		0.0	0.0	Not/AZone	1
        bad line
        9999992	NoLat	NoLat	GB		abc	0.0	Europe/London	1
    """.trimIndent().lines().filter { it.isNotBlank() }.joinToString("\n") { it.trim().replace(Regex(" {2,}"), "\t") }

    private val index = CityIndex.parse(sample.lineSequence().map { it })

    private fun names(query: String) = index.search(query).map { it.name }

    @Test fun `lines with a bad number, a short row or an unknown zone are skipped`() {
        assertEquals(16, index.size)
        assertEquals(2, index.skipped) // the unknown zone and the bad latitude; the short line is not a city row at all
        assertTrue(names("Nowhere").isEmpty())
        assertTrue(names("NoLat").isEmpty())
    }

    @Test fun `fields are read into the right places`() {
        val london = index.search("london").single()
        assertEquals(2643743L, london.id)
        assertEquals("GB", london.country)
        assertEquals("", london.region) // regions are shown for the US only
        assertEquals(51.50853, london.latitude, 1e-9)
        assertEquals(-0.12574, london.longitude, 1e-9)
        assertEquals("Europe/London", london.zoneId)
    }

    @Test fun `a prefix finds the city`() {
        assertEquals(listOf("Karachi"), names("kara"))
        assertEquals(listOf("Mecca"), names("mec"))
    }

    @Test fun `case and accents do not matter, in the query or the name`() {
        assertEquals(listOf("São Paulo"), names("sao"))
        assertEquals(listOf("São Paulo"), names("SÃO PAULO"))
        assertEquals(listOf("São Paulo"), names("sao paulo"))
        assertEquals(listOf("Tromsø"), names("tromso"))
        assertEquals(listOf("Tromsø"), names("Tromsø"))
    }

    @Test fun `a word in the middle of a name also matches, after the names that start with it`() {
        assertEquals(listOf("New York City"), names("york"))
        assertEquals(listOf("Kuala Lumpur"), names("lumpur"))
        assertEquals(listOf("Stoke-on-Trent"), names("trent"))
        // "ke" starts Stoke? no: starts "Karachi"? no. It only appears inside Stoke, which does not start a word.
        assertTrue(names("oke").isEmpty())
    }

    @Test fun `hyphens and extra spaces are the same as a space`() {
        assertEquals(listOf("Stoke-on-Trent"), names("stoke on trent"))
        assertEquals(listOf("Stoke-on-Trent"), names("  stoke   on-trent "))
    }

    @Test fun `only US states keep their region code`() {
        val mixed = CityIndex.parse(
            sequenceOf(
                "1\tSankt Gallen\tSankt Gallen\tCH\tSG\t47.42\t9.37\tEurope/Zurich\t75000\t",
                "2\tCardiff\tCardiff\tGB\tWLS\t51.48\t-3.18\tEurope/London\t340000\t",
                "3\tAustin\tAustin\tUS\tTX\t30.27\t-97.74\tAmerica/Chicago\t950000\t",
                "4\tToronto\tToronto\tCA\t08\t43.7\t-79.4\tAmerica/Toronto\t2700000\t",
            ),
        )
        assertEquals("", mixed.byId(1)!!.region) // a Swiss canton, "SG", must not look like Singapore
        assertEquals("", mixed.byId(2)!!.region) // Wales
        assertEquals("TX", mixed.byId(3)!!.region)
        assertEquals("", mixed.byId(4)!!.region) // Canada's numeric code
    }

    @Test fun `bigger cities come first and cities with the same name are all there`() {
        assertEquals(listOf("Birmingham", "Birmingham"), names("birm"))
        assertEquals(listOf("GB", "US"), index.search("birm").map { it.country })
        assertEquals(listOf("MO", "IL"), index.search("springfield").map { it.region })
    }

    @Test fun `names that start with the query come before names that only contain it as a later word`() {
        val mixed = CityIndex.parse(
            sequenceOf(
                "1\tNew Mecca\tNew Mecca\tSA\t\t21.0\t39.0\tAsia/Riyadh\t900000",
                "2\tMeccano\tMeccano\tSA\t\t22.0\t39.0\tAsia/Riyadh\t10",
            ),
        )
        assertEquals(listOf("Meccano", "New Mecca"), mixed.search("mecc").map { it.name })
    }

    @Test fun `aliases find a city under another name`() {
        val aliased = CityIndex.parse(
            sequenceOf(
                "104515\tMakkah\tMakkah\tSA\t\t21.4266\t39.8256\tAsia/Riyadh\t1578722\tMecca;Makka;Makkah al Mukarramah",
                "109223\tMadinah\tMadinah\tSA\t\t24.4686\t39.6142\tAsia/Riyadh\t1300000\tMedina;Al Madinah al Munawwarah",
                "4401982\tMedina\tMedina\tBR\t\t-16.2\t-41.5\tAmerica/Bahia\t20000\t",
            ),
        )
        assertEquals(listOf("Makkah"), aliased.search("mecca").map { it.name })
        assertEquals(listOf("Makkah"), aliased.search("MAKKA").map { it.name }.take(1))
        // The bigger city comes first even though the Brazilian one is called Medina.
        assertEquals(listOf("Madinah", "Medina"), aliased.search("medina").map { it.name })
        assertEquals(listOf("Madinah"), aliased.search("munawwarah").map { it.name }) // a later word of an alias
        assertEquals(1, aliased.search("makkah").size) // no duplicate when several of its names match
    }

    @Test fun `an apostrophe is not a word break`() {
        val ap = CityIndex.parse(
            sequenceOf(
                "107304\tHa'il\tHa'il\tSA\t\t27.5\t41.7\tAsia/Riyadh\t605930\t",
                "1\tO\u2019Fallon\tO'Fallon\tUS\tMO\t38.8\t-90.7\tAmerica/Chicago\t80000\t",
            ),
        )
        assertEquals(listOf("Ha'il"), ap.search("hail").map { it.name })
        assertEquals(listOf("Ha'il"), ap.search("ha'il").map { it.name })
        assertEquals(listOf("O\u2019Fallon"), ap.search("ofallon").map { it.name })
        assertEquals(listOf("O\u2019Fallon"), ap.search("o\u2019fallon").map { it.name })
    }

    @Test fun `an empty or symbol only query finds nothing`() {
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   ").isEmpty())
        assertTrue(index.search("---").isEmpty())
    }

    @Test fun `results are limited`() {
        val many = CityIndex.parse((1..100).asSequence().map { "$it\tTown $it\tTown $it\tGB\t\t51.0\t-1.0\tEurope/London\t${1000 - it}" })
        assertEquals(30, many.search("town").size)
        assertEquals(5, many.search("town", limit = 5).size)
    }

    @Test fun `the nearest city to a point`() {
        // A little north of Jurong, Singapore
        assertEquals("Singapore", index.nearest(1.35, 103.7)!!.place.name)
        // Near Johor, closer to Kuala Lumpur than to Jakarta
        assertEquals("Kuala Lumpur", index.nearest(2.9, 101.7)!!.place.name)
        // Hampstead, London
        val here = index.nearest(51.556, -0.178)!!
        assertEquals("London", here.place.name)
        assertTrue("about 8 km, was ${here.kilometres}", here.kilometres in 5.0..12.0)
    }

    @Test fun `nearest works across the date line and the poles`() {
        val line = CityIndex.parse(
            sequenceOf(
                "1\tSuva\tSuva\tFJ\t\t-18.14\t178.44\tPacific/Fiji\t90000",
                "2\tApia\tApia\tWS\t\t-13.83\t-171.76\tPacific/Apia\t37000",
            ),
        )
        // Just east of the date line, near Apia and also near Fiji: Apia is closer.
        assertEquals("Apia", line.nearest(-14.0, -175.0)!!.place.name)
        // Just west of the line
        assertEquals("Suva", line.nearest(-17.0, 179.9)!!.place.name)
        assertNotNull(line.nearest(-89.9, 0.0))
    }

    @Test fun `nearest on an empty list is null`() {
        assertNull(CityIndex.parse(emptySequence()).nearest(0.0, 0.0))
    }

    @Test fun `distance is right for known pairs`() {
        // London to Paris, about 344 km
        assertEquals(344.0, CityIndex.distanceKm(51.5074, -0.1278, 48.8566, 2.3522), 3.0)
        assertEquals(0.0, CityIndex.distanceKm(10.0, 10.0, 10.0, 10.0), 1e-9)
    }

    @Test fun `places can be found again by id`() {
        assertEquals("Karachi", index.byId(1174872)?.name)
        assertNull(index.byId(42))
    }

    @Test fun `the default place is Singapore on the official timetable`() {
        assertTrue(Place.SINGAPORE.usesMuis)
        assertEquals(1880252L, Place.SINGAPORE.id)
        assertEquals(index.byId(1880252)!!.zoneId, Place.SINGAPORE.zoneId)
        assertTrue(index.byId(2643743)?.usesMuis == false)
    }

    @Test fun `a place is shown with its region and country`() {
        assertEquals("Springfield, MO, United States", index.search("springfield").first().displayName())
        assertEquals("Karachi, Pakistan", index.search("karachi").single().displayName())
        assertEquals("Singapore, Singapore", Place.SINGAPORE.displayName())
    }
}
