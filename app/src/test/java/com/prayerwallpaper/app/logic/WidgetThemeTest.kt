package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemeTest {
    private val navy = 0xFF0F2A53.toInt()
    private val white = 0xFFFFFFFF.toInt()

    private fun alpha(color: Int) = (color ushr 24) / 255f

    // --- glass ---

    @Test fun `glass fill is white at the chosen opacity`() {
        val t = glassTheme(0.12f, navy, relativeLuminance(navy))
        assertEquals(0xFFFFFF, t.fill and 0xFFFFFF)
        assertEquals(0.12f, alpha(t.fill), 0.005f)
        assertEquals(0f, alpha(glassTheme(0f, navy, 0.05f).fill), 0f)
        assertEquals(1f, alpha(glassTheme(1f, navy, 0.05f).fill), 0f)
        assertEquals(1f, alpha(glassTheme(5f, navy, 0.05f).fill), 0f) // out of range is clamped
    }

    @Test fun `the glass hairline is about 20 percent white`() {
        val t = glassTheme(0.12f, navy, 0.05f)
        assertEquals(0xFFFFFF, t.border and 0xFFFFFF)
        assertEquals(0.20f, alpha(t.border), 0.005f)
    }

    @Test fun `glass text follows the wallpaper like the lock screen's, light on dark and dark on light`() {
        val light = glassTheme(0.12f, navy, 0.05f)
        val dark = glassTheme(0.12f, white, 0.9f)
        assertTrue(relativeLuminance(light.text) > 0.5f)
        assertTrue(relativeLuminance(dark.text) < 0.2f)
        assertEquals(textColorsFor(navy, dark = false).text, light.text)
        assertEquals(textColorsFor(white, dark = true).text, dark.text)
    }

    // --- dark glass ---

    @Test fun `dark glass fill is black at the chosen opacity, with the same white hairline`() {
        val t = glassTheme(0.45f, navy, 0.05f, GlassTint.DARK)
        assertEquals(0x000000, t.fill and 0xFFFFFF)
        assertEquals(0.45f, alpha(t.fill), 0.005f)
        assertEquals(0xFFFFFF, t.border and 0xFFFFFF)
        assertEquals(1f, alpha(glassTheme(5f, navy, 0.05f, GlassTint.DARK).fill), 0f)
    }

    @Test fun `dark glass always has light text, even over a bright wallpaper`() {
        for (luminance in listOf(0.05f, 0.5f, 0.9f)) {
            val t = glassTheme(0.45f, white, luminance, GlassTint.DARK)
            assertTrue("$luminance", relativeLuminance(t.text) > 0.5f)
            assertEquals(textColorsFor(white, dark = false).text, t.text)
        }
    }

    @Test fun `dark glass is readable even over a white wallpaper at its default strength and not when it is too faint`() {
        assertTrue(glassContrast(DEFAULT_DARK_GLASS_OPACITY, white, 0.9f, GlassTint.DARK) >= GLASS_MIN_CONTRAST)
        assertFalse(glassContrast(0.05f, white, 0.9f, GlassTint.DARK) >= GLASS_MIN_CONTRAST)
    }

    @Test fun `the glass tint reaches the theme through the look, and the card ignores it`() {
        val standard = AccessOptions()
        val dark = widgetLook(WidgetStyle.GLASS, CardStyle.AUTO, 0.45f, follow = false, access = standard, tint = GlassTint.DARK)
        assertEquals(0x000000, dark.theme(white, 0.9f).fill and 0xFFFFFF)
        val card = widgetLook(WidgetStyle.CARD, CardStyle.DARK, 0.45f, follow = false, access = standard, tint = GlassTint.DARK)
        assertEquals(0xFF000000.toInt(), card.theme(white, 0.9f).fill)
    }

    @Test fun `glass marks the current entry with a translucent pill and no dot`() {
        val t = glassTheme(0.12f, navy, 0.05f)
        assertTrue(alpha(t.pill) in 0.1f..0.3f)
        assertFalse(t.dot)
    }

    @Test fun `dim text is the text colour a little more transparent`() {
        val t = glassTheme(0.12f, navy, 0.05f)
        assertEquals(t.text and 0xFFFFFF, t.dimText and 0xFFFFFF)
        assertTrue(alpha(t.dimText) < 1f)
    }

    // --- the card ---

    @Test fun `the dark card is black with white text and a yellow highlight`() {
        val t = cardTheme(CardLook.DARK)
        assertEquals(0xFF000000.toInt(), t.fill)
        assertEquals(0xFFFFFFFF.toInt(), t.text)
        assertEquals(0xFFFFD60A.toInt(), t.pill)
        assertEquals(0xFF000000.toInt(), t.pillText)
        assertTrue(t.dot)
    }

    @Test fun `the light card is white with near-black text and a deep blue highlight`() {
        val t = cardTheme(CardLook.LIGHT)
        assertEquals(0xFFFFFFFF.toInt(), t.fill)
        assertEquals(0xFF111111.toInt(), t.text)
        assertEquals(0xFF0842A0.toInt(), t.pill)
        assertEquals(0xFFFFFFFF.toInt(), t.pillText)
        assertTrue(t.dot)
    }

    // --- contrast thresholds from the accessibility add-on: text 7:1, highlight against the card 3:1 ---

    @Test fun `both cards meet the contrast thresholds`() {
        for (look in CardLook.entries) {
            val t = cardTheme(look)
            assertTrue("$look text on card ${contrastRatio(t.text, t.fill)}", contrastRatio(t.text, t.fill) >= 7f)
            assertTrue("$look text on highlight ${contrastRatio(t.pillText, t.pill)}", contrastRatio(t.pillText, t.pill) >= 7f)
            assertTrue("$look highlight on card ${contrastRatio(t.pill, t.fill)}", contrastRatio(t.pill, t.fill) >= 3f)
        }
    }

    // --- which look applies ---

    private val standard = AccessOptions()

    @Test fun `following the lock screen takes its text size and bold, and high contrast means the card`() {
        val big = AccessOptions(TextSize.LARGE, highContrast = false, bold = true)
        val look = widgetLook(WidgetStyle.GLASS, CardStyle.AUTO, 0.12f, follow = true, access = big)
        assertEquals(WidgetStyle.GLASS, look.style)
        assertEquals(TextSize.LARGE, look.textSize)
        assertTrue(look.bold)
        assertEquals(WidgetStyle.CARD, widgetLook(WidgetStyle.GLASS, CardStyle.DARK, 0.12f, true, AccessOptions(highContrast = true)).style)
    }

    @Test fun `not following gives standard text whatever the lock screen does`() {
        val look = widgetLook(WidgetStyle.GLASS, CardStyle.AUTO, 0.12f, follow = false, access = AccessOptions(TextSize.EXTRA_LARGE, highContrast = true, bold = true))
        assertEquals(WidgetStyle.GLASS, look.style)
        assertEquals(TextSize.STANDARD, look.textSize)
        assertFalse(look.bold)
    }

    @Test fun `the card style is the chosen colour, or auto from the wallpaper`() {
        assertEquals(0xFF000000.toInt(), widgetLook(WidgetStyle.CARD, CardStyle.DARK, 0.12f, false, standard).theme(white, 0.9f).fill)
        assertEquals(0xFFFFFFFF.toInt(), widgetLook(WidgetStyle.CARD, CardStyle.LIGHT, 0.12f, false, standard).theme(navy, 0.05f).fill)
        // Auto: a dark card on a bright wallpaper, a light card on a dark one.
        assertEquals(0xFF000000.toInt(), widgetLook(WidgetStyle.CARD, CardStyle.AUTO, 0.12f, false, standard).theme(white, 0.9f).fill)
        assertEquals(0xFFFFFFFF.toInt(), widgetLook(WidgetStyle.CARD, CardStyle.AUTO, 0.12f, false, standard).theme(navy, 0.05f).fill)
    }

    // --- the legibility warning ---

    @Test fun `glass on a dark wallpaper is legible at low opacity and not when it turns white`() {
        val lum = relativeLuminance(navy)
        assertTrue(glassContrast(0.12f, navy, lum) >= GLASS_MIN_CONTRAST)
        assertTrue(glassContrast(0f, navy, lum) >= GLASS_MIN_CONTRAST)
        assertFalse(glassContrast(1f, navy, lum) >= GLASS_MIN_CONTRAST) // light text on a solid white fill
    }

    @Test fun `dark text on a light wallpaper is legible at low opacity`() {
        assertTrue(glassContrast(0.12f, white, 0.9f) >= GLASS_MIN_CONTRAST)
    }
}
