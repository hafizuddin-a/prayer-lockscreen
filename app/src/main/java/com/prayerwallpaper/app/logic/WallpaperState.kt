package com.prayerwallpaper.app.logic

/**
 * Whether the lock screen is showing this app, from what Android reports. [lockIsOurs] is whether a
 * separate live wallpaper on the lock screen is ours, or null when there is none. [lockId] is Android's
 * id for the lock screen wallpaper, negative when the lock screen has none of its own.
 *
 * Applying the wallpaper to both screens gives one shared wallpaper and no lock wallpaper of its own
 * (id -1), so the lock screen then follows the home screen. A separate static image on the lock screen
 * has an id but no live wallpaper info, and is not ours.
 */
fun lockScreenIsOurs(lockIsOurs: Boolean?, lockId: Int, homeIsOurs: Boolean): Boolean = when {
    lockIsOurs != null -> lockIsOurs
    lockId < 0 -> homeIsOurs
    else -> false
}
