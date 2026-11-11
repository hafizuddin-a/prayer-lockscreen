package com.prayerwallpaper.app.wallpaper

import android.app.KeyguardManager
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import java.time.Instant
import com.prayerwallpaper.app.AppClock
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.inBackground
import com.prayerwallpaper.app.data.snapshot
import com.prayerwallpaper.app.logic.PrayerSnapshot
import com.prayerwallpaper.app.logic.SINGAPORE
import com.prayerwallpaper.app.logic.countdown
import com.prayerwallpaper.app.logic.earliest
import com.prayerwallpaper.app.logic.nextMidnight
import com.prayerwallpaper.app.logic.nextMinuteStart
import com.prayerwallpaper.app.logic.redrawDelayMs

/** What one draw put on the screen: the snapshot, and whether a countdown line is part of it. */
private class Frame(val snapshot: PrayerSnapshot, val countdown: Boolean)

/**
 * Draws on the lock screen engine only: the home engine gets the background and no list. The
 * engine never touches the network; it reads the cache the refresh job maintains.
 *
 * It redraws only when something changed: the surface, the screen turning on, the next prayer
 * boundary while visible, or the system clock or zone changing. While the countdown line is on the
 * screen it also redraws on each whole minute, because that is when the line's minutes change. No
 * render loop, no alarms, and nothing is scheduled while the screen is off or the list is not shown.
 */
class PrayerWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = PrayerEngine()

    private inner class PrayerEngine : Engine() {
        private val app = application as PrayerApp
        private val renderer = WallpaperRenderer(this@PrayerWallpaperService)
        private val handler = Handler(Looper.getMainLooper())
        private var width = 0
        private var height = 0
        private var visible = false

        private val boundaryTimer = Runnable { redraw("boundary") }
        // SharedPreferences keeps listeners weakly, so this field is what keeps it alive.
        private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            notifyColorsChanged()
            if (visible) redraw("settings")
        }
        private val clockChanged = { if (visible) redraw("fake clock") }
        // A reminder alarm arrived on another thread: the highlight may have moved, so draw again here, on this one.
        private val reminderArrived = { handler.post { if (visible) redraw("reminder") }; Unit }
        private val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (visible) redraw(intent.action ?: "time change")
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_USER_PRESENT) // unlocked: hide the list if this engine is also the home screen
            }
            registerReceiver(timeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            AppClock.addChangeListener(clockChanged)
            RedrawSignal.addListener(reminderArrived)
            app.settings.register(settingsListener)
            Log.i(TAG, "engine created: flags=${flagNames(wallpaperFlags)} preview=$isPreview")
        }

        override fun onDestroy() {
            handler.removeCallbacks(boundaryTimer)
            AppClock.removeChangeListener(clockChanged)
            RedrawSignal.removeListener(reminderArrived)
            app.settings.unregister(settingsListener)
            unregisterReceiver(timeReceiver)
            Log.i(TAG, "engine destroyed")
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width
            this.height = height
            redraw("surface changed")
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            redraw("surface redraw")
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            this.visible = visible
            // The current prayer is recomputed from the clock on every draw, so waking is enough.
            if (visible) {
                redraw("visible")
                // Reminders: make sure the next alarm is still set. Throttled to once in 30 minutes and off the main thread.
                inBackground(TAG) { app.reminders.ensureScheduledThrottled("visible") }
                // Widgets: redraw them if they still show an older picture than the latest boundary. Throttled to once in five minutes.
                inBackground(TAG) { app.widgets.refreshIfStale() }
            } else {
                handler.removeCallbacks(boundaryTimer)
            }
        }

        /** The system themes the clock and shade from these, so they come from what is actually painted. */
        override fun onComputeColors(): WallpaperColors =
            WallpaperColors.fromBitmap(renderer.sample(app.looks.current(), width.takeIf { it > 0 } ?: 1080, height.takeIf { it > 0 } ?: 2400))

        /**
         * Previews report the destination they stand for too, so the home preview stays list-free. When
         * the user applied the wallpaper to both screens, Android runs one engine for both, and the list
         * is drawn only while the phone is locked so it never sits behind the home screen icons.
         */
        private fun showsList(): Boolean {
            val flags = wallpaperFlags
            if (flags and WallpaperManager.FLAG_LOCK == 0) return false
            if (flags and WallpaperManager.FLAG_SYSTEM == 0) return true
            return getSystemService(KeyguardManager::class.java).isKeyguardLocked
        }

        /** Draws, then arms one timer for the next change, but only while the screen is on. */
        private fun redraw(reason: String) {
            handler.removeCallbacks(boundaryTimer)
            val frame = draw() ?: return
            val snapshot = frame.snapshot
            if (visible) {
                val now = app.clock.instant()
                // The next prayer boundary, or midnight in Singapore if that is sooner and the Hijri date is showing, or the
                // next whole minute if that is sooner and the countdown is on the screen.
                val boundary = earliest(snapshot.nextChange, hijriBoundary())
                val due = earliest(boundary, if (frame.countdown) nextMinuteStart(now) else null)
                redrawDelayMs(due, now)?.let { handler.postDelayed(boundaryTimer, it) }
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "redraw($reason) visible=$visible highlight=${snapshot.highlight} nextChange=${snapshot.nextChange} countdown=${frame.countdown}")
            }
        }

        /**
         * When the Hijri date next changes at a time that is not already a prayer boundary: 00:00 in Singapore, or null when
         * no line is showing or the date changes at Maghrib (the highlight timer already fires then).
         */
        private fun hijriBoundary(): Instant? {
            val settings = app.settings.read()
            val midnight = settings.showHijri && !settings.hijriAtMaghrib && settings.place.usesMuis && !app.hijri.isEmpty
            return if (midnight) nextMidnight(app.clock.instant(), SINGAPORE) else null
        }

        private fun draw(): Frame? {
            if (width == 0 || height == 0) return null
            val holder = surfaceHolder
            val canvas = holder.lockCanvas() ?: return null
            try {
                val settings = app.settings.read()
                val timetable = app.timetable(settings)
                val snapshot = timetable.snapshot(app.clock)
                val list = showsList()
                // Only where the list itself is drawn, so a shared home screen never ticks every minute for a line it hides.
                val countdown = if (list && settings.showCountdown) snapshot.countdown(app.clock.instant(), settings.effectiveNames) else null
                renderer.draw(
                    canvas, width, height, snapshot, missing = timetable.whenMissing, drawList = list, look = app.looks.current(),
                    names = settings.effectiveNames, hijri = app.hijriLine(settings, timetable), hijriAlign = settings.hijriAlign,
                    countdown = countdown, access = app.accessOptions(settings),
                )
                return Frame(snapshot, countdown != null)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }
    }

    private fun flagNames(flags: Int) = buildList {
        if (flags and WallpaperManager.FLAG_SYSTEM != 0) add("SYSTEM")
        if (flags and WallpaperManager.FLAG_LOCK != 0) add("LOCK")
    }.ifEmpty { listOf("none") }.joinToString("|")

    private companion object {
        const val TAG = "PrayerWallpaper"
    }
}
