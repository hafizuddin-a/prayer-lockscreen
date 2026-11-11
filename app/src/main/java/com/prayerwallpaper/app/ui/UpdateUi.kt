package com.prayerwallpaper.app.ui

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.ui.components.BannerKind
import com.prayerwallpaper.app.ui.components.StatusBanner
import com.prayerwallpaper.app.update.UpdateState

/** The one thing a person can do about an update right now, as a button label and what it does; null while something is already under way. */
private class UpdateAction(val label: String, val run: () -> Unit)

@Composable
private fun updateActionFor(app: PrayerApp, state: UpdateState): UpdateAction? {
    val context = LocalContext.current
    return when (state) {
        is UpdateState.Available -> UpdateAction("Update") { app.updates.updateAsync() }
        is UpdateState.NeedsInstallPermission -> UpdateAction("Allow") { SystemScreens.open(context, SystemScreens.unknownSources(context)) }
        is UpdateState.Failed -> if (state.release != null) UpdateAction("Try again") { app.updates.updateAsync() } else UpdateAction("Check for updates") { app.updates.checkAsync(force = true) }
        is UpdateState.Idle, is UpdateState.UpToDate -> UpdateAction("Check for updates") { app.updates.checkAsync(force = true) }
        is UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> null
    }
}

/** How far the download has got, in words. */
private fun downloadWords(state: UpdateState.Downloading): String {
    val total = state.release.apkBytes
    return if (total > 0) "${(state.bytes * 100 / total).coerceIn(0, 100)}%" else "Downloading…"
}

/**
 * The top of the home screen while an update needs attention: it is available, needs Android's OK, is on its way, or failed. Shows
 * nothing when the app is up to date, and nothing at all in a build that does not update itself.
 */
@Composable
fun UpdateBanner(app: PrayerApp) {
    if (!BuildConfig.SELF_UPDATE) return
    val state by app.updates.state.collectAsState()
    val action = updateActionFor(app, state)
    when (val s = state) {
        is UpdateState.Available -> StatusBanner(
            BannerKind.ATTENTION, "Update available", body = "Version ${s.release.version} is ready.",
            actionLabel = action?.label, onAction = action?.run,
        )
        is UpdateState.NeedsInstallPermission -> StatusBanner(
            BannerKind.ATTENTION, "Allow updates", body = "Android needs your OK. Tap Allow, switch it on, then come back.",
            actionLabel = action?.label, onAction = action?.run,
        )
        is UpdateState.Downloading -> StatusBanner(BannerKind.ATTENTION, "Downloading update", body = downloadWords(s))
        is UpdateState.Installing -> StatusBanner(BannerKind.ATTENTION, "Installing update", body = "Follow Android's prompt if it asks.")
        // A failed check is shown under About; the home screen only reports an update that was tried and failed.
        is UpdateState.Failed -> if (s.release != null) StatusBanner(
            BannerKind.WARNING, "Couldn't update", body = s.message, actionLabel = action?.label, onAction = action?.run,
        )
        is UpdateState.Idle, is UpdateState.UpToDate, is UpdateState.Checking -> Unit
    }
}

/** The About screen's Updates section: where things stand, one button, and the switch for automatic updates. Not shown in a Play build. */
@Composable
fun UpdatesSection(app: PrayerApp, settings: SettingsState) {
    if (!BuildConfig.SELF_UPDATE) return
    val state by app.updates.state.collectAsState()
    val action = updateActionFor(app, state)
    Text("Updates", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    Text(
        when (val s = state) {
            is UpdateState.Idle -> "Version ${BuildConfig.VERSION_NAME}."
            is UpdateState.UpToDate -> "You have the latest version."
            is UpdateState.Checking -> "Checking…"
            is UpdateState.Available -> "Version ${s.release.version} is available."
            is UpdateState.NeedsInstallPermission -> "Android needs your OK to install updates. Tap Allow, switch it on, then come back."
            is UpdateState.Downloading -> "Downloading version ${s.release.version}: ${downloadWords(s)}"
            is UpdateState.Installing -> "Installing version ${s.release.version}. Follow Android's prompt if it asks."
            is UpdateState.Failed -> s.message
        },
    )
    if (action != null) {
        val primary = state is UpdateState.Available || state is UpdateState.NeedsInstallPermission
        if (primary) Button(onClick = action.run) { Text(action.label) } else OutlinedButton(onClick = action.run) { Text(action.label) }
    }
    SwitchRow(
        "Keep the app up to date", "Looks on GitHub once a month for a new version and installs it.",
        settings.settings.autoUpdate,
        onChange = { on ->
            settings.change { it.copy(autoUpdate = on) }
            app.syncBackgroundJobs()
        },
    )
}
