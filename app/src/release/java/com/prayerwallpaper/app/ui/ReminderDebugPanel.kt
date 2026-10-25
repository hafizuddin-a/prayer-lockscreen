package com.prayerwallpaper.app.ui

import androidx.compose.runtime.Composable
import com.prayerwallpaper.app.PrayerApp

/** Release builds have no reminder debug controls: the real prayer times and the app's own choice of alarm method always apply. */
@Composable
@Suppress("UNUSED_PARAMETER")
internal fun ReminderDebugPanel(app: PrayerApp, onChanged: () -> Unit) = Unit
