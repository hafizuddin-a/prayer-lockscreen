package com.prayerwallpaper.app.logic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.URISyntaxException
import java.time.Duration

/** Where updates come from: the app's own GitHub releases, and nowhere else. */
object UpdateSource {
    private const val OWNER = "hafizuddin-a"
    private const val REPO = "prayer-lockscreen"

    /** GitHub's answer for the newest published release (never a draft or a pre-release). */
    const val LATEST_URL = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    /** The only place an APK may be downloaded from: this host and path. GitHub then redirects to its own file servers. */
    const val DOWNLOAD_HOST = "github.com"
    const val DOWNLOAD_PATH = "/$OWNER/$REPO/releases/download/"

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
    // The same numbers: the final release comes after a pre-release of it (1.9.0 after 1.9.0-rc1), and never the other way round.
    return isPreRelease(current) && !isPreRelease(candidate)
}

private fun isPreRelease(text: String): Boolean = text.trim().removePrefix("v").removePrefix("V").substringBefore('+').contains('-')

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
 * Whether an APK may be downloaded from [url]: this app's GitHub releases, over HTTPS, with nothing odd in the address. Release builds
 * accept nothing else. A debug build also accepts the emulator's view of the computer it runs on (`http://10.0.2.2:<port>`), so the update
 * can be tried without publishing anything. Dot segments, a login part, a fragment or an odd port are refused, so a path under the
 * releases folder cannot climb out of it.
 */
fun isTrustedApkUrl(url: String, debug: Boolean): Boolean {
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        return false
    }
    if (uri.rawUserInfo != null || uri.rawFragment != null) return false
    val path = uri.rawPath ?: return false
    val hidden = listOf("%2e", "%2f", "%5c")
    if ('\\' in path || hidden.any { path.contains(it, ignoreCase = true) } || path.split('/').any { it == "." || it == ".." }) return false
    return when (uri.scheme) {
        "https" -> uri.host.equals(UpdateSource.DOWNLOAD_HOST, ignoreCase = true) && uri.port == -1 && path.startsWith(UpdateSource.DOWNLOAD_PATH)
        "http" -> debug && uri.host == "10.0.2.2" && uri.port != -1
        else -> false
    }
}

/** The largest APK the app will accept: this app is about 4 MB, so anything near this is not what GitHub was asked for. */
const val MAX_APK_BYTES = 64L * 1024 * 1024

/** How many bytes a download may be: what GitHub said the file is, or the general limit when it did not say. */
fun downloadLimit(declaredBytes: Long): Long = if (declaredBytes in 1..MAX_APK_BYTES) declaredBytes else MAX_APK_BYTES

/** How often the app asks GitHub on its own. A person's own "Check for updates" always asks. */
val UPDATE_CHECK_INTERVAL: Duration = Duration.ofDays(30)

/** After a check that failed (no network, GitHub busy or rate limiting), the app's own checks wait this long before trying again. */
val UPDATE_RETRY_AFTER_FAILURE: Duration = Duration.ofHours(6)

fun shouldCheckForUpdate(nowMillis: Long, lastCheckMillis: Long, force: Boolean, lastFailureMillis: Long = 0): Boolean {
    if (force) return true
    if (lastFailureMillis > 0 && nowMillis >= lastFailureMillis && nowMillis - lastFailureMillis < UPDATE_RETRY_AFTER_FAILURE.toMillis()) return false
    return lastCheckMillis <= 0 || nowMillis - lastCheckMillis >= UPDATE_CHECK_INTERVAL.toMillis() || nowMillis < lastCheckMillis
}

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

/**
 * Whether an update's signing can be right for the installed app: the same signers, or the installed signers appear in the signing history
 * the update carries (a key that was rotated, which Android allows). Android makes the final decision; this only lets the app explain a
 * mismatch in plain words without refusing a rotation. Each signer is its certificate's SHA-256.
 */
fun signersContinue(installed: Set<String>, updateCurrent: Set<String>, updateHistory: Set<String>): Boolean =
    installed.isNotEmpty() && updateCurrent.isNotEmpty() && (installed == updateCurrent || installed.all { it in updateHistory })

private val CONFIRM_ACTIONS = setOf("android.content.pm.action.CONFIRM_INSTALL", "android.content.pm.action.CONFIRM_PRE_APPROVAL")

/**
 * Whether the screen Android hands back after an install ("tap to finish") is what it should be before the app starts it with its own
 * standing: the installer's confirm action, aimed at another package (never this app's own screens), carrying no data to open, and, when it
 * names a session, one of this app's own.
 */
fun isSafeInstallConfirmation(
    action: String?, targetPackage: String?, ownPackage: String, hasData: Boolean, sessionId: Int?, ownSessionIds: Set<Int>,
): Boolean = action in CONFIRM_ACTIONS && targetPackage != null && targetPackage != ownPackage && !hasData &&
    (sessionId == null || sessionId in ownSessionIds)
