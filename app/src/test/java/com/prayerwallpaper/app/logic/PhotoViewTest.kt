package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoViewTest {
    // A wide photo (2000 x 1000) behind a tall phone (1000 x 2000): it overflows sideways only.
    private val photoW = 2000
    private val photoH = 1000
    private val viewW = 1000f
    private val viewH = 2000f

    private fun place(view: PhotoView) = photoPlacement(photoW, photoH, viewW, viewH, view)

    @Test fun `zoom 1 fills the screen and the photo is centred sideways`() {
        val p = place(PhotoView())
        assertEquals(4000f, p.width, 0.01f) // scaled by 2 to fill the height
        assertEquals(2000f, p.height, 0.01f)
        assertEquals(-1500f, p.left, 0.01f)
        assertEquals(0f, p.top, 0.01f)
    }

    @Test fun `pan 0 shows the left of the photo and pan 1 the right`() {
        assertEquals(0f, place(PhotoView(panX = 0f)).left, 0.01f)
        assertEquals(-3000f, place(PhotoView(panX = 1f)).left, 0.01f)
    }

    @Test fun `zoom scales the photo and keeps it covering the screen`() {
        assertEquals(8000f, place(PhotoView(zoom = 2f)).width, 0.01f)
        for (zoom in listOf(1f, 1.7f, 4f)) for (pan in listOf(0f, 0.3f, 1f)) {
            val p = place(PhotoView(zoom, pan, pan))
            assertTrue("zoom $zoom pan $pan", p.left <= 0.01f && p.left + p.width >= viewW - 0.01f)
            assertTrue("zoom $zoom pan $pan", p.top <= 0.01f && p.top + p.height >= viewH - 0.01f)
        }
    }

    @Test fun `out of range values are clamped`() {
        assertEquals(place(PhotoView(4f, 1f, 1f)).width, place(PhotoView(99f, 5f, 5f)).width, 0.01f)
        assertEquals(place(PhotoView(1f, 0f, 0f)).left, place(PhotoView(0.2f, -3f, -3f)).left, 0.01f)
    }

    private fun adjust(view: PhotoView, fx: Float = 500f, fy: Float = 1000f, zoom: Float = 1f, dx: Float = 0f, dy: Float = 0f) =
        adjustPhotoView(view, photoW, photoH, viewW, viewH, fx, fy, zoom, dx, dy)

    @Test fun `pinching keeps the photo point under the fingers fixed`() {
        val start = PhotoView(zoom = 1.5f, panX = 0.3f, panY = 0.4f)
        val fx = 300f
        val fy = 700f
        val before = place(start)
        val u = (fx - before.left) / before.width
        val v = (fy - before.top) / before.height
        val end = adjust(start, fx, fy, zoom = 1.8f)
        val after = place(end)
        assertEquals(u, (fx - after.left) / after.width, 0.001f)
        assertEquals(v, (fy - after.top) / after.height, 0.001f)
        assertEquals(2.7f, end.zoom, 0.001f)
    }

    @Test fun `dragging right moves the photo right, which reveals more of its left side`() {
        val start = PhotoView(zoom = 2f, panX = 0.5f)
        val end = adjust(start, dx = 100f)
        assertEquals(place(start).left + 100f, place(end).left, 0.5f)
        assertTrue(end.panX < start.panX)
    }

    @Test fun `a long drag stops at the photo's edge instead of showing a gap`() {
        val end = adjust(PhotoView(zoom = 2f), dx = 100000f, dy = 100000f)
        assertEquals(0f, end.panX, 0.0001f)
        assertEquals(0f, end.panY, 0.0001f)
        val p = place(end)
        assertTrue(p.left <= 0.01f && p.top <= 0.01f)
    }

    @Test fun `zoom is limited to 1 and the maximum`() {
        assertEquals(MAX_PHOTO_ZOOM, adjust(PhotoView(), zoom = 100f).zoom, 0.0001f)
        assertEquals(MIN_PHOTO_ZOOM, adjust(PhotoView(zoom = 2f), zoom = 0.001f).zoom, 0.0001f)
    }

    @Test fun `an axis where the photo does not overflow keeps its pan`() {
        // At zoom 1 the photo exactly fills the height, so vertical drags change nothing.
        val end = adjust(PhotoView(), dy = 300f)
        assertEquals(0.5f, end.panY, 0.0001f)
    }
}
