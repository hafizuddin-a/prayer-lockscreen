package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NextReminderTest {
    private val sg = ZoneId.of("Asia/Singapore")

    // Real MUIS-shaped times for Monday 5 and Tuesday 6 October 2026.
    private fun day(date: String, vararg times: String) = PrayerDay(LocalDate.parse(date), times.map(LocalTime::parse))
    private val days = listOf(
        day("2026-10-05", "05:33", "06:50", "12:54", "16:04", "18:56", "20:05"),
        day("2026-10-06", "05:33", "06:49", "12:53", "16:03", "18:55", "20:04"),
    ).associateBy { it.date }

    private val five = setOf(Prayer.SUBUH, Prayer.ZOHOR, Prayer.ASAR, Prayer.MAGHRIB, Prayer.ISYAK)
    private fun at(date: String, time: String): Instant = ZonedDateTime.parse("${date}T$time+08:00[Asia/Singapore]").toInstant()

    private fun next(date: String, time: String, prayers: Set<Prayer> = five, pre: Int = 0, map: Map<LocalDate, PrayerDay> = days) =
        nextReminder(map, at(date, time), sg, prayers, pre)

    private fun alarmOf(result: NextReminder) = (result as NextReminder.Due).alarm

    // --- which prayer ---

    @Test fun `between two prayers it is the next one`() {
        val a = alarmOf(next("2026-10-05", "14:00"))
        assertEquals(Prayer.ASAR, a.prayer)
        assertEquals(ReminderKind.AT, a.kind)
        assertEquals(at("2026-10-05", "16:04"), a.at)
        assertEquals(LocalDate.parse("2026-10-05"), a.date)
    }

    @Test fun `before the first prayer of the day it is the first`() {
        assertEquals(Prayer.SUBUH, alarmOf(next("2026-10-05", "00:10")).prayer)
        assertEquals(Prayer.SUBUH, alarmOf(next("2026-10-05", "05:32:59")).prayer)
    }

    @Test fun `exactly at a prayer time that prayer is done and the next one is planned`() {
        // Strictly after now: an alarm for this very moment would be for something that has already begun.
        assertEquals(Prayer.ZOHOR, alarmOf(next("2026-10-05", "05:33")).prayer)
        assertEquals(Prayer.ASAR, alarmOf(next("2026-10-05", "12:54")).prayer)
    }

    @Test fun `after Isyak the next is tomorrow's first prayer`() {
        val a = alarmOf(next("2026-10-05", "20:05"))
        assertEquals(Prayer.SUBUH, a.prayer)
        assertEquals(LocalDate.parse("2026-10-06"), a.date)
        assertEquals(at("2026-10-06", "05:33"), a.at)
        assertEquals(Prayer.SUBUH, alarmOf(next("2026-10-05", "23:59")).prayer)
    }

    @Test fun `after midnight it is the new day's prayers`() {
        assertEquals(LocalDate.parse("2026-10-06"), alarmOf(next("2026-10-06", "00:00")).date)
    }

    // --- which prayers are selected ---

    @Test fun `Syuruk is left out unless chosen`() {
        assertEquals(Prayer.ZOHOR, alarmOf(next("2026-10-05", "05:40")).prayer) // Syuruk 06:50 skipped
        assertEquals(Prayer.SYURUK, alarmOf(next("2026-10-05", "05:40", prayers = five + Prayer.SYURUK)).prayer)
    }

    @Test fun `only the chosen prayers count`() {
        val onlyAsar = setOf(Prayer.ASAR)
        assertEquals(Prayer.ASAR, alarmOf(next("2026-10-05", "01:00", prayers = onlyAsar)).prayer)
        // After today's Asar it is tomorrow's Asar.
        val a = alarmOf(next("2026-10-05", "17:00", prayers = onlyAsar))
        assertEquals(LocalDate.parse("2026-10-06"), a.date)
        assertEquals(at("2026-10-06", "16:03"), a.at)
    }

    @Test fun `no prayer chosen is nothing to plan`() {
        assertEquals(NextReminder.NothingSelected, next("2026-10-05", "10:00", prayers = emptySet()))
    }

    // --- pre-reminders ---

    @Test fun `a pre-reminder comes before the prayer by each size`() {
        for (minutes in PRE_REMINDER_CHOICES.filter { it > 0 }) {
            val a = alarmOf(next("2026-10-05", "12:00", pre = minutes))
            assertEquals("pre $minutes", ReminderKind.PRE, a.kind)
            assertEquals(Prayer.ZOHOR, a.prayer)
            assertEquals(at("2026-10-05", "12:54").minusSeconds(minutes * 60L), a.at)
        }
    }

    @Test fun `with a pre-reminder, the prayer time itself is still an alarm`() {
        val pre = alarmOf(next("2026-10-05", "12:00", pre = 10))
        assertEquals(ReminderKind.PRE, pre.kind)
        val at = alarmOf(next("2026-10-05", "12:44:01", pre = 10)) // just after the pre-reminder
        assertEquals(ReminderKind.AT, at.kind)
        assertEquals(at("2026-10-05", "12:54"), at.at)
    }

    @Test fun `with no pre-reminder there is only the prayer time`() {
        assertEquals(ReminderKind.AT, alarmOf(next("2026-10-05", "12:00", pre = 0)).kind)
    }

    @Test fun `a pre-reminder for tomorrow's first prayer is planned tonight`() {
        val a = alarmOf(next("2026-10-05", "22:00", pre = 15))
        assertEquals(ReminderKind.PRE, a.kind)
        assertEquals(at("2026-10-06", "05:18"), a.at)
    }

    @Test fun `a pre-reminder whose time has passed is skipped but the prayer is not`() {
        // 12:50 is after Zohor's 10 minute heads-up (12:44) and before Zohor (12:54).
        val a = alarmOf(next("2026-10-05", "12:50", pre = 10))
        assertEquals(ReminderKind.AT, a.kind)
        assertEquals(Prayer.ZOHOR, a.prayer)
    }

    // --- end of the data ---

    @Test fun `with tomorrow missing, today's remaining alarms are still planned`() {
        val onlyToday = days.filterKeys { it == LocalDate.parse("2026-10-05") }
        assertEquals(Prayer.ASAR, alarmOf(next("2026-10-05", "14:00", map = onlyToday)).prayer)
    }

    @Test fun `with tomorrow missing and today's done, nothing is planned`() {
        val onlyToday = days.filterKeys { it == LocalDate.parse("2026-10-05") }
        assertEquals(NextReminder.NoData, next("2026-10-05", "21:00", map = onlyToday))
    }

    @Test fun `with no times at all, nothing is planned`() {
        assertEquals(NextReminder.NoData, next("2026-10-05", "10:00", map = emptyMap()))
    }

    @Test fun `it never reaches past tomorrow for times`() {
        val farOff = mapOf(LocalDate.parse("2026-10-20") to day("2026-10-20", "05:33", "06:50", "12:54", "16:04", "18:56", "20:05"))
        assertEquals(NextReminder.NoData, next("2026-10-05", "10:00", map = farOff))
    }

    // --- time zones and clock changes ---

    @Test fun `the day is the place's, not the phone's`() {
        // 17:00 UTC on 5 October is 01:00 on the 6th in Singapore: tomorrow's prayers are the ones that count.
        val a = nextReminder(days, Instant.parse("2026-10-05T17:00:00Z"), sg, five, 0)
        assertEquals(LocalDate.parse("2026-10-06"), alarmOf(a).date)
        assertEquals(Prayer.SUBUH, alarmOf(a).prayer)
    }

    @Test fun `another zone reads the same times in its own day`() {
        val london = ZoneId.of("Europe/London") // BST in October: UTC+1
        val a = alarmOf(nextReminder(days, Instant.parse("2026-10-05T10:00:00Z"), london, setOf(Prayer.ZOHOR), 0))
        assertEquals(Instant.parse("2026-10-05T11:54:00Z"), a.at) // 12:54 BST
    }

    @Test fun `when the clock moves the answer follows it`() {
        // A clock set forward past Zohor makes Asar the next, set back makes Subuh the next again.
        assertEquals(Prayer.ZOHOR, alarmOf(next("2026-10-05", "06:00")).prayer)
        assertEquals(Prayer.ASAR, alarmOf(next("2026-10-05", "13:00")).prayer)
        assertEquals(Prayer.SUBUH, alarmOf(next("2026-10-05", "02:00")).prayer)
    }

    // --- the alarm's identity ---

    @Test fun `an alarm has a key made of its time, kind and prayer`() {
        val a = alarmOf(next("2026-10-05", "14:00"))
        assertEquals("${a.at.toEpochMilli()}/AT/ASAR", a.key)
        assertEquals("${a.at.toEpochMilli()}/TEST/-", ReminderAlarm(ReminderKind.TEST, null, LocalDate.MIN, a.at).key)
    }

    @Test fun `two different alarms never share a key`() {
        val keys = setOf(
            alarmOf(next("2026-10-05", "00:00", pre = 10)).key, alarmOf(next("2026-10-05", "05:30", pre = 10)).key,
            alarmOf(next("2026-10-05", "05:40", pre = 10)).key, alarmOf(next("2026-10-05", "12:50", pre = 10)).key,
        )
        assertEquals(4, keys.size)
    }

    // --- the stale rule ---

    @Test fun `an alarm just inside the limit is fresh`() {
        assertFalse(isStale(Duration.ofMinutes(29).plusSeconds(59)))
        assertFalse(isStale(Duration.ZERO))
        assertFalse(isStale(Duration.ofSeconds(90)))
    }

    @Test fun `exactly at the limit is still fresh`() {
        assertFalse(isStale(STALE_LIMIT))
    }

    @Test fun `an alarm just outside the limit is stale`() {
        assertTrue(isStale(STALE_LIMIT.plusSeconds(1)))
        assertTrue(isStale(Duration.ofHours(3)))
    }

    @Test fun `the limit is a named constant of 30 minutes and can be overridden`() {
        assertEquals(Duration.ofMinutes(30), STALE_LIMIT)
        assertTrue(isStale(Duration.ofMinutes(6), Duration.ofMinutes(5)))
        assertFalse(isStale(Duration.ofMinutes(5), Duration.ofMinutes(5)))
    }
}
