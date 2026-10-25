package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupTest {
    // --- the pages ---

    @Test fun `the pages run welcome, apply, notifications, reminders, exact alarms, widgets, done`() {
        val all = listOf(
            SetupStep.WELCOME, SetupStep.APPLY, SetupStep.NOTIFICATIONS, SetupStep.REMINDERS, SetupStep.EXACT_ALARMS, SetupStep.WIDGETS, SetupStep.DONE,
        )
        assertEquals(all, SetupStep.entries.toList())
        assertEquals(all, SetupStep.pages(exactAlarms = true))
    }

    @Test fun `a build without exact alarms leaves that page out`() {
        assertEquals(
            listOf(SetupStep.WELCOME, SetupStep.APPLY, SetupStep.NOTIFICATIONS, SetupStep.REMINDERS, SetupStep.WIDGETS, SetupStep.DONE),
            SetupStep.pages(exactAlarms = false),
        )
        assertEquals(SetupStep.WIDGETS, SetupStep.REMINDERS.next(exactAlarms = false))
        assertEquals(SetupStep.REMINDERS, SetupStep.WIDGETS.previous(exactAlarms = false))
        assertNull(SetupStep.EXACT_ALARMS.next(exactAlarms = false)) // not a page of this build
        assertNull(SetupStep.EXACT_ALARMS.number(exactAlarms = false))
    }

    @Test fun `next and previous walk the pages and stop at the ends`() {
        assertEquals(SetupStep.APPLY, SetupStep.WELCOME.next(true))
        assertEquals(SetupStep.NOTIFICATIONS, SetupStep.APPLY.next(true))
        assertEquals(SetupStep.REMINDERS, SetupStep.NOTIFICATIONS.next(true))
        assertEquals(SetupStep.EXACT_ALARMS, SetupStep.REMINDERS.next(true))
        assertEquals(SetupStep.WIDGETS, SetupStep.EXACT_ALARMS.next(true))
        assertEquals(SetupStep.NOTIFICATIONS, SetupStep.REMINDERS.previous(true))
        assertEquals(SetupStep.REMINDERS, SetupStep.EXACT_ALARMS.previous(true))
        assertEquals(SetupStep.EXACT_ALARMS, SetupStep.WIDGETS.previous(true))
        assertEquals(SetupStep.DONE, SetupStep.WIDGETS.next(true))
        assertNull(SetupStep.DONE.next(true))
        assertNull(SetupStep.WELCOME.previous(true))
        assertEquals(SetupStep.WELCOME, SetupStep.APPLY.previous(true))
        assertEquals(SetupStep.WIDGETS, SetupStep.DONE.previous(true))
    }

    @Test fun `every page but the closing one is numbered, out of six, or out of five without exact alarms`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), SetupStep.entries.take(6).map { it.number(true) })
        assertNull(SetupStep.DONE.number(true))
        assertEquals(6, setupStepCount(true))
        assertEquals(listOf(1, 2, 3, 4, 5), SetupStep.pages(false).take(5).map { it.number(false) })
        assertNull(SetupStep.DONE.number(false))
        assertEquals(5, setupStepCount(false))
    }

    // --- where the wallpaper stands ---

    @Test fun `on the lock screen only is done`() {
        assertEquals(ApplyState.DONE, applyState(lockIsOurs = true, homeIsOurs = false, openedPicker = true))
        assertEquals(ApplyState.DONE, applyState(lockIsOurs = true, homeIsOurs = false, openedPicker = false))
    }

    @Test fun `on both screens says the home screen changed too`() {
        assertEquals(ApplyState.HOME_ALSO, applyState(lockIsOurs = true, homeIsOurs = true, openedPicker = true))
        assertEquals(ApplyState.HOME_ALSO, applyState(lockIsOurs = true, homeIsOurs = true, openedPicker = false))
    }

    @Test fun `on the home screen only is the wrong choice, whether or not the picker was just opened`() {
        assertEquals(ApplyState.HOME_ONLY, applyState(lockIsOurs = false, homeIsOurs = true, openedPicker = true))
        assertEquals(ApplyState.HOME_ONLY, applyState(lockIsOurs = false, homeIsOurs = true, openedPicker = false))
    }

    @Test fun `nothing changed is not started before the picker is opened and not seen yet after`() {
        assertEquals(ApplyState.NOT_STARTED, applyState(lockIsOurs = false, homeIsOurs = false, openedPicker = false))
        assertEquals(ApplyState.NOT_SEEN_YET, applyState(lockIsOurs = false, homeIsOurs = false, openedPicker = true))
    }

    @Test fun `every combination of the three inputs has exactly one answer`() {
        val all = listOf(true, false).flatMap { lock -> listOf(true, false).flatMap { home -> listOf(true, false).map { Triple(lock, home, it) } } }
        assertEquals(8, all.size)
        for ((lock, home, opened) in all) applyState(lock, home, opened)
        // The lock screen being ours is never reported as a failure, and never as not started.
        for ((_, home, opened) in all) {
            val state = applyState(true, home, opened)
            assertTrue(state == ApplyState.DONE || state == ApplyState.HOME_ALSO)
        }
    }

    // --- who sees the setup ---

    @Test fun `a fresh install sees the setup`() {
        assertTrue(shouldShowSetup(setupDone = false, accessAsked = false, lockIsOurs = false, captured = false, captureSkipped = false))
    }

    @Test fun `an update never shows the setup to someone who already used the app`() {
        // Each one of these alone means the app has been used before, so the setup stays away.
        assertFalse(shouldShowSetup(setupDone = true, accessAsked = false, lockIsOurs = false, captured = false, captureSkipped = false))
        assertFalse(shouldShowSetup(setupDone = false, accessAsked = true, lockIsOurs = false, captured = false, captureSkipped = false))
        assertFalse(shouldShowSetup(setupDone = false, accessAsked = false, lockIsOurs = true, captured = false, captureSkipped = false))
        assertFalse(shouldShowSetup(setupDone = false, accessAsked = false, lockIsOurs = false, captured = true, captureSkipped = false))
        assertFalse(shouldShowSetup(setupDone = false, accessAsked = false, lockIsOurs = false, captured = false, captureSkipped = true))
    }

    @Test fun `only the all-false combination shows the setup`() {
        val bools = listOf(false, true)
        var shown = 0
        for (a in bools) for (b in bools) for (c in bools) for (d in bools) for (e in bools) {
            if (shouldShowSetup(a, b, c, d, e)) shown++
        }
        assertEquals(1, shown)
    }
}
