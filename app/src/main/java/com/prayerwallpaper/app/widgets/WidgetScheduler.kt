package com.prayerwallpaper.app.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.prayerwallpaper.app.logic.WIDGET_SAFETY_DELAY
import java.time.Duration
import java.time.Instant

/**
 * The two alarms that keep the widgets' picture right: one at the next boundary (a prayer time or midnight) and a safety alarm two
 * minutes after it. Both are inexact, which needs no permission: the widgets never ask for exact alarms. Each boundary alarm, when it
 * runs, sets up the next pair, so nothing is planned further ahead than one boundary. Alarms do not survive a reboot, so the
 * receivers for boot, an update and a clock change set them again.
 *
 * The boundary alarm is a window alarm, `setWindow(boundary, 10 minutes)`: Android may deliver it any time from the boundary to ten
 * minutes after, never before. That is the shortest window it grants an app without the exact alarm permission (shorter ones are
 * rounded up). `setAndAllowWhileIdle`, which the spec names, was measured on the emulator and is given a window of 75% of the time
 * left, up to 45 minutes, so on a boundary hours away it can be nearly an hour late. The safety alarm does use
 * `setAndAllowWhileIdle`, so there is also an alarm that may run while the phone is idle.
 */
class WidgetScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /**
     * Sets the pair for [boundary]. Both instants are in the app's clock, which is the real one except in a debug build with a fake
     * time; the alarms are set that same distance from now on the real clock, so a boundary parked a minute away rings in a minute.
     */
    fun schedule(boundary: Instant, now: Instant, realNowMillis: Long = System.currentTimeMillis()) {
        val ahead = Duration.between(now, boundary).toMillis().coerceAtLeast(0)
        // A little after the boundary, never before it: an alarm a millisecond early would find the picture not yet due to change.
        val real = realNowMillis + ahead + SLACK.toMillis()
        alarms.setWindow(AlarmManager.RTC_WAKEUP, real, BOUNDARY_WINDOW.toMillis(), pendingIntent(REQUEST_BOUNDARY, ACTION_BOUNDARY, boundary, create = true)!!)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, real + WIDGET_SAFETY_DELAY.toMillis(), pendingIntent(REQUEST_SAFETY, ACTION_SAFETY, boundary, create = true)!!)
    }

    fun cancel() {
        for ((request, action) in listOf(REQUEST_BOUNDARY to ACTION_BOUNDARY, REQUEST_SAFETY to ACTION_SAFETY)) {
            pendingIntent(request, action, null, create = false)?.let {
                alarms.cancel(it)
                it.cancel()
            }
        }
    }

    /** Whether both pending intents exist. That is all Android lets an app ask: it does not say the alarm is still registered. */
    fun isSet(): Boolean =
        pendingIntent(REQUEST_BOUNDARY, ACTION_BOUNDARY, null, create = false) != null && pendingIntent(REQUEST_SAFETY, ACTION_SAFETY, null, create = false) != null

    private fun pendingIntent(request: Int, action: String, boundary: Instant?, create: Boolean): PendingIntent? {
        val intent = Intent(context, WidgetReceiver::class.java).setAction(action)
        boundary?.let { intent.putExtra(EXTRA_BOUNDARY, it.toEpochMilli()) }
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, request, intent, flags)
    }

    companion object {
        const val ACTION_BOUNDARY = "com.prayerwallpaper.app.WIDGET_BOUNDARY"
        const val ACTION_SAFETY = "com.prayerwallpaper.app.WIDGET_SAFETY"
        const val EXTRA_BOUNDARY = "boundary"
        /** The shortest window Android grants an app that does not hold the exact alarm permission. */
        val BOUNDARY_WINDOW: Duration = Duration.ofMinutes(10)
        private val SLACK: Duration = Duration.ofMillis(500)
        private const val REQUEST_BOUNDARY = 1
        private const val REQUEST_SAFETY = 2
    }
}
