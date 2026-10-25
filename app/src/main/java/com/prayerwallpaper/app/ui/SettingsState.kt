package com.prayerwallpaper.app.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.data.HttpStatusException
import com.prayerwallpaper.app.data.PrayerData
import com.prayerwallpaper.app.logic.PrayerSnapshot
import com.prayerwallpaper.app.data.Timetable
import com.prayerwallpaper.app.data.snapshot
import kotlinx.coroutines.delay
import java.time.Instant
import com.prayerwallpaper.app.settings.AppSettings
import com.prayerwallpaper.app.settings.ColorCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** The settings as a screen holds them: read when the screen opens and when the app comes back, written on every [change]. */
@Stable
class SettingsState(private val app: PrayerApp, initial: AppSettings) {
    var settings by mutableStateOf(initial)
        private set

    fun change(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings)
        settings = next
        app.settings.write(next)
    }

    /** Pick up anything another screen, a dialog or the wallpaper engine changed. */
    fun reload() {
        settings = app.settings.read()
    }
}

@Composable
fun rememberSettings(app: PrayerApp, resumes: Int): SettingsState {
    val state = remember { SettingsState(app, app.settings.read()) }
    LaunchedEffect(resumes) { state.reload() }
    return state
}

/** Whether the lock screen and the home screen are showing this app. Read again each time the app comes back to the front. */
@Stable
class WallpaperStatus(val lockIsOurs: Boolean, val homeIsOurs: Boolean)

@Composable
fun rememberWallpaperStatus(resumes: Int): WallpaperStatus {
    val context = LocalContext.current
    val capture = remember { ColorCapture(context) }
    val status = remember(resumes) { WallpaperStatus(capture.lockIsThisApp(), capture.homeIsThisApp()) }
    if (BuildConfig.DEBUG) {
        // Evidence for the setup flow: what the system hands out for the lock screen right now.
        LaunchedEffect(resumes) {
            val c = capture.readLockColors()
            Log.i("PrayerSettings", "lock colours now (ours=${status.lockIsOurs}, home ours=${status.homeIsOurs}): " + (c?.let { "%08X %08X %08X".format(it.primary, it.secondary, it.tertiary ?: 0) } ?: "none"))
        }
    }
    return status
}

/**
 * The downloaded prayer times and a way to fetch them again. The wallpaper never fetches, so the app does it as soon as it
 * opens when the place uses the MUIS timetable (a no-op while the cache is fresh): pass [auto] on the screen that should start it.
 */
@Stable
class DataRefresh(private val app: PrayerApp, private val scope: CoroutineScope) {
    var data: PrayerData by mutableStateOf(app.repository.data())
        private set
    var status by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set

    fun refresh(force: Boolean) {
        scope.launch {
            busy = true
            status = "Fetching…"
            status = withContext(Dispatchers.IO) {
                try {
                    val result = app.repository.refresh(force)
                    if (result.fetchedYears.isEmpty()) "Up to date, nothing to fetch." else "Fetched ${result.fetchedYears.joinToString()}; ${result.rejectedRows} rows rejected."
                } catch (e: HttpStatusException) {
                    if (e.code == 429) "Too many requests to data.gov.sg. Try again in a minute." else "Server error: HTTP ${e.code}"
                } catch (e: IOException) {
                    "Can't reach data.gov.sg. Check your connection."
                }
            }
            data = app.repository.data()
            busy = false
        }
    }
}

@Composable
fun rememberDataRefresh(app: PrayerApp, usesMuis: Boolean, auto: Boolean): DataRefresh {
    val scope = rememberCoroutineScope()
    val refresh = remember { DataRefresh(app, scope) }
    if (auto) LaunchedEffect(usesMuis) { if (usesMuis) refresh.refresh(force = false) }
    return refresh
}

/** The times as of now, moved on every 30 seconds while the screen is open: what "Today's times" lists and the preview's spoken description says. */
@Stable
class LiveTimes(val snapshot: PrayerSnapshot, val now: Instant, val is24Hour: Boolean)

@Composable
fun rememberLiveTimes(app: PrayerApp, timetable: Timetable, resumes: Int): LiveTimes {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    val snapshot = remember(timetable, tick, resumes) { timetable.snapshot(app.clock) }
    val now = remember(snapshot, tick) { app.clock.instant() }
    return LiveTimes(snapshot, now, android.text.format.DateFormat.is24HourFormat(context))
}
