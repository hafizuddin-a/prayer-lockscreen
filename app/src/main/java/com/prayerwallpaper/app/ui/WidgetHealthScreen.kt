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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import com.prayerwallpaper.app.logic.WIDGET_REASONS_NOT_COUNTED
import com.prayerwallpaper.app.logic.formatAgo
import com.prayerwallpaper.app.logic.formatHealthTime
import com.prayerwallpaper.app.logic.formatLateness
import com.prayerwallpaper.app.logic.widgetShareText
import com.prayerwallpaper.app.widgets.WidgetHealth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * How current the home screen widgets are, measured: when they were last redrawn, the next boundary and whether its alarm is set, and
 * for each boundary of the last 14 days how long after it the widgets were redrawn (planned against actual, with the median and
 * the worst). Built to the same rules as the rest of the settings: it scrolls, every control is at least 48 dp, and words carry the state.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetHealthScreen(app: PrayerApp, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var health by remember { mutableStateOf<WidgetHealth?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) { health = withContext(Dispatchers.IO) { app.widgets.health() } }
    LaunchedEffect(Unit) { while (true) { delay(5_000); refresh++ } }

    ScreenScaffold("Widget health", onBack, spacing = 12.dp) {

        val h = health
        if (h == null) {
            Text("Checking…")
            return@ScreenScaffold
        }
        val summary = h.history.summary()
        SectionCard("Now") {
            Text(
                if (h.widgets == 0) "No widget on the home screen yet. Alarms are only set while there is one."
                else "${h.widgets} ${if (h.widgets == 1) "widget" else "widgets"} on the home screen.",
            )
            Text("Last update: " + (h.history.lastUpdate?.let { "${formatHealthTime(it, h.zone)}, ${formatAgo(it, h.now)}" } ?: "none yet"))
            Text("Next boundary: ${formatHealthTime(h.nextBoundary, h.zone)} (in ${formatLateness(Duration.between(h.now, h.nextBoundary).seconds.coerceAtLeast(0))})")
            Text(
                if (h.widgets == 0) "Alarm: not needed without a widget."
                else if (h.alarmsSet) "Alarm: set. The widgets are redrawn then."
                else "Alarm: not found. Refreshing the widgets sets it again.",
            )
        }
        SectionCard("Last 14 days") {
            Text(
                if (summary.medianSeconds == null) "No boundary recorded yet."
                else "${summary.count} boundaries: median ${formatLateness(summary.medianSeconds)} late, worst ${formatLateness(summary.worstSeconds ?: 0)}.",
            )
            Text(
                "Late means how long after a prayer time (or midnight) the widgets were redrawn. The countdown is never late.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                onClick = { showHistory = !showHistory },
                modifier = Modifier.semantics { stateDescription = if (showHistory) "Expanded" else "Collapsed" },
            ) { Text(if (showHistory) "Hide the list" else "Show the list") }
            if (showHistory) {
                if (h.history.entries.isEmpty()) Text("Nothing recorded yet.")
                for (e in h.history.entries.reversed()) {
                    val counted = if (e.reason in WIDGET_REASONS_NOT_COUNTED) ", not counted" else ""
                    Text("${formatHealthTime(e.plannedAt, h.zone)}: redrawn ${formatLateness(e.latenessSeconds)} later (${e.reason}$counted)", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SectionCard("Actions") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { app.widgets.updateAll("refresh button") }
                        Toast.makeText(context, "Widgets refreshed", Toast.LENGTH_SHORT).show()
                        refresh++
                    }
                }) { Text("Refresh widgets now") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { shareText(app, h) }
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share widget history"))
                    }
                }) { Text("Share as text") }
            }
        }
    }
}

/** The history and the facts that explain it, as plain text. */
private fun shareText(app: PrayerApp, health: WidgetHealth): String {
    val settings = app.settings.read()
    val header = listOf(
        "App ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}${if (BuildConfig.DEBUG) ", debug" else ""}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}",
        "Place: ${settings.place.displayName()}; widgets on the home screen: ${health.widgets}; boundary alarm set: ${health.alarmsSet}",
    )
    return widgetShareText(header, health.history, health.nextBoundary, health.zone, health.now)
}
