package com.prayerwallpaper.app.work

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.prayerwallpaper.app.PrayerApp
import java.util.concurrent.TimeUnit

/**
 * Once a month, with the network up: asks GitHub for the newest release, and installs it if the person left automatic updates on and
 * Android lets the app do that without asking, otherwise tells them (once) if notifications are allowed. One small request a month.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext as PrayerApp
        return try {
            app.updates.runInBackground()
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "update check failed: $e")
            // Tomorrow's run tries again.
            Result.success()
        }
    }

    companion object {
        private const val TAG = "PrayerUpdate"
        private const val UNIQUE_NAME = "prayer-update"

        /** Runs the monthly job while automatic updates are on, and stops it when they are off. Safe to call on every launch. */
        fun setEnabled(context: Context, enabled: Boolean) {
            val manager = WorkManager.getInstance(context)
            if (!enabled) {
                manager.cancelUniqueWork(UNIQUE_NAME)
                return
            }
            val request = PeriodicWorkRequest.Builder(UpdateWorker::class.java, 30, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            manager.enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
