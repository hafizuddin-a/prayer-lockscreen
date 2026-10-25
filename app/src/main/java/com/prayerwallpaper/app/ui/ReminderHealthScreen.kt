package com.prayerwallpaper.app.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import com.prayerwallpaper.app.logic.HealthFix
import com.prayerwallpaper.app.logic.HealthLevel
import com.prayerwallpaper.app.logic.HealthReport
import com.prayerwallpaper.app.logic.ReminderKind
import com.prayerwallpaper.app.logic.ReminderOutcome
import com.prayerwallpaper.app.logic.formatAgo
import com.prayerwallpaper.app.logic.formatHealthTime
import com.prayerwallpaper.app.logic.formatLateness
import com.prayerwallpaper.app.logic.historyShareText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Says what could stop reminders and offers the fix, and measures: each row has a status written as a word (OK, Warning, Problem),
 * a plain sentence and a button where one helps. Below are the actions (a test reminder, the system screens, a reset, the history as
 * text to share) and the history itself, collapsed until asked for. Built to the same rules as the rest of the settings: it scrolls,
 * every control is at least 48 dp, and the words carry the state, not the colour.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderHealthScreen(app: PrayerApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<HealthReport?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showHistory by remember { mutableStateOf(false) }
    val zone = remember { app.timetable().zone }

    // Read on opening, after each action, and every few seconds while open so a test reminder shows up when it arrives.
    LaunchedEffect(refresh) { report = withContext(Dispatchers.IO) { app.reminderHealth() } }
    LaunchedEffect(Unit) { while (true) { delay(5_000); refresh++ } }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    ScreenScaffold("Reminder health", onBack, spacing = 12.dp) {

        val current = report
        if (current == null) {
            Text("Checking…")
        } else {
            Text(
                current.status.text, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { contentDescription = "Status: ${current.status.text}" },
            )
            for (row in current.rows) {
                SectionCard("${row.title}: ${row.level.word}") {
                    Text(row.detail)
                    row.fix?.let { fix ->
                        OutlinedButton(onClick = {
                            when (fix) {
                                HealthFix.NOTIFICATION_SETTINGS -> SystemScreens.open(context, SystemScreens.notifications(context))
                                HealthFix.EXACT_ALARM_SETTINGS -> SystemScreens.open(context, SystemScreens.exactAlarms(context))
                                HealthFix.BATTERY_SETTINGS -> SystemScreens.open(context, SystemScreens.battery())
                                HealthFix.RESET_REMINDERS -> scope.launch {
                                    withContext(Dispatchers.IO) { app.reminders.cancelAll(); app.reminders.ensureScheduled("reset") }
                                    refresh++
                                }
                            }
                        }) {
                            Text(
                                when (fix) {
                                    HealthFix.NOTIFICATION_SETTINGS -> "Open notification settings"
                                    HealthFix.EXACT_ALARM_SETTINGS -> "Open exact alarm permission screen"
                                    HealthFix.BATTERY_SETTINGS -> "Open battery settings"
                                    HealthFix.RESET_REMINDERS -> "Reset reminders"
                                },
                            )
                        }
                    }
                }
            }
        }

        SectionCard("Actions") {
            Text("A test takes the same path as a real reminder.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { app.reminders.scheduleTest() }
                        toast("A test reminder will arrive in about a minute")
                        refresh++
                    }
                }) { Text("Send a test reminder in 1 minute") }
                OutlinedButton(onClick = { SystemScreens.open(context, SystemScreens.notifications(context)) }) { Text("Open notification settings") }
                if (BuildConfig.EXACT_ALARMS) {
                    OutlinedButton(onClick = { SystemScreens.open(context, SystemScreens.exactAlarms(context)) }) { Text("Open exact alarm permission screen") }
                }
                OutlinedButton(onClick = { SystemScreens.open(context, SystemScreens.battery()) }) { Text("Open battery settings") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val state = withContext(Dispatchers.IO) { app.reminders.cancelAll(); app.reminders.ensureScheduled("reset") }
                        toast(if (state.set) "Reminders reset. The next one is set." else "Reminders reset. Nothing to plan right now.")
                        refresh++
                    }
                }) { Text("Reset reminders") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { shareText(app, report) }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share history"))
                    }
                }) { Text("Share history as text") }
            }
        }

        ReminderDebugPanel(app) { refresh++ }

        SectionCard("History") {
            val history = remember(report) { app.reminders.history() }
            val s = history.summary()
            Text("${s.fired} shown, ${s.blocked} blocked, ${s.stale} too late, ${s.missed} missed, ${s.pending} waiting.")
            OutlinedButton(
                onClick = { showHistory = !showHistory },
                modifier = Modifier.semantics { stateDescription = if (showHistory) "Expanded" else "Collapsed" },
            ) { Text(if (showHistory) "Hide the last 14 days" else "Show the last 14 days") }
            if (showHistory) {
                val now = Instant.now()
                if (history.entries.isEmpty()) Text("Nothing recorded yet.")
                for (e in history.entries.reversed()) {
                    val what = when (e.kind) {
                        ReminderKind.AT -> e.prayer?.label(app.settings.read().effectiveNames) ?: ""
                        ReminderKind.PRE -> "${e.prayer?.label(app.settings.read().effectiveNames)} heads-up"
                        ReminderKind.TEST -> "Test"
                    }
                    val outcome = when (e.outcome) {
                        ReminderOutcome.FIRED -> "shown, ${formatLateness(e.latenessSeconds ?: 0)} late"
                        ReminderOutcome.BLOCKED -> "blocked (notifications off), ${formatLateness(e.latenessSeconds ?: 0)} late"
                        ReminderOutcome.STALE -> "too late to show, ${formatLateness(e.latenessSeconds ?: 0)} late"
                        ReminderOutcome.MISSED -> "never arrived"
                        ReminderOutcome.PENDING -> "waiting"
                    }
                    Text("${formatHealthTime(e.plannedAt, zone)}  $what  (${e.method.name.lowercase().replace('_', ' ')}): $outcome, ${formatAgo(e.plannedAt, now)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** The history and the facts that explain it, as plain text. */
private fun shareText(app: PrayerApp, report: HealthReport?): String {
    val settings = app.settings.read()
    val zone = app.timetable().zone
    val header = buildList {
        add("App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}${if (BuildConfig.DEBUG) ", debug" else ""}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        add("Place: ${settings.place.displayName()}; times from ${if (settings.place.usesMuis) "MUIS" else "calculation"}")
        add("Prayers: ${settings.reminderPrayers.joinToString { it.label(settings.effectiveNames) }}; heads-up ${if (settings.preReminderMinutes == 0) "off" else "${settings.preReminderMinutes} min"}; ${if (settings.silentReminders) "silent" else "with sound"}")
        report?.let { r ->
            add("Status: ${r.status.text}")
            r.rows.filter { it.level != HealthLevel.OK }.forEach { add("${it.title}: ${it.level.word}. ${it.detail}") }
        }
    }
    return historyShareText(header, app.reminders.history(), zone, settings.effectiveNames, Instant.now())
}
