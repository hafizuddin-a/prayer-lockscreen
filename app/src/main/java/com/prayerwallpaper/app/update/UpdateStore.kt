package com.prayerwallpaper.app.update

import android.content.SharedPreferences
import com.prayerwallpaper.app.logic.ReleaseInfo

/**
 * What the app remembers about updates between runs: when it last asked GitHub, the newest release it saw, and which version it already
 * told the person about with a notification (so it tells them once). In its own preferences file, apart from the settings.
 */
class UpdateStore(private val prefs: SharedPreferences) {
    fun lastCheckMillis(): Long = prefs.getLong(LAST_CHECK, 0)

    /** When the app's last check failed (no network, GitHub busy), or 0. Its own checks wait a while after that; a person's own check does not. */
    fun lastFailureMillis(): Long = prefs.getLong(LAST_FAILURE, 0)

    fun recordFailure(nowMillis: Long) = prefs.edit().putLong(LAST_FAILURE, nowMillis).apply()

    /** The newest release seen at the last check, whether or not it is newer than the running app. Null when there was none. */
    fun known(): ReleaseInfo? {
        val version = prefs.getString(VERSION, null) ?: return null
        val url = prefs.getString(URL, null) ?: return null
        return ReleaseInfo(version, url, prefs.getLong(BYTES, 0))
    }

    /** After a check: the time, and the release found (or none, which forgets the old one). */
    fun record(nowMillis: Long, release: ReleaseInfo?) {
        prefs.edit().apply {
            putLong(LAST_CHECK, nowMillis)
            remove(LAST_FAILURE)
            if (release == null) {
                remove(VERSION); remove(URL); remove(BYTES)
            } else {
                putString(VERSION, release.version); putString(URL, release.apkUrl); putLong(BYTES, release.apkBytes)
            }
        }.apply()
    }

    fun notifiedVersion(): String? = prefs.getString(NOTIFIED, null)

    fun setNotified(version: String) = prefs.edit().putString(NOTIFIED, version).apply()

    /** Debug builds only: the address to ask instead of GitHub, so an update can be tried from the computer the emulator runs on. */
    fun debugFeed(): String? = prefs.getString(DEBUG_FEED, null)?.takeIf { it.isNotBlank() }

    fun setDebugFeed(url: String?) = prefs.edit().apply { if (url.isNullOrBlank()) remove(DEBUG_FEED) else putString(DEBUG_FEED, url) }.apply()

    fun clear() = prefs.edit().remove(LAST_CHECK).remove(LAST_FAILURE).remove(VERSION).remove(URL).remove(BYTES).remove(NOTIFIED).apply()

    private companion object {
        const val LAST_CHECK = "last_check"
        const val LAST_FAILURE = "last_failure"
        const val VERSION = "version"
        const val URL = "apk_url"
        const val BYTES = "apk_bytes"
        const val NOTIFIED = "notified_version"
        const val DEBUG_FEED = "debug_feed"
    }
}
