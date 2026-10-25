package com.prayerwallpaper.app.logic

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearanceTest {
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test fun `mixing hits both ends and the middle`() {
        assertEquals(black, mixColors(black, white, 0f))
        assertEquals(white, mixColors(black, white, 1f))
        assertEquals(argb(128, 128, 128), mixColors(black, white, 0.5f))
    }

    @Test fun `gradient curve keeps the endpoints and fades fast through the top`() {
        val top = argb(80, 110, 150)
        val bottom = argb(0, 16, 64)
        val colors = GradientCurve.colors(top, bottom)
        assertEquals(top, colors.first())
        assertEquals(bottom, colors.last())
        assertEquals(GradientCurve.POSITIONS.size, colors.size)
        // 45 percent of the way down only 22 percent of the top colour is left, far less than linear (55).
        val red = (colors[2] shr 16) and 0xFF
        assertTrue("red at 45% was $red", red in 14..24)
    }

    @Test fun `luminance of black white and mid grey`() {
        assertEquals(0f, relativeLuminance(black), 0.001f)
        assertEquals(1f, relativeLuminance(white), 0.001f)
        assertEquals(0.216f, relativeLuminance(argb(128, 128, 128)), 0.01f)
    }

    @Test fun `text colour flips with the background`() {
        assertFalse(prefersDarkText(relativeLuminance(argb(15, 42, 83)))) // the navy default
        assertTrue(prefersDarkText(relativeLuminance(argb(235, 240, 250)))) // a pale sky
        assertTrue(prefersDarkText(relativeLuminance(white)))
        assertFalse(prefersDarkText(relativeLuminance(black)))
    }

    @Test fun `mean luminance only looks at the asked rectangle`() {
        // 4x2 image: left half white, right half black.
        val pixels = intArrayOf(white, white, black, black, white, white, black, black)
        assertEquals(1f, meanLuminance(pixels, 4, 0, 0, 2, 2), 0.001f)
        assertEquals(0f, meanLuminance(pixels, 4, 2, 0, 4, 2), 0.001f)
        assertEquals(0.5f, meanLuminance(pixels, 4, 0, 0, 4, 2), 0.001f)
        assertEquals(0f, meanLuminance(pixels, 4, 1, 1, 1, 1), 0.001f) // empty rectangle
    }

    @Test fun `photos are scaled down to fit but never up`() {
        assertEquals(1440 to 3200, scaledPhotoSize(4000, 8888)) // limited by the short side 1440 first
        assertEquals(1000 to 2000, scaledPhotoSize(1000, 2000)) // already small
        assertEquals(2160 to 1440, scaledPhotoSize(3000, 2000)) // landscape: the short side is held to 1440
        val (w, h) = scaledPhotoSize(6000, 30000)
        assertTrue(h <= 3200 && w >= 1)
    }

    @Test fun `match colours go light to dark and drop duplicates`() {
        val light = argb(200, 210, 240)
        val mid = argb(80, 100, 160)
        val dark = argb(10, 20, 50)
        assertArrayEquals(intArrayOf(light, mid, dark), matchGradientColors(mid, dark, light))
        assertArrayEquals(intArrayOf(light, dark), matchGradientColors(dark, light, null))
        assertArrayEquals(intArrayOf(dark), matchGradientColors(dark, dark, dark))
    }

    private fun channels(c: Int) = Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    @Test fun `mean colour averages each channel`() {
        val pixels = intArrayOf(argb(0, 0, 0), argb(100, 200, 50), argb(100, 200, 50), argb(0, 0, 0))
        assertEquals(argb(50, 100, 25), meanColor(pixels, 2, 0, 0, 2, 2))
        assertEquals(argb(100, 200, 50), meanColor(pixels, 2, 1, 0, 2, 1))
        assertEquals(argb(0, 0, 0), meanColor(pixels, 2, 0, 0, 0, 0)) // empty rectangle
    }

    @Test fun `on the navy default the light text is the reference's soft blue-white`() {
        val (r, g, b) = channels(textColorsFor(argb(15, 42, 83), dark = false).text)
        // The reference text is #B9D3FD = (185, 211, 253).
        assertTrue("got $r,$g,$b", Math.abs(r - 185) <= 8 && Math.abs(g - 211) <= 8 && Math.abs(b - 253) <= 4)
    }

    @Test fun `on a black or grey background the text has no blue tint`() {
        for (bg in listOf(argb(0, 0, 0), argb(13, 13, 13), argb(40, 40, 40))) {
            val (r, g, b) = channels(textColorsFor(bg, dark = false).text)
            assertTrue("$r,$g,$b on $bg", r == g && g == b && r >= 245)
        }
    }

    @Test fun `tint follows the hue of the background`() {
        val (r, g, b) = channels(textColorsFor(argb(60, 10, 10), dark = false).text) // a dark red
        assertTrue("$r,$g,$b", r > g && r > b)
    }

    @Test fun `dark text on a pale background is dark and keeps a faint tint`() {
        val colors = textColorsFor(argb(176, 205, 245), dark = true)
        val (r, g, b) = channels(colors.text)
        assertTrue("$r,$g,$b", r < 70 && g < 70 && b < 70 && b >= r)
        assertTrue(relativeLuminance(colors.text) < 0.05f)
        assertEquals(0x26, (colors.pill ushr 24) and 0xFF)
    }

    @Test fun `the pill is the text colour at a fixed alpha and the icon is a little dimmer than the text`() {
        val light = textColorsFor(argb(15, 42, 83), dark = false)
        assertEquals(0x30, (light.pill ushr 24) and 0xFF)
        assertEquals(light.text and 0xFFFFFF, light.pill and 0xFFFFFF)
        assertTrue(relativeLuminance(light.icon) < relativeLuminance(light.text))
    }

    @Test fun `text is left alone when it fits and shrunk in proportion when it does not`() {
        assertEquals(1f, textFitScale(needed = 90f, available = 100f), 0.0001f)
        assertEquals(1f, textFitScale(needed = 100f, available = 100f), 0.0001f)
        assertEquals(0.909f, textFitScale(needed = 110f, available = 100f), 0.001f)
    }

    @Test fun `a very wide font never shrinks the text below the floor`() {
        assertEquals(0.7f, textFitScale(needed = 200f, available = 100f), 0.0001f)
        assertEquals(0.8f, textFitScale(needed = 200f, available = 100f, floor = 0.8f), 0.0001f)
    }

    // The tones measured on the Android 16 emulator under a dark red wallpaper, whose lock clock came out as FCD8D4.
    private val redTones = TextTones(light = 0xFFFFDAD6.toInt(), lightDim = 0xFFFFB4AB.toInt(), dark = 0xFF775652.toInt(), darkDim = 0xFF5D3F3C.toInt())

    @Test fun `following the system on a dark wallpaper uses the clock's light tone`() {
        // Already at full brightness in its strongest channel, so the scrim compensation has nothing to add.
        val colors = tonedTextColors(supportsDarkText = false, tones = redTones)
        assertEquals(0xFFFFDAD6.toInt(), colors.text)
        assertEquals(0xFFFFB4AB.toInt(), colors.icon)
    }

    @Test fun `following the system on a bright wallpaper uses the clock's dark tone, brightened to survive the scrim`() {
        val colors = tonedTextColors(supportsDarkText = true, tones = redTones)
        assertEquals(undoScrim(0xFF775652.toInt()), colors.text)
        assertEquals(undoScrim(0xFF5D3F3C.toInt()), colors.icon)
        // Under the scrim (0.8 of the colour) it comes back to the clock's own tone, give or take rounding.
        for (shift in listOf(16, 8, 0)) {
            val shown = ((colors.text shr shift) and 0xFF) * 0.8f
            val wanted = ((0xFF775652.toInt() shr shift) and 0xFF).toFloat()
            assertEquals(wanted, shown, 1.5f)
        }
    }

    @Test fun `undoing the scrim keeps the tint and never goes past white`() {
        assertEquals(0xFF6D768D.toInt(), undoScrim(0xFF575E71.toInt()))   // each channel x1.25: 87 to 109, 94 to 118, 113 to 141
        assertEquals(0xFFDAE2FF.toInt(), undoScrim(0xFFDAE2FF.toInt()))   // strongest channel already 255: unchanged
        assertEquals(0xFFFFFFFF.toInt(), undoScrim(0xFFFFFFFF.toInt()))
        assertEquals(0xFF000000.toInt(), undoScrim(0xFF000000.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), undoScrim(0xFFCCCCCC.toInt()))   // 204 / 0.8 = 255
        val tinted = undoScrim(0xFFB0A090.toInt())
        assertTrue("brighter", (tinted shr 16 and 0xFF) > 0xB0)
        assertTrue("same order of channels", (tinted shr 16 and 0xFF) >= (tinted shr 8 and 0xFF) && (tinted shr 8 and 0xFF) >= (tinted and 0xFF))
    }

    @Test fun `icons are brightened by the same factor as their text, so they stay a touch dimmer`() {
        val tones = TextTones(light = 0xFFFFFFFF.toInt(), lightDim = 0xFFE0E0E0.toInt(), dark = 0xFF202020.toInt(), darkDim = 0xFF303030.toInt())
        val light = tonedTextColors(false, tones)
        assertEquals(0xFFFFFFFF.toInt(), light.text)
        assertEquals(0xFFE0E0E0.toInt(), light.icon) // text is already at 255, so the factor is 1 and the icon is untouched
        val dark = tonedTextColors(true, tones)
        assertEquals(0xFF282828.toInt(), dark.text)  // x1.25
        assertEquals(0xFF3C3C3C.toInt(), dark.icon)  // same factor
    }

    @Test fun `the brightening factor is 1 over the scrim, capped by the brightest channel`() {
        assertEquals(1.25f, scrimBoost(0xFF575E71.toInt()), 0.0001f)
        assertEquals(1f, scrimBoost(0xFFDAE2FF.toInt()), 0.0001f)
        assertEquals(1.0f, scrimBoost(0xFFFFFFFF.toInt()), 0.0001f)
        assertEquals(1f, scrimBoost(0xFF000000.toInt()), 0.0001f)
        assertEquals(1.25f, scrimBoost(0xFFCCCCCC.toInt()), 0.0001f)
        assertEquals(1f, scrimBoost(0xFF575E71.toInt(), scrim = 0f), 0.0001f)
    }

    @Test fun `no scrim means no change`() {
        assertEquals(0xFF575E71.toInt(), undoScrim(0xFF575E71.toInt(), scrim = 0f))
    }

    @Test fun `the highlight is the text colour, translucent, and subtler under dark text`() {
        val light = tonedTextColors(false, redTones)
        val dark = tonedTextColors(true, redTones)
        assertEquals(light.text and 0xFFFFFF, light.pill and 0xFFFFFF)
        assertEquals(dark.text and 0xFFFFFF, dark.pill and 0xFFFFFF)
        assertEquals(0x30, light.pill ushr 24)
        assertEquals(0x26, dark.pill ushr 24)
    }

    @Test fun `the text colours are opaque`() {
        for (hint in listOf(false, true)) assertEquals(0xFF, tonedTextColors(hint, redTones).text ushr 24)
    }

    @Test fun `a picked colour is used as it is, brightened only to survive the scrim`() {
        assertEquals(0xFFFFFFFF.toInt(), customTextColors(0xFFFFFFFF.toInt()).text)       // white needs nothing
        assertEquals(0xFF000000.toInt(), customTextColors(0xFF000000.toInt()).text)       // black stays black
        assertEquals(undoScrim(0xFF404040.toInt()), customTextColors(0xFF404040.toInt()).text)
        assertEquals(0xFF505050.toInt(), customTextColors(0xFF404040.toInt()).text)       // x1.25
        for (shift in listOf(16, 8, 0)) assertEquals(0x40.toFloat(), ((customTextColors(0xFF404040.toInt()).text shr shift) and 0xFF) * 0.8f, 1f)
    }

    @Test fun `a picked colour keeps its tint and its icons are a touch dimmer`() {
        val colors = customTextColors(0xFFFFB0A0.toInt())      // already near full brightness, so no boost
        assertEquals(0xFFFFB0A0.toInt(), colors.text)
        val icon = colors.icon
        assertTrue((icon shr 16 and 0xFF) < 0xFF)
        assertTrue((icon shr 16 and 0xFF) >= (icon shr 8 and 0xFF) && (icon shr 8 and 0xFF) >= (icon and 0xFF))
        // White: icons 95%, which is F2
        assertEquals(0xFFF2F2F2.toInt(), customTextColors(0xFFFFFFFF.toInt()).icon)
    }

    @Test fun `the highlight under a picked colour is the colour, translucent, and stronger under light text`() {
        val light = customTextColors(0xFFFFFFFF.toInt())
        val dark = customTextColors(0xFF000000.toInt())
        assertEquals(0xFFFFFF, light.pill and 0xFFFFFF)
        assertEquals(0x000000, dark.pill and 0xFFFFFF)
        assertEquals(0x30, light.pill ushr 24)
        assertEquals(0x26, dark.pill ushr 24)
    }

    @Test fun `a picked colour is always opaque`() {
        assertEquals(0xFF, customTextColors(0x80336699.toInt()).text ushr 24)
    }
}
