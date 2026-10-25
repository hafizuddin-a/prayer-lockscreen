package com.prayerwallpaper.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri
import com.prayerwallpaper.app.reminders.AndroidReminderNotifier

/**
 * The system screens the reminder settings send people to. All are documented intents, and none needs a permission. Each is opened
 * inside try/catch: on a phone where the screen does not exist the person is told so, and the app carries on.
 */
internal object SystemScreens {
    /** The app's notification settings: the switch for all of its notifications, and its channels. */
    fun notifications(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** The settings of one channel, where the notification sound is chosen. */
    fun channel(context: Context, silent: Boolean) =
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, AndroidReminderNotifier.channelId(silent))

    /** The screen where the person allows this app to set exact alarms. */
    fun exactAlarms(context: Context) = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())

    /** The list of apps and their battery optimisation. Opening it asks for no permission, unlike the direct request, which this app never makes. */
    fun battery() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun open(context: Context, intent: Intent, failure: String = "This phone has no screen for that setting") {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
        }
    }
}
