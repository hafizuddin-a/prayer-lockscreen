package com.prayerwallpaper.app.logic

import kotlin.math.abs
import kotlin.math.max

const val MIN_PHOTO_ZOOM = 1f
const val MAX_PHOTO_ZOOM = 4f

/** Where a photo is drawn: its top-left corner and size, in view pixels. */
class PhotoPlacement(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * How a photo is framed. [zoom] is 1 (just fills the screen) up to [MAX_PHOTO_ZOOM]. [panX] and
 * [panY] slide the photo behind the screen: 0 puts its left or top edge on the screen's edge, 1 its
 * right or bottom edge, 0.5 centres it. At zoom 1 only the axis where the photo overflows can move.
 */
data class PhotoView(val zoom: Float = 1f, val panX: Float = 0.5f, val panY: Float = 0.5f)

/** Always covers the view: the photo is scaled to fill it first, then zoomed, so no edge ever shows. */
fun photoPlacement(photoWidth: Int, photoHeight: Int, viewWidth: Float, viewHeight: Float, view: PhotoView): PhotoPlacement {
    val scale = max(viewWidth / photoWidth, viewHeight / photoHeight) * view.zoom.coerceIn(MIN_PHOTO_ZOOM, MAX_PHOTO_ZOOM)
    val width = photoWidth * scale
    val height = photoHeight * scale
    return PhotoPlacement(
        left = (viewWidth - width) * view.panX.coerceIn(0f, 1f),
        top = (viewHeight - height) * view.panY.coerceIn(0f, 1f),
        width = width,
        height = height,
    )
}

/**
 * Applies one pinch or drag step. The photo point under the fingers ([focusX], [focusY]) stays under
 * them while [zoomFactor] changes the zoom, then the photo follows the drag by ([dragX], [dragY]).
 * The result is clamped so the photo still covers the view.
 */
fun adjustPhotoView(
    current: PhotoView,
    photoWidth: Int,
    photoHeight: Int,
    viewWidth: Float,
    viewHeight: Float,
    focusX: Float,
    focusY: Float,
    zoomFactor: Float,
    dragX: Float,
    dragY: Float,
): PhotoView {
    val before = photoPlacement(photoWidth, photoHeight, viewWidth, viewHeight, current)
    val zoom = (current.zoom * zoomFactor).coerceIn(MIN_PHOTO_ZOOM, MAX_PHOTO_ZOOM)
    val after = photoPlacement(photoWidth, photoHeight, viewWidth, viewHeight, current.copy(zoom = zoom))
    val left = focusX - (focusX - before.left) / before.width * after.width + dragX
    val top = focusY - (focusY - before.top) / before.height * after.height + dragY
    return PhotoView(
        zoom = zoom,
        panX = panFor(left, viewWidth - after.width, current.panX),
        panY = panFor(top, viewHeight - after.height, current.panY),
    )
}

/** Converts a position back to a 0..1 pan, or keeps [fallback] on an axis where the photo does not overflow. */
private fun panFor(position: Float, range: Float, fallback: Float): Float =
    if (abs(range) < 0.5f) fallback else (position / range).coerceIn(0f, 1f)
