package com.prayerwallpaper.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.HomeSummary
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.logic.AsrRule
import com.prayerwallpaper.app.logic.CalcMethod
import com.prayerwallpaper.app.logic.MAX_ADJUSTMENT_MINUTES
import com.prayerwallpaper.app.logic.MethodDefaults
import com.prayerwallpaper.app.logic.formatClock
import com.prayerwallpaper.app.settings.AppSettings
import com.prayerwallpaper.app.ui.components.ScreenScaffold
import java.time.LocalDate

@Composable
fun CalculationScreen(app: PrayerApp, onDone: () -> Unit) {
    val context = LocalContext.current
    val store = app.settings
    var settings by remember { mutableStateOf(store.read()) }
    fun change(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings)
        settings = next
        store.write(next)
    }

    val place = settings.place
    val defaults = MethodDefaults.forCountry(place.country)

    ScreenScaffold("Calculation", onDone, spacing = 14.dp) {
        Text(HomeSummary.placeName(place), style = MaterialTheme.typography.titleSmall)

        if (place.usesMuis) {
            Text("Singapore uses the official MUIS times, so there is nothing to calculate.")
            return@ScreenScaffold
        }

        SectionCard("Today") {
            val timetable = remember(settings) { app.timetable(settings) }
            val today = LocalDate.now(app.clock.withZone(timetable.zone))
            val day = timetable.days(today)[today]
            if (day == null) {
                Text("Can't calculate times here today. The sun may not rise or set.")
            } else {
                val is24Hour = DateFormat.is24HourFormat(context)
                for (prayer in Prayer.entries) {
                    val clock = formatClock(day[prayer], is24Hour)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(day.label(prayer, settings.effectiveNames))
                        Text((clock.time + " " + clock.suffix).trim())
                    }
                }
            }
        }

        SectionCard("Method") {
            for (method in CalcMethod.entries) {
                ChoiceRow(
                    selected = method == settings.effectiveMethod,
                    title = method.title + if (method == defaults.method) " (usual here)" else "",
                    description = method.detail,
                ) { change { it.copy(method = method) } }
            }
        }

        SectionCard(Prayer.ASAR.label(settings.effectiveNames)) {
            for (rule in AsrRule.entries) {
                ChoiceRow(
                    selected = rule == settings.effectiveAsr,
                    title = rule.title + if (rule == defaults.asr) " (usual here)" else "",
                    description = rule.detail,
                ) { change { it.copy(asr = rule) } }
            }
        }

        SectionCard("Adjust by minutes") {
            Text("Add or take off minutes to match your mosque.", style = MaterialTheme.typography.bodySmall)
            for (prayer in Prayer.entries) {
                val minutes = settings.adjustments[prayer.ordinal]
                fun nudge(by: Int) = change { s ->
                    s.copy(adjustments = s.adjustments.mapIndexed { i, m -> if (i == prayer.ordinal) (m + by).coerceIn(-MAX_ADJUSTMENT_MINUTES, MAX_ADJUSTMENT_MINUTES) else m })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(prayer.label(settings.effectiveNames), Modifier.weight(1f))
                    OutlinedButton(
                        onClick = { nudge(-1) }, enabled = minutes > -MAX_ADJUSTMENT_MINUTES,
                        modifier = Modifier.semantics { contentDescription = "${prayer.label(settings.effectiveNames)}, one minute earlier" },
                    ) { Text("−") }
                    Text(
                        if (minutes == 0) "0 min" else "%+d min".format(minutes),
                        Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    OutlinedButton(
                        onClick = { nudge(1) }, enabled = minutes < MAX_ADJUSTMENT_MINUTES,
                        modifier = Modifier.semantics { contentDescription = "${prayer.label(settings.effectiveNames)}, one minute later" },
                    ) { Text("+") }
                }
            }
        }

        OutlinedButton(
            onClick = { change { it.copy(method = null, asr = null, adjustments = AppSettings.NO_ADJUSTMENTS) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Back to the usual settings for ${place.name}") }

        Text(Legal.CALCULATED_DISCLAIMER, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun ChoiceRow(selected: Boolean, title: String, description: String?, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = 2.dp, end = 12.dp))
        Column {
            Text(title)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
