package com.prayerwallpaper.app.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.SizeF
import android.widget.RemoteViews
import com.prayerwallpaper.app.ReminderDebug
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.data.PrayerDay
import com.prayerwallpaper.app.logic.WidgetHistory
import com.prayerwallpaper.app.logic.lastWidgetBoundary
import com.prayerwallpaper.app.logic.nextWidgetBoundary
import com.prayerwallpaper.app.logic.widgetLook
import com.prayerwallpaper.app.logic.widgetState
import com.prayerwallpaper.app.logic.widgetTextMultiplier
import com.prayerwallpaper.app.logic.theme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The widget providers, in the order they are offered. */
val WIDGET_PROVIDERS: List<Class<*>> = listOf(NextPrayerWidget::class.java, TodayTimesWidget::class.java, PrayerStripWidget::class.java)

/**
 * Builds and applies the RemoteViews of every widget on the home screen. It reads only what is already on the phone (the cached
 * timetable, the settings, the wallpaper's colours), works everything out from the app's clock, and never fetches anything or does
 * heavy work, so it is cheap to call whenever something might have changed.
 */
class WidgetUpdater(private val app: PrayerApp) {
    private val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    private val scheduler = WidgetScheduler(app)
    val history = WidgetHistoryStore(File(app.filesDir, "widget_history.json"))
    /** When the widgets were last redrawn, kept in memory so the wallpaper's check costs no file read. Null until read once. */
    @Volatile private var lastUpdateCache: Instant? = null
    @Volatile private var lastUpdateKnown = false
    @Volatile private var envKeyCache: String? = null

    /**
     * What the widgets' sizes and drop decisions were worked out for: the phone's font scale, language, clock style and screen density. They
     * are laid out for these, so when one changes the widgets are laid out again, even though no boundary has passed.
     */
    private fun envKey(): String {
        val c = app.resources.configuration
        return "${c.fontScale}|${c.locales[0].toLanguageTag()}|${android.text.format.DateFormat.is24HourFormat(app)}|${c.densityDpi}"
    }

    fun widgetIds(provider: Class<*>): IntArray = manager.getAppWidgetIds(ComponentName(app, provider))

    fun hasWidgets(): Boolean = WIDGET_PROVIDERS.any { widgetIds(it).isNotEmpty() }

    /** How many widgets are on the home screen. */
    fun widgetCount(): Int = WIDGET_PROVIDERS.sumOf { widgetIds(it).size }

    /**
     * Redraws every widget, records how late that was for the boundary it follows, and sets the alarm for the next boundary. Safe to
     * call whenever anything might have changed: it reads the cache and the settings and does nothing heavy. With no widget left it
     * cancels the alarms instead.
     */
    @Synchronized
    fun updateAll(reason: String) {
        if (!hasWidgets()) {
            scheduler.cancel()
            return
        }
        val moment = moment()
        val env = environment(moment)
        for (provider in WIDGET_PROVIDERS) {
            for (id in widgetIds(provider)) {
                val views = viewsFor(provider, env, manager.getAppWidgetOptions(id)) ?: continue
                manager.updateAppWidget(id, views)
            }
        }
        val updated = history.update { it.afterUpdate(moment.now, lastWidgetBoundary(moment.days, moment.now, moment.zone), reason, envKey()) }
        lastUpdateCache = updated.lastUpdate
        envKeyCache = updated.envKey
        lastUpdateKnown = true
        scheduler.schedule(nextWidgetBoundary(moment.days, moment.now, moment.zone), moment.now, moment.realNow)
        Log.i(TAG, "updated ($reason) at ${moment.now}, next boundary ${nextWidgetBoundary(moment.days, moment.now, moment.zone)}")
    }

    private val ticket = java.util.concurrent.atomic.AtomicLong()

    /**
     * For the widget providers, whose callbacks come in bursts (adding one widget, or an update of the app, calls each provider in turn):
     * waits a moment, and only the last call of a burst goes on to redraw, once, for all of them. Blocks, so not for the main thread.
     */
    fun updateAllDebounced(reason: String) {
        val mine = ticket.incrementAndGet()
        Thread.sleep(DEBOUNCE_MS)
        if (ticket.get() == mine) updateAll(reason)
    }

    /**
     * Cheap, and safe to call as often as the wallpaper becomes visible: one comparison in memory. It redraws the widgets only if a
     * boundary has passed since they were last redrawn (or the font scale, language or clock style has changed), so in effect once per boundary however often it is asked. (The spec's "at most
     * once every five minutes" would leave the widgets stale for up to five minutes right after a boundary.)
     */
    fun refreshIfStale(reason: String = "wallpaper visible") {
        if (!hasWidgets()) return
        val moment = moment()
        if (!lastUpdateKnown) {
            val stored = history.read()
            lastUpdateCache = stored.lastUpdate
            envKeyCache = stored.envKey
            lastUpdateKnown = true
        }
        val last = lastUpdateCache
        if (last == null || last < lastWidgetBoundary(moment.days, moment.now, moment.zone) || envKeyCache != envKey()) updateAll(reason)
    }

    /** The safety alarm: redraw only if nothing has since [boundary], and make sure the next boundary alarm is set. */
    fun onSafetyAlarm(boundary: Instant?) {
        val last = history.read().lastUpdate
        if (boundary == null || last == null || last < boundary) updateAll("safety alarm") else if (!scheduler.isSet()) updateAll("alarm reset")
    }

    /** What the health screen shows. Reads a file and the alarm service: not for the main thread. */
    fun health(): WidgetHealth {
        val moment = moment()
        return WidgetHealth(
            history = history.read(), widgets = widgetCount(), alarmsSet = scheduler.isSet(),
            nextBoundary = nextWidgetBoundary(moment.days, moment.now, moment.zone), zone = moment.zone, now = moment.now,
        )
    }

    /** The timetable around now, in the place's zone, as the widgets read it. A debug build may swap in its fast test day. */
    private class Moment(val now: Instant, val elapsed: Long, val realNow: Long, val zone: ZoneId, val days: Map<LocalDate, PrayerDay>)

    private fun moment(): Moment {
        val settings = app.settings.read()
        val timetable = app.timetable(settings)
        val zone = timetable.zone
        val now = app.clock.instant()
        val days = ReminderDebug.days(zone) ?: timetable.days(LocalDate.now(app.clock.withZone(zone)))
        return Moment(now, SystemClock.elapsedRealtime(), System.currentTimeMillis(), zone, days)
    }

    /**
     * Android 15 and later lets an app publish a preview for the widget picker: the widget's own picture, with the real times, instead of the
     * sample times in its preview layout. The system limits how often this may be called, so a refusal is only logged. Older Androids keep
     * the preview layout.
     */
    fun publishPreviews() {
        if (android.os.Build.VERSION.SDK_INT < 35) return
        val env = environment()
        for (provider in WIDGET_PROVIDERS) {
            val views = build(provider, env, previewSize(provider)) ?: continue
            try {
                manager.setWidgetPreview(ComponentName(app, provider), android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, views)
            } catch (e: RuntimeException) {
                Log.w(TAG, "preview for ${provider.simpleName} not published: $e")
            }
        }
        Log.i(TAG, "previews published")
    }

    /** The state and look as of now, shared by every widget of one update. */
    fun environment(): WidgetEnv = environment(moment())

    private fun environment(moment: Moment): WidgetEnv {
        val settings = app.settings.read()
        val timetable = app.timetable(settings)
        val state = widgetState(moment.days, moment.now, moment.zone, settings.effectiveNames, app.hijriLine(settings, timetable)?.date, timetable.whenMissing)

        val access = app.accessOptions(settings)
        val look = widgetLook(settings.widgetStyle, settings.widgetCard, settings.glassOpacity, settings.widgetFollowAccess, access, settings.widgetGlass)
        val backdrop = Backdrop.read(app)
        val resources = app.resources
        val fontScale = resources.configuration.fontScale
        return WidgetEnv(
            context = app, now = moment.now, elapsed = moment.elapsed, state = state, look = look, theme = look.theme(backdrop.color, backdrop.luminance),
            fontScale = fontScale, textMultiplier = widgetTextMultiplier(look.textSize, fontScale),
            is24Hour = android.text.format.DateFormat.is24HourFormat(app), locale = resources.configuration.locales[0],
            openApp = openAppIntent(app), measure = PaintMeasure(resources.displayMetrics.density),
        )
    }

    /** One RemoteViews for the widget, or one per size the launcher may show it at, so each is laid out for its own room. */
    private fun viewsFor(provider: Class<*>, env: WidgetEnv, options: Bundle): RemoteViews? {
        if (build(provider, env, SizeF(1f, 1f)) == null) return null
        val sizes = widgetSizes(options)
        return if (sizes.size > 1) RemoteViews(sizes.associateWith { build(provider, env, it)!! }) else build(provider, env, sizes.first())
    }

    /** The RemoteViews of one widget laid out for [size] (dp). The settings preview and the debug renderer use this too, so they cannot drift from the widget. */
    fun build(provider: Class<*>, env: WidgetEnv, size: SizeF): RemoteViews? = when (provider) {
        NextPrayerWidget::class.java -> NextPrayerView.build(env, size)
        TodayTimesWidget::class.java -> TodayTimesView.build(env, size)
        PrayerStripWidget::class.java -> PrayerStripView.build(env, size)
        else -> null
    }

    private companion object {
        const val TAG = "WidgetUpdater"
        const val DEBOUNCE_MS = 700L
    }
}

/**
 * The sizes, in dp, a launcher may draw a widget at (usually one for portrait, one for landscape). The widget is laid out for
 * each, so none is ever clipped. Without them, the minimum width and the maximum height of the portrait case.
 */
fun widgetSizes(options: Bundle): List<SizeF> {
    val listed = options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java).orEmpty().distinct()
    if (listed.isNotEmpty()) return listed
    val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 146
    val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0).takeIf { it > 0 } ?: 72
    return listOf(SizeF(minW.toFloat(), maxH.toFloat()))
}

/** What the widget health screen shows. [alarmsSet] only says the pending intents exist: Android does not say whether the alarm is still registered. */
class WidgetHealth(
    val history: WidgetHistory,
    val widgets: Int,
    val alarmsSet: Boolean,
    val nextBoundary: Instant,
    val zone: ZoneId,
    val now: Instant,
)

/** The size, in dp, a widget of this kind usually is on a phone: the size its picker preview and the settings preview are drawn at. */
fun previewSize(provider: Class<*>): SizeF = when (provider) {
    NextPrayerWidget::class.java -> SizeF(170f, 72f)
    TodayTimesWidget::class.java -> SizeF(320f, 160f)
    else -> SizeF(com.prayerwallpaper.app.logic.WidgetMetrics.STRIP_USUAL_WIDTH_DP, 72f)
}
