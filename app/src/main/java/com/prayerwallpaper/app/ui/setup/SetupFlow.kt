package com.prayerwallpaper.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.SetupStep
import com.prayerwallpaper.app.logic.applyState
import com.prayerwallpaper.app.ui.chooseHomeWallpaper
import com.prayerwallpaper.app.ui.openLiveWallpaperPicker
import com.prayerwallpaper.app.ui.rememberSettings
import com.prayerwallpaper.app.ui.rememberWallpaperStatus

/**
 * The first-run setup, one page at a time: a welcome with the real preview, putting the wallpaper on the lock screen (checked when the
 * user comes back from Android's screen), keeping notifications from covering the list, then reminders, exact alarms and widgets, which
 * are optional, and a closing page. The page the user is on
 * ([step]) and whether the user is back from Android's screen ([openedPicker]) are held by the caller, so a trip to the place chooser returns to
 * the same page. [onExit] goes to the home screen; the setup counts as done only through its last page.
 */
@Composable
fun SetupFlow(
    app: PrayerApp, resumes: Int, step: SetupStep, onStep: (SetupStep) -> Unit, openedPicker: Boolean, onOpenedPicker: (Boolean) -> Unit,
    onChangePlace: () -> Unit, onAdjustPosition: () -> Unit, onExit: () -> Unit,
) {
    val context = LocalContext.current
    val state = rememberSettings(app, resumes)
    val settings = state.settings
    val wallpaper = rememberWallpaperStatus(resumes)
    val apply = applyState(wallpaper.lockIsOurs, wallpaper.homeIsOurs, openedPicker)
    /** Leaving before the end: the larger text question has been seen, but the setup is not done, so the home screen offers it again. */
    fun leaveEarly() {
        state.change { it.copy(accessAsked = true) }
        onExit()
    }

    fun finish() {
        state.change { it.copy(accessAsked = true, setupDone = true) }
        onExit()
    }

    fun back() {
        val previous = step.previous(BuildConfig.EXACT_ALARMS)
        if (previous == null) leaveEarly() else onStep(previous)
    }

    fun openPicker() {
        onOpenedPicker(true)
        openLiveWallpaperPicker(context)
    }

    BackHandler { back() }

    // The page after a trip to Android's screen: say what happened. A new visit to the page starts clean.
    LaunchedEffect(step) { if (step != SetupStep.APPLY) onOpenedPicker(false) }

    when (step) {
        SetupStep.WELCOME -> WelcomePage(
            app, resumes, state, onNext = { onStep(SetupStep.APPLY) }, onSkip = ::leaveEarly, onChangePlace = onChangePlace,
        )
        SetupStep.APPLY -> ApplyPage(
            app = app, state = state, apply = apply,
            onOpenPicker = ::openPicker, onChooseHome = { chooseHomeWallpaper(context) },
            onNext = { onStep(SetupStep.NOTIFICATIONS) }, onBack = ::back,
        )
        SetupStep.NOTIFICATIONS -> NotificationsPage(onAdjustPosition = onAdjustPosition, onNext = { onStep(SetupStep.REMINDERS) }, onBack = ::back)
        SetupStep.REMINDERS -> RemindersPage(app, resumes, state, onNext = { onStep(SetupStep.REMINDERS.next(BuildConfig.EXACT_ALARMS)!!) }, onBack = ::back)
        SetupStep.EXACT_ALARMS -> ExactAlarmsPage(app, resumes, state, onNext = { onStep(SetupStep.WIDGETS) }, onBack = ::back)
        SetupStep.WIDGETS -> WidgetsPage(app, resumes, state, onNext = { onStep(SetupStep.DONE) }, onBack = ::back)
        SetupStep.DONE -> DonePage(app, resumes, state, apply, onFinish = ::finish, onBack = ::back)
    }
}
