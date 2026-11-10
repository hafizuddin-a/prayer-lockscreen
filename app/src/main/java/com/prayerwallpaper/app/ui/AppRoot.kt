package com.prayerwallpaper.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.AppScreen
import com.prayerwallpaper.app.logic.Navigation
import com.prayerwallpaper.app.logic.SetupStep
import com.prayerwallpaper.app.logic.shouldShowSetup
import com.prayerwallpaper.app.settings.ColorCapture
import com.prayerwallpaper.app.ui.setup.SetupFlow
import com.prayerwallpaper.app.ui.theme.PrayerTheme

/** [resumes] changes each time the activity comes back to the front, so screens can re-read system state. */
@Composable
fun AppRoot(app: PrayerApp, resumes: Int) {
    val context = LocalContext.current
    PrayerTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Whether this start is a first run is decided once, here: answering the first setup page changes the very things it is read from.
            val firstRun = remember {
                val s = app.settings.read()
                shouldShowSetup(s.setupDone, s.accessAsked, ColorCapture(context).lockIsThisApp(), s.captured != null, s.captureSkipped)
            }
            var stack by rememberSaveable(
                stateSaver = listSaver(save = { it.map(AppScreen::name) }, restore = { names -> names.map(AppScreen::valueOf) }),
            ) { mutableStateOf(listOf(if (firstRun) AppScreen.SETUP else AppScreen.HOME)) }
            // The setup's place is kept here, not inside it, so a trip to the place chooser comes back to the same page.
            var setupStep by rememberSaveable { mutableStateOf(SetupStep.WELCOME) }
            // Android's wallpaper screen counts as seen only once the user is back from it (the app resumed after it was opened), so
            // the setup never says "not on your lock screen yet" while that screen is still opening.
            var pickerOpenedAt by rememberSaveable { mutableIntStateOf(-1) }
            val openedPicker = pickerOpenedAt in 0 until resumes

            fun open(to: AppScreen) { stack = Navigation.open(stack, to) }
            fun back() { stack = Navigation.back(stack) }
            fun runSetup(from: SetupStep) { setupStep = from; pickerOpenedAt = -1; open(AppScreen.SETUP) }

            BackHandler(enabled = Navigation.handlesBack(stack)) { back() }
            when (stack.last()) {
                AppScreen.HOME -> HomeScreen(app, resumes, open = ::open, onSetUp = { runSetup(SetupStep.APPLY) }, onRunSetupAgain = { runSetup(SetupStep.WELCOME) })
                AppScreen.SETUP -> SetupFlow(
                    app, resumes, step = setupStep, onStep = { setupStep = it }, openedPicker = openedPicker, onOpenedPicker = { pickerOpenedAt = if (it) resumes else -1 },
                    onChangePlace = { open(AppScreen.PLACE) }, onAdjustPosition = { open(AppScreen.LOOK) }, onExit = { stack = listOf(AppScreen.HOME) },
                )
                AppScreen.LOOK -> LookScreen(app, resumes, onBack = ::back, onAdjustPhoto = { open(AppScreen.PHOTO) })
                AppScreen.LOCATION -> LocationScreen(app, resumes, onBack = ::back, onChangePlace = { open(AppScreen.PLACE) }, onCalculation = { open(AppScreen.CALCULATION) })
                AppScreen.PLACE -> PlaceScreen(app, onDone = ::back)
                AppScreen.CALCULATION -> CalculationScreen(app, onDone = ::back)
                AppScreen.PHOTO -> PhotoAdjustScreen(app, onDone = ::back)
                AppScreen.ACCESS -> AccessibilityScreen(app, resumes, onBack = ::back)
                AppScreen.REMINDERS -> RemindersScreen(app, resumes, onBack = ::back, onHealth = { open(AppScreen.REMINDER_HEALTH) })
                AppScreen.REMINDER_HEALTH -> ReminderHealthScreen(app, onBack = ::back)
                AppScreen.WIDGETS -> WidgetsScreen(app, resumes, onBack = ::back, onHealth = { open(AppScreen.WIDGET_HEALTH) })
                AppScreen.WIDGET_HEALTH -> WidgetHealthScreen(app, onBack = ::back)
                AppScreen.ABOUT -> AboutScreen(app, resumes, onBack = ::back)
            }
        }
    }
}
