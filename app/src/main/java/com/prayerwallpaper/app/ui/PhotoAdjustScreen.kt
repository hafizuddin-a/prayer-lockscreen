package com.prayerwallpaper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.MAX_PHOTO_ZOOM
import com.prayerwallpaper.app.logic.MIN_PHOTO_ZOOM
import com.prayerwallpaper.app.logic.PhotoView
import com.prayerwallpaper.app.logic.adjustPhotoView
import com.prayerwallpaper.app.settings.AppSettings
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import kotlin.math.roundToInt

/**
 * Frames the background photo. The preview is the real screen's shape and uses the real renderer, so
 * what lines up here is what the lock screen shows. Drag to move, pinch to zoom; the sliders do the
 * same for anyone who prefers them.
 */
@Composable
fun PhotoAdjustScreen(app: PrayerApp, onDone: () -> Unit) {
    val store = app.settings
    var settings by remember { mutableStateOf(store.read()) }
    fun change(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings)
        settings = next
        store.write(next)
    }

    val bitmap = remember(settings.photoVersion) { app.photos.bitmap(settings.photoVersion) }
    if (bitmap == null) {
        ScreenScaffold("Adjust photo", onDone) {
            Text("Choose a photo first.")
            Button(onClick = onDone) { Text("Back") }
        }
        return
    }

    val screen = LocalResources.current.displayMetrics
    val aspect = screen.widthPixels.toFloat() / screen.heightPixels
    val timetable = remember { app.timetable(settings) }
    val look = remember(settings) { app.looks.of(settings) }

    ScreenScaffold("Adjust photo", onDone, scrolling = false, spacing = 6.dp) {
        Text("Drag to move, pinch to zoom.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // The preview takes whatever height is left, at the real screen's proportions.
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            PhonePreview(
                timetable = timetable,
                clock = app.clock,
                look = look,
                names = settings.effectiveNames,
                hijri = app.hijriLine(settings, timetable),
                hijriAlign = settings.hijriAlign,
                showCountdown = settings.showCountdown,
                access = app.accessOptions(settings),
                onListTopChange = {},
                aspect = aspect,
                fitHeight = true,
                onPhotoTransform = { centroid, zoom, pan, size ->
                    change {
                        val view = adjustPhotoView(
                            PhotoView(it.photoZoom, it.photoPanX, it.photoPanY),
                            bitmap.width, bitmap.height, size.width.toFloat(), size.height.toFloat(),
                            centroid.x, centroid.y, zoom, pan.x, pan.y,
                        )
                        it.copy(photoZoom = view.zoom, photoPanX = view.panX, photoPanY = view.panY)
                    }
                },
            )
        }

        Text("Zoom: ${"%.1f".format(settings.photoZoom)}×", style = MaterialTheme.typography.labelMedium)
        Slider(
            settings.photoZoom, { z -> change { it.copy(photoZoom = z) } }, valueRange = MIN_PHOTO_ZOOM..MAX_PHOTO_ZOOM,
            modifier = Modifier.semantics { contentDescription = "Zoom"; stateDescription = "%.1f times".format(settings.photoZoom) },
        )
        Text("Left to right: ${(settings.photoPanX * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(
            settings.photoPanX, { x -> change { it.copy(photoPanX = x) } },
            modifier = Modifier.semantics { contentDescription = "Photo position, left to right"; stateDescription = "${(settings.photoPanX * 100).roundToInt()}%" },
        )
        Text("Top to bottom: ${(settings.photoPanY * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(
            settings.photoPanY, { y -> change { it.copy(photoPanY = y) } },
            modifier = Modifier.semantics { contentDescription = "Photo position, top to bottom"; stateDescription = "${(settings.photoPanY * 100).roundToInt()}%" },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { change { it.copy(photoZoom = 1f, photoPanX = 0.5f, photoPanY = 0.5f) } }, modifier = Modifier.weight(1f)) { Text("Reset") }
            Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text("Done") }
        }
    }
}
