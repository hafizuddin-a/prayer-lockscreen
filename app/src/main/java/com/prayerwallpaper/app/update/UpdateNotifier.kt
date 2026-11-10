package com.prayerwallpaper.app.update

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.prayerwallpaper.app.MainActivity
import com.prayerwallpaper.app.R
import com.prayerwallpaper.app.logic.ReleaseInfo

/**
 * The two quiet notifications an update can need, on a channel of their own (low importance: no sound, no banner). Only shown where
 * the app's notifications are already allowed; nobody is asked for the permission because of an update.
 */
class UpdateNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.update_channel_description)
            },
        )
    }

    fun canPost(): Boolean {
        ensureChannel()
        if (!manager.areNotificationsEnabled()) return false
        val channel = manager.getNotificationChannel(CHANNEL)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** "A new version is ready": tapping it opens the app, whose home screen has the Update button. */
    fun postAvailable(release: ReleaseInfo) {
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        post(ID_AVAILABLE, "Update available", "Version ${release.version} of Prayer Live Wallpaper is ready.", open)
    }

    /** Android wants a tap to finish the install and the app is not on screen: tapping this shows Android's own prompt. */
    fun postReady(confirm: Intent, release: ReleaseInfo) {
        val prompt = PendingIntent.getActivity(context, 1, confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        post(ID_READY, "Finish updating", "Tap to install version ${release.version}.", prompt)
    }

    private fun post(id: Int, title: String, text: String, tap: PendingIntent) {
        ensureChannel()
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        manager.notify(TAG, id, notification)
    }

    companion object {
        const val CHANNEL = "app_updates"
        private const val TAG = "update"
        private const val ID_AVAILABLE = 1
        private const val ID_READY = 2
    }
}
