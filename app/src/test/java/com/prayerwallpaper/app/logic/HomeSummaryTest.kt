package com.prayerwallpaper.app.logic

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSummaryTest {
    private val karachi = Place(1174872, "Karachi", "", "PK", 24.8608, 67.0104, "Asia/Karachi")

    @Test fun `the look says the background, the alignment and the countdown`() {
        assertEquals("Gradient, centred, countdown on", HomeSummary.look("Gradient", HijriAlign.CENTER, true))
        assertEquals("Photo, left, countdown off", HomeSummary.look("Photo", HijriAlign.LEFT, false))
    }

    @Test fun `the location says the place, where the times come from and the names`() {
        assertEquals("Singapore, official MUIS, Malay names", HomeSummary.location(Place.SINGAPORE, NameSet.MALAY))
        assertEquals("Karachi, Pakistan, calculated, English names", HomeSummary.location(karachi, NameSet.ENGLISH))
    }

    @Test fun `reminders say off, or on with the number of prayers and the health line`() {
        assertEquals("Off", HomeSummary.reminders(false, 5, "All good"))
        assertEquals("On, 5 prayers, all good", HomeSummary.reminders(true, 5, "All good"))
        assertEquals("On, 1 prayer", HomeSummary.reminders(true, 1, null))
        assertEquals("On, no prayers chosen", HomeSummary.reminders(true, 0, ""))
        assertEquals("On, 4 prayers, needs attention", HomeSummary.reminders(true, 4, "Needs attention: Notifications are off."))
    }

    @Test fun `widgets say none yet, how many, or what they are while that is not known`() {
        assertEquals("Add to your home screen", HomeSummary.widgets(null))
        assertEquals("None added yet", HomeSummary.widgets(0))
        assertEquals("1 added", HomeSummary.widgets(1))
        assertEquals("3 added", HomeSummary.widgets(3))
    }

    @Test fun `reading says follows your phone, or the choices made`() {
        assertEquals("Follows your phone", HomeSummary.reading(true, AccessOptions(textSize = TextSize.LARGE)))
        assertEquals("Standard text", HomeSummary.reading(false, AccessOptions()))
        assertEquals("Large text", HomeSummary.reading(false, AccessOptions(textSize = TextSize.LARGE)))
        assertEquals("Extra large text, high contrast, bold", HomeSummary.reading(false, AccessOptions(textSize = TextSize.EXTRA_LARGE, highContrast = true, bold = true)))
    }
}
