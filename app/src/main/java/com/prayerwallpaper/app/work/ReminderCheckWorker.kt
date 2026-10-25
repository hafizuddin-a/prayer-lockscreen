package com.prayerwallpaper.app.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.prayerwallpaper.app.PrayerApp
import java.util.concurrent.TimeUnit

/**
 * Once a day, while reminders are on: checks that the next alarm still exists and sets it again if not, and marks as missed any
 * alarm that never arrived. No network is needed, so unlike the data refresh it runs on a day without a connection too, and for a
 * place whose times are calculated, where the data refresh does not run at all.
 */
class ReminderCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        (applicationContext as PrayerApp).reminders.ensureScheduled("daily check")
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "reminder-check"

        /** Runs the daily check while [enabled] and stops it otherwise. Safe to call on every launch: an existing schedule is kept. */
        fun setEnabled(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) {
                work.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            work.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequest.Builder(ReminderCheckWorker::class.java, 1, TimeUnit.DAYS).build())
        }
    }
}
