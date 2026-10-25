package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ReminderHistoryTest {
    private val t0 = Instant.parse("2026-10-05T04:00:00Z")
    private fun entry(minutes: Long, prayer: Prayer = Prayer.ZOHOR, kind: ReminderKind = ReminderKind.AT, method: AlarmMethod = AlarmMethod.EXACT) =
        HistoryEntry(t0.plus(Duration.ofMinutes(minutes)), kind, prayer, method)

    private fun fired(minutes: Long, lateSeconds: Long, outcome: ReminderOutcome = ReminderOutcome.FIRED): HistoryEntry {
        val e = entry(minutes)
        return e.copy(outcome = outcome, firedAt = e.plannedAt.plusSeconds(lateSeconds))
    }

    // --- planning ---

    @Test fun `a planned alarm starts pending with no delivery time`() {
        val h = ReminderHistory().planned(entry(60), now = t0)
        val e = h.entries.single()
        assertEquals(ReminderOutcome.PENDING, e.outcome)
        assertNull(e.firedAt)
        assertNull(e.latenessSeconds)
    }

    @Test fun `planning a new next alarm replaces the earlier plan that was not due yet`() {
        val h = ReminderHistory().planned(entry(60), t0).planned(entry(90, Prayer.ASAR), t0)
        assertEquals(listOf(Prayer.ASAR), h.entries.map { it.prayer })
    }

    @Test fun `an alarm already overdue is not dropped by the next plan`() {
        // Planned for t0+10 min, and it is now t0+15 min with no delivery: it may still arrive.
        val overdue = entry(10)
        val h = ReminderHistory(listOf(overdue)).planned(entry(60, Prayer.ASAR), now = t0.plusSeconds(15 * 60))
        assertEquals(listOf(Prayer.ZOHOR, Prayer.ASAR), h.entries.map { it.prayer })
    }

    @Test fun `planning the same alarm again does not duplicate it`() {
        val h = ReminderHistory().planned(entry(60), t0).planned(entry(60), t0)
        assertEquals(1, h.entries.size)
    }

    @Test fun `planning does not touch what has already been delivered`() {
        val delivered = fired(5, 3)
        val h = ReminderHistory(listOf(delivered)).planned(entry(60), now = t0.plusSeconds(600))
        assertEquals(2, h.entries.size)
        assertEquals(ReminderOutcome.FIRED, h.entries.first().outcome)
    }

    @Test fun `forgetting the future leaves the past alone`() {
        val h = ReminderHistory(listOf(fired(5, 3), entry(10), entry(120))).withoutFuturePending(now = t0.plusSeconds(15 * 60))
        assertEquals(2, h.entries.size) // the delivered one and the overdue one
        assertTrue(h.entries.none { it.plannedAt > t0.plusSeconds(15 * 60) })
    }

    // --- delivery ---

    @Test fun `delivery records the actual time and the lateness in seconds`() {
        val e = entry(60)
        val h = ReminderHistory().planned(e, t0).delivered(e, e.plannedAt.plusSeconds(4), ReminderOutcome.FIRED)
        val done = h.entries.single()
        assertEquals(ReminderOutcome.FIRED, done.outcome)
        assertEquals(4L, done.latenessSeconds)
    }

    @Test fun `lateness is never negative`() {
        val e = entry(60)
        val h = ReminderHistory().delivered(e, e.plannedAt.minusSeconds(2), ReminderOutcome.FIRED)
        assertEquals(0L, h.entries.single().latenessSeconds)
    }

    @Test fun `a delivery for an alarm the log never saw is still kept`() {
        val h = ReminderHistory().delivered(entry(60), t0.plusSeconds(3700), ReminderOutcome.STALE)
        assertEquals(ReminderOutcome.STALE, h.entries.single().outcome)
    }

    @Test fun `entries can be found by key`() {
        val e = entry(60)
        val h = ReminderHistory(listOf(e))
        assertEquals(e, h.find(e.key))
        assertNull(h.find("0/AT/ZOHOR"))
    }

    // --- missed detection ---

    @Test fun `an alarm just inside the stale limit is not missed yet`() {
        val h = ReminderHistory(listOf(entry(0))).markMissed(now = t0.plus(STALE_LIMIT))
        assertEquals(ReminderOutcome.PENDING, h.entries.single().outcome)
    }

    @Test fun `an alarm past the stale limit with no delivery is missed`() {
        val h = ReminderHistory(listOf(entry(0))).markMissed(now = t0.plus(STALE_LIMIT).plusSeconds(1))
        assertEquals(ReminderOutcome.MISSED, h.entries.single().outcome)
        assertNull(h.entries.single().firedAt)
    }

    @Test fun `an alarm that has not come due is never missed`() {
        val h = ReminderHistory(listOf(entry(120))).markMissed(now = t0)
        assertEquals(ReminderOutcome.PENDING, h.entries.single().outcome)
    }

    @Test fun `delivered alarms are never marked missed`() {
        val h = ReminderHistory(listOf(fired(0, 5), fired(1, 2000, ReminderOutcome.STALE), fired(2, 1, ReminderOutcome.BLOCKED)))
            .markMissed(now = t0.plus(Duration.ofDays(3)))
        assertEquals(listOf(ReminderOutcome.FIRED, ReminderOutcome.STALE, ReminderOutcome.BLOCKED), h.entries.map { it.outcome })
    }

    @Test fun `the stale limit for missing can be changed`() {
        val h = ReminderHistory(listOf(entry(0))).markMissed(now = t0.plusSeconds(61), limitFor = { Duration.ofMinutes(1) })
        assertEquals(ReminderOutcome.MISSED, h.entries.single().outcome)
    }

    // --- 14 days ---

    @Test fun `entries older than 14 days are dropped, newer ones kept`() {
        val now = t0.plus(Duration.ofDays(20))
        val old = entry(0)
        val edge = HistoryEntry(now.minus(Duration.ofDays(14)), ReminderKind.AT, Prayer.ASAR, AlarmMethod.EXACT)
        val recent = HistoryEntry(now.minus(Duration.ofDays(13)), ReminderKind.AT, Prayer.ISYAK, AlarmMethod.EXACT)
        val kept = ReminderHistory(listOf(old, edge, recent)).prune(now).entries
        assertEquals(listOf(Prayer.ASAR, Prayer.ISYAK), kept.map { it.prayer })
    }

    @Test fun `entries are always in time order`() {
        val h = ReminderHistory(listOf(entry(90, Prayer.ASAR), entry(30, Prayer.ZOHOR), entry(60, Prayer.MAGHRIB)))
        assertEquals(listOf(Prayer.ZOHOR, Prayer.MAGHRIB, Prayer.ASAR), h.entries.map { it.prayer })
    }

    // --- the summary ---

    @Test fun `an empty log has zero counts and no lateness`() {
        val s = ReminderHistory().summary()
        assertEquals(HistorySummary(0, 0, 0, 0, 0, null, null), s)
    }

    @Test fun `counts by outcome`() {
        val h = ReminderHistory(listOf(
            fired(0, 1), fired(1, 2), fired(2, 3),
            fired(3, 4, ReminderOutcome.BLOCKED), fired(4, 2500, ReminderOutcome.STALE),
            entry(5).copy(outcome = ReminderOutcome.MISSED), entry(6).copy(outcome = ReminderOutcome.MISSED), entry(1000),
        ))
        val s = h.summary()
        assertEquals(3, s.fired)
        assertEquals(1, s.blocked)
        assertEquals(1, s.stale)
        assertEquals(2, s.missed)
        assertEquals(1, s.pending)
    }

    @Test fun `median and worst lateness over what was delivered`() {
        val s = ReminderHistory(listOf(fired(0, 2), fired(1, 10), fired(2, 4), fired(3, 6), fired(4, 100))).summary()
        assertEquals(6L, s.medianLatenessSeconds) // 2 4 6 10 100
        assertEquals(100L, s.worstLatenessSeconds)
    }

    @Test fun `the median of an even number is the middle two averaged, rounded up`() {
        val s = ReminderHistory(listOf(fired(0, 2), fired(1, 5), fired(2, 8), fired(3, 20))).summary()
        assertEquals(7L, s.medianLatenessSeconds) // (5 + 8) / 2 = 6.5 -> 7
    }

    @Test fun `a single delivery is its own median and worst`() {
        val s = ReminderHistory(listOf(fired(0, 3))).summary()
        assertEquals(3L, s.medianLatenessSeconds)
        assertEquals(3L, s.worstLatenessSeconds)
    }

    @Test fun `missed and pending alarms have no lateness to count`() {
        val s = ReminderHistory(listOf(entry(0).copy(outcome = ReminderOutcome.MISSED), entry(500))).summary()
        assertNull(s.medianLatenessSeconds)
        assertNull(s.worstLatenessSeconds)
    }

    @Test fun `a stale alarm counts in the worst lateness, because it measures Android`() {
        val s = ReminderHistory(listOf(fired(0, 3), fired(1, 4000, ReminderOutcome.STALE))).summary()
        assertEquals(4000L, s.worstLatenessSeconds)
    }

    // --- the stale limit depends on how the alarm was set ---

    @Test fun `an inexact alarm is waited for 90 minutes before it is called missed, an exact one 30`() {
        val exact = entry(0, Prayer.ZOHOR, method = AlarmMethod.EXACT)
        val inexact = entry(0, Prayer.ASAR, method = AlarmMethod.INEXACT)
        val at45 = ReminderHistory(listOf(exact, inexact)).markMissed(now = t0.plus(Duration.ofMinutes(45)))
        assertEquals(ReminderOutcome.MISSED, at45.find(exact.key)!!.outcome)
        assertEquals(ReminderOutcome.PENDING, at45.find(inexact.key)!!.outcome)
        val at91 = ReminderHistory(listOf(inexact)).markMissed(now = t0.plus(Duration.ofMinutes(91)))
        assertEquals(ReminderOutcome.MISSED, at91.find(inexact.key)!!.outcome)
    }

    @Test fun `the limits are 30 and 90 minutes and the alarm clock method counts as exact`() {
        assertEquals(Duration.ofMinutes(30), staleLimitFor(AlarmMethod.EXACT))
        assertEquals(Duration.ofMinutes(30), staleLimitFor(AlarmMethod.ALARM_CLOCK))
        assertEquals(Duration.ofMinutes(90), staleLimitFor(AlarmMethod.INEXACT))
        // Longer than the one hour window Android allows an inexact alarm, so a reminder that arrived as promised is shown.
        assertTrue(STALE_LIMIT_INEXACT > Duration.ofHours(1))
    }

    // --- the test reminder is not part of the chain ---

    @Test fun `a planned test is not dropped when the next real alarm is planned`() {
        val test = entry(5, prayer = Prayer.ZOHOR, kind = ReminderKind.TEST).copy(prayer = null)
        val h = ReminderHistory(listOf(test)).planned(entry(60, Prayer.ASAR), now = t0)
        assertEquals(2, h.entries.size)
    }

    @Test fun `forgetting the future leaves a waiting test unless asked`() {
        val test = entry(5, kind = ReminderKind.TEST).copy(prayer = null)
        assertEquals(1, ReminderHistory(listOf(test, entry(60))).withoutFuturePending(now = t0).entries.size)
        assertTrue(ReminderHistory(listOf(test, entry(60))).withoutFuturePending(now = t0, includeTests = true).entries.isEmpty())
    }

    @Test fun `the prayer time is kept apart from the alarm time, and defaults to it`() {
        val pre = entry(30, kind = ReminderKind.PRE).copy(prayerAtOrNull = t0.plusSeconds(40 * 60))
        assertEquals(t0.plusSeconds(40 * 60), pre.prayerAt)
        assertEquals(entry(10).plannedAt, entry(10).prayerAt)
    }
}
