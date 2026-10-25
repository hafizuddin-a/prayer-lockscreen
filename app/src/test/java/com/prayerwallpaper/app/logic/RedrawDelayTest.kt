package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RedrawDelayTest {
    private val now = Instant.parse("2026-10-05T04:55:00Z")

    @Test fun `waits until the boundary plus a little slack`() {
        assertEquals(60_000 + REDRAW_SLACK_MS, redrawDelayMs(now.plusSeconds(60), now))
    }

    @Test fun `a boundary already passed still waits the slack, never a negative delay`() {
        assertEquals(REDRAW_SLACK_MS, redrawDelayMs(now.minusSeconds(5), now))
        assertEquals(REDRAW_SLACK_MS, redrawDelayMs(now, now))
    }

    @Test fun `nothing due means no timer`() {
        assertNull(redrawDelayMs(null, now))
    }

    @Test fun `the next midnight in Singapore is 16 00 UTC`() {
        // 23:59:30 in Singapore on 5 October is 15:59:30 UTC.
        assertEquals(Instant.parse("2026-10-05T16:00:00Z"), nextMidnight(Instant.parse("2026-10-05T15:59:30Z"), SINGAPORE))
        // Exactly midnight asks for the following one, never "now".
        assertEquals(Instant.parse("2026-10-06T16:00:00Z"), nextMidnight(Instant.parse("2026-10-05T16:00:00Z"), SINGAPORE))
        assertEquals(Instant.parse("2026-10-05T16:00:00Z"), nextMidnight(Instant.parse("2026-10-05T04:00:00Z"), SINGAPORE))
    }

    @Test fun `midnight follows the zone it is asked for`() {
        // London is on BST in October: midnight 6 October is 23:00 UTC on the 5th.
        assertEquals(Instant.parse("2026-10-05T23:00:00Z"), nextMidnight(Instant.parse("2026-10-05T12:00:00Z"), java.time.ZoneId.of("Europe/London")))
    }

    @Test fun `the earlier of two boundaries wins, and a missing one is ignored`() {
        val a = Instant.parse("2026-10-05T20:05:00Z")
        val b = Instant.parse("2026-10-05T16:00:00Z")
        assertEquals(b, earliest(a, b))
        assertEquals(b, earliest(b, a))
        assertEquals(a, earliest(a, null))
        assertEquals(b, earliest(null, b))
        assertNull(earliest(null, null))
    }

    @Test fun `a prayer boundary after midnight is beaten by midnight, so the Hijri date changes on time`() {
        // Past Isyak the next change is tomorrow's Subuh, hours after midnight; midnight must still fire.
        val subuh = Instant.parse("2026-10-05T21:33:00Z") // 05:33 on 6 October in Singapore
        val midnight = nextMidnight(Instant.parse("2026-10-05T13:00:00Z"), SINGAPORE)
        assertEquals(midnight, earliest(subuh, midnight))
    }
}
