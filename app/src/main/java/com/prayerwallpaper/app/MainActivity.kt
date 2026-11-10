package com.prayerwallpaper.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.prayerwallpaper.app.ui.AppRoot

class MainActivity : ComponentActivity() {
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One black and white look on every phone, so the bars are always the dark kind, whatever the phone's own theme.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        val app = application as PrayerApp
        setContent { AppRoot(app, resumes) }
    }

    override fun onStart() {
        super.onStart()
        (application as PrayerApp).updates.appVisible = true
    }

    override fun onStop() {
        (application as PrayerApp).updates.appVisible = false
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        resumes++
        // The settings app opening is a moment to bring the widgets up to date, off the main thread.
        val app = application as PrayerApp
        Thread {
            app.widgets.updateAll("app opened")
            app.widgets.publishPreviews()
        }.start()
        // Looks for a new version if it is time (the build downloaded from GitHub only), and goes on with an update that was waiting for a permission.
        app.updates.onAppResumed()
    }
}
