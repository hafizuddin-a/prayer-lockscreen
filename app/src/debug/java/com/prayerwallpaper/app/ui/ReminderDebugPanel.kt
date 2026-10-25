package com.prayerwallpaper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.ReminderDebug
import com.prayerwallpaper.app.logic.AlarmMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Debug builds only. Two things for comparing how reliably Android delivers reminders on a real phone: which alarm method is used
 * (the app's own choice, exact, inexact, or the alarm clock one that shows an icon in the status bar), and a fast schedule that puts
 * all six prayers two minutes apart, the first in about three minutes. The release build has an empty twin of this file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReminderDebugPanel(app: PrayerApp, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var forced by remember { mutableStateOf(ReminderDebug.method()) }
    var fast by remember { mutableStateOf(ReminderDebug.days(app.timetable().zone) != null) }

    fun replan() = scope.launch {
        withContext(Dispatchers.IO) { app.reminders.ensureScheduled("debug panel") }
        onChanged()
    }

    SectionCard("Debug only") {
        Text("Alarm method: ${forced?.name?.lowercase()?.replace('_', ' ') ?: "the app's own choice"}", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (method in listOf<AlarmMethod?>(null) + AlarmMethod.entries) {
                val selected = method == forced
                val pick = { ReminderDebug.setMethod(method); forced = method; replan(); Unit }
                val label = method?.name?.lowercase()?.replace('_', ' ') ?: "auto"
                if (selected) Button(onClick = pick) { Text(label) } else OutlinedButton(onClick = pick) { Text(label) }
            }
        }
        Text(if (fast) "The fast schedule is on: today's prayer times are replaced by ones two minutes apart." else "The fast schedule is off.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { fast = ReminderDebug.startFast(app.timetable().zone, Instant.now()); replan() }) {
                Text("Schedule all prayers at 2-minute intervals starting in 2 minutes")
            }
            OutlinedButton(onClick = { ReminderDebug.stopFast(); fast = false; replan() }) { Text("Back to the real prayer times") }
        }
    }
}
