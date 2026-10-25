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

    override fun onResume() {
        super.onResume()
        resumes++
        // The settings app opening is a moment to bring the widgets up to date, off the main thread.
        val app = application as PrayerApp
        Thread {
            app.widgets.updateAll("app opened")
            app.widgets.publishPreviews()
        }.start()
    }
}
