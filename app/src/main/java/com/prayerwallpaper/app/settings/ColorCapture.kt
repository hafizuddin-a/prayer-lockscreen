package com.prayerwallpaper.app.settings

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import com.prayerwallpaper.app.logic.lockScreenIsOurs
import com.prayerwallpaper.app.wallpaper.PrayerWallpaperService

/**
 * Reads the colours of the current lock screen wallpaper. This needs no permission, but once this
 * live wallpaper is applied the system returns its colours instead of the user's, so the colours
 * have to be captured before applying.
 */
class ColorCapture(private val context: Context) {
    private val manager = WallpaperManager.getInstance(context)

    private val ours = ComponentName(context, PrayerWallpaperService::class.java)

    /** True when the lock screen is showing this app, including when one shared wallpaper covers both screens. */
    fun lockIsThisApp(): Boolean = lockScreenIsOurs(
        lockIsOurs = manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK)?.let { it.component == ours },
        lockId = manager.getWallpaperId(WallpaperManager.FLAG_LOCK),
        homeIsOurs = homeIsThisApp(),
    )

    /** True when the home screen wallpaper is this app too, which replaced the user's own home wallpaper. */
    fun homeIsThisApp(): Boolean = manager.getWallpaperInfo(WallpaperManager.FLAG_SYSTEM)?.component == ours

    /** The lock screen's colours, or null when the system has none to give. */
    fun readLockColors(): CapturedColors? {
        val colors = manager.getWallpaperColors(WallpaperManager.FLAG_LOCK) ?: return null
        return CapturedColors(
            primary = colors.primaryColor.toArgb(),
            secondary = (colors.secondaryColor ?: colors.primaryColor).toArgb(),
            tertiary = colors.tertiaryColor?.toArgb(),
        )
    }
}
