package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReminderHealthTest {
    private val sg = ZoneId.of("Asia/Singapore")
    private val now = Instant.parse("2026-10-07T04:00:00Z") // 12:00 on 7 October in Singapore
    private val alarm = ReminderAlarm(ReminderKind.AT, Prayer.ASAR, LocalDate.parse("2026-10-07"), Instant.parse("2026-10-07T08:04:00Z"))

    private fun good() = HealthInput(
        remindersOn = true, notificationsAllowed = true, channelImportance = 4, silentReminders = false,
        exactAlarmsInBuild = true, exactAllowed = true,
        next = NextReminder.Due(alarm), nextMethod = AlarmMethod.EXACT, nextPendingIntentExists = true,
        batteryOptimised = false, backgroundRestricted = false, standbyBucket = StandbyBucket.ACTIVE,
        data = DataHealth(true, Instant.parse("2026-10-05T05:40:00Z"), LocalDate.parse("2026-12-31")),
        history = ReminderHistory(),
    )

    private fun report(input: HealthInput) = healthReport(input, now, sg)
    private fun row(r: HealthReport, id: HealthRowId) = r.rows.single { it.id == id }

    private fun entry(minutesAgo: Long, outcome: ReminderOutcome, lateSeconds: Long? = null, method: AlarmMethod = AlarmMethod.EXACT): HistoryEntry {
        val planned = now.minus(Duration.ofMinutes(minutesAgo))
        return HistoryEntry(planned, ReminderKind.AT, Prayer.ZOHOR, method, outcome, lateSeconds?.let { planned.plusSeconds(it) })
    }

    // --- the one-line status ---

    @Test fun `everything fine is All good`() {
        val r = report(good())
        assertEquals(ReminderStatus(true, "All good"), r.status)
        assertTrue(r.rows.all { it.level == HealthLevel.OK })
    }

    @Test fun `reminders off says Off, whatever else is wrong`() {
        assertEquals("Off", report(good().copy(remindersOn = false, notificationsAllowed = false)).status.text)
    }

    @Test fun `notifications off needs attention and is named first`() {
        val r = report(good().copy(notificationsAllowed = false, exactAllowed = false))
        assertFalse(r.status.allGood)
        assertEquals("Needs attention: notifications are off", r.status.text)
        assertEquals(HealthLevel.PROBLEM, row(r, HealthRowId.NOTIFICATIONS).level)
        assertEquals(HealthFix.NOTIFICATION_SETTINGS, row(r, HealthRowId.NOTIFICATIONS).fix)
    }

    @Test fun `a channel the user switched off is a problem too`() {
        val r = report(good().copy(channelImportance = 0))
        assertEquals(HealthLevel.PROBLEM, row(r, HealthRowId.NOTIFICATIONS).level)
        assertTrue(row(r, HealthRowId.NOTIFICATIONS).detail.contains("switched off"))
        assertFalse(r.status.allGood)
    }

    @Test fun `a channel set to low importance is a warning that needs attention`() {
        val r = report(good().copy(channelImportance = 2))
        assertEquals(HealthLevel.WARNING, row(r, HealthRowId.NOTIFICATIONS).level)
        assertEquals("Needs attention: notifications are muted", r.status.text)
    }

    @Test fun `a channel not created yet is fine`() {
        assertTrue(report(good().copy(channelImportance = null)).status.allGood)
    }

    @Test fun `exact alarms not allowed is named, with the hour of lateness`() {
        val r = report(good().copy(exactAllowed = false))
        assertEquals(HealthLevel.WARNING, row(r, HealthRowId.EXACT_ALARMS).level)
        assertEquals(HealthFix.EXACT_ALARM_SETTINGS, row(r, HealthRowId.EXACT_ALARMS).fix)
        assertTrue(r.status.text.startsWith("Needs attention: exact alarms are not allowed"))
        assertTrue(r.status.text.contains("up to an hour late"))
    }

    @Test fun `a build without exact alarms is approximate by design, with no fix button and no complaint`() {
        val r = report(good().copy(exactAlarmsInBuild = false, exactAllowed = false, nextMethod = AlarmMethod.INEXACT))
        val e = row(r, HealthRowId.EXACT_ALARMS)
        assertEquals(HealthLevel.OK, e.level)
        assertNull(e.fix)
        assertTrue(e.detail.startsWith("Approximate times"))
        assertTrue(r.status.allGood)
    }

    @Test fun `the first problem in the screen's order is the one named`() {
        val r = report(good().copy(exactAllowed = false, backgroundRestricted = true))
        assertTrue(r.status.text.contains("exact alarms"))
    }

    // --- the next alarm ---

    @Test fun `the next alarm is described with the prayer, time, kind and method`() {
        val d = row(report(good()), HealthRowId.NEXT_ALARM).detail
        assertTrue(d, d.startsWith("Asar Wed 7 Oct, 16:04, at the time, exact."))
    }

    @Test fun `a heads-up and an inexact alarm are described as such`() {
        val pre = alarm.copy(kind = ReminderKind.PRE)
        val d = row(report(good().copy(next = NextReminder.Due(pre), nextMethod = AlarmMethod.INEXACT)), HealthRowId.NEXT_ALARM).detail
        assertTrue(d, d.contains("heads-up, approximate."))
    }

    @Test fun `an alarm whose pending intent is gone is a problem with a reset button`() {
        val r = report(good().copy(nextPendingIntentExists = false))
        val n = row(r, HealthRowId.NEXT_ALARM)
        assertEquals(HealthLevel.PROBLEM, n.level)
        assertEquals(HealthFix.RESET_REMINDERS, n.fix)
        assertEquals("Needs attention: the next reminder is not set", r.status.text)
    }

    @Test fun `the end of the data is a warning that does not change the status`() {
        val r = report(good().copy(next = NextReminder.NoData, nextMethod = null))
        assertEquals(HealthLevel.WARNING, row(r, HealthRowId.NEXT_ALARM).level)
        assertTrue(r.status.allGood)
    }

    @Test fun `no prayer chosen is a warning`() {
        assertEquals(HealthLevel.WARNING, row(report(good().copy(next = NextReminder.NothingSelected, nextMethod = null)), HealthRowId.NEXT_ALARM).level)
    }

    // --- battery ---

    @Test fun `battery optimisation or a rare bucket is only a warning`() {
        assertEquals(HealthLevel.WARNING, row(report(good().copy(batteryOptimised = true)), HealthRowId.BATTERY).level)
        assertEquals(HealthLevel.WARNING, row(report(good().copy(standbyBucket = StandbyBucket.RARE)), HealthRowId.BATTERY).level)
        assertTrue(report(good().copy(batteryOptimised = true)).status.allGood)
    }

    @Test fun `restricted background activity is a problem`() {
        val r = report(good().copy(backgroundRestricted = true))
        assertEquals(HealthLevel.PROBLEM, row(r, HealthRowId.BATTERY).level)
        assertEquals(HealthFix.BATTERY_SETTINGS, row(r, HealthRowId.BATTERY).fix)
        assertFalse(r.status.allGood)
    }

    @Test fun `what Android could not say is shown as unknown, never as fine`() {
        val d = row(report(good().copy(batteryOptimised = null, backgroundRestricted = null, standbyBucket = null)), HealthRowId.BATTERY).detail
        assertTrue(d, d.contains("unknown"))
    }

    @Test fun `the standby bucket is named`() {
        assertEquals("Active", StandbyBucket.name(10))
        assertEquals("Working set", StandbyBucket.name(20))
        assertEquals("Frequent", StandbyBucket.name(30))
        assertEquals("Rare", StandbyBucket.name(40))
        assertEquals("Restricted", StandbyBucket.name(45))
        assertEquals("unknown", StandbyBucket.name(null))
    }

    // --- data ---

    @Test fun `data that covers well ahead is fine, with when it was fetched`() {
        val d = row(report(good()), HealthRowId.DATA).detail
        assertTrue(d, d.contains("Last downloaded") && d.contains("2026-12-31"))
    }

    @Test fun `data about to run out, or missing, is a warning`() {
        assertEquals(HealthLevel.WARNING, row(report(good().copy(data = DataHealth(true, now, LocalDate.parse("2026-10-07")))), HealthRowId.DATA).level)
        assertEquals(HealthLevel.WARNING, row(report(good().copy(data = DataHealth(true, null, null))), HealthRowId.DATA).level)
    }

    @Test fun `a calculated place has nothing to download`() {
        val r = row(report(good().copy(data = DataHealth(false, null, null))), HealthRowId.DATA)
        assertEquals(HealthLevel.OK, r.level)
        assertTrue(r.detail.contains("Calculated"))
    }

    // --- history ---

    @Test fun `an empty history says so`() {
        assertEquals("Nothing recorded yet.", row(report(good()), HealthRowId.HISTORY).detail)
    }

    @Test fun `a miss in the last 7 days is a problem and changes the status`() {
        val r = report(good().copy(history = ReminderHistory(listOf(entry(60, ReminderOutcome.MISSED)))))
        assertEquals(HealthLevel.PROBLEM, row(r, HealthRowId.HISTORY).level)
        assertEquals("Needs attention: a reminder never arrived", r.status.text)
    }

    @Test fun `a miss older than 7 days no longer counts against the status`() {
        val old = entry(8 * 24 * 60, ReminderOutcome.MISSED)
        val r = report(good().copy(history = ReminderHistory(listOf(old))))
        assertTrue(r.status.allGood)
    }

    @Test fun `blocked or too late reminders are warnings`() {
        val r = report(good().copy(history = ReminderHistory(listOf(entry(60, ReminderOutcome.BLOCKED, 2), entry(30, ReminderOutcome.FIRED, 1)))))
        assertEquals(HealthLevel.WARNING, row(r, HealthRowId.HISTORY).level)
        assertTrue(r.status.allGood)
    }

    @Test fun `the history row gives counts and the lateness numbers`() {
        val h = ReminderHistory(listOf(entry(300, ReminderOutcome.FIRED, 0), entry(200, ReminderOutcome.FIRED, 121, AlarmMethod.INEXACT), entry(100, ReminderOutcome.FIRED, 2)))
        val d = row(report(good().copy(history = h)), HealthRowId.HISTORY).detail
        assertEquals("3 shown, 0 blocked, 0 too late, 0 missed; median 2 s late, worst 2 min 1 s.", d)
    }

    // --- words ---

    @Test fun `every level has a word, so status is never colour alone`() {
        assertEquals(listOf("OK", "Warning", "Problem"), HealthLevel.entries.map { it.word })
    }

    @Test fun `lateness reads in seconds, minutes and hours`() {
        assertEquals("0 s", formatLateness(0))
        assertEquals("59 s", formatLateness(59))
        assertEquals("1 min", formatLateness(60))
        assertEquals("2 min 1 s", formatLateness(121))
        assertEquals("1 h", formatLateness(3600))
        assertEquals("1 h 2 min", formatLateness(3720))
    }

    @Test fun `times are shown in the place's zone`() {
        assertEquals("Wed 7 Oct, 16:04", formatHealthTime(Instant.parse("2026-10-07T08:04:00Z"), sg))
        assertEquals("Wed 7 Oct, 09:04", formatHealthTime(Instant.parse("2026-10-07T08:04:00Z"), ZoneId.of("Europe/London")))
    }

    // --- the turn-on flow ---

    @Test fun `without notifications the first thing asked for is notifications`() {
        assertEquals(PermissionNeed.NOTIFICATIONS, nextPermissionNeed(false, false, true, false))
        assertEquals(PermissionNeed.NOTIFICATIONS, nextPermissionNeed(false, true, true, true))
    }

    @Test fun `with notifications but no exact alarms, exact alarms are asked once`() {
        assertEquals(PermissionNeed.EXACT_ALARMS, nextPermissionNeed(true, false, true, false))
        assertEquals("a no is not asked again", PermissionNeed.NONE, nextPermissionNeed(true, false, true, true))
    }

    @Test fun `with everything allowed nothing is asked`() {
        assertEquals(PermissionNeed.NONE, nextPermissionNeed(true, true, true, false))
    }

    @Test fun `a build without exact alarms never asks for them`() {
        assertEquals(PermissionNeed.NONE, nextPermissionNeed(true, false, false, false))
    }
}
