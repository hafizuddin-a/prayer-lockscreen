package com.prayerwallpaper.app.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.net.toUri
import android.widget.Toast
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.wallpaper.PrayerWallpaperService

/** Opens Android's own screen for choosing this live wallpaper, or the list of live wallpapers if that is all the phone has. */
internal fun openLiveWallpaperPicker(context: Context) {
    val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(context, PrayerWallpaperService::class.java))
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "This phone has no live wallpaper picker", Toast.LENGTH_LONG).show()
        }
    }
}

/** Opens [url] in the user's browser or the app that handles it (Instagram for its own links). Does nothing if nothing can. */
internal fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
    }
}

/** Opens the system's wallpaper chooser so the user can give the home screen a wallpaper of its own again. */
internal fun chooseHomeWallpaper(context: Context) {
    try {
        // A chooser, not the bare intent, so the system offers no "Always" default.
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose a wallpaper app"))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Open your phone's Wallpaper settings", Toast.LENGTH_LONG).show()
    }
}

/**
 * Documented Settings screens to try for the lock screen notification setting, most specific first.
 * Android has no public action for that one setting. Display settings is the nearest page: on stock
 * Android 16 it leads to Lock screen, which holds "Notifications on lock screen" and "Dynamic clock".
 */
private val LOCK_SCREEN_SETTINGS_ACTIONS = listOf(
    Settings.ACTION_DISPLAY_SETTINGS,
    Settings.ACTION_SETTINGS,
)

/** Opens the first of [LOCK_SCREEN_SETTINGS_ACTIONS] that exists. Returns the action used, or null. */
internal fun openLockScreenSettings(context: Context): String? {
    for (action in LOCK_SCREEN_SETTINGS_ACTIONS) {
        try {
            context.startActivity(Intent(action))
            if (BuildConfig.DEBUG) Log.i("PrayerSettings", "opened $action")
            return action
        } catch (_: ActivityNotFoundException) {
            if (BuildConfig.DEBUG) Log.i("PrayerSettings", "no activity for $action")
        } catch (_: SecurityException) {
        }
    }
    return null
}
