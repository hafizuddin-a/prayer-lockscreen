package com.prayerwallpaper.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class FakeableClockTest {
    private var nanos = 0L
    private val clock = FakeableClock { nanos }

    @Test fun `follows the system clock until a time is set`() {
        assertFalse(clock.isFake)
        val before = Instant.now()
        val read = clock.instant()
        assertTrue(!read.isBefore(before) && read.isBefore(before.plusSeconds(5)))
    }

    @Test fun `runs in real time from the fake instant`() {
        val start = Instant.parse("2026-10-05T04:55:00Z")
        clock.set(start)
        assertTrue(clock.isFake)
        assertEquals(start, clock.instant())
        nanos += 90_000_000_000L // 90 seconds pass
        assertEquals(start.plusSeconds(90), clock.instant())
    }

    @Test fun `setting again re-anchors and reset goes back to the system clock`() {
        clock.set(Instant.parse("2026-10-05T04:55:00Z"))
        nanos += 10_000_000_000L
        clock.set(Instant.parse("2026-10-06T00:00:00Z"))
        assertEquals(Instant.parse("2026-10-06T00:00:00Z"), clock.instant())
        clock.reset()
        assertFalse(clock.isFake)
    }

    @Test fun `has no zone of its own and can be viewed in any zone`() {
        assertEquals(ZoneOffset.UTC, clock.zone)
        for (id in listOf("Asia/Singapore", "Europe/London", "America/Los_Angeles")) {
            assertEquals(id, clock.withZone(ZoneId.of(id)).zone.id)
        }
    }

    @Test fun `a zoned view follows the fake instant and later changes to it`() {
        val london = clock.withZone(ZoneId.of("Europe/London"))
        clock.set(Instant.parse("2026-10-05T04:55:00Z"))
        assertEquals(Instant.parse("2026-10-05T04:55:00Z"), london.instant())
        nanos += 60_000_000_000L
        assertEquals(Instant.parse("2026-10-05T04:56:00Z"), london.instant())
        clock.set(Instant.parse("2026-10-06T00:00:00Z"))
        assertEquals(Instant.parse("2026-10-06T00:00:00Z"), london.instant())
        clock.reset()
        assertFalse(clock.isFake)
        assertEquals(london.instant().epochSecond.toDouble(), Instant.now().epochSecond.toDouble(), 5.0)
    }

    @Test fun `a view of a view still shares the anchor`() {
        val tokyo = clock.withZone(ZoneId.of("Asia/Tokyo")).withZone(ZoneId.of("Asia/Karachi"))
        clock.set(Instant.parse("2026-10-05T04:55:00Z"))
        assertEquals("Asia/Karachi", tokyo.zone.id)
        assertEquals(Instant.parse("2026-10-05T04:55:00Z"), tokyo.instant())
    }
}
