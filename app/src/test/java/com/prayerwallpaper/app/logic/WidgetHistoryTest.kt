package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class WidgetHistoryTest {
    private val sg = ZoneId.of("Asia/Singapore")
    private val noon = Instant.parse("2026-10-07T04:54:00Z") // 12:54 in Singapore, Zohor
    private fun at(seconds: Long) = noon.plusSeconds(seconds)

    @Test fun `the first update ever records nothing, there was no earlier picture`() {
        val h = WidgetHistory().afterUpdate(at(5), noon, "onUpdate")
        assertEquals(at(5), h.lastUpdate)
        assertTrue(h.entries.isEmpty())
    }

    @Test fun `an update after a boundary is recorded with its lateness`() {
        val h = WidgetHistory(lastUpdate = at(-3600)).afterUpdate(at(42), noon, "boundary")
        assertEquals(listOf(WidgetUpdateEntry(noon, at(42), "boundary")), h.entries)
        assertEquals(42, h.entries.single().latenessSeconds)
    }

    @Test fun `only the first update after a boundary counts for it`() {
        var h = WidgetHistory(lastUpdate = at(-3600)).afterUpdate(at(20), noon, "boundary")
        h = h.afterUpdate(at(130), noon, "safety")
        h = h.afterUpdate(at(900), noon, "visible")
        assertEquals(1, h.entries.size)
        assertEquals("boundary", h.entries.single().reason)
    }

    @Test fun `an update that is not after a new boundary records nothing`() {
        val h = WidgetHistory(lastUpdate = at(10)).afterUpdate(at(600), noon, "settings")
        assertTrue(h.entries.isEmpty())
        assertEquals(at(600), h.lastUpdate)
    }

    @Test fun `a boundary caught up by some other update, such as the wallpaper becoming visible, is recorded with that reason`() {
        val h = WidgetHistory(lastUpdate = at(-3600)).afterUpdate(at(300), noon, "visible")
        assertEquals("visible", h.entries.single().reason)
    }

    @Test fun `median and worst are over the entries that say something about alarms`() {
        val entries = listOf(10L, 20L, 30L, 4000L).map { WidgetUpdateEntry(noon, noon.plusSeconds(it), "boundary") }
        val h = WidgetHistory(at(0), entries)
        assertEquals(WidgetSummary(4, 25, 4000), h.summary())
    }

    @Test fun `an odd count has a middle one`() {
        val h = WidgetHistory(at(0), listOf(5L, 90L, 7L).map { WidgetUpdateEntry(noon, noon.plusSeconds(it), "boundary") })
        assertEquals(7L, h.summary().medianSeconds)
        assertEquals(90L, h.summary().worstSeconds)
    }

    @Test fun `an update after the phone was off or the app replaced is listed but not counted`() {
        val h = WidgetHistory(at(0), listOf(
            WidgetUpdateEntry(noon, noon.plusSeconds(8 * 3600), "boot"),
            WidgetUpdateEntry(noon, noon.plusSeconds(15), "boundary"),
        ))
        assertEquals(WidgetSummary(1, 15, 15), h.summary())
        assertEquals(2, h.entries.size)
    }

    @Test fun `no entries means no median`() {
        assertEquals(WidgetSummary(0, null, null), WidgetHistory().summary())
    }

    @Test fun `entries older than 14 days are dropped`() {
        val old = WidgetUpdateEntry(noon.minus(Duration.ofDays(15)), noon.minus(Duration.ofDays(15)).plusSeconds(5), "boundary")
        val recent = WidgetUpdateEntry(noon.minus(Duration.ofDays(3)), noon.minus(Duration.ofDays(3)).plusSeconds(5), "boundary")
        val h = WidgetHistory(at(-60), listOf(old, recent)).afterUpdate(at(0), noon.minusSeconds(60), "settings")
        assertEquals(listOf(recent), h.entries)
    }

    @Test fun `lateness is never negative`() {
        assertEquals(0, WidgetUpdateEntry(at(10), at(5), "boundary").latenessSeconds)
    }

    // --- the share text ---

    @Test fun `the share text has the summary and one line per boundary`() {
        val h = WidgetHistory(at(100), listOf(WidgetUpdateEntry(noon, at(42), "boundary"), WidgetUpdateEntry(noon.plusSeconds(3600), at(3600 + 8 * 3600), "boot")))
        val text = widgetShareText(listOf("App 1.6.0"), h, at(7200), sg, at(200))
        assertTrue(text.startsWith("Prayer widgets: update history\nApp 1.6.0\n"))
        assertTrue(text, "Summary: 1 boundaries; median 42 s late, worst 42 s" in text)
        assertTrue(text, "Wed 7 Oct, 12:54 | 12:54:42 | +42 s | boundary" in text)
        assertTrue("(not counted)" in text)
        assertTrue("Last update: Wed 7 Oct, 12:55" in text)
        assertTrue("next boundary: Wed 7 Oct, 14:54" in text)
    }

    @Test fun `an empty history says so`() {
        val text = widgetShareText(emptyList(), WidgetHistory(), null, sg, noon)
        assertTrue("(nothing recorded yet)" in text)
        assertTrue("Last update: never; next boundary: none" in text)
        assertTrue("no lateness recorded" in text)
        assertNull(WidgetHistory().summary().medianSeconds)
    }
}

class WidgetHistoryEnvTest {
    private val noon = Instant.parse("2026-10-07T04:54:00Z")

    @Test fun `the environment key is kept from one update to the next unless a new one is given`() {
        val first = WidgetHistory().afterUpdate(noon, noon, "onUpdate", "1.0|en|false|420")
        assertEquals("1.0|en|false|420", first.envKey)
        assertEquals("1.0|en|false|420", first.afterUpdate(noon.plusSeconds(60), noon, "settings").envKey)
        assertEquals("1.3|en|false|420", first.afterUpdate(noon.plusSeconds(60), noon, "configuration changed", "1.3|en|false|420").envKey)
    }
}
