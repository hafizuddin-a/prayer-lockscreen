package com.prayerwallpaper.app.ui.theme

import com.prayerwallpaper.app.logic.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The black and white palette is fixed, so these are the whole contrast story: every text colour on every ground it is drawn on, and the
 * edges and indicators that must be seen. A change to a colour that lets a ratio drop below its floor fails the build.
 */
class BrandContrastTest {
    private val grounds = mapOf("background" to Mono.BACKGROUND, "card" to Mono.CARD, "raised" to Mono.RAISED)

    private fun atLeast(floor: Float, foreground: Int, background: Int, what: String) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$what is $ratio:1, needs at least $floor:1", ratio >= floor)
    }

    @Test fun `body text is at least 7 to 1 on every ground`() {
        for ((name, ground) in grounds) atLeast(7f, Mono.TEXT, ground, "text on $name")
    }

    @Test fun `secondary text is at least 7 to 1 on every ground`() {
        for ((name, ground) in grounds) atLeast(7f, Mono.TEXT_SECONDARY, ground, "secondary text on $name")
    }

    @Test fun `the accent, links and the error colour read as text on every ground`() {
        for ((name, ground) in grounds) {
            atLeast(4.5f, Mono.ACCENT, ground, "accent on $name")
            atLeast(4.5f, Mono.SECONDARY, ground, "link colour on $name")
            atLeast(4.5f, Mono.ERROR, ground, "error on $name")
            atLeast(4.5f, Mono.SUCCESS, ground, "success on $name")
        }
    }

    @Test fun `button text is at least 7 to 1 on its fill`() {
        atLeast(7f, Mono.ON_ACCENT, Mono.ACCENT, "navy on the accent")
        atLeast(7f, Mono.ON_SECONDARY, Mono.SECONDARY, "navy on the link colour")
        atLeast(7f, Mono.ON_SUCCESS, Mono.SUCCESS, "text on success")
        atLeast(7f, Mono.ERROR, Mono.ON_ERROR, "text on the error container")
    }

    @Test fun `the edges of outlined buttons and fields are at least 3 to 1 against the card and the page`() {
        atLeast(3f, Mono.OUTLINE, Mono.CARD, "outline on card")
        atLeast(3f, Mono.OUTLINE, Mono.BACKGROUND, "outline on background")
    }

    @Test fun `the empty part of a slider is at least 3 to 1 against a card`() {
        atLeast(3f, Mono.SLIDER_TRACK, Mono.CARD, "slider track on card")
    }

    @Test fun `the accent stands out from the slider track and the card it sits on`() {
        atLeast(3f, Mono.ACCENT, Mono.SLIDER_TRACK, "slider thumb on its track")
        atLeast(3f, Mono.ACCENT, Mono.CARD, "selected choice on card")
    }

    @Test fun `the page and the cards are different enough to see a card's edge`() {
        // A card is separated from the page by colour alone, so it must not melt into it.
        assertTrue(contrastRatio(Mono.CARD, Mono.BACKGROUND) >= 1.1f)
        assertTrue(contrastRatio(Mono.RAISED, Mono.CARD) >= 1.1f)
    }
}
