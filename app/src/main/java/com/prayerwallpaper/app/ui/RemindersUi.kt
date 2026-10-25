package com.prayerwallpaper.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.logic.HealthReport
import com.prayerwallpaper.app.logic.PRE_REMINDER_CHOICES
import com.prayerwallpaper.app.logic.PermissionNeed
import com.prayerwallpaper.app.logic.nextPermissionNeed
import com.prayerwallpaper.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A checkbox with its words, as one control of at least 48 dp: "Asar, checkbox, checked". */
@Composable
internal fun CheckRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(end = 12.dp))
        Text(title)
    }
}

/** Where the turn-on flow is. Each step is a short explanation in plain words before Android's own prompt or screen. */
private enum class Step { NONE, EXPLAIN_NOTIFICATIONS, EXPLAIN_EXACT, NOTIFICATIONS_DENIED }

/**
 * The turn-on flow for reminders, shared by the Reminders screen and the setup. Turning reminders on walks through what Android needs:
 * notifications first (without them nothing can be shown, so a "no" turns the switch back off), then exact alarms (without them
 * reminders still work, only up to an hour late, so a "no" keeps them on and says so). The setup has a page of its own for exact
 * alarms, so it passes [offerExact] false and the pop-up for them is left out there. The battery exemption is never asked for. Every
 * return to the app checks again. Show [Dialogs] wherever the flow is used.
 */
@Stable
internal class ReminderFlow(private val app: PrayerApp, private val context: Context, private val scope: CoroutineScope, private val offerExact: Boolean) {
    private var step by mutableStateOf(Step.NONE)
    var exactAsked by mutableStateOf(false)
    var report by mutableStateOf<HealthReport?>(null)
    var refresh by mutableIntStateOf(0)

    lateinit var change: ((AppSettings) -> AppSettings) -> Unit
    lateinit var askNotifications: () -> Unit

    fun notificationsAllowed() = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** After a setting changed: keep the daily jobs right and plan the next alarm again, off the main thread. */
    fun applyChange() {
        app.syncBackgroundJobs()
        scope.launch {
            withContext(Dispatchers.IO) { app.reminders.ensureScheduled("settings changed") }
            refresh++
        }
    }

    private fun turnOn() {
        change { it.copy(remindersOn = true) }
        applyChange()
        val need = nextPermissionNeed(notificationsAllowed(), app.reminders.canScheduleExact(), BuildConfig.EXACT_ALARMS, exactAsked)
        if (need == PermissionNeed.EXACT_ALARMS && offerExact) step = Step.EXPLAIN_EXACT
    }

    /** The switch: on starts the permission steps, off cancels what was planned. */
    fun setEnabled(on: Boolean) {
        if (on) {
            if (notificationsAllowed()) turnOn() else step = Step.EXPLAIN_NOTIFICATIONS
        } else {
            change { it.copy(remindersOn = false) }
            applyChange()
        }
    }

    fun onNotificationAnswer(granted: Boolean) {
        if (granted) {
            turnOn()
        } else {
            change { it.copy(remindersOn = false) }
            applyChange()
            step = Step.NOTIFICATIONS_DENIED
        }
    }

    fun openExactAlarmSettings() {
        exactAsked = true
        SystemScreens.open(context, SystemScreens.exactAlarms(context))
    }

    @Composable
    fun Dialogs() {
        when (step) {
            Step.NONE -> Unit
            Step.EXPLAIN_NOTIFICATIONS -> AlertDialog(
                onDismissRequest = { step = Step.NONE },
                title = { Text("Allow notifications?") },
                text = { Text("Reminders are notifications, so Android needs your OK. They're used for nothing else.") },
                confirmButton = { TextButton(onClick = { step = Step.NONE; askNotifications() }) { Text("Continue") } },
                dismissButton = { TextButton(onClick = { step = Step.NONE }) { Text("Not now") } },
            )
            Step.EXPLAIN_EXACT -> AlertDialog(
                onDismissRequest = { exactAsked = true; step = Step.NONE },
                title = { Text("Reminders at the exact minute?") },
                text = {
                    Text(
                        "To get reminders on the exact minute, allow alarms in Android's settings. If you skip, they can be up to an hour late.",
                    )
                },
                confirmButton = { TextButton(onClick = { step = Step.NONE; openExactAlarmSettings() }) { Text("Open settings") } },
                dismissButton = { TextButton(onClick = { exactAsked = true; step = Step.NONE }) { Text("Skip") } },
            )
            Step.NOTIFICATIONS_DENIED -> AlertDialog(
                onDismissRequest = { step = Step.NONE },
                title = { Text("Notifications are off") },
                text = { Text("Reminders can't work without notifications, so they are off. Allow notifications in Android's settings, then turn reminders on again.") },
                confirmButton = { TextButton(onClick = { step = Step.NONE; SystemScreens.open(context, SystemScreens.notifications(context)) }) { Text("Open notification settings") } },
                dismissButton = { TextButton(onClick = { step = Step.NONE }) { Text("Close") } },
            )
        }
    }
}

@Composable
internal fun rememberReminderFlow(
    app: PrayerApp, settings: AppSettings, resumes: Int, change: ((AppSettings) -> AppSettings) -> Unit, offerExact: Boolean = true,
): ReminderFlow {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember { ReminderFlow(app, context, scope, offerExact) }
    flow.change = change
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> flow.onNotificationAnswer(granted) }
    flow.askNotifications = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }

    // Coming back to the app, from a system screen or from elsewhere: plan again if anything changed (it does nothing if not).
    LaunchedEffect(resumes) {
        withContext(Dispatchers.IO) { app.reminders.ensureScheduled("app opened", force = false) }
        flow.refresh++
    }
    LaunchedEffect(settings, resumes, flow.refresh) { flow.report = withContext(Dispatchers.IO) { app.reminderHealth() } }
    return flow
}

/** The Reminders screen's content: the switch and, once on, which prayers, the heads-up, the sound, and a way into Reminder health. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RemindersCard(
    app: PrayerApp, settings: AppSettings, resumes: Int, change: ((AppSettings) -> AppSettings) -> Unit, onHealth: () -> Unit,
) {
    val context = LocalContext.current
    val flow = rememberReminderFlow(app, settings, resumes, change)

    SectionCard(null) {
        SwitchRow(
            "Prayer reminders",
            "A notification at each prayer time. Stays on this phone.",
            settings.remindersOn,
            onChange = flow::setEnabled,
        )

        if (settings.remindersOn) {
            GroupHeading("Prayers to remind")
            for (prayer in Prayer.entries) {
                CheckRow(prayer.label(settings.effectiveNames), prayer in settings.reminderPrayers) { on ->
                    change { it.copy(reminderPrayers = if (on) it.reminderPrayers + prayer else it.reminderPrayers - prayer) }
                    flow.applyChange()
                }
            }

            GroupHeading("Heads-up before the prayer")
            for (minutes in PRE_REMINDER_CHOICES) {
                ChoiceRow(
                    settings.preReminderMinutes == minutes, if (minutes == 0) "Off" else "$minutes minutes before",
                    if (minutes == 0) "Only at the prayer time." else "An extra reminder $minutes minutes before.",
                ) {
                    change { it.copy(preReminderMinutes = minutes) }
                    flow.applyChange()
                }
            }

            SwitchRow(
                "Silent reminders", "Vibrate, but make no sound.", settings.silentReminders,
                onChange = { on ->
                    change { it.copy(silentReminders = on) }
                    flow.applyChange()
                },
            )
            OutlinedButton(onClick = {
                SystemScreens.open(context, if (settings.silentReminders) SystemScreens.notifications(context) else SystemScreens.channel(context, silent = false))
            }) { Text("Notification sound settings") }

            if (BuildConfig.EXACT_ALARMS && !app.reminders.canScheduleExact()) {
                Text(
                    "Exact alarms are off, so reminders can be up to an hour late.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = flow::openExactAlarmSettings) { Text("Allow exact alarms") }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onHealth) { Text("Reminder health") }
            Text(flow.report?.status?.text ?: "Checking…", style = MaterialTheme.typography.titleSmall)
        }
    }

    flow.Dialogs()
}
