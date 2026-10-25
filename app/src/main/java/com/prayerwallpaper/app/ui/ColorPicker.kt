package com.prayerwallpaper.app.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** A coloured dot. */
@Composable
fun Swatch(color: Int, size: Int = 28) {
    Box(
        Modifier
            .size(size.dp)
            .background(Color(color), CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
    )
}

@Composable
fun Swatches(colors: List<Int>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { Swatch(it, 36) }
    }
}

/** A label and swatch that opens hue, saturation and brightness sliders when tapped. */
@Composable
fun ColorRow(label: String, color: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = if (open) "Hide the colour sliders" else "Show the colour sliders", role = Role.Button) { open = !open }
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }.heightIn(min = 48.dp).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Swatch(color)
            Spacer(Modifier.width(12.dp))
            Text(label, Modifier.weight(1f))
            Text(String.format("#%06X", color and 0xFFFFFF), style = MaterialTheme.typography.labelLarge)
        }
        if (open) HsvSliders(color, onChange)
    }
}

@Composable
private fun HsvSliders(color: Int, onChange: (Int) -> Unit) {
    val initial = remember { FloatArray(3).also { AndroidColor.colorToHSV(color, it) } }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var saturation by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }
    fun emit() = onChange(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))

    Column(Modifier.padding(start = 40.dp)) {
        Text("Hue", style = MaterialTheme.typography.labelMedium)
        Slider(hue, { hue = it; emit() }, valueRange = 0f..360f, modifier = Modifier.semantics { contentDescription = "Hue"; stateDescription = "${hue.toInt()} degrees" })
        Text("Saturation", style = MaterialTheme.typography.labelMedium)
        Slider(saturation, { saturation = it; emit() }, modifier = Modifier.semantics { contentDescription = "Saturation"; stateDescription = "${(saturation * 100).toInt()}%" })
        Text("Brightness", style = MaterialTheme.typography.labelMedium)
        Slider(value, { value = it; emit() }, modifier = Modifier.semantics { contentDescription = "Brightness"; stateDescription = "${(value * 100).toInt()}%" })
    }
}
