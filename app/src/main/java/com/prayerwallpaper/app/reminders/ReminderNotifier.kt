package com.prayerwallpaper.app.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.prayerwallpaper.app.MainActivity
import com.prayerwallpaper.app.R
import com.prayerwallpaper.app.logic.ReminderContent
import java.time.Duration

/** What the scheduler needs from the notification service, so it can be tested without a phone. */
interface ReminderNotifier {
    /** Whether a reminder would be shown: the app's notifications are on, and so is the channel it would use. */
    fun canPost(silent: Boolean): Boolean

    /** Shows [content]. [id] makes a second reminder replace nothing: every alarm has its own. */
    fun post(id: Int, content: ReminderContent, silent: Boolean)
}

/**
 * The real notifications, on two channels made when first needed. A channel's sound cannot be changed in code once it exists, so
 * "silent reminders" picks the other channel instead: [CHANNEL] (the user's notification sound, heads-up) or [CHANNEL_SILENT]
 * (vibration only). Tapping a reminder opens the app; it clears itself after an hour so a stale one never lingers.
 */
class AndroidReminderNotifier(private val context: Context) : ReminderNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Creates the channels if they do not exist. Safe to call any time: Android keeps what the user changed. */
    fun ensureChannels() {
        manager.createNotificationChannel(
            // HIGH: a heads-up banner and the user's notification sound, which is how a prayer reminder should arrive.
            NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.reminder_channel_description)
            },
        )
        manager.createNotificationChannel(
            // DEFAULT with no sound: it vibrates and sits in the shade without a banner. LOW would not vibrate at all.
            NotificationChannel(CHANNEL_SILENT, context.getString(R.string.reminder_channel_silent_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.reminder_channel_silent_description)
                setSound(null, null)
                enableVibration(true)
            },
        )
    }

    override fun canPost(silent: Boolean): Boolean {
        ensureChannels()
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(channelId(silent))
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    override fun post(id: Int, content: ReminderContent, silent: Boolean) {
        ensureChannels()
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, channelId(silent))
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setCategory(Notification.CATEGORY_REMINDER)
            // The content is a prayer name and a time, nothing private, so it shows on the lock screen too.
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setTimeoutAfter(TIMEOUT.toMillis())
            .build()
        manager.notify(TAG, id, notification)
    }

    companion object {
        const val CHANNEL = "prayer_reminders"
        const val CHANNEL_SILENT = "prayer_reminders_silent"
        private const val TAG = "reminder"
        private val TIMEOUT: Duration = Duration.ofMinutes(60)

        fun channelId(silent: Boolean) = if (silent) CHANNEL_SILENT else CHANNEL
    }
}
