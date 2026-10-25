package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ReminderShareTest {
    private val sg = ZoneId.of("Asia/Singapore")
    private val now = Instant.parse("2026-10-07T04:00:00Z")
    private val header = listOf("App 1.5.0 (6), Android 16 (API 36), Google Pixel 9")

    private fun planned(h: Int, m: Int, prayer: Prayer, kind: ReminderKind = ReminderKind.AT, method: AlarmMethod = AlarmMethod.EXACT, outcome: ReminderOutcome, lateSeconds: Long? = null): HistoryEntry {
        val at = Instant.parse("2026-10-06T00:00:00Z").plusSeconds(h * 3600L + m * 60L)
        return HistoryEntry(at, kind, prayer, method, outcome, lateSeconds?.let { at.plusSeconds(it) })
    }

    @Test fun `it starts with the app and phone, the period and the summary`() {
        val text = historyShareText(header, ReminderHistory(), sg, NameSet.MALAY, now)
        val lines = text.lines()
        assertEquals("Prayer reminders: history", lines[0])
        assertEquals(header[0], lines[1])
        assertTrue(lines[2], lines[2].startsWith("Last 14 days up to Wed 7 Oct, 12:00 (Asia/Singapore)"))
        assertTrue(lines[3], lines[3].startsWith("Summary: 0 shown, 0 blocked, 0 too late, 0 missed, 0 waiting; no lateness recorded"))
    }

    @Test fun `an empty history says so`() {
        assertTrue(historyShareText(header, ReminderHistory(), sg, NameSet.MALAY, now).contains("(nothing recorded yet)"))
    }

    @Test fun `each alarm is a line of planned, alarm, method, outcome, arrival and lateness`() {
        val h = ReminderHistory(listOf(planned(21, 33, Prayer.SUBUH, outcome = ReminderOutcome.FIRED, lateSeconds = 3)))
        val line = historyShareText(header, h, sg, NameSet.MALAY, now).lines().single { it.contains("Subuh") }
        assertEquals("Wed 7 Oct, 05:33 | Subuh at the time | exact | fired | 05:33:03 | +3 s", line)
    }

    @Test fun `a heads-up, an inexact alarm and a test are told apart`() {
        val h = ReminderHistory(listOf(
            planned(4, 54, Prayer.ZOHOR, ReminderKind.PRE, AlarmMethod.INEXACT, ReminderOutcome.FIRED, 121),
            planned(8, 0, Prayer.ASAR, outcome = ReminderOutcome.PENDING).copy(prayer = null, kind = ReminderKind.TEST),
        ))
        val text = historyShareText(header, h, sg, NameSet.MALAY, now)
        assertTrue(text, text.contains("Zohor heads-up | inexact | fired | 12:56:01 | +2 min 1 s"))
        assertTrue(text, text.contains("| test | exact | pending | - | -"))
    }

    @Test fun `missed and blocked alarms have no arrival`() {
        val h = ReminderHistory(listOf(
            planned(10, 0, Prayer.ASAR, outcome = ReminderOutcome.MISSED),
            planned(11, 0, Prayer.ISYAK, outcome = ReminderOutcome.BLOCKED, lateSeconds = 2),
        ))
        val text = historyShareText(header, h, sg, NameSet.MALAY, now)
        assertTrue(text, text.contains("| missed | - | -"))
        assertTrue(text, text.contains("| blocked | 19:00:02 | +2 s"))
    }

    @Test fun `the summary line carries the numbers`() {
        val h = ReminderHistory(listOf(
            planned(1, 0, Prayer.SUBUH, outcome = ReminderOutcome.FIRED, lateSeconds = 2),
            planned(2, 0, Prayer.ZOHOR, outcome = ReminderOutcome.FIRED, lateSeconds = 4),
            planned(3, 0, Prayer.ASAR, outcome = ReminderOutcome.MISSED),
        ))
        val summary = historyShareText(header, h, sg, NameSet.MALAY, now).lines().single { it.startsWith("Summary") }
        assertEquals("Summary: 2 shown, 0 blocked, 0 too late, 1 missed, 0 waiting; median 3 s late, worst 4 s", summary)
    }

    @Test fun `the names follow the app's choice`() {
        val h = ReminderHistory(listOf(planned(5, 0, Prayer.SUBUH, outcome = ReminderOutcome.FIRED, lateSeconds = 1)))
        assertTrue(historyShareText(header, h, sg, NameSet.ENGLISH, now).contains("Fajr at the time"))
    }

    @Test fun `it holds no personal data, only the log and what the caller put in the header`() {
        val h = ReminderHistory(listOf(planned(5, 0, Prayer.SUBUH, outcome = ReminderOutcome.FIRED, lateSeconds = 1)))
        val text = historyShareText(header, h, sg, NameSet.MALAY, now)
        assertFalse(text.contains("@"))
        assertFalse(text.contains("latitude", ignoreCase = true))
    }

    @Test fun `how long ago is told in minutes, hours or days`() {
        assertEquals("5 min ago", formatAgo(now.minusSeconds(300), now))
        assertEquals("3 h ago", formatAgo(now.minusSeconds(3 * 3600 + 100), now))
        assertEquals("47 h ago", formatAgo(now.minusSeconds(47 * 3600), now))
        assertEquals("3 d ago", formatAgo(now.minusSeconds(3 * 86400), now))
        assertEquals("soon", formatAgo(now.plusSeconds(60), now))
    }
}
