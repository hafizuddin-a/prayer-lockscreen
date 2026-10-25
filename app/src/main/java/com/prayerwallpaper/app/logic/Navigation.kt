package com.prayerwallpaper.app.logic

/** Every screen of the settings app. There is no navigation library: the app keeps a short list of the screens it came through. */
enum class AppScreen { HOME, SETUP, LOOK, LOCATION, PLACE, CALCULATION, PHOTO, ACCESS, REMINDERS, REMINDER_HEALTH, WIDGETS, WIDGET_HEALTH, ABOUT }

/**
 * The history behind the Back button, oldest first, always starting with the screen the app opened on. Pure functions on a list so the
 * rules can be tested: Back goes to the screen you came from, the setup can open the place chooser and return to the same page,
 * and Home is always one tap from anywhere.
 */
object Navigation {
    /**
     * Opening [to] from the current screen. Going Home starts the history again. A screen that is already in the history is
     * returned to (the history is cut back to it) instead of being stacked twice, so Back never walks in a circle.
     */
    fun open(stack: List<AppScreen>, to: AppScreen): List<AppScreen> {
        if (to == AppScreen.HOME) return listOf(AppScreen.HOME)
        val at = stack.indexOf(to)
        return if (at >= 0) stack.subList(0, at + 1).toList() else stack + to
    }

    /** Back. With nothing behind it (the setup opened first) it lands on Home, which is where leaving the setup early belongs. */
    fun back(stack: List<AppScreen>): List<AppScreen> = when {
        stack.size > 1 -> stack.dropLast(1)
        else -> listOf(AppScreen.HOME)
    }

    /** Whether Back is the app's to handle. On Home it is left to Android, which closes the app. */
    fun handlesBack(stack: List<AppScreen>): Boolean = stack.last() != AppScreen.HOME
}
