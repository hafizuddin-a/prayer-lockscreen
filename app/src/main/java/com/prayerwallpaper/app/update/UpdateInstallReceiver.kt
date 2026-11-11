package com.prayerwallpaper.app.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.isSafeInstallConfirmation

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
                if (confirm != null && isInstallersOwnScreen(context, intent, confirm)) {
                    app.updates.onNeedsConfirmation(confirm)
                } else {
                    // Not what Android's installer sends: never start it. The person sees the same message as for any other failure.
                    Log.w("PrayerUpdate", "ignored an unexpected confirm screen: action=${confirm?.action} package=${confirm?.component?.packageName ?: confirm?.`package`}")
                    app.updates.onInstallResult(PackageInstaller.STATUS_FAILURE)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> app.updates.onInstallResult(status)
        }
    }

    /** The screen Android hands back is started with this app's own standing, so check it is the installer's and not something else. */
    private fun isInstallersOwnScreen(context: Context, result: Intent, confirm: Intent): Boolean {
        val sessionId = result.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1).takeIf { it >= 0 }
        val own = context.packageManager.packageInstaller.mySessions.map { it.sessionId }.toSet()
        return isSafeInstallConfirmation(
            action = confirm.action, targetPackage = confirm.component?.packageName ?: confirm.`package`, ownPackage = context.packageName,
            hasData = confirm.data != null || confirm.clipData != null, sessionId = sessionId, ownSessionIds = own,
        )
    }

    companion object {
        const val ACTION = "com.prayerwallpaper.app.UPDATE_INSTALL_RESULT"
    }
}
