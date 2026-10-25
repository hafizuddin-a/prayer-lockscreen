package com.prayerwallpaper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.logic.CardStyle
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.TodayRow

/**
 * A switch with its words. The whole row is the switch, so it is one control for a screen reader ("Show countdown, switch,
 * on") and a touch target at least 48 dp tall, instead of a small switch with its label somewhere else.
 */
@Composable
internal fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.6f)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** The three one-tap looks for the preview. Each sets text size, high contrast and bold together, and stops following the system. */
private enum class Preset(val title: String, val options: AccessOptions) {
    STANDARD("Standard", AccessOptions()),
    LARGE("Large", AccessOptions(textSize = TextSize.LARGE)),
    LARGE_CONTRAST("Large + high contrast", AccessOptions(textSize = TextSize.LARGE, highContrast = true)),
}

/** The preset that [shown] matches exactly, or null when the options are something else. */
private fun presetOf(shown: AccessOptions): Preset? = Preset.entries.firstOrNull {
    it.options.textSize == shown.textSize && it.options.highContrast == shown.highContrast && !shown.bold
}

/**
 * The accessibility controls. [shown] is what is in force now (the phone's own settings while following them), and every
 * change goes through [onChange], which the caller turns into "stop following the system and keep everything else as it is
 * shown". [onFollow] turns following back on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccessibilityControls(
    following: Boolean, shown: AccessOptions, cardStyle: CardStyle,
    onFollow: (Boolean) -> Unit, onChange: (AccessOptions) -> Unit,
) {
    val current = presetOf(shown)
    Text("Quick settings", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (preset in Preset.entries) {
            val isCurrent = preset == current
            val pick = { onChange(preset.options.copy(cardStyle = shown.cardStyle)) }
            val modifier = Modifier.semantics { stateDescription = if (isCurrent) "Selected" else "Not selected"; selected = isCurrent }
            if (isCurrent) Button(onClick = pick, modifier = modifier) { Text(preset.title) } else OutlinedButton(onClick = pick, modifier = modifier) { Text(preset.title) }
        }
    }

    SwitchRow(
        "Follow system settings",
        "Uses your phone's settings. Changing anything below turns this off.",
        following, onFollow,
    )

    Text("Text size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    ChoiceRow(shown.textSize == TextSize.STANDARD, "Standard", "Two columns, the usual size.") { onChange(shown.copy(textSize = TextSize.STANDARD)) }
    ChoiceRow(shown.textSize == TextSize.LARGE, "Large", "One column of big rows.") { onChange(shown.copy(textSize = TextSize.LARGE)) }
    ChoiceRow(shown.textSize == TextSize.EXTRA_LARGE, "Extra large", "One column of even bigger rows.") { onChange(shown.copy(textSize = TextSize.EXTRA_LARGE)) }

    SwitchRow(
        "High contrast", "A solid card behind the list.",
        shown.highContrast, { onChange(shown.copy(highContrast = it)) },
    )
    if (shown.highContrast) {
        Text("Card style", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        ChoiceRow(cardStyle == CardStyle.AUTO, "Auto", "Light on dark backgrounds, dark on bright ones.") { onChange(shown.copy(cardStyle = CardStyle.AUTO)) }
        ChoiceRow(cardStyle == CardStyle.DARK, "Dark card", "Black, with white text and a yellow highlight.") { onChange(shown.copy(cardStyle = CardStyle.DARK)) }
        ChoiceRow(cardStyle == CardStyle.LIGHT, "Light card", "White, with dark text and a blue highlight.") { onChange(shown.copy(cardStyle = CardStyle.LIGHT)) }
    }

    SwitchRow("Bold text", "Heavier letters for the whole list.", shown.bold, { onChange(shown.copy(bold = it)) })
}

/**
 * Today's times as plain text, for a screen reader: the wallpaper is a picture and cannot be read aloud. Each row is one
 * phrase and the current prayer, the one the wallpaper highlights, says so.
 */
@Composable
internal fun TodaysTimesCard(rows: List<TodayRow>, countdownText: String?) {
    SectionCard("Today's times") {
        if (rows.isEmpty()) {
            Text("No prayer times to show yet.")
        } else {
            rows.forEach { row ->
                // One stop for a screen reader, saying the whole row, instead of a name and then a time.
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 32.dp).clearAndSetSemantics { contentDescription = row.spoken },
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (row.current) "${row.label} (now)" else row.label, style = if (row.current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge)
                    Text(row.time, style = if (row.current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge)
                }
            }
            if (countdownText != null) Text(countdownText, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** The one-time question after install: three buttons, or skip to keep following the phone's settings. */
@Composable
internal fun LargerTextDialog(onChoose: (AccessOptions?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onChoose(null) },
        title = { Text("Would you like larger text?") },
        text = { Text("Make the list on your lock screen bigger? Change it any time in Easier reading.") },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onChoose(AccessOptions()) }, modifier = Modifier.fillMaxWidth()) { Text("Standard") }
                OutlinedButton(onClick = { onChoose(AccessOptions(textSize = TextSize.LARGE)) }, modifier = Modifier.fillMaxWidth()) { Text("Large") }
                OutlinedButton(onClick = { onChoose(AccessOptions(textSize = TextSize.LARGE, highContrast = true)) }, modifier = Modifier.fillMaxWidth()) { Text("Large with high contrast") }
                TextButton(onClick = { onChoose(null) }, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
            }
        },
    )
}
