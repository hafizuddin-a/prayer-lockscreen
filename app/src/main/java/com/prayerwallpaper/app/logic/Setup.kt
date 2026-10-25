package com.prayerwallpaper.app.logic

/**
 * The pages of the first-run setup. All but [DONE] are numbered ("Step 2 of 6"); [DONE] is the closing page and has no number.
 * [EXACT_ALARMS] is only for a build that uses exact alarms (`-PexactAlarms=false` leaves it out), so every question about the order
 * takes `exactAlarms`, which the screens pass from `BuildConfig.EXACT_ALARMS`.
 */
enum class SetupStep {
    /** [NOTIFICATIONS] is about the phone's lock screen notifications covering the list (it is not the reminders). */
    WELCOME, APPLY, NOTIFICATIONS, REMINDERS, EXACT_ALARMS, WIDGETS, DONE;

    /** The page after this one, or null on the closing page. */
    fun next(exactAlarms: Boolean): SetupStep? = pages(exactAlarms).let { pages -> pages.indexOf(this).takeIf { it >= 0 }?.let { pages.getOrNull(it + 1) } }

    /** The page before this one, or null on the first page. */
    fun previous(exactAlarms: Boolean): SetupStep? = pages(exactAlarms).let { pages -> pages.indexOf(this).takeIf { it >= 0 }?.let { pages.getOrNull(it - 1) } }

    /** 1-based number for "Step n of [setupStepCount]", or null on the closing page. */
    fun number(exactAlarms: Boolean): Int? = pages(exactAlarms).indexOf(this).takeIf { it >= 0 && this != DONE }?.plus(1)

    companion object {
        /** The pages this build shows, in order. */
        fun pages(exactAlarms: Boolean): List<SetupStep> = entries.filter { exactAlarms || it != EXACT_ALARMS }
    }
}

/** How many numbered pages there are: every page but the closing one. */
fun setupStepCount(exactAlarms: Boolean): Int = SetupStep.pages(exactAlarms).size - 1

/**
 * Where the live wallpaper stands, from what Android reports. The setup page and the status line on the home screen both read it.
 *
 * Applying the wallpaper to both screens gives one shared wallpaper that is ours for the home screen too, which replaced the
 * user's own home wallpaper: that is [HOME_ALSO]. Choosing "Home screen" only leaves the lock screen alone: [HOME_ONLY].
 */
enum class ApplyState {
    /** Not on the lock screen and the picker was not opened from here. */
    NOT_STARTED,

    /** On the lock screen, and the home screen keeps its own wallpaper. */
    DONE,

    /** On the lock screen, and on the home screen too. */
    HOME_ALSO,

    /** On the home screen but not the lock screen: "Home screen" was chosen where "Lock screen" was meant. */
    HOME_ONLY,

    /** The picker was opened from here and nothing changed: it was closed, or the wallpaper was not set. */
    NOT_SEEN_YET,
}

fun applyState(lockIsOurs: Boolean, homeIsOurs: Boolean, openedPicker: Boolean): ApplyState = when {
    lockIsOurs && homeIsOurs -> ApplyState.HOME_ALSO
    lockIsOurs -> ApplyState.DONE
    homeIsOurs -> ApplyState.HOME_ONLY
    openedPicker -> ApplyState.NOT_SEEN_YET
    else -> ApplyState.NOT_STARTED
}

/**
 * Whether the app opens on the setup. Only a fresh install does: anything that shows the app was already in use, the larger text
 * question answered, a colour capture made or skipped, or the lock screen already ours, keeps an updating user on the home screen.
 * Decided once when the app starts, never again while the setup runs (answering the first page changes some of these).
 */
fun shouldShowSetup(setupDone: Boolean, accessAsked: Boolean, lockIsOurs: Boolean, captured: Boolean, captureSkipped: Boolean): Boolean =
    !setupDone && !accessAsked && !lockIsOurs && !captured && !captureSkipped
