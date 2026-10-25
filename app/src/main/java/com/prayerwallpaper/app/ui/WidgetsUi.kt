package com.prayerwallpaper.app.ui

import android.util.SizeF
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.logic.CardStyle
import com.prayerwallpaper.app.logic.GLASS_MIN_CONTRAST
import com.prayerwallpaper.app.logic.GlassTint
import com.prayerwallpaper.app.logic.WidgetStyle
import com.prayerwallpaper.app.logic.glassContrast
import com.prayerwallpaper.app.logic.mixColors
import com.prayerwallpaper.app.settings.AppSettings
import com.prayerwallpaper.app.widgets.Backdrop
import com.prayerwallpaper.app.widgets.NextPrayerWidget
import com.prayerwallpaper.app.widgets.PrayerStripWidget
import com.prayerwallpaper.app.widgets.TodayTimesWidget
import com.prayerwallpaper.app.widgets.previewSize
import com.prayerwallpaper.app.widgets.requestPin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class WidgetKind(val name: String, val provider: Class<*>) {
    // The sizes the preview draws at, in dp: what a widget of that kind usually is on a phone. The preview is the widget's own RemoteViews
    // at that size, so it cannot drift from the real thing.
    val widthDp: Float get() = previewSize(provider).width
    val heightDp: Float get() = previewSize(provider).height
}

internal val WIDGET_KINDS = listOf(
    WidgetKind("Next prayer", NextPrayerWidget::class.java),
    WidgetKind("Today's times", TodayTimesWidget::class.java),
    WidgetKind("All six in a row", PrayerStripWidget::class.java),
)

/** What is behind the widgets on the home screen (the wallpaper's colour), read on opening and each time the screen comes back. */
@Composable
internal fun rememberBackdrop(app: PrayerApp, resumes: Int): Backdrop {
    var backdrop by remember { mutableStateOf(Backdrop.Default) }
    LaunchedEffect(resumes) { backdrop = withContext(Dispatchers.IO) { Backdrop.read(app) } }
    return backdrop
}

/**
 * Each widget with its live preview and the button that adds it. Used by the Widgets screen and by the setup. [hint] is the line
 * shown when a launcher cannot be asked to add a widget, and it is cleared by a successful ask.
 */
@Composable
internal fun WidgetOffers(app: PrayerApp, settings: AppSettings, backdrop: Backdrop) {
    val context = LocalContext.current
    var hint by remember { mutableStateOf<String?>(null) }
    for (kind in WIDGET_KINDS) {
        WidgetPreview(app, settings, kind, backdrop)
        OutlinedButton(
            onClick = {
                hint = if (requestPin(context, kind.provider)) null
                else "Your launcher can't add it from here. Touch and hold the home screen, choose Widgets, and find this app."
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add: ${kind.name}") }
    }
    hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
}

/**
 * The home screen widgets: a live preview of each with the button that adds it, the look (glass or card, how opaque the glass is,
 * and whether to follow the lock screen's accessibility settings), and a way into the widget health screen once one is placed.
 */
@Composable
fun WidgetsCard(app: PrayerApp, settings: AppSettings, resumes: Int, change: ((AppSettings) -> AppSettings) -> Unit, onWidgetHealth: () -> Unit) {
    var placed by remember { mutableStateOf<Int?>(null) }
    val backdrop = rememberBackdrop(app, resumes)
    // How many widgets are on the home screen: read on opening and each time the screen comes back.
    LaunchedEffect(resumes) { placed = withContext(Dispatchers.IO) { app.widgets.widgetCount() } }

    SectionCard(null) {
        Text(
            "Prayer times on your home screen. No account, no internet needed.",
            style = MaterialTheme.typography.bodyMedium,
        )

        GroupHeading("Add to the home screen")
        WidgetOffers(app, settings, backdrop)
        when (placed) {
            0 -> Text("No widgets added yet.", style = MaterialTheme.typography.bodyMedium)
            null -> Unit
            else -> OutlinedButton(onClick = onWidgetHealth, modifier = Modifier.fillMaxWidth()) { Text("Widget health") }
        }
    }

    SectionCard("Look of the widgets") {
        val contrast = glassContrast(settings.glassOpacity, backdrop.color, backdrop.luminance, settings.widgetGlass)
        if (settings.widgetStyle == WidgetStyle.GLASS && contrast < GLASS_MIN_CONTRAST) {
            Text(
                "Hard to read at this opacity. Try another opacity, or the Card style.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            )
        }

        GroupHeading("Style")
        ChoiceRow(settings.widgetStyle == WidgetStyle.GLASS, "Glass", "A see-through background over your wallpaper.") { change { it.copy(widgetStyle = WidgetStyle.GLASS) } }
        ChoiceRow(settings.widgetStyle == WidgetStyle.CARD, "Card", "A solid background, with a dot by the current time.") { change { it.copy(widgetStyle = WidgetStyle.CARD) } }

        if (settings.widgetStyle == WidgetStyle.GLASS) {
            GroupHeading("Glass colour")
            ChoiceRow(settings.widgetGlass == GlassTint.LIGHT, "Light", "A white tint. Text suits your wallpaper.") { change { it.copy(widgetGlass = GlassTint.LIGHT) } }
            ChoiceRow(settings.widgetGlass == GlassTint.DARK, "Dark", "A dark tint, with white text.") { change { it.copy(widgetGlass = GlassTint.DARK) } }
            val percent = (settings.glassOpacity * 100).roundToInt()
            Text("Glass opacity: $percent%", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.glassOpacity,
                onValueChange = { value ->
                    change { if (it.widgetGlass == GlassTint.DARK) it.copy(widgetDarkGlassOpacity = value) else it.copy(widgetGlassOpacity = value) }
                },
                valueRange = 0f..1f,
                modifier = Modifier.semantics { contentDescription = "Glass opacity"; stateDescription = "$percent%" },
            )
        } else {
            GroupHeading("Card colour")
            ChoiceRow(settings.widgetCard == CardStyle.AUTO, "Auto", "Light on dark wallpapers, dark on bright ones.") { change { it.copy(widgetCard = CardStyle.AUTO) } }
            ChoiceRow(settings.widgetCard == CardStyle.DARK, "Dark card", "Black, with white text and a yellow highlight.") { change { it.copy(widgetCard = CardStyle.DARK) } }
            ChoiceRow(settings.widgetCard == CardStyle.LIGHT, "Light card", "White, with dark text and a blue highlight.") { change { it.copy(widgetCard = CardStyle.LIGHT) } }
        }

        SwitchRow(
            "Same text settings as lock screen",
            "Uses the text size, bold and contrast from Easier reading.",
            settings.widgetFollowAccess, { on -> change { it.copy(widgetFollowAccess = on) } },
        )
    }
}

/**
 * One widget's own RemoteViews, drawn here over the wallpaper's colours. Rebuilt whenever the settings change, and laid out for the width
 * it is actually shown at, so what the preview holds (six columns, or three when six do not fit) is what a widget of that width would hold.
 */
@Composable
internal fun WidgetPreview(app: PrayerApp, settings: AppSettings, kind: WidgetKind, backdrop: Backdrop) {
    val top = Color(backdrop.color)
    val bottom = Color(mixColors(backdrop.color, 0xFF000000.toInt(), 0.35f))
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The widget fills the card's width but for a small margin, and is never wider than a widget of its kind usually is.
        val width = minOf(kind.widthDp, maxWidth.value - 2 * PREVIEW_MARGIN_H).coerceAtLeast(120f)
        var views by remember { mutableStateOf<RemoteViews?>(null) }
        LaunchedEffect(settings, width) {
            views = withContext(Dispatchers.IO) { app.widgets.build(kind.provider, app.widgets.environment(), SizeF(width, kind.heightDp)) }
        }
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Brush.verticalGradient(listOf(top, bottom)))
                .padding(horizontal = PREVIEW_MARGIN_H.dp, vertical = 8.dp)
                .clearAndSetSemantics { contentDescription = "Preview of the ${kind.name} widget" },
            contentAlignment = Alignment.Center,
        ) {
            views?.let { rv ->
                AndroidView(
                    factory = { ctx -> FrameLayout(ctx) },
                    update = { frame ->
                        frame.removeAllViews()
                        frame.addView(rv.apply(frame.context, frame))
                    },
                    modifier = Modifier.width(width.dp).height(kind.heightDp.dp),
                )
            }
            if (views == null) Column(Modifier.height(kind.heightDp.dp), verticalArrangement = Arrangement.Center) { Text("…", color = Color.White) }
        }
    }
}

/** The space left either side of a widget preview in its backdrop, in dp. */
private const val PREVIEW_MARGIN_H = 4f
