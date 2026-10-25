package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Following the phone's own accessibility settings: which values map to which options, at exactly the thresholds. */
class AccessibilityTest {
    private fun size(fontScale: Float) = SystemAccessibility(fontScale = fontScale).textSize()

    @Test fun `the usual font scale is Standard`() {
        assertEquals(TextSize.STANDARD, size(1.0f))
        assertEquals(TextSize.STANDARD, size(0.85f))
        assertEquals(TextSize.STANDARD, size(1.1f))
    }

    @Test fun `a font scale of 1_15 or more is Large`() {
        assertEquals(TextSize.STANDARD, size(1.1499f))
        assertEquals(TextSize.LARGE, size(1.15f))
        assertEquals(TextSize.LARGE, size(1.3f))
        assertEquals(TextSize.LARGE, size(1.4999f))
    }

    @Test fun `a font scale of 1_5 or more is Extra large`() {
        assertEquals(TextSize.EXTRA_LARGE, size(1.5f))
        assertEquals(TextSize.EXTRA_LARGE, size(2.0f))
        assertEquals(TextSize.EXTRA_LARGE, size(3.0f))
    }

    @Test fun `the thresholds are the named constants`() {
        assertEquals(1.15f, LARGE_FONT_SCALE, 0f)
        assertEquals(1.5f, EXTRA_LARGE_FONT_SCALE, 0f)
        assertEquals(0.5f, HIGH_CONTRAST_LEVEL, 0f)
    }

    @Test fun `a contrast level of 0_5 or more is High contrast`() {
        assertFalse(SystemAccessibility(contrast = 0f).toOptions().highContrast)
        assertFalse(SystemAccessibility(contrast = 0.4999f).toOptions().highContrast)
        assertTrue(SystemAccessibility(contrast = 0.5f).toOptions().highContrast)
        assertTrue(SystemAccessibility(contrast = 1f).toOptions().highContrast)
    }

    @Test fun `only a positive font weight adjustment turns Bold text on`() {
        assertFalse(SystemAccessibility(fontWeightAdjustment = 0).toOptions().bold)
        assertFalse(SystemAccessibility(fontWeightAdjustment = -100).toOptions().bold)
        assertTrue(SystemAccessibility(fontWeightAdjustment = 1).toOptions().bold)
        assertTrue(SystemAccessibility(fontWeightAdjustment = 300).toOptions().bold)
    }

    @Test fun `the three system values combine freely`() {
        val all = SystemAccessibility(fontScale = 1.6f, fontWeightAdjustment = 300, contrast = 1f).toOptions()
        assertEquals(AccessOptions(TextSize.EXTRA_LARGE, highContrast = true, bold = true), all)
        val none = SystemAccessibility().toOptions()
        assertEquals(AccessOptions(), none)
        assertTrue(none.isStandard)
        assertFalse(all.isStandard)
    }

    @Test fun `the card style is the user's own even while following the system`() {
        assertEquals(CardStyle.DARK, SystemAccessibility(contrast = 1f).toOptions(CardStyle.DARK).cardStyle)
    }

    // --- what the settings turn into ---

    private val loud = SystemAccessibility(fontScale = 2f, fontWeightAdjustment = 300, contrast = 1f)

    @Test fun `following the system, the stored choices are not used`() {
        val settings = AppSettings(followSystem = true, textSize = TextSize.LARGE, highContrast = false, boldText = false)
        assertEquals(AccessOptions(TextSize.EXTRA_LARGE, highContrast = true, bold = true), settings.accessOptions(loud))
    }

    @Test fun `not following the system, the stored choices are used and the system is ignored`() {
        val settings = AppSettings(followSystem = false, textSize = TextSize.LARGE, highContrast = false, boldText = true, cardStyle = CardStyle.LIGHT)
        assertEquals(AccessOptions(TextSize.LARGE, highContrast = false, cardStyle = CardStyle.LIGHT, bold = true), settings.accessOptions(loud))
    }

    @Test fun `a new install follows the system and with an ordinary phone looks as it always did`() {
        val settings = AppSettings()
        assertTrue(settings.followSystem)
        assertTrue(settings.accessOptions(SystemAccessibility()).isStandard)
    }
}
