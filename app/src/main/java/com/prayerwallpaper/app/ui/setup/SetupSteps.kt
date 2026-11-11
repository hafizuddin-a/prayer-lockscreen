package com.prayerwallpaper.app.ui.setup

import android.widget.Toast
import kotlinx.coroutines.launch
import com.prayerwallpaper.app.settings.BackgroundMode
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.logic.ApplyState
import com.prayerwallpaper.app.logic.HomeSummary
import com.prayerwallpaper.app.logic.SetupStep
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.setupStepCount
import com.prayerwallpaper.app.logic.previewDescription
import com.prayerwallpaper.app.settings.AppSettings
import com.prayerwallpaper.app.ui.PhonePreview
import com.prayerwallpaper.app.ui.SettingsState
import com.prayerwallpaper.app.ui.SystemScreens
import com.prayerwallpaper.app.ui.SwitchRow
import com.prayerwallpaper.app.ui.WidgetOffers
import com.prayerwallpaper.app.ui.components.BannerKind
import com.prayerwallpaper.app.ui.components.DecorativeIcon
import com.prayerwallpaper.app.ui.components.Icons
import com.prayerwallpaper.app.ui.components.StatusBanner
import com.prayerwallpaper.app.ui.components.StepProgress
import com.prayerwallpaper.app.ui.openLockScreenSettings
import com.prayerwallpaper.app.ui.rememberBackdrop
import com.prayerwallpaper.app.ui.rememberDataRefresh
import com.prayerwallpaper.app.ui.rememberLiveTimes
import com.prayerwallpaper.app.ui.rememberReminderFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The frame of one page: back arrow and progress on top, the content in the middle (it scrolls, so nothing is lost at a large font
 * size), and the buttons fixed at the bottom where a thumb reaches. The title is a heading and is announced when the page changes.
 */
@Composable
private fun SetupPage(
    step: SetupStep, title: String, onBack: (() -> Unit)?,
    actions: @Composable ColumnScope.() -> Unit, content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(painterResource(Icons.Back), contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface) }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            step.number(BuildConfig.EXACT_ALARMS)?.let { StepProgress(it, setupStepCount(BuildConfig.EXACT_ALARMS), Modifier.weight(1f)) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                title, style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            content()
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = actions)
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(label, style = MaterialTheme.typography.titleSmall) }
}

@Composable
private fun QuietAction(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Welcome

/** The four answers to "Make the text bigger?". The first three are the same presets as Easier reading; the last follows the phone. */
private enum class SizeChoice(val title: String, val options: AccessOptions?) {
    FOLLOW("Follow my phone", null),
    STANDARD("Standard", AccessOptions()),
    LARGE("Large", AccessOptions(textSize = TextSize.LARGE)),
    LARGE_CONTRAST("Large with high contrast", AccessOptions(textSize = TextSize.LARGE, highContrast = true)),
}

private fun AppSettings.sizeChoice(): SizeChoice? = when {
    followSystem -> SizeChoice.FOLLOW
    else -> SizeChoice.entries.firstOrNull { c -> c.options?.let { it.textSize == textSize && it.highContrast == highContrast && !boldText } == true }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WelcomePage(app: PrayerApp, resumes: Int, state: SettingsState, onNext: () -> Unit, onSkip: () -> Unit, onChangePlace: () -> Unit) {
    val settings = state.settings
    val refresh = rememberDataRefresh(app, settings.place.usesMuis, auto = true)
    val look = remember(settings) { app.looks.of(settings) }
    val timetable = remember(refresh.data, settings) { app.timetable(settings, refresh.data) }
    val configuration = LocalConfiguration.current
    val access = remember(settings, resumes, configuration) { app.accessOptions(settings) }
    val hijriLine = app.hijriLine(settings, timetable)
    val times = rememberLiveTimes(app, timetable, resumes)
    val screen = LocalResources.current.displayMetrics
    val current = settings.sizeChoice()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The same photo import as the Look screen: a new photo starts centred at 1x.
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { app.photos.import(uri) } }
                result.onSuccess { version -> state.change { it.copy(mode = BackgroundMode.PHOTO, photoVersion = version, photoZoom = 1f, photoPanX = 0.5f, photoPanY = 0.5f) } }
                result.onFailure { Toast.makeText(context, "Could not use that photo", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    SetupPage(
        SetupStep.WELCOME, "Prayer times on your lock screen", onBack = null,
        actions = {
            PrimaryAction("Get started", onNext)
            QuietAction("Skip setup", onSkip)
        },
    ) {
        Body("Takes about a minute.")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PhonePreview(
                timetable = timetable, clock = app.clock, look = look, names = settings.effectiveNames, hijri = hijriLine,
                hijriAlign = settings.hijriAlign, showCountdown = settings.showCountdown, access = access,
                onListTopChange = {}, modifier = Modifier.fillMaxWidth(0.4f), aspect = screen.widthPixels.toFloat() / screen.heightPixels,
                draggable = false,
                description = previewDescription(times.snapshot, times.now, settings.effectiveNames, times.is24Hour),
            )
        }

        val place = settings.place
        Text(
            if (place.usesMuis) "Singapore times, from MUIS." else "Times for ${HomeSummary.placeName(place)}.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (place.usesMuis && refresh.data.years.isEmpty() && !refresh.busy && refresh.status.isNotBlank()) {
            Text(
                "The times download once, so connect to the internet. ${refresh.status}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onChangePlace, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (place.usesMuis) "Not in Singapore? Change place" else "Change place") }

        Text("Pick a photo for the background?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(if (settings.photoVersion == 0L) "Choose a photo" else "Choose a different photo")
        }
        Text(
            if (settings.photoVersion == 0L) "Or keep the default. Change it later in Look." else "Done. Move and zoom it later in Look.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Make the text bigger?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (choice in SizeChoice.entries) {
                val isSelected = choice == current
                val pick = {
                    state.change { s ->
                        val asked = s.copy(accessAsked = true)
                        val options = choice.options
                        if (options == null) asked.copy(followSystem = true) else asked.copy(followSystem = false, textSize = options.textSize, highContrast = options.highContrast, boldText = options.bold)
                    }
                }
                val modifier = Modifier.semantics { stateDescription = if (isSelected) "Selected" else "Not selected"; selected = isSelected }
                if (isSelected) Button(onClick = pick, modifier = modifier) { Text(choice.title) } else OutlinedButton(onClick = pick, modifier = modifier) { Text(choice.title) }
            }
        }
        Text(
            "Change it later in Easier reading.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (BuildConfig.SELF_UPDATE) {
            Text(
                "The app looks on GitHub once a month for a new version. You can turn that off in About.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Apply to the lock screen

private fun boldWords(before: String, bold: String, after: String) = buildAnnotatedString {
    append(before)
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
    append(after)
}

@Composable
private fun NumberedStep(number: Int, text: AnnotatedString) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(top = 3.dp))
    }
}

/** A drawing of the choice Android asks for, with Lock screen picked. Phones word it differently, so it says so. */
@Composable
private fun PickerSchematic() {
    val description = "Choices: Home screen, Lock screen, or both. Pick Lock screen."
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Your phone may word it differently.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for ((label, picked) in listOf("Home screen" to false, "Lock screen" to true, "Home screen and lock screen" to false)) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(MaterialTheme.shapes.medium)
                    .border(if (picked) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.medium)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = if (picked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (picked) {
                    Text("Pick this", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    DecorativeIcon(Icons.Check, tint = MaterialTheme.colorScheme.primary, size = 20.dp)
                }
            }
        }
    }
}

@Composable
internal fun ApplyPage(
    apply: ApplyState, matchColours: Boolean, onMatchColours: (Boolean) -> Unit, onOpenPicker: () -> Unit, onChooseHome: () -> Unit,
    onNext: () -> Unit, onBack: () -> Unit,
) {
    val done = apply == ApplyState.DONE || apply == ApplyState.HOME_ALSO
    SetupPage(
        SetupStep.APPLY, "Put it on your lock screen", onBack,
        actions = {
            when (apply) {
                ApplyState.DONE, ApplyState.HOME_ALSO -> PrimaryAction("Continue", onNext)
                ApplyState.NOT_STARTED -> {
                    PrimaryAction("Open wallpaper screen", onOpenPicker)
                    QuietAction("Skip for now", onNext)
                }
                ApplyState.NOT_SEEN_YET, ApplyState.HOME_ONLY -> {
                    PrimaryAction("Try again", onOpenPicker)
                    QuietAction("Skip for now", onNext)
                }
            }
        },
    ) {
        when (apply) {
            ApplyState.DONE -> StatusBanner(BannerKind.OK, "It's on your lock screen", body = "Your home screen is unchanged.")
            ApplyState.HOME_ALSO -> {
                StatusBanner(
                    BannerKind.WARNING, "Home screen changed too",
                    body = "Your home wallpaper was replaced. The list only shows on the lock screen.",
                    actionLabel = "Choose a home wallpaper", onAction = onChooseHome, quietAction = true,
                )
            }
            ApplyState.HOME_ONLY -> StatusBanner(
                BannerKind.WARNING, "That was the home screen",
                body = "Try again and choose Lock screen.",
            )
            ApplyState.NOT_SEEN_YET -> StatusBanner(
                BannerKind.ATTENTION, "Not on your lock screen yet",
                body = "Try again and choose Lock screen.",
            )
            ApplyState.NOT_STARTED -> Unit
        }

        if (!done) {
            NumberedStep(1, AnnotatedString("Tap the button below."))
            NumberedStep(2, boldWords("Tap Set wallpaper, then choose ", "Lock screen", "."))
            NumberedStep(3, AnnotatedString("Come back here."))
            PickerSchematic()
            SwitchRow(
                "Match my current lock screen's colours",
                "Use your wallpaper's colours as the background.",
                matchColours, onMatchColours,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Notifications covering the list

@Composable
internal fun NotificationsPage(onAdjustPosition: () -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    SetupPage(
        SetupStep.NOTIFICATIONS, "Keep the list visible", onBack,
        actions = { PrimaryAction("Continue", onNext) },
    ) {
        Body("Notifications can cover the list on your lock screen. You can change that now, or skip this.")
        Text("Show less on the lock screen", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Body("Set notifications to icons only, or hide them.")
        OutlinedButton(
            onClick = { if (openLockScreenSettings(context) == null) Toast.makeText(context, "Could not open Settings", Toast.LENGTH_SHORT).show() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Open lock screen settings") }
        Text("Or move the list", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Body("Put the list lower than the notifications.")
        OutlinedButton(onClick = onAdjustPosition, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Move the list") }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Reminders, widgets

@Composable
internal fun RemindersPage(app: PrayerApp, resumes: Int, state: SettingsState, onNext: () -> Unit, onBack: () -> Unit) {
    val settings = state.settings
    val flow = rememberReminderFlow(app, settings, resumes, state::change, offerExact = false) // the next page asks for exact alarms
    SetupPage(
        SetupStep.REMINDERS, "Prayer reminders", onBack,
        actions = {
            if (settings.remindersOn) {
                PrimaryAction("Continue", onNext)
            } else {
                PrimaryAction("Turn on reminders") { flow.setEnabled(true) }
                QuietAction("Not now", onNext)
            }
        },
    ) {
        Body("A notification at each prayer time. Everything stays on this phone.")
        if (!settings.remindersOn) Body("Android will ask you to allow notifications.")
        if (settings.remindersOn) {
            StatusBanner(
                BannerKind.OK, "Reminders are on",
                body = "For every prayer except Syuruk. Change this under Reminders.",
            )
        }
    }
    flow.Dialogs()
}

/**
 * Exact alarms, for reminders only (the widgets never use them). Allowed or not is read again on every return from Android's screen
 * ([resumes]). Skipping is fine: reminders still come, up to an hour late.
 */
@Composable
internal fun ExactAlarmsPage(app: PrayerApp, resumes: Int, state: SettingsState, onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val allowed = remember(resumes) { app.reminders.canScheduleExact() }
    SetupPage(
        SetupStep.EXACT_ALARMS, "Exact alarms", onBack,
        actions = {
            if (allowed) {
                PrimaryAction("Continue", onNext)
            } else {
                PrimaryAction("Allow exact alarms") { SystemScreens.open(context, SystemScreens.exactAlarms(context)) }
                QuietAction("Not now", onNext)
            }
        },
    ) {
        Body("Reminders arrive on the exact minute. Without this they can be up to an hour late.")
        if (!state.settings.remindersOn) Body("Reminders are off, so you can skip this.")
        if (allowed) {
            StatusBanner(BannerKind.OK, "Exact alarms are on")
        } else {
            StatusBanner(BannerKind.ATTENTION, "Exact alarms are off", body = "Turn it on in Android's screen, then come back.")
        }
    }
}

@Composable
internal fun WidgetsPage(app: PrayerApp, resumes: Int, state: SettingsState, onNext: () -> Unit, onBack: () -> Unit) {
    val settings = state.settings
    val backdrop = rememberBackdrop(app, resumes)
    SetupPage(
        SetupStep.WIDGETS, "Home screen widgets", onBack,
        actions = { PrimaryAction("Continue", onNext) },
    ) {
        Body("Add prayer times to your home screen. You can also do this later.")
        WidgetOffers(app, settings, backdrop)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Done

@Composable
private fun SummaryRow(ok: Boolean, text: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DecorativeIcon(if (ok) Icons.Check else Icons.Info, tint = if (ok) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun DonePage(app: PrayerApp, resumes: Int, state: SettingsState, apply: ApplyState, onFinish: () -> Unit, onBack: () -> Unit) {
    val settings = state.settings
    var placed by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(resumes) { placed = withContext(Dispatchers.IO) { app.widgets.widgetCount() } }
    val onLock = apply == ApplyState.DONE || apply == ApplyState.HOME_ALSO
    SetupPage(
        SetupStep.DONE, if (onLock) "You're all set" else "Almost there", onBack,
        actions = { PrimaryAction("Finish", onFinish) },
    ) {
        SummaryRow(onLock, if (onLock) "On your lock screen" else "Not on your lock screen yet. Use Set up on the home screen.")
        SummaryRow(settings.remindersOn, if (settings.remindersOn) "Reminders are on" else "Reminders are off")
        if (BuildConfig.EXACT_ALARMS && settings.remindersOn) {
            val exact = remember(resumes) { app.reminders.canScheduleExact() }
            SummaryRow(exact, if (exact) "Exact alarms are on" else "Exact alarms are off: reminders can be late")
        }
        SummaryRow((placed ?: 0) > 0, if ((placed ?: 0) > 0) "Widgets added" else "No widgets yet")
        Body("You can change anything later from the home screen.")
    }
}
