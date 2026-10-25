package com.prayerwallpaper.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Place
import com.prayerwallpaper.app.logic.AsrRule
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.logic.AlarmMethod
import com.prayerwallpaper.app.logic.CardStyle
import com.prayerwallpaper.app.logic.HijriAlign
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.CalcMethod
import com.prayerwallpaper.app.settings.AppSettings
import com.prayerwallpaper.app.data.snapshot
import com.prayerwallpaper.app.settings.BackgroundMode
import com.prayerwallpaper.app.settings.CapturedColors
import com.prayerwallpaper.app.settings.TextColorSource
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Debug builds only. Sets a fake time (read in the selected place's zone) so highlighting can be tested without waiting.
 * The clock keeps running from the time you set. Also scriptable, e.g. from adb:
 *
 *   am start -n com.prayerwallpaper.app/.DebugClockActivity --es at 2026-10-05T04:00:00 --ez finish true
 *   ... --ez next true        jump to the next change (a prayer time, or midnight)
 *   ... --ei before 60        jump to 60 seconds before the next change
 *   ... --ez real true        back to real time
 *
 * The place and how its times are worked out can be set too: --es place London,GB,51.5085,-0.1257,Europe/London
 * (name, country, latitude, longitude, zone, comma separated because adb's shell eats pipes; Singapore,SG,1.2897,103.8501,Asia/Singapore
 * goes back to MUIS),
 * --es method UMM_AL_QURA, --es asr HANAFI, --es names ENGLISH|MALAY, --es adjust 0,0,0,0,5,0, --ez resetcalc true.
 * Set the place before the time: --es at is read in the place's zone.
 *
 * Reminders (alarms use the real clock; the fake one does not apply): --ez reminders true|false (the setting), --es reminderprayers SUBUH,ZOHOR,
 * --ei reminderpre 10, --es remindermethod EXACT|INEXACT|ALARM_CLOCK|none (forces the alarm method), --ez reminderfast true (replaces today's
 * six prayer times by ones two minutes apart, the first in about three), --ez reminderfast false, --ez reminderensure true (plan the next alarm now),
 * --ez reminderreset true (cancel everything), --ez remindersilent true|false (vibrate-only channel), --ez remindertest true (the health screen's test, a minute from now), --ez reminderlog true (print the history to logcat: adb logcat -d -s Reminders).
 *
 * It can also set the look, for scripted screenshots: --es mode MATCH|PHOTO|SOLID|GRADIENT, --es textcolors BACKGROUND|CUSTOM|SYSTEM, --ei textcolor -16777216 (the custom colour, a signed ARGB int), --ez showhijri false, --ez hijrimaghrib false, --es hijrialign LEFT|CENTER, --ez hijrievents false, --ez countdown false, --ez access true (logs what the system reports: `adb logcat -s SystemAccess`), accessibility: --es textsize STANDARD|LARGE|EXTRA_LARGE, --ez highcontrast true, --es cardstyle AUTO|DARK|LIGHT, --ez bold true (--ez accessasked false shows the first-run question again; any of these turns "follow system" off, like changing it by hand; --ez followsystem true turns it back on), --es hijrievent "a long name" (forces an event name; "none" clears it, an empty value would swallow the next flag), --es photo <path of an image the app can read>, --es captured -3407872,-16751104,-12303292 (up to three signed ARGB ints: the colours "match my wallpaper" uses; then --es mode MATCH),
 * --ef dim 0.3, --ef listTop 0.5, photo framing --ef zoom 2 --ef panX 0 --ef panY 0.3, and colours as signed ints: --ei solid -16777216 (also gradientTop, gradientBottom).
 */
class DebugClockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        if (intent.getBooleanExtra("finish", false)) return
        val app = application as PrayerApp
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { ClockScreen(app) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.getBooleanExtra("palette", false)) logSystemPalette()
        if (intent.getBooleanExtra("access", false)) {
            val app = application as PrayerApp
            android.util.Log.i("SystemAccess", "${app.systemAccessibility()} -> ${app.accessOptions(app.settings.read())}")
        }
        applyPlace(intent)
        // The time is read in the zone of the place the list is for, so "04:00" means 04:00 there.
        intent.getStringExtra("at")?.let {
            AppClock.setFakeTime(LocalDateTime.parse(it).atZone((application as PrayerApp).timetable().zone).toInstant())
        }
        if (intent.getBooleanExtra("next", false)) stepToNextChange(0)
        if (intent.hasExtra("before")) stepToNextChange(intent.getIntExtra("before", 0).toLong())
        if (intent.getBooleanExtra("real", false)) AppClock.useRealTime()
        if (intent.hasExtra("hijrievent")) EventOverride.name = intent.getStringExtra("hijrievent")?.takeIf { it != "none" }
        applyPhoto(intent)
        applyLook(intent)
        applyReminders(intent)
        applyWidgets(intent)
        if (intent.getBooleanExtra("finish", false)) finish()
    }

    /** Widgets: the settings behind them, and rendering one to a PNG (see [WidgetDebug]). */
    private fun applyWidgets(intent: Intent) {
        val keys = listOf("widgetstyle", "widgetcard", "widgetopacity", "widgetfollow", "widgettint", "widgetdarkopacity")
        if (keys.any { intent.hasExtra(it) }) {
            val app = application as PrayerApp
            app.settings.update(sync = true) { s ->
                s.copy(
                    widgetStyle = intent.getStringExtra("widgetstyle")?.let { com.prayerwallpaper.app.logic.WidgetStyle.valueOf(it) } ?: s.widgetStyle,
                    widgetCard = intent.getStringExtra("widgetcard")?.let { CardStyle.valueOf(it) } ?: s.widgetCard,
                    widgetGlassOpacity = if (intent.hasExtra("widgetopacity")) intent.getFloatExtra("widgetopacity", s.widgetGlassOpacity) else s.widgetGlassOpacity,
                    widgetFollowAccess = if (intent.hasExtra("widgetfollow")) intent.getBooleanExtra("widgetfollow", true) else s.widgetFollowAccess,
                    widgetGlass = intent.getStringExtra("widgettint")?.let { com.prayerwallpaper.app.logic.GlassTint.valueOf(it) } ?: s.widgetGlass,
                    widgetDarkGlassOpacity = if (intent.hasExtra("widgetdarkopacity")) intent.getFloatExtra("widgetdarkopacity", s.widgetDarkGlassOpacity) else s.widgetDarkGlassOpacity,
                )
            }
        }
        // --ez widgetfast true: today's six times become two minutes apart (the same fast day the reminders use), so the widgets can be
        // watched crossing a boundary every two minutes; false puts the real times back. The first boundary is about three minutes away.
        if (intent.hasExtra("widgetfast")) {
            val app = application as PrayerApp
            if (intent.getBooleanExtra("widgetfast", false)) {
                if (!ReminderDebug.startFast(app.timetable().zone, java.time.Instant.now(), intent.getIntExtra("widgetstep", 2).toLong())) Toast.makeText(this, "Too close to midnight for the fast schedule", Toast.LENGTH_LONG).show()
            } else {
                ReminderDebug.stopFast()
            }
            Thread { app.widgets.updateAll("debug fast schedule") }.start()
        }
        // --ei widgetalarmtest 4: three kinds of inexact alarm for the same moment 4 minutes ahead; see WidgetDebug.alarmTest.
        if (intent.hasExtra("widgetalarmtest")) WidgetDebug.alarmTest(application as PrayerApp, intent.getIntExtra("widgetalarmtest", 4))
        // --ez widgetupdate true: what a boundary alarm would do, now.
        if (intent.getBooleanExtra("widgetupdate", false)) Thread { (application as PrayerApp).widgets.updateAll("debug") }.start()
        intent.getStringExtra("widgetshot")?.let { kind ->
            WidgetDebug.shot(
                application as PrayerApp, this, kind,
                intent.getIntExtra("w", 146).toFloat(), intent.getIntExtra("h", 72).toFloat(),
                if (intent.hasExtra("fontscale")) intent.getFloatExtra("fontscale", 1f) else null,
                intent.getStringExtra("lang"),
                if (intent.hasExtra("h24")) intent.getBooleanExtra("h24", false) else null,
                (intent.getStringExtra("bgcolor")?.toLongOrNull(16)?.toInt() ?: 0x0F2A53) or (0xFF shl 24),
                intent.getStringExtra("out") ?: "shot",
            )
        }
    }

    private fun applyReminders(intent: Intent) {
        val app = application as PrayerApp
        val keys = listOf("reminders", "reminderprayers", "reminderpre", "remindermethod", "reminderfast", "reminderensure", "reminderreset", "reminderlog", "remindersilent", "remindertest")
        if (keys.none { intent.hasExtra(it) }) return
        intent.getStringExtra("remindermethod")?.let { name -> ReminderDebug.setMethod(AlarmMethod.entries.firstOrNull { it.name == name }) }
        if (intent.hasExtra("reminderfast")) {
            if (intent.getBooleanExtra("reminderfast", false)) {
                val zone = app.timetable().zone
                if (!ReminderDebug.startFast(zone, java.time.Instant.now())) Toast.makeText(this, "Too close to midnight for the fast schedule", Toast.LENGTH_LONG).show()
            } else {
                ReminderDebug.stopFast()
            }
        }
        app.settings.update(sync = true) { s ->
            s.copy(
                remindersOn = if (intent.hasExtra("reminders")) intent.getBooleanExtra("reminders", false) else s.remindersOn,
                reminderPrayers = intent.getStringExtra("reminderprayers")?.split(',')?.mapNotNull { n -> Prayer.entries.firstOrNull { it.name == n.trim() } }?.toSet() ?: s.reminderPrayers,
                preReminderMinutes = if (intent.hasExtra("reminderpre")) intent.getIntExtra("reminderpre", 0) else s.preReminderMinutes,
                silentReminders = if (intent.hasExtra("remindersilent")) intent.getBooleanExtra("remindersilent", false) else s.silentReminders,
            )
        }
        if (intent.getBooleanExtra("remindertest", false)) app.reminders.scheduleTest()
        if (intent.getBooleanExtra("reminderreset", false)) app.reminders.cancelAll()
        val state = app.reminders.ensureScheduled("debug")
        android.util.Log.i("Reminders", "STATE next=${state.next} method=${state.method} set=${state.set}")
        if (intent.getBooleanExtra("reminderlog", false)) {
            val zone = app.timetable().zone
            val h = app.reminders.history()
            h.entries.forEach {
                android.util.Log.i(
                    "Reminders", "ENTRY planned=${it.plannedAt.atZone(zone).toLocalTime()} ${it.kind} ${it.prayer ?: "-"} ${it.method} ${it.outcome} " +
                        "fired=${it.firedAt?.atZone(zone)?.toLocalTime() ?: "-"} late=${it.latenessSeconds ?: "-"}s",
                )
            }
            val m = h.summary()
            android.util.Log.i("Reminders", "SUMMARY fired=${m.fired} blocked=${m.blocked} stale=${m.stale} missed=${m.missed} pending=${m.pending} median=${m.medianLatenessSeconds}s worst=${m.worstLatenessSeconds}s")
        }
    }

    /** The Material You colours the system exposes to apps, one logcat line each: `adb logcat -s SystemPalette`. */
    private fun logSystemPalette() {
        val fields = android.R.color::class.java.fields.filter { it.name.startsWith("system_") && it.type == Int::class.javaPrimitiveType }
        val numbered = Regex("(system_(?:accent|neutral)\\d)_(\\d+)")
        val (tones, named) = fields.partition { numbered.matches(it.name) }
        for ((palette, group) in tones.groupBy { numbered.matchEntire(it.name)!!.groupValues[1] }.toSortedMap()) {
            val line = group.sortedBy { numbered.matchEntire(it.name)!!.groupValues[2].toInt() }
                .joinToString(" ") { "%s=%06X".format(numbered.matchEntire(it.name)!!.groupValues[2], getColor(it.getInt(null)) and 0xFFFFFF) }
            android.util.Log.i("SystemPalette", "$palette: $line")
        }
        android.util.Log.i("SystemPalette", "named: " + named.sortedBy { it.name }.joinToString(" ") { "%s=%06X".format(it.name.removePrefix("system_"), getColor(it.getInt(null)) and 0xFFFFFF) })
    }

    private fun applyPlace(intent: Intent) {
        val app = application as PrayerApp
        intent.getStringExtra("place")?.let { spec ->
            // A bad spec must not crash the app: the wallpaper engine shares this process, and Android resets
            // a live wallpaper whose process keeps crashing.
            val f = spec.split(',')
            val place = runCatching { Place(0, f[0], "", f[1], f[2].toDouble(), f[3].toDouble(), f[4]).also { it.zone } }.getOrNull()
            if (place == null) Toast.makeText(this, "Bad place: use name,CC,lat,lon,zone", Toast.LENGTH_LONG).show() else app.choosePlace(place, sync = true)
        }
        app.settings.update(sync = true) { s ->
            s.copy(
                method = intent.getStringExtra("method")?.let { CalcMethod.valueOf(it) } ?: s.method,
                asr = intent.getStringExtra("asr")?.let { AsrRule.valueOf(it) } ?: s.asr,
                names = intent.getStringExtra("names")?.let { NameSet.valueOf(it) } ?: s.names,
                adjustments = intent.getStringExtra("adjust")?.split(',')?.map { it.trim().toInt() } ?: s.adjustments,
            ).let { if (intent.getBooleanExtra("resetcalc", false)) it.copy(method = null, asr = null, adjustments = AppSettings.NO_ADJUSTMENTS) else it }
        }
    }

    /** --es photo /data/data/com.prayerwallpaper.app/cache/x.png: use that file as the photo background. Never throws. */
    private fun applyPhoto(intent: Intent) {
        val path = intent.getStringExtra("photo") ?: return
        val app = application as PrayerApp
        val version = runCatching { app.photos.import(android.net.Uri.fromFile(java.io.File(path))) }.getOrNull()
        if (version == null) {
            Toast.makeText(this, "Could not read $path", Toast.LENGTH_LONG).show()
            return
        }
        app.settings.update(sync = true) { it.copy(mode = BackgroundMode.PHOTO, photoVersion = version, photoZoom = 1f, photoPanX = 0.5f, photoPanY = 0.5f) }
    }

    private fun applyLook(intent: Intent) {
        val app = application as PrayerApp
        app.settings.update(sync = true) { s ->
            s.copy(
                mode = intent.getStringExtra("mode")?.let { BackgroundMode.valueOf(it) } ?: s.mode,
                captured = intent.getStringExtra("captured")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size >= 2 }
                    ?.let { CapturedColors(it[0], it[1], it.getOrNull(2)) } ?: s.captured,
                textColors = intent.getStringExtra("textcolors")?.let { TextColorSource.valueOf(it) } ?: s.textColors,
                showHijri = if (intent.hasExtra("showhijri")) intent.getBooleanExtra("showhijri", true) else s.showHijri,
                hijriAtMaghrib = if (intent.hasExtra("hijrimaghrib")) intent.getBooleanExtra("hijrimaghrib", true) else s.hijriAtMaghrib,
                hijriAlign = intent.getStringExtra("hijrialign")?.let { HijriAlign.valueOf(it) } ?: s.hijriAlign,
                showHijriEvents = if (intent.hasExtra("hijrievents")) intent.getBooleanExtra("hijrievents", true) else s.showHijriEvents,
                showCountdown = if (intent.hasExtra("countdown")) intent.getBooleanExtra("countdown", true) else s.showCountdown,
                // Setting any option by hand stops following the system, as it does in the app.
                followSystem = when {
                    intent.hasExtra("followsystem") -> intent.getBooleanExtra("followsystem", true)
                    listOf("textsize", "highcontrast", "cardstyle", "bold").any { intent.hasExtra(it) } -> false
                    else -> s.followSystem
                },
                textSize = intent.getStringExtra("textsize")?.let { TextSize.valueOf(it) } ?: s.textSize,
                highContrast = if (intent.hasExtra("highcontrast")) intent.getBooleanExtra("highcontrast", false) else s.highContrast,
                cardStyle = intent.getStringExtra("cardstyle")?.let { CardStyle.valueOf(it) } ?: s.cardStyle,
                boldText = if (intent.hasExtra("bold")) intent.getBooleanExtra("bold", false) else s.boldText,
                accessAsked = if (intent.hasExtra("accessasked")) intent.getBooleanExtra("accessasked", true) else s.accessAsked,
                customTextColor = if (intent.hasExtra("textcolor")) intent.getIntExtra("textcolor", s.customTextColor) else s.customTextColor,
                dim = if (intent.hasExtra("dim")) intent.getFloatExtra("dim", 0f) else s.dim,
                listTop = if (intent.hasExtra("listTop")) intent.getFloatExtra("listTop", s.listTop) else s.listTop,
                photoZoom = if (intent.hasExtra("zoom")) intent.getFloatExtra("zoom", 1f) else s.photoZoom,
                photoPanX = if (intent.hasExtra("panX")) intent.getFloatExtra("panX", 0.5f) else s.photoPanX,
                photoPanY = if (intent.hasExtra("panY")) intent.getFloatExtra("panY", 0.5f) else s.photoPanY,
                solid = if (intent.hasExtra("solid")) intent.getIntExtra("solid", s.solid) else s.solid,
                gradientTop = if (intent.hasExtra("gradientTop")) intent.getIntExtra("gradientTop", s.gradientTop) else s.gradientTop,
                gradientBottom = if (intent.hasExtra("gradientBottom")) intent.getIntExtra("gradientBottom", s.gradientBottom) else s.gradientBottom,
            )
        }
    }

    private fun stepToNextChange(secondsBefore: Long) {
        val app = application as PrayerApp
        val next = app.timetable().snapshot(AppClock.clock).nextChange
        if (next == null) {
            Toast.makeText(this, "No timetable for this day", Toast.LENGTH_SHORT).show()
            return
        }
        AppClock.setFakeTime(next.minusSeconds(secondsBefore))
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy HH:mm:ss")

@Composable
private fun ClockScreen(app: PrayerApp) {
    var ticks by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            ticks++
        }
    }
    check(ticks >= 0) // read so the screen redraws every second

    val timetable = app.timetable()
    val now = ZonedDateTime.now(app.clock.withZone(timetable.zone))
    val snapshot = timetable.snapshot(app.clock)
    val shift = { by: Duration -> AppClock.setFakeTime(app.clock.instant().plus(by)) }
    val step = { before: Long ->
        snapshot.nextChange?.let { AppClock.setFakeTime(it.minusSeconds(before)) }
        Unit
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Fake clock (debug build only)", style = MaterialTheme.typography.titleLarge)
        Text(if (AppClock.isFake) "FAKE time, running" else "Real time", style = MaterialTheme.typography.labelLarge)
        Text(timeFormat.format(now), style = MaterialTheme.typography.headlineSmall)
        Text("Place zone: ${timetable.zone}")
        Text("Highlighted: ${snapshot.highlight?.label(NameSet.MALAY) ?: "none"}")
        Text("Next change: ${snapshot.nextChange?.atZone(timetable.zone)?.format(timeFormat) ?: "none"}")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ step(0) }) { Text("Next change") }
            Button({ step(60) }) { Text("60 s before it") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ shift(Duration.ofDays(-1)) }) { Text("-1 day") }
            Button({ shift(Duration.ofDays(1)) }) { Text("+1 day") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ shift(Duration.ofHours(-1)) }) { Text("-1 h") }
            Button({ shift(Duration.ofHours(1)) }) { Text("+1 h") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ shift(Duration.ofMinutes(-10)) }) { Text("-10 min") }
            Button({ shift(Duration.ofMinutes(10)) }) { Text("+10 min") }
        }
        Button({ AppClock.useRealTime() }) { Text("Back to real time") }
    }
}
