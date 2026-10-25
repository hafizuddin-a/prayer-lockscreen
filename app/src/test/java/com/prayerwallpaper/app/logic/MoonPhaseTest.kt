package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPhaseTest {
    private fun phases() = (1..30).map(::moonPhaseForHijriDay)

    @Test fun `the month begins with the new crescent and ends with the dark moon`() {
        assertEquals(MoonPhase.WAXING_CRESCENT, moonPhaseForHijriDay(1))
        assertEquals(MoonPhase.NEW, moonPhaseForHijriDay(29))
        assertEquals(MoonPhase.NEW, moonPhaseForHijriDay(30))
    }

    @Test fun `the moon is full in the middle of the month`() {
        for (day in 13..16) assertEquals("day $day", MoonPhase.FULL, moonPhaseForHijriDay(day))
        assertEquals(MoonPhase.FIRST_QUARTER, moonPhaseForHijriDay(7))
        assertEquals(MoonPhase.LAST_QUARTER, moonPhaseForHijriDay(22))
    }

    @Test fun `the phases go round in order through the month with no gaps`() {
        val order = listOf(
            MoonPhase.WAXING_CRESCENT, MoonPhase.FIRST_QUARTER, MoonPhase.WAXING_GIBBOUS, MoonPhase.FULL,
            MoonPhase.WANING_GIBBOUS, MoonPhase.LAST_QUARTER, MoonPhase.WANING_CRESCENT, MoonPhase.NEW,
        )
        assertEquals(order, phases().distinct())
    }

    @Test fun `every phase is used, and the day ranges are the documented ones`() {
        assertEquals(MoonPhase.entries.toSet(), phases().toSet())
        val runs = phases().groupingBy { it }.eachCount()
        assertEquals(5, runs[MoonPhase.WAXING_CRESCENT])
        assertEquals(4, runs[MoonPhase.FIRST_QUARTER])
        assertEquals(3, runs[MoonPhase.WAXING_GIBBOUS])
        assertEquals(4, runs[MoonPhase.FULL])
        assertEquals(4, runs[MoonPhase.WANING_GIBBOUS])
        assertEquals(3, runs[MoonPhase.LAST_QUARTER])
        assertEquals(4, runs[MoonPhase.WANING_CRESCENT])
        assertEquals(3, runs[MoonPhase.NEW])
    }

    @Test fun `waxing comes before full and waning after`() {
        val waxing = setOf(MoonPhase.WAXING_CRESCENT, MoonPhase.FIRST_QUARTER, MoonPhase.WAXING_GIBBOUS)
        val waning = setOf(MoonPhase.WANING_GIBBOUS, MoonPhase.LAST_QUARTER, MoonPhase.WANING_CRESCENT)
        (1..12).forEach { assertTrue("day $it", moonPhaseForHijriDay(it) in waxing) }
        (17..27).forEach { assertTrue("day $it", moonPhaseForHijriDay(it) in waning) }
    }

    @Test fun `a day outside the Hijri month is rejected, not drawn as something`() {
        assertThrows(IllegalArgumentException::class.java) { moonPhaseForHijriDay(0) }
        assertThrows(IllegalArgumentException::class.java) { moonPhaseForHijriDay(31) }
    }
}
