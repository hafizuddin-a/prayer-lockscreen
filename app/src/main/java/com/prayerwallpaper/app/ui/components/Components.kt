package com.prayerwallpaper.app.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.R

/** The app's own line icons (`res/drawable/ic_*.xml`). */
object Icons {
    val Look = R.drawable.ic_look
    val Place = R.drawable.ic_place
    val Bell = R.drawable.ic_bell
    val Widgets = R.drawable.ic_widgets
    val TextSize = R.drawable.ic_text_size
    val Info = R.drawable.ic_info
    val Chevron = R.drawable.ic_chevron
    val Check = R.drawable.ic_check
    val Warning = R.drawable.ic_warning
    val Back = R.drawable.ic_back
    val Chat = R.drawable.ic_chat
}

/** A picture only: a screen reader skips it, because the words next to it say everything. */
@Composable
fun DecorativeIcon(icon: Int, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, size: Dp = 24.dp) {
    Icon(painterResource(icon), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

/**
 * The frame every screen shares: a back arrow and the title (a heading for a screen reader) over the content. The content
 * scrolls, unless [scrolling] is false because it brings its own list or takes the whole height. Pass a null [onBack] on the
 * first screen. Drawn inside the safe area, so nothing sits under the status bar or the gesture bar.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrolling: Boolean = true,
    scrollState: ScrollState = rememberScrollState(),
    spacing: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = if (onBack != null) 4.dp else 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(painterResource(Icons.Back), contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface) }
            }
            Text(
                title,
                style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp).semantics { heading() },
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().then(if (scrolling) Modifier.verticalScroll(scrollState) else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(spacing),
            content = content,
        )
    }
}

/**
 * One row of the home screen that opens a screen of its own: an icon, the name, and a line saying what is set now. One control for
 * a screen reader ("Look, Gradient, centred, countdown on, button"), at least 72 dp tall.
 */
@Composable
fun Tile(icon: Int, title: String, summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick).heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            DecorativeIcon(icon, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DecorativeIcon(Icons.Chevron, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How a [StatusBanner] reads. The words always say it too: colour is never the only signal. */
enum class BannerKind { OK, ATTENTION, WARNING }

/**
 * A short status with an icon, a title, an optional explanation and an optional button ([quietAction] draws it as an outline, for a
 * page that has its own main button). Changes are announced by a screen reader, which matters coming back from Android's wallpaper screen.
 */
@Composable
fun StatusBanner(
    kind: BannerKind, title: String, modifier: Modifier = Modifier, body: String? = null,
    actionLabel: String? = null, onAction: (() -> Unit)? = null, quietAction: Boolean = false,
) {
    val tint = when (kind) {
        BannerKind.OK -> MaterialTheme.colorScheme.tertiary
        BannerKind.ATTENTION -> MaterialTheme.colorScheme.primary
        BannerKind.WARNING -> MaterialTheme.colorScheme.error
    }
    val icon = when (kind) {
        BannerKind.OK -> Icons.Check
        BannerKind.ATTENTION -> Icons.Info
        BannerKind.WARNING -> Icons.Warning
    }
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The icon sits in a box as tall as the title's first line, so it is centred on that line whatever the font size.
        val titleLine = with(LocalDensity.current) { maxOf(MaterialTheme.typography.titleSmall.lineHeight.toDp(), 24.dp) }
        Box(Modifier.height(titleLine), contentAlignment = Alignment.Center) { DecorativeIcon(icon, tint = tint) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(2.dp))
                if (kind == BannerKind.OK || quietAction) OutlinedButton(onClick = onAction) { Text(actionLabel) } else Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/** "Step 2 of 4" with one bar per step. The bars are for the eye; the words carry the position for a screen reader. */
@Composable
fun StepProgress(step: Int, count: Int, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Step $step of $count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().clearAndSetSemantics { }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 1..count) {
                Box(
                    Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
    }
}
