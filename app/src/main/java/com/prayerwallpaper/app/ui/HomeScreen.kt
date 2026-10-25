package com.prayerwallpaper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.logic.ApplyState
import com.prayerwallpaper.app.logic.AppScreen
import com.prayerwallpaper.app.logic.HomeSummary
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.applyState
import com.prayerwallpaper.app.logic.previewDescription
import com.prayerwallpaper.app.ui.components.BannerKind
import com.prayerwallpaper.app.ui.components.Icons
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import com.prayerwallpaper.app.ui.components.StatusBanner
import com.prayerwallpaper.app.ui.components.Tile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** How much of the phone's height the home screen's preview shows: down to just under the list, and the rest fades away. */
private const val PEEK_SHARE = 0.56f

/**
 * The first screen: whether the wallpaper is on the lock screen, a picture of it, and one row for each group of settings. Each row
 * says in a few words what is set now and opens a screen of its own.
 */
@Composable
fun HomeScreen(app: PrayerApp, resumes: Int, open: (AppScreen) -> Unit, onSetUp: () -> Unit, onRunSetupAgain: () -> Unit) {
    val context = LocalContext.current
    val state = rememberSettings(app, resumes)
    val settings = state.settings
    val wallpaper = rememberWallpaperStatus(resumes)
    // The wallpaper never fetches, so the app does it as soon as it opens (a no-op when the cache is fresh).
    val refresh = rememberDataRefresh(app, settings.place.usesMuis, auto = true)

    val look = remember(settings) { app.looks.of(settings) }
    val timetable = remember(refresh.data, settings) { app.timetable(settings, refresh.data) }
    // The phone's own accessibility settings are read again whenever the configuration changes and each time the app comes back.
    val configuration = LocalConfiguration.current
    val access = remember(settings, resumes, configuration) { app.accessOptions(settings) }
    val hijriLine = app.hijriLine(settings, timetable)

    // The preview's text alternative: the next prayer moves on while the app is open.
    val times = rememberLiveTimes(app, timetable, resumes)
    val screen = LocalResources.current.displayMetrics
    val previewText = previewDescription(times.snapshot, times.now, settings.effectiveNames, times.is24Hour)

    // What the Reminders and Widgets rows say, read off the main thread.
    var placed by remember { mutableStateOf<Int?>(null) }
    var health by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resumes, settings) {
        withContext(Dispatchers.IO) {
            placed = app.widgets.widgetCount()
            health = if (settings.remindersOn) app.reminderHealth().status.text else null
        }
    }

    if (!settings.accessAsked) {
        LargerTextDialog { choice ->
            state.change { s ->
                val asked = s.copy(accessAsked = true)
                if (choice == null) asked else asked.copy(followSystem = false, textSize = choice.textSize, highContrast = choice.highContrast, boldText = choice.bold)
            }
        }
    }

    ScreenScaffold("Prayer Live Wallpaper", onBack = null) {
        // Singapore's times are downloaded once; without them (first run with no internet) the list would only say "open the app".
        // (The status is blank until a download was tried, so this never flashes up before the first attempt.)
        if (settings.place.usesMuis && refresh.data.years.isEmpty() && !refresh.busy && refresh.status.isNotBlank()) {
            StatusBanner(
                BannerKind.WARNING, "No prayer times yet",
                body = "Connect to the internet to download them. ${refresh.status}",
                actionLabel = "Try again", onAction = { refresh.refresh(force = true) },
            )
        }

        when (applyState(wallpaper.lockIsOurs, wallpaper.homeIsOurs, openedPicker = false)) {
            ApplyState.DONE -> StatusBanner(BannerKind.OK, "On your lock screen")
            ApplyState.HOME_ALSO -> StatusBanner(
                BannerKind.WARNING, "Home screen changed too",
                body = "Your home wallpaper was replaced. The list only shows on the lock screen.",
                actionLabel = "Choose a home wallpaper", onAction = { chooseHomeWallpaper(context) },
            )
            ApplyState.HOME_ONLY -> StatusBanner(
                BannerKind.WARNING, "Only on your home screen",
                body = "Set it again and choose Lock screen.",
                actionLabel = "Set up", onAction = onSetUp,
            )
            ApplyState.NOT_STARTED, ApplyState.NOT_SEEN_YET -> StatusBanner(
                BannerKind.ATTENTION, "Not on your lock screen yet",
                actionLabel = "Set up", onAction = onSetUp,
            )
        }

        // A phone that peeks up from below: the clock and the list at a readable size, without the long empty lower half of the screen.
        // With larger text the list is much taller, so the whole phone is shown rather than a crop that would cut it off.
        val peeking = access.textSize == TextSize.STANDARD && !access.highContrast
        val phoneWidth = if (peeking) 0.58f else 0.46f
        val aspect = screen.widthPixels.toFloat() / screen.heightPixels
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val width = maxWidth * phoneWidth
            Box(Modifier.width(width).height(width / aspect * (if (peeking) PEEK_SHARE else 1f)).clipToBounds()) {
                PhonePreview(
                    timetable = timetable, clock = app.clock, look = look, names = settings.effectiveNames, hijri = hijriLine,
                    hijriAlign = settings.hijriAlign, showCountdown = settings.showCountdown, access = access,
                    onListTopChange = {}, modifier = Modifier.fillMaxWidth().wrapContentHeight(align = Alignment.Top, unbounded = true), aspect = aspect,
                    draggable = false, description = previewText,
                )
                if (peeking) {
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(0.62f to Color.Transparent, 1f to MaterialTheme.colorScheme.background),
                        ),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Icons.Look, "Look", HomeSummary.look(settings.mode.title, settings.hijriAlign, settings.showCountdown), onClick = { open(AppScreen.LOOK) })
            Tile(Icons.Place, "Location and names", HomeSummary.location(settings.place, settings.effectiveNames), onClick = { open(AppScreen.LOCATION) })
            Tile(Icons.Bell, "Reminders", HomeSummary.reminders(settings.remindersOn, settings.reminderPrayers.size, health), onClick = { open(AppScreen.REMINDERS) })
            Tile(Icons.Widgets, "Home screen widgets", HomeSummary.widgets(placed), onClick = { open(AppScreen.WIDGETS) })
            Tile(
                Icons.TextSize, "Easier reading",
                HomeSummary.reading(settings.followSystem, AccessOptions(settings.textSize, settings.highContrast, settings.cardStyle, settings.boldText)),
                onClick = { open(AppScreen.ACCESS) },
            )
            Tile(Icons.Chat, "Talk to the Developer", "Message ${Legal.FEEDBACK_HANDLE} on Instagram", onClick = { openUrl(context, Legal.FEEDBACK_URL) })
            Tile(Icons.Info, "About", "Version ${BuildConfig.VERSION_NAME}, licences, privacy", onClick = { open(AppScreen.ABOUT) })
        }

        TextButton(onClick = onRunSetupAgain, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Run setup again") }
    }
}
