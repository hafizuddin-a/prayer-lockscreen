package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.logic.AppScreen.CALCULATION
import com.prayerwallpaper.app.logic.AppScreen.HOME
import com.prayerwallpaper.app.logic.AppScreen.LOCATION
import com.prayerwallpaper.app.logic.AppScreen.LOOK
import com.prayerwallpaper.app.logic.AppScreen.PHOTO
import com.prayerwallpaper.app.logic.AppScreen.PLACE
import com.prayerwallpaper.app.logic.AppScreen.SETUP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {
    @Test fun `opening a screen puts it on top and back returns to where you came from`() {
        val stack = Navigation.open(listOf(HOME), LOOK)
        assertEquals(listOf(HOME, LOOK), stack)
        assertEquals(listOf(HOME), Navigation.back(stack))
    }

    @Test fun `a screen opened from another screen goes back to that screen, not to Home`() {
        val stack = Navigation.open(Navigation.open(listOf(HOME), LOCATION), CALCULATION)
        assertEquals(listOf(HOME, LOCATION, CALCULATION), stack)
        assertEquals(listOf(HOME, LOCATION), Navigation.back(stack))
        val photo = Navigation.open(Navigation.open(listOf(HOME), LOOK), PHOTO)
        assertEquals(LOOK, Navigation.back(photo).last())
    }

    @Test fun `the setup can open the place chooser and Back returns to the setup`() {
        val stack = Navigation.open(listOf(SETUP), PLACE)
        assertEquals(listOf(SETUP, PLACE), stack)
        assertEquals(listOf(SETUP), Navigation.back(stack))
    }

    @Test fun `back from the only screen when it is not Home lands on Home`() {
        assertEquals(listOf(HOME), Navigation.back(listOf(SETUP)))
    }

    @Test fun `opening Home starts the history again`() {
        assertEquals(listOf(HOME), Navigation.open(listOf(HOME, LOCATION, PLACE), HOME))
    }

    @Test fun `opening a screen already in the history returns to it instead of stacking it twice`() {
        assertEquals(listOf(HOME, LOCATION), Navigation.open(listOf(HOME, LOCATION, PLACE), LOCATION))
        assertEquals(listOf(HOME, LOOK), Navigation.open(listOf(HOME, LOOK), LOOK))
    }

    @Test fun `Back is the app's on every screen but Home`() {
        assertFalse(Navigation.handlesBack(listOf(HOME)))
        assertTrue(Navigation.handlesBack(listOf(HOME, LOOK)))
        assertTrue(Navigation.handlesBack(listOf(SETUP)))
    }

    @Test fun `the history never gets longer than the number of screens`() {
        var stack = listOf(HOME)
        repeat(50) { i -> stack = Navigation.open(stack, AppScreen.entries[i % AppScreen.entries.size]) }
        assertTrue(stack.size <= AppScreen.entries.size)
        assertEquals(stack.size, stack.toSet().size)
    }
}
