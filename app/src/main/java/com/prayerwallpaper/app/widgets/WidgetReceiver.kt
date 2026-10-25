package com.prayerwallpaper.app.widgets

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import java.time.Instant

/**
 * Receives the widget alarms and the system events that can leave the widgets showing an old picture or without their alarms: a
 * reboot (alarms do not survive one), the app being updated, and the clock or time zone changing. The work runs off the main thread
 * under [goAsync]; the app holds no wake lock of its own.
 */
class WidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (BuildConfig.DEBUG) Log.i("WidgetReceiver", "$action delivered at ${Instant.now()}")
        val app = context.applicationContext as PrayerApp
        val boundary = intent.getLongExtra(WidgetScheduler.EXTRA_BOUNDARY, -1L).takeIf { it >= 0 }?.let(Instant::ofEpochMilli)
        val pending = goAsync()
        Thread {
            try {
                when (action) {
                    WidgetScheduler.ACTION_BOUNDARY -> app.widgets.updateAll("boundary alarm")
                    WidgetScheduler.ACTION_SAFETY -> app.widgets.onSafetyAlarm(boundary)
                    Intent.ACTION_BOOT_COMPLETED -> app.widgets.updateAll("boot")
                    Intent.ACTION_MY_PACKAGE_REPLACED -> app.widgets.updateAll("app updated")
                    Intent.ACTION_TIME_CHANGED -> app.widgets.updateAll("time changed")
                    Intent.ACTION_TIMEZONE_CHANGED -> app.widgets.updateAll("time zone changed")
                    Intent.ACTION_LOCALE_CHANGED -> app.widgets.updateAll("language changed")
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}
