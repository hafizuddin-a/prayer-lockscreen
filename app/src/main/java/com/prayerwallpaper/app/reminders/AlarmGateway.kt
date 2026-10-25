package com.prayerwallpaper.app.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.MainActivity
import com.prayerwallpaper.app.logic.AlarmMethod
import com.prayerwallpaper.app.logic.ReminderAlarm

/**
 * What the scheduler needs from Android's alarm service, so its decisions can be tested without a phone. Each alarm has a
 * pending intent of its own (told apart by [ReminderAlarm.key]), so replacing the next alarm never cancels one that is overdue.
 */
interface AlarmGateway {
    /** Whether Android lets the app set an exact alarm now: the user granted it in system settings. */
    fun canScheduleExact(): Boolean

    /** Sets [alarm] by [method], replacing the same alarm if it is already set. Returns the method actually used. */
    fun schedule(alarm: ReminderAlarm, method: AlarmMethod): AlarmMethod

    fun cancel(alarm: ReminderAlarm)

    /**
     * Whether the pending intent for [alarm] exists. That is all Android lets an app ask: it does not say the alarm is still
     * registered (turning the exact alarm permission off removes alarms and leaves the pending intents).
     */
    fun exists(alarm: ReminderAlarm): Boolean
}

/** The real thing, on [AlarmManager]. Only documented calls; no foreground service and no wake lock of the app's own. */
class AndroidAlarmGateway(private val context: Context, private val exactAllowed: () -> Boolean = { true }) : AlarmGateway {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = exactAllowed() && alarms.canScheduleExactAlarms()

    override fun schedule(alarm: ReminderAlarm, method: AlarmMethod): AlarmMethod {
        val pending = pendingIntent(alarm, create = true)!!
        val at = alarm.at.toEpochMilli()
        var used = method
        try {
            when (method) {
                AlarmMethod.EXACT -> alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                AlarmMethod.INEXACT -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                AlarmMethod.ALARM_CLOCK -> alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, showIntent()), pending)
            }
        } catch (e: SecurityException) {
            // The permission was taken back between the check and the call. A late reminder beats none.
            if (BuildConfig.DEBUG) Log.w(TAG, "exact alarm refused, using an inexact one: $e")
            used = AlarmMethod.INEXACT
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
        return used
    }

    override fun cancel(alarm: ReminderAlarm) {
        pendingIntent(alarm, create = false)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    override fun exists(alarm: ReminderAlarm): Boolean = pendingIntent(alarm, create = false) != null

    private fun pendingIntent(alarm: ReminderAlarm, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_ALARM)
            // The data tells one alarm's pending intent from another's. It is not a real address and nothing resolves it.
            .setData("reminder://alarm/${alarm.key}".toUri())
            .putExtra(ReminderReceiver.EXTRA_PLANNED_AT, alarm.at.toEpochMilli())
            .putExtra(ReminderReceiver.EXTRA_KIND, alarm.kind.name)
            .putExtra(ReminderReceiver.EXTRA_PRAYER, alarm.prayer?.name)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /** What tapping the alarm clock icon in the status bar opens. Only used when the debug switch picks the alarm clock method. */
    private fun showIntent(): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private companion object {
        const val TAG = "Reminders"
    }
}
