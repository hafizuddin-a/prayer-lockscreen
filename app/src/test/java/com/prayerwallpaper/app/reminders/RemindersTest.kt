package com.prayerwallpaper.app.reminders

import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import com.prayerwallpaper.app.data.Timetable
import com.prayerwallpaper.app.data.WhenMissing
import com.prayerwallpaper.app.logic.AlarmMethod
import com.prayerwallpaper.app.logic.NextReminder
import com.prayerwallpaper.app.logic.ReminderAlarm
import com.prayerwallpaper.app.logic.ReminderKind
import com.prayerwallpaper.app.logic.ReminderOutcome
import com.prayerwallpaper.app.logic.STALE_LIMIT
import com.prayerwallpaper.app.logic.STALE_LIMIT_INEXACT
import com.prayerwallpaper.app.settings.AppSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** A phone's alarm service, in memory: remembers what was set, and can pretend the exact alarm permission is off. */
private class FakeGateway : AlarmGateway {
    var exactAllowed = true
    val set = mutableMapOf<String, Pair<ReminderAlarm, AlarmMethod>>()
    var scheduleCalls = 0
    var failExactOnce = false

    override fun canScheduleExact() = exactAllowed

    override fun schedule(alarm: ReminderAlarm, method: AlarmMethod): AlarmMethod {
        scheduleCalls++
        val used = if (method == AlarmMethod.EXACT && failExactOnce) AlarmMethod.INEXACT.also { failExactOnce = false } else method
        set[alarm.key] = alarm to used
        return used
    }

    override fun cancel(alarm: ReminderAlarm) {
        set.remove(alarm.key)
    }

    override fun exists(alarm: ReminderAlarm) = alarm.key in set
}

/** The notification service, in memory: remembers what was shown, and can pretend notifications or the channel are off. */
private class FakeNotifier : ReminderNotifier {
    var allowed = true
    val posted = mutableListOf<Triple<Int, com.prayerwallpaper.app.logic.ReminderContent, Boolean>>()
    override fun canPost(silent: Boolean) = allowed
    override fun post(id: Int, content: com.prayerwallpaper.app.logic.ReminderContent, silent: Boolean) {
        posted += Triple(id, content, silent)
    }
}

private class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone).let { fixed -> object : Clock() { override fun getZone() = zone; override fun withZone(z: ZoneId) = this; override fun instant() = this@MutableClock.now } }
    override fun instant(): Instant = now
}

class RemindersTest {
    private val sg = ZoneId.of("Asia/Singapore")
    private lateinit var dir: File
    private lateinit var gateway: FakeGateway
    private lateinit var clock: MutableClock
    private lateinit var settings: AppSettings
    private var redraws = 0
    private lateinit var notifier: FakeNotifier
    private var use24 = false
    private lateinit var reminders: Reminders

    private fun day(date: String, vararg times: String) = PrayerDay(LocalDate.parse(date), times.map(LocalTime::parse))
    private val days = listOf(
        day("2026-10-05", "05:33", "06:50", "12:54", "16:04", "18:56", "20:05"),
        day("2026-10-06", "05:33", "06:49", "12:53", "16:03", "18:55", "20:04"),
    ).associateBy { it.date }
    private var table: Map<LocalDate, PrayerDay> = days

    private fun at(date: String, time: String): Instant = ZonedDateTime.parse("${date}T$time+08:00[Asia/Singapore]").toInstant()

    @Before fun setUp() {
        dir = java.nio.file.Files.createTempDirectory("reminders").toFile()
        gateway = FakeGateway()
        notifier = FakeNotifier()
        use24 = false
        clock = MutableClock(at("2026-10-05", "13:00"))
        settings = AppSettings(remindersOn = true)
        redraws = 0
        reminders = Reminders(
            settings = { settings },
            timetable = { object : Timetable {
                override val zone = sg
                override val whenMissing = WhenMissing.LOAD
                override fun days(today: LocalDate) = table
            } },
            store = ReminderHistoryStore(File(dir, "history.json")),
            gateway = gateway, notifier = notifier, clock = clock, is24Hour = { use24 }, redraw = { redraws++ },
        )
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private fun scheduled() = gateway.set.values.map { it.first }.sortedBy { it.at }

    // --- planning ---

    @Test fun `turned on, the next alarm is set exactly when Android allows it`() {
        val state = reminders.ensureScheduled("test")
        assertTrue(state.set)
        assertEquals(AlarmMethod.EXACT, state.method)
        val a = scheduled().single()
        assertEquals(Prayer.ASAR, a.prayer)
        assertEquals(at("2026-10-05", "16:04"), a.at)
    }

    @Test fun `without the exact alarm permission it falls back to an inexact alarm`() {
        gateway.exactAllowed = false
        val state = reminders.ensureScheduled("test")
        assertEquals(AlarmMethod.INEXACT, state.method)
        assertEquals(AlarmMethod.INEXACT, gateway.set.values.single().second)
        assertEquals(AlarmMethod.INEXACT, reminders.history().entries.single().method)
    }

    @Test fun `if Android refuses the exact alarm at the last moment the inexact one is used and recorded`() {
        gateway.failExactOnce = true
        val state = reminders.ensureScheduled("test")
        assertEquals(AlarmMethod.INEXACT, state.method)
        assertEquals(AlarmMethod.INEXACT, reminders.history().entries.single().method)
    }

    @Test fun `turned off, nothing is set and anything set before is cancelled`() {
        reminders.ensureScheduled("on")
        assertEquals(1, gateway.set.size)
        settings = settings.copy(remindersOn = false)
        val state = reminders.ensureScheduled("off")
        assertEquals(NextReminder.Off, state.next)
        assertTrue(gateway.set.isEmpty())
        assertTrue(reminders.history().entries.none { it.outcome == ReminderOutcome.PENDING })
    }

    @Test fun `changing the pre-reminder replaces the alarm with the new one, and only one is ever set`() {
        reminders.ensureScheduled("on")
        settings = settings.copy(preReminderMinutes = 10)
        reminders.ensureScheduled("changed")
        val a = scheduled().single()
        assertEquals(ReminderKind.PRE, a.kind)
        assertEquals(at("2026-10-05", "15:54"), a.at)
        assertEquals(1, reminders.history().entries.size)
    }

    @Test fun `changing the chosen prayers replaces the alarm`() {
        reminders.ensureScheduled("on")
        settings = settings.copy(reminderPrayers = setOf(Prayer.ISYAK))
        reminders.ensureScheduled("changed")
        assertEquals(Prayer.ISYAK, scheduled().single().prayer)
    }

    @Test fun `with every prayer switched off nothing is set`() {
        settings = settings.copy(reminderPrayers = emptySet())
        val state = reminders.ensureScheduled("test")
        assertEquals(NextReminder.NothingSelected, state.next)
        assertTrue(gateway.set.isEmpty())
    }

    @Test fun `at the end of the data nothing is set`() {
        table = days.filterKeys { it == LocalDate.parse("2026-10-05") }
        clock.now = at("2026-10-05", "21:00")
        val state = reminders.ensureScheduled("test")
        assertEquals(NextReminder.NoData, state.next)
        assertTrue(gateway.set.isEmpty())
    }

    // --- being sure ---

    @Test fun `asked again when nothing changed, a forced call sets it again and an unforced one leaves it`() {
        reminders.ensureScheduled("first")
        assertEquals(1, gateway.scheduleCalls)
        reminders.ensureScheduled("again", force = false)
        assertEquals(1, gateway.scheduleCalls)
        reminders.ensureScheduled("forced")
        assertEquals(2, gateway.scheduleCalls)
    }

    @Test fun `if the pending intent has gone, even an unforced call sets the alarm again`() {
        reminders.ensureScheduled("first")
        gateway.set.clear()
        val state = reminders.ensureScheduled("again", force = false)
        assertTrue(state.set)
        assertEquals(1, gateway.set.size)
    }

    @Test fun `if the method that would be used has changed, an unforced call sets it again`() {
        reminders.ensureScheduled("first")
        gateway.exactAllowed = false
        val state = reminders.ensureScheduled("permission", force = false)
        assertTrue(state.set)
        assertEquals(AlarmMethod.INEXACT, state.method)
    }

    @Test fun `when the clock moves back the alarm for the earlier prayer is set`() {
        reminders.ensureScheduled("first")
        clock.now = at("2026-10-05", "02:00")
        reminders.ensureScheduled("time changed")
        assertEquals(Prayer.SUBUH, scheduled().single().prayer)
    }

    // --- an alarm arriving ---

    @Test fun `an alarm on time is recorded as fired with its lateness, and the next one is set`() {
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plusSeconds(3)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        val fired = reminders.history().entries.single { it.outcome == ReminderOutcome.FIRED }
        assertEquals(3L, fired.latenessSeconds)
        assertEquals(clock.now, fired.firedAt)
        assertEquals(Prayer.MAGHRIB, scheduled().single().prayer) // the chain goes on
        assertEquals(1, redraws)
    }

    @Test fun `the alarm that arrived has its pending intent released`() {
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertFalse(a.key in gateway.set)
    }

    @Test fun `an alarm just inside the stale limit is fired`() {
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plus(STALE_LIMIT)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertEquals(ReminderOutcome.FIRED, reminders.history().find(a.key.let { a.key })!!.outcome)
    }

    @Test fun `an alarm past the stale limit is stale, but the chain still goes on`() {
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plus(STALE_LIMIT).plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertEquals(ReminderOutcome.STALE, reminders.history().find(a.key)!!.outcome)
        assertEquals(Prayer.MAGHRIB, scheduled().single().prayer)
    }

    @Test fun `an alarm after Isyak chains on to tomorrow's first prayer`() {
        clock.now = at("2026-10-05", "19:00")
        reminders.ensureScheduled("on")
        val isyak = scheduled().single()
        assertEquals(Prayer.ISYAK, isyak.prayer)
        clock.now = isyak.at.plusSeconds(2)
        reminders.onAlarm(isyak.at, isyak.kind, isyak.prayer)
        val next = scheduled().single()
        assertEquals(Prayer.SUBUH, next.prayer)
        assertEquals(LocalDate.parse("2026-10-06"), next.date)
    }

    @Test fun `an alarm that arrives after reminders were turned off is ignored`() {
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        settings = settings.copy(remindersOn = false)
        clock.now = a.at.plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertTrue(reminders.history().entries.none { it.outcome == ReminderOutcome.FIRED })
        assertEquals(0, redraws)
    }

    @Test fun `six alarms in a row each record their own lateness`() {
        clock.now = at("2026-10-05", "00:00")
        settings = settings.copy(reminderPrayers = Prayer.entries.toSet())
        reminders.ensureScheduled("on")
        repeat(6) { i ->
            val a = scheduled().single()
            clock.now = a.at.plusSeconds(i + 1L)
            reminders.onAlarm(a.at, a.kind, a.prayer)
        }
        val summary = reminders.history().summary()
        assertEquals(6, summary.fired)
        assertEquals(6L, summary.worstLatenessSeconds)
        assertEquals(4L, summary.medianLatenessSeconds) // 1 2 3 4 5 6 -> (3 + 4 + 1) / 2
    }

    // --- missed ---

    @Test fun `an alarm that never arrived is marked missed by the next check, once it is past the stale limit`() {
        reminders.ensureScheduled("on")
        val lost = scheduled().single()
        // The alarm vanished: it never arrives. Just inside the limit it is still waited for...
        gateway.set.clear()
        clock.now = lost.at.plus(STALE_LIMIT)
        reminders.ensureScheduled("later")
        assertEquals(ReminderOutcome.PENDING, reminders.history().find(lost.key)!!.outcome)
        // ...and past it, it is missed.
        clock.now = lost.at.plus(STALE_LIMIT).plusSeconds(1)
        reminders.ensureScheduled("later still")
        assertEquals(ReminderOutcome.MISSED, reminders.history().find(lost.key)!!.outcome)
        assertEquals(1, reminders.history().summary().missed)
    }

    @Test fun `the chain recovers from a lost alarm by setting the next one`() {
        reminders.ensureScheduled("on")
        val lost = scheduled().single()
        gateway.set.clear()
        clock.now = lost.at.plusSeconds(2 * 3600) // 18:04, before Maghrib
        val state = reminders.ensureScheduled("daily job")
        assertTrue(state.set)
        assertEquals(Prayer.MAGHRIB, scheduled().single().prayer)
    }

    @Test fun `an overdue alarm is not cancelled when the next one is set`() {
        reminders.ensureScheduled("on")
        val overdue = scheduled().single()
        clock.now = overdue.at.plusSeconds(60) // late, not yet delivered
        reminders.ensureScheduled("check")
        assertTrue("the overdue alarm can still arrive", overdue.key in gateway.set)
        assertEquals(2, gateway.set.size)
    }

    // --- the log ---

    @Test fun `the log survives the process being killed`() {
        reminders.ensureScheduled("on")
        val again = Reminders(
            settings = { settings }, timetable = { object : Timetable {
                override val zone = sg
                override val whenMissing = WhenMissing.LOAD
                override fun days(today: LocalDate) = table
            } },
            store = ReminderHistoryStore(File(dir, "history.json")), gateway = gateway, clock = clock,
        )
        assertEquals(1, again.history().entries.size)
    }

    @Test fun `a damaged log reads as empty instead of crashing`() {
        File(dir, "history.json").writeText("{ not json")
        assertTrue(ReminderHistoryStore(File(dir, "history.json")).read().entries.isEmpty())
    }

    @Test fun `entries with names from a newer version are skipped, not guessed`() {
        File(dir, "history.json").writeText(
            """{"version":2,"entries":[{"plannedAtEpochMs":1,"kind":"AT","prayer":"ZOHOR","method":"EXACT","outcome":"PENDING"},""" +
                """{"plannedAtEpochMs":2,"kind":"SIREN","prayer":"ZOHOR","method":"EXACT","outcome":"PENDING"}]}""",
        )
        assertEquals(1, ReminderHistoryStore(File(dir, "history.json")).read().entries.size)
    }

    @Test fun `old entries are pruned when the schedule is checked`() {
        reminders.ensureScheduled("on")
        clock.now = clock.now.plusSeconds(15L * 24 * 3600)
        table = emptyMap()
        reminders.ensureScheduled("much later")
        assertNull(reminders.history().entries.firstOrNull { it.plannedAt < clock.now.minusSeconds(14L * 24 * 3600) })
    }

    @Test fun `turning everything off forgets plans that were waiting and cancels their alarms`() {
        reminders.ensureScheduled("on")
        reminders.cancelAll()
        assertTrue(gateway.set.isEmpty())
        assertTrue(reminders.history().entries.isEmpty())
    }

    // --- what is shown ---

    @Test fun `an alarm on time shows the prayer's name and time`() {
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plusSeconds(2)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        val shown = notifier.posted.single()
        assertEquals("Asar", shown.second.title)
        assertEquals("4:04 pm", shown.second.text)
        assertFalse("not silent unless chosen", shown.third)
    }

    @Test fun `a heads-up shows how long to go and the prayer's time`() {
        settings = settings.copy(preReminderMinutes = 10)
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertEquals("Asar in 10 minutes", notifier.posted.single().second.title)
        assertEquals("4:04 pm", notifier.posted.single().second.text)
    }

    @Test fun `the heads-up says the size it was planned with, even if the setting changed since`() {
        settings = settings.copy(preReminderMinutes = 15)
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        settings = settings.copy(preReminderMinutes = 5)
        clock.now = a.at.plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertEquals("Asar in 15 minutes", notifier.posted.single().second.title)
    }

    @Test fun `the time follows the phone's 24 hour setting`() {
        use24 = true
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertEquals("16:04", notifier.posted.single().second.text)
    }

    @Test fun `on a Friday the noon prayer is named Jumaat, or Jumuah in English`() {
        // 9 October 2026 is a Friday.
        table = mapOf(LocalDate.parse("2026-10-09") to day("2026-10-09", "05:33", "06:49", "12:53", "16:03", "18:55", "20:04"), LocalDate.parse("2026-10-10") to day("2026-10-10", "05:33", "06:49", "12:53", "16:03", "18:55", "20:04"))
        clock.now = at("2026-10-09", "10:00")
        settings = settings.copy(reminderPrayers = setOf(Prayer.ZOHOR))
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertEquals("Jumaat", notifier.posted.single().second.title)
    }

    @Test fun `silent reminders go to the silent channel`() {
        settings = settings.copy(silentReminders = true)
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertTrue(notifier.posted.single().third)
    }

    @Test fun `each alarm gets a notification of its own, none replaces another`() {
        clock.now = at("2026-10-05", "00:00")
        reminders.ensureScheduled("on")
        repeat(3) {
            val a = scheduled().single()
            clock.now = a.at.plusSeconds(1)
            reminders.onAlarm(a.at, a.kind, a.prayer)
        }
        assertEquals(3, notifier.posted.map { it.first }.toSet().size)
    }

    // --- blocked and stale ---

    @Test fun `with notifications off nothing is shown and the alarm is logged as blocked`() {
        notifier.allowed = false
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plusSeconds(2)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertTrue(notifier.posted.isEmpty())
        val e = reminders.history().find(a.key)!!
        assertEquals(ReminderOutcome.BLOCKED, e.outcome)
        assertEquals(2L, e.latenessSeconds)
        assertEquals("the chain goes on", Prayer.MAGHRIB, scheduled().single().prayer)
        assertEquals(1, reminders.history().summary().blocked)
    }

    @Test fun `a stale exact alarm is not shown`() {
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plus(STALE_LIMIT).plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertTrue(notifier.posted.isEmpty())
        assertEquals(ReminderOutcome.STALE, reminders.history().find(a.key)!!.outcome)
    }

    @Test fun `an inexact alarm 45 minutes late is still shown, because Android may take up to an hour`() {
        gateway.exactAllowed = false
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plusSeconds(45 * 60)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertEquals(1, notifier.posted.size)
        assertEquals(ReminderOutcome.FIRED, reminders.history().find(a.key)!!.outcome)
    }

    @Test fun `an inexact alarm just past 90 minutes is stale`() {
        gateway.exactAllowed = false
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plus(STALE_LIMIT_INEXACT).plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertTrue(notifier.posted.isEmpty())
        assertEquals(ReminderOutcome.STALE, reminders.history().find(a.key)!!.outcome)
    }

    @Test fun `stale wins over blocked, a reminder too late is not shown either way`() {
        notifier.allowed = false
        reminders.ensureScheduled("on")
        val a = scheduled().single()
        clock.now = a.at.plus(STALE_LIMIT).plusSeconds(1)
        reminders.onAlarm(a.at, a.kind, a.prayer)
        assertEquals(ReminderOutcome.STALE, reminders.history().find(a.key)!!.outcome)
    }

    // --- the test reminder ---

    @Test fun `the test reminder is a real alarm a minute away, set by the same path`() {
        val t = reminders.scheduleTest()
        assertEquals(ReminderKind.TEST, t.kind)
        assertEquals(clock.now.plusSeconds(60), t.at)
        assertEquals(AlarmMethod.EXACT, gateway.set.values.single().second)
        assertEquals(ReminderOutcome.PENDING, reminders.history().find(t.key)!!.outcome)
    }

    @Test fun `the test works with reminders off, shows its own words and does not start the chain`() {
        settings = settings.copy(remindersOn = false)
        val t = reminders.scheduleTest()
        clock.now = t.at.plusSeconds(1)
        reminders.onAlarm(t.at, t.kind, t.prayer)
        assertEquals(com.prayerwallpaper.app.logic.TEST_TITLE, notifier.posted.single().second.title)
        assertEquals(ReminderOutcome.FIRED, reminders.history().find(t.key)!!.outcome)
        assertTrue("reminders are off, so no next alarm", gateway.set.isEmpty())
    }

    @Test fun `a waiting test survives the schedule being checked`() {
        val t = reminders.scheduleTest()
        reminders.ensureScheduled("on")
        reminders.ensureScheduled("again")
        assertTrue(t.key in gateway.set)
    }

    @Test fun `resetting cancels a waiting test too`() {
        val t = reminders.scheduleTest()
        reminders.ensureScheduled("on")
        reminders.cancelAll()
        assertTrue(gateway.set.isEmpty())
        assertFalse(t.key in gateway.set)
    }

    // --- the throttle ---

    @Test fun `a throttled check runs at most once in 30 minutes and never while reminders are off`() {
        reminders.ensureScheduledThrottled("visible")
        assertEquals(1, gateway.scheduleCalls)
        gateway.set.clear()
        clock.now = clock.now.plusSeconds(29 * 60)
        reminders.ensureScheduledThrottled("visible")
        assertEquals("still inside 30 minutes", 1, gateway.scheduleCalls)
        clock.now = clock.now.plusSeconds(2 * 60)
        reminders.ensureScheduledThrottled("visible")
        assertEquals("after 30 minutes it checks, and finds the alarm gone", 2, gateway.scheduleCalls)
        settings = settings.copy(remindersOn = false)
        clock.now = clock.now.plusSeconds(3600)
        reminders.ensureScheduledThrottled("visible")
        assertEquals(2, gateway.scheduleCalls)
    }
}
