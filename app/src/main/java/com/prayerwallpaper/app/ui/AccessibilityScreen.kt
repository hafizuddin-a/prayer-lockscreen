package com.prayerwallpaper.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.logic.SUGGESTED_DIM
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.countdown
import com.prayerwallpaper.app.logic.previewDescription
import com.prayerwallpaper.app.logic.todayRows
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import com.prayerwallpaper.app.wallpaper.WallpaperRenderer
import com.prayerwallpaper.app.wallpaper.planFor
import kotlin.math.roundToInt

/**
 * Larger text, a high contrast card and bold letters for the list on the lock screen, with a picture of the result, and today's
 * times as plain text for a screen reader (the wallpaper is a picture).
 */
@Composable
fun AccessibilityScreen(app: PrayerApp, resumes: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val state = rememberSettings(app, resumes)
    val settings = state.settings
    val change = state::change
    val refresh = rememberDataRefresh(app, settings.place.usesMuis, auto = false)

    val look = remember(settings) { app.looks.of(settings) }
    val timetable = remember(refresh.data, settings) { app.timetable(settings, refresh.data) }
    // What is in force now. The phone's own accessibility settings are read again whenever the configuration changes (font
    // size, bold text) and each time the app comes back to the front.
    val configuration = LocalConfiguration.current
    val access = remember(settings, resumes, configuration) { app.accessOptions(settings) }
    val hijriLine = app.hijriLine(settings, timetable)
    val times = rememberLiveTimes(app, timetable, resumes)
    val screen = LocalResources.current.displayMetrics
    val previewText = previewDescription(times.snapshot, times.now, settings.effectiveNames, times.is24Hour)

    // The fit rule is worked out for the real screen, so the preview can say when it had to make the text smaller.
    val hijriLines = if (hijriLine == null) 0 else if (hijriLine.event != null) 2 else 1
    val plan = planFor(screen.widthPixels, screen.heightPixels, access, settings.listTop, hijriLines, settings.showCountdown)
    val noteRenderer = remember { WallpaperRenderer(context) }
    val mixedBackground = plan != null && access.textSize != TextSize.STANDARD && !access.highContrast && settings.dim < SUGGESTED_DIM &&
        noteRenderer.isMixedBehind(look, screen.widthPixels, screen.heightPixels, plan.contentLeft, plan.listTop, plan.contentWidth, 6 * plan.pitch)

    /** Changing an accessibility option by hand stops following the system and keeps everything else as it is shown now. */
    fun changeAccess(options: AccessOptions) = change {
        it.copy(followSystem = false, textSize = options.textSize, highContrast = options.highContrast, cardStyle = options.cardStyle, boldText = options.bold)
    }

    ScreenScaffold("Easier reading", onBack) {
        SectionCard("Accessibility") {
            Text(
                "Bigger text, a high contrast card, bold letters.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                PhonePreview(
                    timetable = timetable, clock = app.clock, look = look, names = settings.effectiveNames, hijri = hijriLine,
                    hijriAlign = settings.hijriAlign, showCountdown = settings.showCountdown, access = access,
                    onListTopChange = {}, modifier = Modifier.fillMaxWidth(0.42f), aspect = screen.widthPixels.toFloat() / screen.heightPixels,
                    draggable = false, description = previewText,
                )
            }
            if (plan?.reduced == true) {
                Text(
                    "Text was made a little smaller to fit this screen.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                )
            }
            if (mixedBackground) {
                Text(
                    "Your background is light in places and dark in others. Dimming by ${(SUGGESTED_DIM * 100).roundToInt()}% would help.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { change { it.copy(dim = maxOf(it.dim, SUGGESTED_DIM)) } }) { Text("Dim background by ${(SUGGESTED_DIM * 100).roundToInt()}%") }
            }
            AccessibilityControls(
                following = settings.followSystem, shown = access, cardStyle = access.cardStyle,
                onFollow = { on -> if (on) change { it.copy(followSystem = true) } else changeAccess(access) },
                onChange = { options -> changeAccess(options) },
            )
        }

        TodaysTimesCard(todayRows(times.snapshot, settings.effectiveNames, times.is24Hour), times.snapshot.countdown(times.now, settings.effectiveNames)?.let { "${it.text}." })
    }
}
