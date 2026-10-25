package com.prayerwallpaper.app

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import android.util.Log
import com.prayerwallpaper.app.data.CalculatedTimetable
import com.prayerwallpaper.app.data.MuisTimetable
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.data.PrayerApi
import com.prayerwallpaper.app.data.PrayerData
import com.prayerwallpaper.app.data.PrayerRepository
import com.prayerwallpaper.app.data.Place
import com.prayerwallpaper.app.data.PrayerStore
import com.prayerwallpaper.app.data.Timetable
import com.prayerwallpaper.app.data.UrlConnectionHttp
import com.prayerwallpaper.app.data.loadHijriCalendar
import com.prayerwallpaper.app.data.loadIslamicEvents
import com.prayerwallpaper.app.data.snapshot
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.logic.CalcRequest
import com.prayerwallpaper.app.logic.HijriCalendar
import com.prayerwallpaper.app.logic.HijriLine
import com.prayerwallpaper.app.logic.IslamicEvents
import com.prayerwallpaper.app.logic.SINGAPORE
import com.prayerwallpaper.app.logic.SystemAccessibility
import com.prayerwallpaper.app.settings.AppSettings
import com.prayerwallpaper.app.settings.PhotoStore
import com.prayerwallpaper.app.reminders.AndroidAlarmGateway
import com.prayerwallpaper.app.reminders.AndroidReminderNotifier
import com.prayerwallpaper.app.logic.HealthReport
import com.prayerwallpaper.app.logic.healthReport
import com.prayerwallpaper.app.reminders.ReminderHistoryStore
import com.prayerwallpaper.app.reminders.readHealthInput
import com.prayerwallpaper.app.reminders.Reminders
import com.prayerwallpaper.app.settings.SettingsStore
import com.prayerwallpaper.app.wallpaper.RedrawSignal
import com.prayerwallpaper.app.wallpaper.LookProvider
import com.prayerwallpaper.app.widgets.WidgetUpdater
import com.prayerwallpaper.app.work.ReminderCheckWorker
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import com.prayerwallpaper.app.work.RefreshWorker
import java.io.File
import java.time.Clock
import java.time.LocalDate

class PrayerApp : Application() {
    /**
     * What the screens and the wallpaper read. Debug builds can fake it; release is the system clock.
     * It has no zone of its own: [com.prayerwallpaper.app.data.snapshot] reads it in the place's zone.
     */
    val clock: Clock = AppClock.clock

    val repository: PrayerRepository by lazy {
        PrayerRepository(
            PrayerStore(File(filesDir, "prayer_times.json")),
            PrayerApi(UrlConnectionHttp(BuildConfig.DATA_GOV_API_KEY)),
            // Refresh decisions always use real time, so a faked date cannot trigger fetches or prune the cache.
            Clock.system(SINGAPORE),
        )
    }

    /** The Hijri date in Singapore's MUIS calendar, from the bundled month table. Unknown outside what the table covers. */
    val hijri: HijriCalendar by lazy { loadHijriCalendar(this, clock) }

    /**
     * The Hijri date line to draw now (the date, and the Islamic event on that day if any), or null for none: it is off in the settings, the place is not Singapore (the
     * dates are MUIS's Singapore calendar, and a calculated Hijri date is exactly what this app must not show), or the
     * month table does not give a certain answer for today.
     */
    fun hijriLine(current: AppSettings, timetable: Timetable): HijriLine? {
        if (!current.showHijri || !current.place.usesMuis) return null
        // From Maghrib the date is the next day's, and that needs today's Maghrib from the MUIS timetable.
        val maghrib = if (current.hijriAtMaghrib) {
            val today = LocalDate.now(clock.withZone(timetable.zone))
            timetable.days(today)[today]?.get(Prayer.MAGHRIB)
        } else {
            null
        }
        val date = hijri.today(current.hijriAtMaghrib, maghrib) ?: return null
        val event = if (current.showHijriEvents) EventOverride.name ?: islamicEvents.on(date) else null
        return HijriLine(date, event)
    }

    /** The yearly Islamic events from MUIS's key dates, found by the Hijri day of the month. */
    val islamicEvents: IslamicEvents by lazy { loadIslamicEvents(this) }

    /**
     * The user's own accessibility settings as Android reports them right now, from documented public APIs only: the font
     * scale and the bold-text adjustment from the app's configuration, and the contrast level from [UiModeManager].
     */
    fun systemAccessibility(): SystemAccessibility {
        val config = resources.configuration
        val boldAdjustment = config.fontWeightAdjustment.takeIf { it != Configuration.FONT_WEIGHT_ADJUSTMENT_UNDEFINED } ?: 0
        val contrast = getSystemService(UiModeManager::class.java)?.contrast ?: 0f
        return SystemAccessibility(config.fontScale, boldAdjustment, contrast)
    }

    /** The accessibility options to draw with under [current]: the phone's own settings if it follows them. */
    fun accessOptions(current: AppSettings): AccessOptions =
        if (current.followSystem) current.accessOptions(systemAccessibility()) else current.accessOptions(SystemAccessibility())

    val settings: SettingsStore by lazy { SettingsStore(getSharedPreferences("settings", MODE_PRIVATE)) }

    /**
     * Moves the list to [place]. Singapore uses the downloaded MUIS timetable, so only it needs the daily
     * refresh job; any other place is calculated here and the job is stopped.
     */
    fun choosePlace(place: Place, sync: Boolean = false) {
        settings.update(sync) { it.withPlace(place) }
        syncBackgroundJobs()
        // The times for the new place decide the next reminder.
        Thread { reminders.ensureScheduled("place changed") }.start()
    }

    /** Keeps the two daily jobs as the settings need them: the data refresh for Singapore, the reminder check while reminders are on. */
    fun syncBackgroundJobs() {
        val current = settings.read()
        RefreshWorker.setEnabled(this, current.place.usesMuis)
        ReminderCheckWorker.setEnabled(this, current.remindersOn)
    }

    private var calculated: Pair<CalcRequest, CalculatedTimetable>? = null

    /**
     * The times to show for the chosen place: the cached MUIS timetable for Singapore, otherwise times
     * calculated on the phone. Cheap enough to ask on every draw: the cache read is memoised and a
     * calculated timetable is kept while the settings behind it stay the same.
     */
    fun timetable(current: AppSettings = settings.read(), data: PrayerData = repository.data()): Timetable {
        if (current.place.usesMuis) return MuisTimetable(data)
        val request = current.calcRequest()
        return synchronized(this) {
            calculated?.takeIf { it.first == request }?.second
                ?: CalculatedTimetable(request).also { calculated = request to it }
        }
    }

    /** The reminder scheduler. Alarms use the real clock, never the debug fake one: an alarm is a real moment. */
    val reminders: Reminders by lazy {
        Reminders(
            settings = { settings.read() },
            timetable = { timetable(it) },
            store = ReminderHistoryStore(File(filesDir, "reminder_history.json")),
            gateway = AndroidAlarmGateway(this, exactAllowed = { BuildConfig.EXACT_ALARMS }),
            notifier = AndroidReminderNotifier(this),
            is24Hour = { android.text.format.DateFormat.is24HourFormat(this) },
            // A reminder alarm moves things on: the wallpaper redraws, and so do the widgets (an exact alarm, if granted, is on time).
            redraw = { RedrawSignal.fire(); widgets.updateAll("reminder alarm") },
            debugDays = ReminderDebug::days,
            debugMethod = ReminderDebug::method,
            log = { if (BuildConfig.DEBUG) Log.i("Reminders", it) },
        )
    }

    /** The rows and one-line status of the reminder health screen, as of now. Reads files and system services: not for the main thread. */
    fun reminderHealth(): HealthReport {
        val zone = timetable().zone
        return healthReport(readHealthInput(this), java.time.Instant.now(), zone)
    }

    /** The home screen widgets. Reads the cache and the settings only; never fetches anything. */
    val widgets: WidgetUpdater by lazy { WidgetUpdater(this) }

    val photos: PhotoStore by lazy { PhotoStore(this) }
    val looks: LookProvider by lazy { LookProvider(settings, photos) }

    // SharedPreferences keeps its listeners weakly, so this field is what keeps the listener alive.
    private val widgetSettingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key !in LOCK_SCREEN_ONLY_KEYS) widgetsRefreshSoon() }
    private val handler = Handler(Looper.getMainLooper())
    private val refreshWidgets = Runnable { Thread { widgets.updateAll("settings") }.start() }

    /** Any setting can change what a widget shows (the place, the names, the Hijri line, the style). Several changes at once become one update. */
    private fun widgetsRefreshSoon() {
        handler.removeCallbacks(refreshWidgets)
        handler.postDelayed(refreshWidgets, 300)
    }

    /** The phone's font scale, language or density changed while the app was running: the widgets are laid out for the old ones. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Thread { widgets.refreshIfStale("configuration changed") }.start()
    }

    override fun onCreate() {
        super.onCreate()
        ReminderDebug.install(this)
        settings.register(widgetSettingsListener)
        // Only Singapore downloads anything. A calculated place needs no refresh job at all.
        syncBackgroundJobs()
        if (BuildConfig.DEBUG) logToday()
    }

    /** Debug aid for the Phase 0 checkpoint: today's six times from the cache, in logcat. */
    private fun logToday() = Thread {
        val snapshot = timetable().snapshot(clock)
        val times = snapshot.day?.let { day -> day.times.joinToString { it.toString() } } ?: "no data for today"
        Log.i("PrayerData", "day=${snapshot.day?.date} times=[$times] highlight=${snapshot.highlight}")
        Log.i("PrayerData", "system accessibility: ${systemAccessibility()} -> ${accessOptions(settings.read())}")
        Log.i("PrayerData", "hijriLine=${hijriLine(settings.read(), timetable())?.let { it.date.text + (it.event?.let { e -> " | $e" } ?: "") } ?: "none"} certainThrough=${hijri.lastKnownDate} daysLeft=${hijri.daysLeft()}")
    }.start()
}

/** Settings only the lock screen's drawing uses, and that change many times a second while a slider is dragged: they do not touch the widgets. */
private val LOCK_SCREEN_ONLY_KEYS = setOf("list_top", "dim", "photo_zoom", "photo_pan_x", "photo_pan_y")
