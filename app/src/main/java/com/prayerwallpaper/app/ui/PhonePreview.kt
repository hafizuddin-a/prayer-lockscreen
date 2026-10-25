package com.prayerwallpaper.app.ui

import android.graphics.Paint
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.data.Timetable
import com.prayerwallpaper.app.logic.HijriAlign
import com.prayerwallpaper.app.logic.HijriLine
import com.prayerwallpaper.app.logic.countdown
import com.prayerwallpaper.app.data.snapshot
import com.prayerwallpaper.app.wallpaper.ListLayout
import com.prayerwallpaper.app.wallpaper.Look
import com.prayerwallpaper.app.wallpaper.accessBlockExtent
import com.prayerwallpaper.app.wallpaper.listTopRange
import com.prayerwallpaper.app.wallpaper.WallpaperRenderer
import java.time.Clock

/**
 * A phone-shaped preview that draws the real wallpaper with the real renderer. A fake clock sits at
 * the top for scale, since Android draws the real one. Drag anywhere on the list to move it.
 *
 * [draggable] false makes it a picture only (no drag, no outline). [description] is its text alternative for a screen reader.
 *
 * Pass [onPhotoTransform] to use the preview for framing a photo instead: pinch and drag are then
 * reported (finger centre, zoom factor, drag, preview size) and the list stays put. [aspect] is
 * width over height; the photo editor sets it to the real screen's so the framing matches, and
 * [fitHeight] makes the preview as tall as the space it is given instead of as wide.
 */
@Composable
fun PhonePreview(
    timetable: Timetable,
    clock: Clock,
    look: Look,
    names: NameSet,
    hijri: HijriLine?,
    hijriAlign: HijriAlign,
    showCountdown: Boolean,
    access: AccessOptions,
    onListTopChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    aspect: Float = 9f / 19.5f,
    fitHeight: Boolean = false,
    draggable: Boolean = true,
    description: String? = null,
    onPhotoTransform: ((centroid: Offset, zoom: Float, pan: Offset, size: IntSize) -> Unit)? = null,
) {
    val context = LocalContext.current
    val renderer = remember { WallpaperRenderer(context) }
    val currentTop by rememberUpdatedState(look.listTop)
    val hijriShown by rememberUpdatedState(hijri != null)
    val countdownShown by rememberUpdatedState(showCountdown)
    val currentAccess by rememberUpdatedState(access)
    val onChange by rememberUpdatedState(onListTopChange)
    val onTransform by rememberUpdatedState(onPhotoTransform)
    val framingPhoto = onPhotoTransform != null
    val clockPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
        }
    }

    Box(
        modifier
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .then(if (fitHeight) Modifier.fillMaxHeight().aspectRatio(aspect, matchHeightConstraintsFirst = true) else Modifier.fillMaxWidth().aspectRatio(aspect))
            .clip(RoundedCornerShape(26.dp))
            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp))
            .then(
                if (framingPhoto) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ -> onTransform?.invoke(centroid, zoom, pan, size) }
                    }
                } else if (draggable) {
                    Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Only grab a drag that starts on the list; anything else is left for the page to scroll.
                            val big = accessBlockExtent(size.width, size.height, currentTop, currentAccess, if (hijriShown) 1 else 0, countdownShown)
                            // The list as drawn: the big layout may have moved it to keep itself on screen.
                            val top = big?.listTop ?: (currentTop * size.height)
                            val listHeight = big?.listHeight ?: (ListLayout.heightFraction(size.width, size.height) * size.height)
                            // The Hijri line is part of what moves, so grabbing it counts too.
                            val above = big?.above ?: if (hijriShown) maxOf(listHeight * 0.3f, ListLayout.HIJRI_BLOCK * minOf(size.width, size.height)) else listHeight * 0.3f
                            // ...and so is the countdown line under it.
                            val below = big?.below ?: if (countdownShown) maxOf(listHeight * 0.3f, ListLayout.COUNTDOWN_BLOCK * minOf(size.width, size.height)) else listHeight * 0.3f
                            if (down.position.y !in (top - above)..(top + listHeight + below)) return@awaitEachGesture
                            fun move(dy: Float) {
                                val range = listTopRange(size.width, size.height, currentAccess, hijriShown, countdownShown)
                                onChange((currentTop + dy / size.height).coerceIn(range.start, range.endInclusive))
                            }
                            val slop = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                                change.consume()
                                move(overSlop)
                            }
                            if (slop != null) verticalDrag(slop.id) { change ->
                                val dy = change.positionChange().y // read before consuming: a consumed change reports no movement
                                change.consume()
                                move(dy)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier.fillMaxSize().drawWithContent {
                val snapshot = timetable.snapshot(clock)
                drawIntoCanvas { canvas ->
                    val w = size.width.toInt()
                    val h = size.height.toInt()
                    // Worked out here, from the clock, so it is as fresh as the highlight when the preview is redrawn.
                    val countdown = if (showCountdown) snapshot.countdown(clock.instant(), names) else null
                    renderer.draw(canvas.nativeCanvas, w, h, snapshot, timetable.whenMissing, drawList = true, look = look, names = names, hijri = hijri, hijriAlign = hijriAlign, countdown = countdown, access = access)
                    // A stand-in for the system clock, drawn smaller than the real one, for scale only.
                    clockPaint.textSize = size.width * 0.20f
                    canvas.nativeCanvas.drawText("9:41", size.width / 2, size.height * 0.16f, clockPaint)
                    clockPaint.textSize = size.width * 0.055f
                    canvas.nativeCanvas.drawText("Sun, Oct 4", size.width / 2, size.height * 0.20f, clockPaint)
                }
                // A thin outline shows what a drag will move. Not when the drag frames a photo instead.
                if (!framingPhoto && draggable) {
                val big = accessBlockExtent(size.width.toInt(), size.height.toInt(), look.listTop, access, if (hijri == null) 0 else if (hijri.event != null) 2 else 1, showCountdown)
                val listHeight = big?.listHeight ?: (ListLayout.heightFraction(size.width.toInt(), size.height.toInt()) * size.height)
                val inset = size.width * 0.07f
                // The outline takes in the Hijri line and the countdown line when they are showing, since a drag moves all three.
                val above = big?.above ?: if (hijri != null) ListLayout.HIJRI_BLOCK * minOf(size.width, size.height) else 0f
                val below = big?.below ?: if (showCountdown) ListLayout.COUNTDOWN_BLOCK * minOf(size.width, size.height) else 0f
                drawRoundRect(
                    Color.White.copy(alpha = 0.35f),
                    topLeft = androidx.compose.ui.geometry.Offset(inset, (big?.listTop ?: (look.listTop * size.height)) - 6.dp.toPx() - above),
                    size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, listHeight + 12.dp.toPx() + above + below),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                )
                }
                drawContent()
            },
        )
    }
}
