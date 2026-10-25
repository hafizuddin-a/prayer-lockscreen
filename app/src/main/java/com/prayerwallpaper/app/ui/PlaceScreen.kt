package com.prayerwallpaper.app.ui

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.HomeSummary
import com.prayerwallpaper.app.data.CityIndex
import com.prayerwallpaper.app.data.DeviceLocation
import com.prayerwallpaper.app.data.LocationResult
import com.prayerwallpaper.app.data.Place
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Wording for the status line after "Use my location". Kept apart from the screen so a test can pin it. */
internal object PlaceMessages {
    const val PERMISSION_DENIED = "No location permission. You can still search for your city."
    const val TURNED_OFF = "Location is off. Turn it on, or search for your city."
    const val NO_FIX = "Couldn't find you. Try near a window, or search for your city."
    const val LIST_FAILED = "Couldn't load the city list. Restart the app. Singapore still works."
    const val EMPTY_HINT = "Type a city, like Karachi or London. Small towns aren't listed: pick the nearest big city."

    fun foundNearest(place: Place, kilometres: Double): String {
        val distance = if (kilometres < 1) "under 1 km" else "${kilometres.roundToInt()} km"
        return "Using ${place.displayName()}, the nearest city ($distance away)"
    }

    fun noResults(query: String) = "No city starting with \"${query.trim()}\". Try a bigger one nearby."
}

@Composable
fun PlaceScreen(app: PrayerApp, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { app.settings.read().place }
    var index by remember { mutableStateOf<CityIndex?>(null) }
    var listFailed by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    var explaining by rememberSaveable { mutableStateOf(false) }

    // About 34,000 lines of text: read off the main thread, and let go of when the screen closes.
    LaunchedEffect(Unit) {
        // A missing or damaged list must not crash the app: the wallpaper runs in this process too.
        runCatching { withContext(Dispatchers.IO) { CityIndex.fromAssets(context) } }
            .onSuccess { index = it }
            .onFailure { listFailed = true }
    }
    val results by produceState(emptyList<Place>(), index, query) {
        value = index?.let { found -> withContext(Dispatchers.Default) { found.search(query) } } ?: emptyList()
    }

    fun pick(place: Place) {
        app.choosePlace(place)
        onDone()
    }

    fun locate() {
        scope.launch {
            locating = true
            status = null
            val result = DeviceLocation.current(context)
            locating = false
            when (result) {
                is LocationResult.Found -> {
                    val cities = index ?: runCatching { withContext(Dispatchers.IO) { CityIndex.fromAssets(context) } }.getOrNull()?.also { index = it }
                    val nearest = cities?.nearest(result.latitude, result.longitude)
                    if (nearest == null) {
                        status = PlaceMessages.NO_FIX
                    } else {
                        Toast.makeText(context, PlaceMessages.foundNearest(nearest.place, nearest.kilometres), Toast.LENGTH_LONG).show()
                        pick(nearest.place)
                    }
                }
                LocationResult.TurnedOff -> status = PlaceMessages.TURNED_OFF
                LocationResult.NoFix -> status = PlaceMessages.NO_FIX
                LocationResult.NoPermission -> status = PlaceMessages.PERMISSION_DENIED
            }
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else status = PlaceMessages.PERMISSION_DENIED
    }

    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text("Use your location?") },
            text = {
                Text(
                    "Your approximate location is used once, to find the nearest city. It isn't saved or sent anywhere.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explaining = false
                    permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { explaining = false }) { Text("Not now") } },
        )
    }

    ScreenScaffold("Choose a location", onDone, scrolling = false, spacing = 10.dp) {
        Text("Now: ${HomeSummary.placeName(current)}", style = MaterialTheme.typography.titleSmall)

        Button(
            enabled = !locating,
            onClick = { if (DeviceLocation.hasPermission(context)) locate() else explaining = true },
        ) { Text(if (locating) "Finding where you are…" else "Use my location") }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search for a city") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )

        when {
            listFailed -> Text(PlaceMessages.LIST_FAILED, color = MaterialTheme.colorScheme.error)
            index == null -> Text("Loading the city list…", style = MaterialTheme.typography.bodyMedium)
            query.isBlank() -> Text(PlaceMessages.EMPTY_HINT, style = MaterialTheme.typography.bodyMedium)
            results.isEmpty() -> Text(PlaceMessages.noResults(query), style = MaterialTheme.typography.bodyMedium)
            else -> LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.id }) { place ->
                    Column(Modifier.fillMaxWidth().clickable { pick(place) }.padding(vertical = 12.dp)) {
                        Text(place.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            listOf(place.region, Place.countryName(place.country)).filter { it.isNotBlank() }.joinToString(", ") +
                                if (place.usesMuis) " · official MUIS timetable" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
