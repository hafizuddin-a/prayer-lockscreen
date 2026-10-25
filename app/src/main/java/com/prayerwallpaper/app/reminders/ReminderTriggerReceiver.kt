package com.prayerwallpaper.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prayerwallpaper.app.PrayerApp

/**
 * Puts the reminder alarm back after anything that can lose it: a reboot (alarms do not survive one), the app being updated, the
 * clock or the time zone changing, and the exact alarm permission being given or taken away (taking it away cancels every exact alarm).
 * Android sends all of these itself; this receiver is declared in the manifest so they reach the app even when it is not running.
 */
class ReminderTriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext as PrayerApp
        val pending = goAsync()
        Thread {
            try {
                // Always forced: after any of these, the pending intent may still exist while the alarm itself is gone.
                app.reminders.ensureScheduled(action.substringAfterLast('.'))
            } finally {
                pending.finish()
            }
        }.start()
    }
}
