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
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Runs once a day when the network is up. [com.prayerwallpaper.app.data.RefreshPolicy] decides
 * whether anything actually needs downloading, so most runs make no request at all.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val app = applicationContext as PrayerApp
        // Reminders first: check that the next alarm is still set. Does nothing, quickly, while they are off.
        app.reminders.ensureScheduled("daily refresh job")
        // A place with calculated times has nothing to download. Normally the job is cancelled by then; this covers the gap.
        if (!app.settings.read().place.usesMuis) {
            app.widgets.refreshIfStale("daily refresh job")
            return Result.success()
        }
        val repository = app.repository
        return try {
            val result = repository.refresh(force = false)
            Log.i(TAG, "refresh ok, fetched years=${result.fetchedYears}, rejected rows=${result.rejectedRows}")
            // Fresh times may change the next reminder (the end of the data was blocking it, or a time moved).
            if (result.fetchedYears.isNotEmpty()) app.reminders.ensureScheduled("fresh data")
            // The widgets read the same cache: fresh data may fill a gap or move a time, and the daily job is a moment to check they are current.
            if (result.fetchedYears.isNotEmpty()) app.widgets.updateAll("data refresh") else app.widgets.refreshIfStale("daily refresh job")
            Result.success()
        } catch (e: IOException) {
            // Network trouble or HTTP 429/5xx. Back off and retry a few times, then wait for tomorrow.
            Log.w(TAG, "refresh failed (attempt ${runAttemptCount + 1}): $e")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "refresh failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "PrayerRefresh"
        private const val UNIQUE_NAME = "prayer-refresh"
        private const val MAX_ATTEMPTS = 5

        /** Runs the daily job for Singapore and stops it for every other place. Safe to call on every launch. */
        fun setEnabled(context: Context, enabled: Boolean) {
            if (enabled) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }

        /** Safe to call on every launch: an existing schedule is kept. WorkManager restores it after reboot. */
        private fun schedule(context: Context) {
            val request = PeriodicWorkRequest.Builder(RefreshWorker::class.java, 1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
