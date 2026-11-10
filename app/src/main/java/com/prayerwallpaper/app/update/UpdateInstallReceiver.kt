package com.prayerwallpaper.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.prayerwallpaper.app.PrayerApp

/**
 * Where Android reports what happened to an update this app handed it. Not exported: only the pending intent this app gave the installer
 * reaches it. On success the new version replaces the app, so there is nothing to do here.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PrayerApp
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) app.updates.onNeedsConfirmation(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> app.updates.onInstallResult(status)
        }
    }

    companion object {
        const val ACTION = "com.prayerwallpaper.app.UPDATE_INSTALL_RESULT"
    }
}
