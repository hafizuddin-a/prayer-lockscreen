package com.prayerwallpaper.app.reminders

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.os.PowerManager
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.DataHealth
import com.prayerwallpaper.app.logic.HealthInput
import java.time.LocalDate

/**
 * Reads what the reminder health screen needs from Android. Every call is documented API and needs no permission (checked on the
 * emulator): the battery facts and the app's own standby bucket are readable by the app itself. Anything Android refuses is null,
 * which the screen shows as "unknown" rather than as fine.
 */
fun readHealthInput(app: PrayerApp): HealthInput {
    val settings = app.settings.read()
    val nm = app.getSystemService(NotificationManager::class.java)
    val peek = app.reminders.peek()
    val data = app.repository.data()
    return HealthInput(
        remindersOn = settings.remindersOn,
        notificationsAllowed = nm.areNotificationsEnabled(),
        channelImportance = runCatching { nm.getNotificationChannel(AndroidReminderNotifier.channelId(settings.silentReminders))?.importance }.getOrNull(),
        silentReminders = settings.silentReminders,
        exactAlarmsInBuild = BuildConfig.EXACT_ALARMS,
        exactAllowed = app.reminders.canScheduleExact(),
        next = peek.next,
        nextMethod = peek.method,
        nextPendingIntentExists = peek.pendingIntentExists,
        batteryOptimised = runCatching { !app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName) }.getOrNull(),
        backgroundRestricted = runCatching { app.getSystemService(ActivityManager::class.java).isBackgroundRestricted }.getOrNull(),
        standbyBucket = runCatching { app.getSystemService(UsageStatsManager::class.java).appStandbyBucket }.getOrNull(),
        data = if (settings.place.usesMuis) {
            DataHealth(true, data.years.maxOfOrNull { it.fetchedAt }, data.days().keys.maxOrNull())
        } else {
            DataHealth(false, null, null as LocalDate?)
        },
        history = app.reminders.history(),
        names = settings.effectiveNames,
    )
}
