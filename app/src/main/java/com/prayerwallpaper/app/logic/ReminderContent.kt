package com.prayerwallpaper.app.logic

import java.time.Duration
import java.time.Instant

/** The words of a reminder notification. */
data class ReminderContent(val title: String, val text: String)

/**
 * What a reminder says. At the prayer time: the prayer's name, and its time (`Zohor`, `1:00 pm`). A heads-up before it: the name
 * and how long to go, and the same time (`Zohor in 10 minutes`, `1:00 pm`). The test from the health screen says so.
 * [label] is the prayer's name as the list shows it (Jumaat on a Friday, English names if chosen) and [timeText] its time.
 */
fun reminderContent(kind: ReminderKind, label: String, minutesBefore: Long, timeText: String): ReminderContent = when (kind) {
    ReminderKind.AT -> ReminderContent(label, timeText)
    ReminderKind.PRE -> ReminderContent("$label in ${minutesText(minutesBefore)}", timeText)
    ReminderKind.TEST -> ReminderContent(TEST_TITLE, TEST_TEXT)
}

const val TEST_TITLE = "Test reminder"
const val TEST_TEXT = "Reminders are working"

private fun minutesText(minutes: Long) = if (minutes == 1L) "1 minute" else "$minutes minutes"

/** Whole minutes from [plannedAt] to [prayerAt], never below 0: how far ahead a pre-reminder is. */
fun minutesBefore(plannedAt: Instant, prayerAt: Instant): Long = maxOf(0L, Duration.between(plannedAt, prayerAt).toMinutes())
