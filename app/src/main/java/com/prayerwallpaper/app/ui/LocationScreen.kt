package com.prayerwallpaper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.HomeSummary
import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import java.time.format.DateTimeFormatter

private val fetchedFormat = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(com.prayerwallpaper.app.logic.SINGAPORE)

/** Where the times are for, what the prayers are called, and (for Singapore) the downloaded MUIS timetable. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocationScreen(app: PrayerApp, resumes: Int, onBack: () -> Unit, onChangePlace: () -> Unit, onCalculation: () -> Unit) {
    val state = rememberSettings(app, resumes)
    val settings = state.settings
    val change = state::change
    val refresh = rememberDataRefresh(app, settings.place.usesMuis, auto = false)

    ScreenScaffold("Location and names", onBack) {
        SectionCard("Location") {
            val place = settings.place
            val asar = Prayer.ASAR.label(settings.effectiveNames)
            Text(HomeSummary.placeName(place), style = MaterialTheme.typography.titleSmall)
            Text(
                if (place.usesMuis) {
                    "Official MUIS times, from data.gov.sg."
                } else {
                    "Calculated on this phone. ${settings.effectiveMethod.title}, ${settings.effectiveAsr.title} $asar."
                },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onChangePlace) { Text("Change location") }
                if (!place.usesMuis) OutlinedButton(onClick = onCalculation) { Text("Calculation") }
            }
            if (!place.usesMuis) Text(Legal.CALCULATED_DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GroupHeading("Prayer names")
            ChoiceRow(settings.effectiveNames == NameSet.MALAY, "Malay", "Subuh, Syuruk, Zohor, Asar, Maghrib, Isyak") { change { it.copy(names = NameSet.MALAY) } }
            ChoiceRow(settings.effectiveNames == NameSet.ENGLISH, "English and Arabic", "Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha") { change { it.copy(names = NameSet.ENGLISH) } }
        }

        if (settings.place.usesMuis) {
            SectionCard("Prayer times data") {
                if (refresh.data.years.isEmpty()) Text("Nothing downloaded yet.")
                refresh.data.years.forEach { y ->
                    Text("${y.year}: ${y.days.size} days from dataset ${y.datasetId}, fetched ${fetchedFormat.format(y.fetchedAt)}", style = MaterialTheme.typography.bodyMedium)
                }
                Button(onClick = { refresh.refresh(force = true) }, enabled = !refresh.busy) { Text("Refresh now") }
                if (refresh.status.isNotEmpty()) Text(refresh.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
