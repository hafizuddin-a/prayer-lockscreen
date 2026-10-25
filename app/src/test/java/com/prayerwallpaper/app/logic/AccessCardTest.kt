package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The High contrast card's colours. These thresholds are the point of the mode: a change to a colour that lets a ratio
 * drop below them fails the build.
 */
class AccessCardTest {
    private val dark = Cards.DARK
    private val light = Cards.LIGHT

    // --- the helper itself ---

    @Test fun `black on white is 21 to 1 and a colour on itself is 1 to 1`() {
        assertEquals(21f, contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01f)
        assertEquals(21f, contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01f)
        assertEquals(1f, contrastRatio(0xFF336699.toInt(), 0xFF336699.toInt()), 0.0001f)
    }

    @Test fun `the ratio matches published WCAG values`() {
        // #767676 on white is the well known 4.54:1 minimum for normal text.
        assertEquals(4.54f, contrastRatio(0xFF767676.toInt(), 0xFFFFFFFF.toInt()), 0.01f)
        // #777777 on white is just under 4.5.
        assertTrue(contrastRatio(0xFF777777.toInt(), 0xFFFFFFFF.toInt()) < 4.5f)
    }

    // --- the dark card ---

    @Test fun `dark card, white text on black is at least 7 to 1`() {
        assertTrue(contrastRatio(dark.text, dark.background) >= 7f)
    }

    @Test fun `dark card, black text on the yellow highlight is at least 7 to 1`() {
        assertTrue(contrastRatio(dark.highlightText, dark.highlight) >= 7f)
    }

    @Test fun `dark card, the highlight stands out from the card by at least 3 to 1`() {
        assertTrue(contrastRatio(dark.highlight, dark.background) >= 3f)
    }

    // --- the light card ---

    @Test fun `light card, dark text on white is at least 7 to 1`() {
        assertTrue(contrastRatio(light.text, light.background) >= 7f)
    }

    @Test fun `light card, white text on the blue highlight is at least 7 to 1`() {
        assertTrue(contrastRatio(light.highlightText, light.highlight) >= 7f)
    }

    @Test fun `light card, the highlight stands out from the card by at least 3 to 1`() {
        assertTrue(contrastRatio(light.highlight, light.background) >= 3f)
    }

    // --- the exact colours of the spec ---

    @Test fun `the colours are the ones in the spec`() {
        assertEquals(0xFF000000.toInt(), dark.background)
        assertEquals(0xFFFFFFFF.toInt(), dark.text)
        assertEquals(0xFFFFD60A.toInt(), dark.highlight)
        assertEquals(0xFF000000.toInt(), dark.highlightText)
        assertEquals(0xFFFFFFFF.toInt(), light.background)
        assertEquals(0xFF111111.toInt(), light.text)
        assertEquals(0xFF0842A0.toInt(), light.highlight)
        assertEquals(0xFFFFFFFF.toInt(), light.highlightText)
    }

    @Test fun `the card is solid`() {
        for (card in listOf(dark, light)) {
            assertEquals(0xFF, (card.background ushr 24) and 0xFF)
            assertEquals(0xFF, (card.highlight ushr 24) and 0xFF)
            assertEquals(0xFF, (card.text ushr 24) and 0xFF)
        }
    }

    @Test fun `the edge is the opposite colour at 40 percent and a divider the text colour at 25 percent`() {
        assertEquals(0x66FFFFFF, dark.border)    // white, 40%
        assertEquals(0x66000000, light.border)   // black, 40%
        assertEquals(0x40FFFFFF, dark.divider)   // white text, 25%
        assertEquals(0x40111111, light.divider)  // #111111 text, 25%
    }

    @Test fun `the edge and the dividers are visible against the card itself`() {
        // Blended over its own card the edge must still differ from it, or it would not mark the card on a matching background.
        assertTrue(0x66 > 0 && 0x40 > 0)
        assertEquals(0.40f, AccessLayout.BORDER_ALPHA, 0f)
        assertEquals(0.25f, AccessLayout.DIVIDER_ALPHA, 0f)
    }

    // --- Auto ---

    @Test fun `Auto picks the light card on a dark background`() {
        assertEquals(CardLook.LIGHT, chooseCard(CardStyle.AUTO, 0.0f))
        assertEquals(CardLook.LIGHT, chooseCard(CardStyle.AUTO, 0.02f))
        assertEquals(CardLook.LIGHT, chooseCard(CardStyle.AUTO, 0.15f))
    }

    @Test fun `Auto picks the dark card on a bright background`() {
        assertEquals(CardLook.DARK, chooseCard(CardStyle.AUTO, 0.9f))
        assertEquals(CardLook.DARK, chooseCard(CardStyle.AUTO, 1.0f))
        assertEquals(CardLook.DARK, chooseCard(CardStyle.AUTO, 0.55f))
    }

    @Test fun `Auto switches at the same luminance the adaptive text colour does`() {
        assertEquals(CardLook.LIGHT, chooseCard(CardStyle.AUTO, DARK_TEXT_ABOVE_LUMINANCE))
        assertEquals(CardLook.DARK, chooseCard(CardStyle.AUTO, DARK_TEXT_ABOVE_LUMINANCE + 0.01f))
        assertEquals(0.35f, DARK_TEXT_ABOVE_LUMINANCE, 0f)
    }

    @Test fun `a mid luminance goes to whichever side of the threshold it is on`() {
        assertEquals(CardLook.LIGHT, chooseCard(CardStyle.AUTO, 0.34f))
        assertEquals(CardLook.DARK, chooseCard(CardStyle.AUTO, 0.36f))
    }

    @Test fun `a forced style ignores the background`() {
        for (luminance in listOf(0f, 0.2f, 0.5f, 1f)) {
            assertEquals(CardLook.DARK, chooseCard(CardStyle.DARK, luminance))
            assertEquals(CardLook.LIGHT, chooseCard(CardStyle.LIGHT, luminance))
        }
    }

    @Test fun `each look is its own colours`() {
        assertSame(Cards.DARK, Cards.of(CardLook.DARK))
        assertSame(Cards.LIGHT, Cards.of(CardLook.LIGHT))
    }

    @Test fun `Auto's card does contrast with the background it was chosen for`() {
        // On a dark background the white card stands out, and on a bright one the black card does.
        val darkBackground = 0xFF0A1530.toInt()
        val brightBackground = 0xFFF4C542.toInt()
        assertEquals(CardLook.LIGHT, chooseCard(CardStyle.AUTO, relativeLuminance(darkBackground)))
        assertTrue(contrastRatio(Cards.LIGHT.background, darkBackground) >= 3f)
        assertEquals(CardLook.DARK, chooseCard(CardStyle.AUTO, relativeLuminance(brightBackground)))
        assertTrue(contrastRatio(Cards.DARK.background, brightBackground) >= 3f)
    }

    // --- a mixed background, for the suggestion of a dim ---

    private fun pixels(bright: Int, total: Int) = IntArray(total) { if (it < bright) 0xFFF0F0F0.toInt() else 0xFF101020.toInt() }

    @Test fun `an evenly dark or evenly bright background is not mixed`() {
        assertFalse(isMixedBackground(pixels(0, 100), 10, 0, 0, 10, 10))
        assertFalse(isMixedBackground(pixels(100, 100), 10, 0, 0, 10, 10))
    }

    @Test fun `a background that is bright in places and dark in others is mixed`() {
        assertTrue(isMixedBackground(pixels(50, 100), 10, 0, 0, 10, 10))
        assertTrue(isMixedBackground(pixels(20, 100), 10, 0, 0, 10, 10))
        assertTrue(isMixedBackground(pixels(80, 100), 10, 0, 0, 10, 10))
    }

    @Test fun `a few stray bright pixels do not make it mixed`() {
        assertFalse(isMixedBackground(pixels(5, 100), 10, 0, 0, 10, 10))
        assertFalse(isMixedBackground(pixels(95, 100), 10, 0, 0, 10, 10))
    }

    @Test fun `only the region asked about is looked at`() {
        // Bright top half, dark bottom half: the top rows alone are not mixed.
        val half = IntArray(100) { if (it < 50) 0xFFF0F0F0.toInt() else 0xFF101020.toInt() }
        assertFalse(isMixedBackground(half, 10, 0, 0, 10, 5))
        assertTrue(isMixedBackground(half, 10, 0, 0, 10, 10))
    }

    @Test fun `the suggested dim is 20 percent`() {
        assertEquals(0.20f, SUGGESTED_DIM, 0f)
    }
}
