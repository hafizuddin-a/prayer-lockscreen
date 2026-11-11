package com.prayerwallpaper.app.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.HijriAlign
import com.prayerwallpaper.app.logic.hijriStatus
import com.prayerwallpaper.app.logic.previewDescription
import com.prayerwallpaper.app.settings.AppSettings
import com.prayerwallpaper.app.settings.BackgroundMode
import com.prayerwallpaper.app.settings.ColorCapture
import com.prayerwallpaper.app.settings.TextColorSource
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import com.prayerwallpaper.app.wallpaper.listTopRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** How far one tap of Move list up or down moves the list, as a share of the screen height. */
private const val LIST_STEP = 0.02f

/**
 * How the lock screen looks: where the list sits (dragged in a live preview, or with buttons and a slider), what the date and
 * countdown lines say, what is behind the list, and the advice for notifications that cover it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookScreen(app: PrayerApp, resumes: Int, onBack: () -> Unit, onAdjustPhoto: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    val state = rememberSettings(app, resumes)
    val settings = state.settings
    val change = state::change
    val refresh = rememberDataRefresh(app, settings.place.usesMuis, auto = false)
    val wallpaper = rememberWallpaperStatus(resumes)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { app.photos.import(uri) } }
                // A new photo starts centred at 1x; the old framing belonged to the old photo.
                result.onSuccess { version -> change { it.copy(mode = BackgroundMode.PHOTO, photoVersion = version, photoZoom = 1f, photoPanX = 0.5f, photoPanY = 0.5f) } }
                result.onFailure { Toast.makeText(context, "Could not use that photo", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    val look = remember(settings) { app.looks.of(settings) }
    val timetable = remember(refresh.data, settings) { app.timetable(settings, refresh.data) }
    val configuration = LocalConfiguration.current
    val access = remember(settings, resumes, configuration) { app.accessOptions(settings) }
    val hijriLine = app.hijriLine(settings, timetable)
    val hijriShown = hijriLine != null
    val sliderRange = listTopRange(900, 1950, access, hijriShown, settings.showCountdown)
    val times = rememberLiveTimes(app, timetable, resumes)
    val screen = LocalResources.current.displayMetrics
    val previewText = previewDescription(times.snapshot, times.now, settings.effectiveNames, times.is24Hour)

    ScreenScaffold("Look", onBack, scrollState = scroll) {
        SectionCard("Position") {
            Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
                PhonePreview(
                    timetable = timetable,
                    clock = app.clock,
                    look = look,
                    names = settings.effectiveNames,
                    hijri = hijriLine,
                    hijriAlign = settings.hijriAlign,
                    showCountdown = settings.showCountdown,
                    access = access,
                    onListTopChange = { top -> change { it.copy(listTop = top) } },
                    modifier = Modifier.fillMaxWidth(0.62f),
                    aspect = screen.widthPixels.toFloat() / screen.heightPixels,
                    description = previewText,
                )
            }
            Text("Drag the list, or use the buttons or slider. The clock is just a stand-in.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The position in effect: the big layouts cannot go as low as the saved one, and the slider shows that.
            val percent = (settings.listTop.coerceIn(sliderRange.start, sliderRange.endInclusive) * 100).roundToInt()
            Text("List position: $percent% from the top", modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fun nudge(by: Float) = change { it.copy(listTop = (it.listTop + by).coerceIn(sliderRange.start, sliderRange.endInclusive)) }
                OutlinedButton(onClick = { nudge(-LIST_STEP) }) { Text("Move list up") }
                OutlinedButton(onClick = { nudge(LIST_STEP) }) { Text("Move list down") }
            }
            Slider(
                value = settings.listTop.coerceIn(sliderRange.start, sliderRange.endInclusive),
                onValueChange = { top -> change { it.copy(listTop = top) } },
                valueRange = sliderRange.start..sliderRange.endInclusive,
                modifier = Modifier.semantics { contentDescription = "List position"; stateDescription = "$percent% from the top" },
            )
        }

        SectionCard("Hijri date") {
            SwitchRow("Show Hijri date", "A small line above the list.", settings.showHijri, { on -> change { it.copy(showHijri = on) } })
            SwitchRow(
                "Change the date at Maghrib",
                "Off: the date changes at midnight.",
                settings.hijriAtMaghrib, { on -> change { it.copy(hijriAtMaghrib = on) } }, enabled = settings.showHijri,
            )
            SwitchRow(
                "Show Islamic events",
                "Names special days next to the date, like Hari Raya.",
                settings.showHijriEvents, { on -> change { it.copy(showHijriEvents = on) } }, enabled = settings.showHijri,
            )
            val status = hijriStatus(
                settings.place.usesMuis, app.hijri.lastKnownDate,
                java.time.LocalDate.now(app.clock.withZone(com.prayerwallpaper.app.logic.SINGAPORE)),
            )
            Text(
                status.message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Countdown") {
            SwitchRow(
                "Show countdown",
                "A small line under the list, like \"Asar in 2h 14m\".",
                settings.showCountdown, { on -> change { it.copy(showCountdown = on) } },
            )
        }

        SectionCard("Alignment") {
            Text("For the date and countdown lines.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceRow(settings.hijriAlign == HijriAlign.CENTER, "Centred", "In the middle.") { change { it.copy(hijriAlign = HijriAlign.CENTER) } }
            ChoiceRow(settings.hijriAlign == HijriAlign.LEFT, "Left", "Lined up with the list.") { change { it.copy(hijriAlign = HijriAlign.LEFT) } }
        }

        SectionCard("Background") {
            ModeRow(BackgroundMode.PHOTO, settings.mode, "Photo", "Pick a photo. A screenshot of your old wallpaper works well.") { change { s -> s.copy(mode = it) } }
            ModeRow(BackgroundMode.SOLID, settings.mode, "Solid colour", null) { change { s -> s.copy(mode = it) } }
            ModeRow(BackgroundMode.GRADIENT, settings.mode, "Gradient", "Two colours, top to bottom.") { change { s -> s.copy(mode = it) } }

            when (settings.mode) {
                // Kept for old saved settings, which load as Gradient: nothing to show.
                BackgroundMode.MATCH -> Unit
                BackgroundMode.PHOTO -> {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text(if (settings.photoVersion == 0L) "Choose a photo" else "Choose a different photo")
                    }
                    if (settings.photoVersion == 0L) {
                        Text("No photo yet. Showing the gradient.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        OutlinedButton(onClick = onAdjustPhoto) { Text("Adjust position and zoom") }
                        Text(
                            "Drag to move, pinch to zoom.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                BackgroundMode.SOLID -> ColorRow("Colour", settings.solid) { c -> change { it.copy(solid = c) } }
                BackgroundMode.GRADIENT -> {
                    ColorRow("Top", settings.gradientTop) { c -> change { it.copy(gradientTop = c) } }
                    ColorRow("Bottom", settings.gradientBottom) { c -> change { it.copy(gradientBottom = c) } }
                    TextButton(onClick = {
                        change { it.copy(gradientTop = AppSettings.DEFAULT_GRADIENT_TOP, gradientBottom = AppSettings.DEFAULT_GRADIENT_BOTTOM) }
                    }) { Text("Reset to the default blue") }
                }
            }

        }

        // Dim and the text colour read together: how the list's words stand out on the background above.
        SectionCard("Text") {
            GroupHeading("Dim background: ${(settings.dim * 100).roundToInt()}%")
            Slider(
                settings.dim, { d -> change { it.copy(dim = d) } }, valueRange = 0f..AppSettings.MAX_DIM,
                modifier = Modifier.semantics { contentDescription = "Dim background"; stateDescription = "${(settings.dim * 100).roundToInt()}%" },
            )
            Text("Helps the text stand out on bright photos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            GroupHeading("Text colour")
            ChoiceRow(
                settings.textColors == TextColorSource.BACKGROUND, "From the background",
                "Light or dark, to suit the background.",
            ) { change { it.copy(textColors = TextColorSource.BACKGROUND) } }
            ChoiceRow(
                settings.textColors == TextColorSource.CUSTOM, "Pick a colour",
                "One colour for the whole list.",
            ) { change { it.copy(textColors = TextColorSource.CUSTOM) } }
            if (settings.textColors == TextColorSource.CUSTOM) {
                // The sliders keep their own state, so a preset rebuilds them.
                var rebuild by remember { mutableIntStateOf(0) }
                key(rebuild) { ColorRow("Text colour", settings.customTextColor) { c -> change { it.copy(customTextColor = c) } } }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { change { it.copy(customTextColor = 0xFFFFFFFF.toInt()) }; rebuild++ }) { Text("White") }
                    OutlinedButton(onClick = { change { it.copy(customTextColor = 0xFF000000.toInt()) }; rebuild++ }) { Text("Black") }
                }
                Text(
                    "The lock screen dims the wallpaper a little, so the list may look softer than the clock.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ChoiceRow(
                settings.textColors == TextColorSource.SYSTEM, "Android's clock colours",
                "Matches the clock on some phones, like Pixel.",
            ) { change { it.copy(textColors = TextColorSource.SYSTEM) } }
            Text(
                "May be hard to read on photos that are bright in places and dark in others.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard("Notifications covering the list?") {
            Text("Notifications can cover the list. You can:")
            Text("• Move the list lower.")
            TextButton(onClick = { scope.launch { scroll.animateScrollTo(0) } }) { Text("Adjust position") }
            Text("• Show only icons, or hide notifications, in your phone's lock screen settings.")
            Button(onClick = {
                if (openLockScreenSettings(context) == null) Toast.makeText(context, "Could not open Settings", Toast.LENGTH_SHORT).show()
            }) { Text("Open lock screen settings") }
            Text("• Or leave it as it is.")
            Text("This app never reads or changes your phone's settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
