package com.prayerwallpaper.app.ui

import androidx.compose.runtime.Composable
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.ui.components.ScreenScaffold

/** Home screen widgets: live previews with the buttons that add them, and how they look. */
@Composable
fun WidgetsScreen(app: PrayerApp, resumes: Int, onBack: () -> Unit, onHealth: () -> Unit) {
    val state = rememberSettings(app, resumes)
    ScreenScaffold("Home screen widgets", onBack) {
        WidgetsCard(app, state.settings, resumes, state::change, onHealth)
    }
}
