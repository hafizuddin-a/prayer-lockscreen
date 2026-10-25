package com.prayerwallpaper.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.logic.ReminderKind
import java.time.Instant

/**
 * Receives a reminder alarm. The work (a small file and the next alarm) runs off the main thread under [goAsync], so the
 * receiver finishes quickly and never blocks the app. Android keeps the phone awake for the receiver itself; the app holds no
 * wake lock of its own.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM) return
        val plannedAt = intent.getLongExtra(EXTRA_PLANNED_AT, -1L).takeIf { it >= 0 } ?: return
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { name -> ReminderKind.entries.firstOrNull { it.name == name } } ?: return
        val prayer = intent.getStringExtra(EXTRA_PRAYER)?.let { name -> Prayer.entries.firstOrNull { it.name == name } }
        val app = context.applicationContext as PrayerApp
        val pending = goAsync()
        Thread {
            try {
                app.reminders.onAlarm(Instant.ofEpochMilli(plannedAt), kind, prayer)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_ALARM = "com.prayerwallpaper.app.REMINDER_ALARM"
        const val EXTRA_PLANNED_AT = "planned_at"
        const val EXTRA_KIND = "kind"
        const val EXTRA_PRAYER = "prayer"
    }
}
