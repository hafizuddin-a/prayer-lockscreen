package com.prayerwallpaper.app.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Duration

/** Where updates come from: the app's own GitHub releases, and nowhere else. */
object UpdateSource {
    private const val OWNER = "hafizuddin-a"
    private const val REPO = "prayer-lockscreen"

    /** GitHub's answer for the newest published release (never a draft or a pre-release). */
    const val LATEST_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** The only place an APK may be downloaded from. GitHub then redirects to its own file servers. */
    const val DOWNLOAD_PREFIX = "https://github.com/$OWNER/$REPO/releases/download/"

    /** The page a person can open by hand. */
    const val RELEASES_PAGE = "https://github.com/$OWNER/$REPO/releases/latest"

    /** Every release carries its APK under this name, so one fixed link always gives the newest version. */
    const val APK_NAME = "prayer-wallpaper.apk"
}

/** The newest release, reduced to what an update needs. [apkBytes] is 0 when GitHub did not say. */
data class ReleaseInfo(val version: String, val apkUrl: String, val apkBytes: Long)

/** "v1.8.1" and "1.8.1-beta" are both [1, 8, 1]; null when there are no numbers in it. */
fun parseVersion(text: String): List<Int>? {
    val core = text.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
    val parts = core.split('.').map { it.toIntOrNull() ?: return null }
    return parts.takeIf { it.isNotEmpty() }
}

/** Whether [candidate] is a later version than [current]. Anything that is not a version is never newer. */
fun isNewerVersion(candidate: String, current: String): Boolean {
    val a = parseVersion(candidate) ?: return false
    val b = parseVersion(current) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/**
 * Reads GitHub's "latest release" answer. The APK is the asset named [UpdateSource.APK_NAME], or failing that the only `.apk` asset.
 * Null when it is a draft or pre-release, has no usable APK, or is not what GitHub sends.
 */
fun parseLatestRelease(body: String): ReleaseInfo? {
    val root = try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return null
    if (root["draft"]?.jsonPrimitive?.booleanOrNull == true || root["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
    val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
    val version = tag.trim().removePrefix("v").removePrefix("V")
    if (parseVersion(version) == null) return null
    val assets = (root["assets"] as? JsonArray)?.map { it.jsonObject }.orEmpty().filter { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
    val asset = assets.firstOrNull { it["name"]?.jsonPrimitive?.content == UpdateSource.APK_NAME } ?: assets.singleOrNull() ?: return null
    val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
    val size = asset["size"]?.jsonPrimitive?.longOrNull ?: 0L
    return ReleaseInfo(version, url, size.coerceAtLeast(0))
}

/**
 * Whether an APK may be downloaded from [url]. Release builds accept this app's GitHub releases only. A debug build also accepts the
 * emulator's view of the computer it runs on (`http://10.0.2.2`), so the update can be tried without publishing anything.
 */
fun isTrustedApkUrl(url: String, debug: Boolean): Boolean =
    url.startsWith(UpdateSource.DOWNLOAD_PREFIX) || (debug && url.startsWith("http://10.0.2.2:"))

/** How often the app asks GitHub on its own. A person's own "Check for updates" always asks. */
val UPDATE_CHECK_INTERVAL: Duration = Duration.ofDays(30)

fun shouldCheckForUpdate(nowMillis: Long, lastCheckMillis: Long, force: Boolean): Boolean =
    force || lastCheckMillis <= 0 || nowMillis - lastCheckMillis >= UPDATE_CHECK_INTERVAL.toMillis() || nowMillis < lastCheckMillis

/**
 * Whether the app may install an update without being asked: the person switched automatic updates on, Android lets the app install
 * packages, and this app is what installed the copy now running (Android only skips its own prompt for the installer that installed the app).
 */
fun mayInstallWithoutAsking(autoUpdate: Boolean, canInstallPackages: Boolean, installedByThisApp: Boolean): Boolean =
    autoUpdate && canInstallPackages && installedByThisApp

/**
 * What to tell a person when Android refuses an install. [status] is `PackageInstaller`'s `EXTRA_STATUS` (the numbers are Android's:
 * 2 blocked, 3 aborted, 4 invalid, 5 conflict, 6 storage, 7 incompatible). Aborted is the person saying no, so it has no message.
 */
fun installFailureMessage(status: Int): String? = when (status) {
    3 -> null
    5, 7 -> "This update is not signed like the installed app. Uninstall the app, then install the new version from the download page."
    6 -> "There isn't enough space on the phone."
    2 -> "Android blocked the update. Allow this app to install updates, then try again."
    else -> "Android couldn't install the update. Try again later."
}
