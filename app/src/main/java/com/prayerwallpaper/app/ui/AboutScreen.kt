package com.prayerwallpaper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.BuildConfig
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.SINGAPORE

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(app: PrayerApp, onBack: () -> Unit) {
    val context = LocalContext.current
    // When the timetable was last downloaded, which the licence's notice has to state.
    val accessedOn = remember { app.repository.data().years.maxOfOrNull { it.fetchedAt }?.atZone(SINGAPORE)?.toLocalDate() }
    fun open(url: String) = openUrl(context, url)

    ScreenScaffold("About", onBack, spacing = 14.dp) {
        Text("Prayer Live Wallpaper, version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)

        Text("Feedback", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text("Found a bug, or want a feature? Tell me on Instagram.")
        Button(onClick = { open(Legal.FEEDBACK_URL) }) { Text("Talk to the Developer, ${Legal.FEEDBACK_HANDLE}") }


        Text("Singapore", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(Legal.ATTRIBUTION)
        Text(Legal.licenceNotice(accessedOn), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { open(Legal.LICENCE_URL) }) { Text("Open Data Licence") }
        Text(Legal.DISCLAIMER)
        Text(Legal.HIJRI_CREDIT)

        Text("Other places", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(Legal.CALCULATED_DISCLAIMER)
        Text(Legal.ADHAN_CREDIT, style = MaterialTheme.typography.bodySmall)
        Text(Legal.ADHAN_LICENCE, style = MaterialTheme.typography.bodySmall)
        Text(Legal.GEONAMES_CREDIT)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open(Legal.GEONAMES_URL) }) { Text("GeoNames") }
            OutlinedButton(onClick = { open(Legal.CC_BY_URL) }) { Text("CC BY 4.0") }
        }

        Text("Privacy", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(Legal.PRIVACY_SUMMARY)
        if (Legal.PRIVACY_POLICY_URL.isNotBlank()) {
            OutlinedButton(onClick = { open(Legal.PRIVACY_POLICY_URL) }) { Text("Privacy policy") }
        }

        Text("Licences", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(Legal.OPEN_SOURCE)
    }
}
