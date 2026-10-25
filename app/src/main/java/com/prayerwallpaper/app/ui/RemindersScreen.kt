package com.prayerwallpaper.app.ui

import androidx.compose.runtime.Composable
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.ui.components.ScreenScaffold

/** Prayer reminders: the switch with its permission steps, which prayers, the heads-up, the sound, and Reminder health. */
@Composable
fun RemindersScreen(app: PrayerApp, resumes: Int, onBack: () -> Unit, onHealth: () -> Unit) {
    val state = rememberSettings(app, resumes)
    ScreenScaffold("Reminders", onBack) {
        RemindersCard(app, state.settings, resumes, state::change, onHealth)
    }
}
