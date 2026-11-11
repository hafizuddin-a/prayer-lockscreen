package com.prayerwallpaper.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.util.Log
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.data.HttpStatusException
import com.prayerwallpaper.app.data.UrlConnectionHttp
import com.prayerwallpaper.app.logic.ReleaseInfo
import com.prayerwallpaper.app.logic.UpdateSource
import com.prayerwallpaper.app.logic.downloadLimit
import com.prayerwallpaper.app.logic.installFailureMessage
import com.prayerwallpaper.app.logic.isNewerVersion
import com.prayerwallpaper.app.logic.isTrustedApkUrl
import com.prayerwallpaper.app.logic.mayInstallWithoutAsking
import com.prayerwallpaper.app.logic.parseLatestRelease
import com.prayerwallpaper.app.logic.signersContinue
import com.prayerwallpaper.app.logic.shouldCheckForUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps this app up to date from its own GitHub releases, for the build people download from there (a Google Play build has none of it:
 * [BuildConfig.SELF_UPDATE] is false). It asks GitHub for the newest release, downloads the APK, checks it is this app and signed by the
 * same key as the copy that is running, and hands it to Android's installer. Android does the install and enforces the signature itself;
 * these checks only let the app say what is wrong in plain words.
 *
 * It never installs behind a person's back: automatic installs happen only when the person left "Keep the app up to date" on and Android
 * itself decides no prompt is needed, which it does only for the installer that installed the running copy. Otherwise a tap on Update, and
 * Android's own prompt, are needed.
 */
class UpdateManager(private val app: PrayerApp) {
    private val store = UpdateStore(app.getSharedPreferences("updates", Context.MODE_PRIVATE))
    private val notifier = UpdateNotifier(app)
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "update").apply { isDaemon = true } }
    private val http = UrlConnectionHttp(headers = mapOf("Accept" to "application/vnd.github+json", "User-Agent" to USER_AGENT))
    private val cacheDir = File(app.cacheDir, "updates")
    private val checkLock = Any()
    private val installing = AtomicBoolean(false)

    private val _state = MutableStateFlow<UpdateState>(initialState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** True while the app's screen is in front. Decides between Android's prompt and a notification when Android wants a tap. */
    @Volatile var appVisible = false

    init {
        // Whatever an earlier run left behind is either installed already or stale.
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun initialState(): UpdateState = store.known()?.takeIf(::isUpdate)?.let { UpdateState.Available(it) } ?: UpdateState.Idle

    /** Whether [release] is something to offer: a later version, from where an APK may come from. */
    private fun isUpdate(release: ReleaseInfo) = isNewerVersion(release.version, BuildConfig.VERSION_NAME) && isTrustedApkUrl(release.apkUrl, BuildConfig.DEBUG)

    private fun feedUrl(): String = (if (BuildConfig.DEBUG) store.debugFeed() else null) ?: UpdateSource.LATEST_URL

    // --- asking GitHub ---

    /** Asks in the background. [force] is a person's own "Check for updates"; otherwise the app asks at most every few hours. */
    fun checkAsync(force: Boolean) = executor.execute { checkNow(force, quiet = !force) }

    /**
     * Asks GitHub whether there is a newer release, and returns it. A quiet check (the app's own, not the person's) shows nothing while it
     * runs and says nothing if it fails. Blocking: not for the main thread.
     */
    fun checkNow(force: Boolean, quiet: Boolean): ReleaseInfo? = synchronized(checkLock) { checkLocked(force, quiet) }

    private fun checkLocked(force: Boolean, quiet: Boolean): ReleaseInfo? {
        if (!BuildConfig.SELF_UPDATE) return null
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing) return null
        val now = System.currentTimeMillis()
        if (!shouldCheckForUpdate(now, store.lastCheckMillis(), force, store.lastFailureMillis())) return store.known()?.takeIf(::isUpdate)
        if (!quiet) _state.value = UpdateState.Checking
        return try {
            val release = parseLatestRelease(http.get(feedUrl()))
            store.record(now, release)
            val newer = release?.takeIf(::isUpdate)
            _state.value = if (newer != null) UpdateState.Available(newer) else UpdateState.UpToDate(Instant.ofEpochMilli(now))
            newer
        } catch (e: HttpStatusException) {
            // No release published yet is not a problem. Anything else (rate limit, GitHub down) is tried again later.
            if (e.code == 404) {
                store.record(now, null)
                _state.value = UpdateState.UpToDate(Instant.ofEpochMilli(now))
            } else {
                Log.w(TAG, "check failed: $e")
                store.recordFailure(now)
                _state.value = if (quiet) current.keepIfUseful() else UpdateState.Failed(null, "GitHub didn't answer. Try again later.")
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "check failed: $e")
            store.recordFailure(now)
            _state.value = if (quiet) current.keepIfUseful() else UpdateState.Failed(null, "Can't reach GitHub. Check your connection.")
            null
        }
    }

    /** After a failed quiet check the screen keeps showing an update it already knew about, and nothing otherwise. */
    private fun UpdateState.keepIfUseful(): UpdateState = if (this is UpdateState.Available) this else UpdateState.Idle

    // --- installing ---

    /** A tap on Update (or Try again). */
    fun updateAsync() = executor.execute { installNow() }

    /**
     * Downloads the newest release and hands it to Android's installer. Blocking: not for the main thread. Stops at "needs permission" if
     * Android has not yet been told that this app may install apps; the screen then opens that setting and calls again when the person is back.
     */
    fun installNow() {
        if (!BuildConfig.SELF_UPDATE) return
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.NeedsInstallPermission -> s.release
            is UpdateState.Failed -> s.release ?: store.known()?.takeIf(::isUpdate)
            else -> null
        } ?: return
        if (!isUpdate(release)) {
            _state.value = UpdateState.Idle
            return
        }
        if (!installing.compareAndSet(false, true)) return
        try {
            if (!app.packageManager.canRequestPackageInstalls()) {
                _state.value = UpdateState.NeedsInstallPermission(release)
                return
            }
            _state.value = UpdateState.Downloading(release, 0)
            val file = download(release) { _state.value = UpdateState.Downloading(release, it) }
            val problem = problemWith(file)
            if (problem != null) {
                file.delete()
                _state.value = UpdateState.Failed(release, problem)
                return
            }
            _state.value = UpdateState.Installing(release)
            commit(file)
        } catch (e: IOException) {
            Log.w(TAG, "download failed: $e")
            _state.value = UpdateState.Failed(release, "Couldn't download the update. Check your connection and try again.")
        } catch (e: Exception) {
            Log.w(TAG, "install failed: $e")
            _state.value = UpdateState.Failed(release, "Couldn't install the update. Try again later.")
        } finally {
            installing.set(false)
        }
    }

    private fun download(release: ReleaseInfo, progress: (Long) -> Unit): File {
        require(isTrustedApkUrl(release.apkUrl, BuildConfig.DEBUG)) { "not an address updates may come from" }
        cacheDir.mkdirs()
        // A fixed name: nothing from the release's own text becomes part of a path.
        val target = File(cacheDir, "update.apk")
        val part = File(cacheDir, "update.apk.part")
        target.delete()
        val limit = downloadLimit(release.apkBytes)
        val connection = URL(release.apkUrl).openConnection() as HttpURLConnection
        var finished = false
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", USER_AGENT)
            // GitHub answers with a redirect to its file servers; both ends are HTTPS, so it is followed.
            if (connection.responseCode != 200) throw HttpStatusException(connection.responseCode, null)
            if (connection.contentLengthLong > limit) throw IOException("the file is larger than the $limit bytes expected")
            var total = 0L
            var reported = 0L
            connection.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        // Never write past what was expected, whatever the server keeps sending.
                        if (total + n > limit) throw IOException("the download is larger than the $limit bytes expected")
                        out.write(buffer, 0, n)
                        total += n
                        if (total - reported >= REPORT_EVERY_BYTES) {
                            progress(total)
                            reported = total
                        }
                    }
                }
            }
            progress(total)
            if (release.apkBytes > 0 && total != release.apkBytes) throw IOException("the download is $total bytes, GitHub said ${release.apkBytes}")
            if (!part.renameTo(target)) throw IOException("could not move the download into place")
            finished = true
            return target
        } finally {
            connection.disconnect()
            if (!finished) part.delete()
        }
    }

    /** Plain words for what is wrong with a downloaded APK, or null when it is this app, newer, and signed by the same key. */
    private fun problemWith(file: File): String? {
        val pm = app.packageManager
        val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return "The download is damaged. Try again."
        if (archive.packageName != app.packageName) return "The download is not for this app."
        val installed = pm.getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.longVersionCode <= installed.longVersionCode) return "This version is already installed."
        // The same key, or a key that was rotated from the installed one (Android allows that, and decides for itself in the end).
        val history = archive.signingInfo?.signingCertificateHistory.orEmpty().map(::fingerprint).toSet()
        if (!signersContinue(signers(installed), signers(archive), history)) return installFailureMessage(INCOMPATIBLE)
        return null
    }

    private fun signers(info: android.content.pm.PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map(::fingerprint).toSet()

    private fun fingerprint(signature: android.content.pm.Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Gives the APK to Android's installer. The result comes to [UpdateInstallReceiver]. */
    private fun commit(file: File) {
        val installer = app.packageManager.packageInstaller
        // An earlier attempt that was never finished would otherwise pile up.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            setSize(file.length())
            // Ask Android to skip its prompt. It does so only for the installer that installed the running copy; otherwise it asks as usual.
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("update.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val result = Intent(app, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
            // Mutable, because Android fills in the result (and, when it needs a tap, the screen to show).
            val pending = PendingIntent.getBroadcast(app, id, result, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    // --- what Android answers ---

    /** Android needs a tap to finish ([UpdateInstallReceiver] has checked the screen it names). On screen: show its prompt. Otherwise a notification, if allowed, else the Update button next time. */
    fun onNeedsConfirmation(confirm: Intent) {
        val release = store.known()?.takeIf(::isUpdate)
        // Only the flag that starts a screen of its own: nothing else the intent carried (a permission to read a file, say) goes with it.
        val prompt = Intent(confirm).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        when {
            appVisible -> app.startActivity(prompt)
            release != null && notifier.canPost() -> notifier.postReady(prompt, release)
            else -> _state.value = release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
        }
    }

    fun onInstallResult(status: Int) {
        val release = store.known()?.takeIf(::isUpdate)
        val message = installFailureMessage(status)
        _state.value = when {
            message != null -> UpdateState.Failed(release, message)
            release != null -> UpdateState.Available(release) // the person said no to Android's prompt
            else -> UpdateState.Idle
        }
    }

    // --- the screens and the monthly job ---

    /** The app came to the front: finish what was waiting for the install permission, and look for an update if it is time. */
    fun onAppResumed() {
        if (!BuildConfig.SELF_UPDATE) return
        val s = _state.value
        if (s is UpdateState.NeedsInstallPermission && app.packageManager.canRequestPackageInstalls()) updateAsync()
        if (app.settings.read().autoUpdate) checkAsync(force = false)
    }

    /** The monthly job. Not for the main thread. */
    fun runInBackground() {
        if (!BuildConfig.SELF_UPDATE) return
        val settings = app.settings.read()
        if (!settings.autoUpdate) return
        val release = checkNow(force = false, quiet = true) ?: return
        val allowed = mayInstallWithoutAsking(settings.autoUpdate, app.packageManager.canRequestPackageInstalls(), installedByThisApp())
        // Never replace the app while someone is using it.
        if (allowed && !appVisible) {
            installNow()
        } else if (store.notifiedVersion() != release.version && notifier.canPost()) {
            notifier.postAvailable(release)
            store.setNotified(release.version)
        }
    }

    /** Whether the copy now running was installed by this app, which is what lets Android skip its prompt for the next update. */
    private fun installedByThisApp(): Boolean =
        app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName == app.packageName

    // --- debug builds only ---

    /** Test hooks for the debug screen: which address to ask, and a clean slate. */
    fun debugSetFeed(url: String?) = store.setDebugFeed(url)

    fun debugReset() {
        store.clear()
        _state.value = UpdateState.Idle
    }

    private companion object {
        const val TAG = "PrayerUpdate"
        val USER_AGENT = "PrayerLiveWallpaper/${BuildConfig.VERSION_NAME}"
        const val REPORT_EVERY_BYTES = 128 * 1024L

        /** `PackageInstaller.STATUS_FAILURE_INCOMPATIBLE`, spelt out because the message table is plain numbers. */
        const val INCOMPATIBLE = 7
    }
}
