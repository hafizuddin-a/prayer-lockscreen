package com.prayerwallpaper.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wording the project rules require must not drift by accident. */
class LegalTest {
    @Test fun `attribution is exactly the required wording`() {
        assertEquals(
            "Prayer times data: Majlis Ugama Islam Singapura (MUIS), via data.gov.sg, under the Open Data Licence. " +
                "This app is not affiliated with or endorsed by MUIS.",
            Legal.ATTRIBUTION,
        )
    }

    @Test fun `the feedback link goes to the developer's Instagram, over https, and says nothing is sent by the app`() {
        assertEquals("@madebyhafiz", Legal.FEEDBACK_HANDLE)
        assertEquals("https://www.instagram.com/madebyhafiz/", Legal.FEEDBACK_URL)
        assertTrue(Legal.FEEDBACK_URL.endsWith("/${Legal.FEEDBACK_HANDLE.removePrefix("@")}/"))
    }

    @Test fun `the about screen warns that times may differ from official announcements`() {
        assertTrue(Legal.DISCLAIMER.contains("may differ from official announcements"))
    }

    @Test fun `the privacy summary makes only the promised claims`() {
        val text = Legal.PRIVACY_SUMMARY
        assertTrue(text.contains("no personal data"))
        assertTrue(text.contains("no accounts"))
        assertTrue(text.contains("no analytics"))
        assertTrue(text.contains("no ads"))
        assertTrue(text.contains("data.gov.sg"))
    }

    @Test fun `the privacy summary tells people about the location button and its limits`() {
        val text = Legal.PRIVACY_SUMMARY
        assertTrue(text.contains("Use my location"))
        assertTrue(text.contains("once"))
        assertTrue(text.contains("not saved"))
        assertTrue(text.contains("not sent anywhere"))
    }

    @Test fun `calculated times are described as calculated and as possibly different`() {
        assertTrue(Legal.CALCULATED_DISCLAIMER.contains("calculated"))
        assertTrue(Legal.CALCULATED_DISCLAIMER.contains("can differ"))
        assertTrue(Legal.CALCULATED_DISCLAIMER.contains("local mosque"))
    }

    @Test fun `GeoNames is credited as CC BY 4_0 with its link and says the list was changed`() {
        assertTrue(Legal.GEONAMES_CREDIT.contains("GeoNames"))
        assertTrue(Legal.GEONAMES_CREDIT.contains("CC BY 4.0"))
        assertTrue(Legal.GEONAMES_CREDIT.contains("shortened and reformatted"))
        assertTrue(Legal.GEONAMES_URL.startsWith("https://"))
        assertEquals("https://creativecommons.org/licenses/by/4.0/", Legal.CC_BY_URL)
    }

    @Test fun `the Adhan licence carries its copyright line and permission notice`() {
        assertTrue(Legal.ADHAN_LICENCE.startsWith("The MIT License (MIT)"))
        assertTrue(Legal.ADHAN_LICENCE.contains("Copyright (c) 2016 Batoul Apps"))
        assertTrue(Legal.ADHAN_LICENCE.contains("The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software."))
        assertTrue(Legal.ADHAN_LICENCE.contains("THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND"))
        assertTrue(Legal.ADHAN_CREDIT.contains("Adhan"))
        assertTrue(Legal.ADHAN_CREDIT.contains("MIT"))
    }

    @Test fun `the Hijri dates and events are credited to the Muis Calendar, next to the not affiliated line`() {
        assertEquals("Hijri month start dates and Islamic events follow the Muis Calendar published by MUIS.", Legal.HIJRI_CREDIT)
        assertTrue(Legal.ATTRIBUTION.contains("not affiliated with or endorsed by MUIS"))
    }

    @Test fun `the licence link is https`() {
        assertTrue(Legal.LICENCE_URL.startsWith("https://"))
    }

    @Test fun `the privacy policy is hosted at the GitHub Pages address, over https, so the About screen shows its button`() {
        assertEquals("https://hafizuddin-a.github.io/prayer-lockscreen/", Legal.PRIVACY_POLICY_URL)
        assertTrue(Legal.PRIVACY_POLICY_URL.startsWith("https://"))
    }

    @Test fun `the licence notice has the licence's required parts`() {
        val notice = Legal.licenceNotice(java.time.LocalDate.of(2026, 10, 4))
        assertEquals(
            "Contains information from Muslim Prayer Timetables accessed on 4 October 2026 from data.gov.sg which is made " +
                "available under the terms of the Singapore Open Data Licence version 1.0 https://data.gov.sg/open-data-licence",
            notice,
        )
    }

    @Test fun `before the first download the notice leaves the date out instead of inventing one`() {
        val notice = Legal.licenceNotice(null)
        assertFalse(notice.contains("accessed on"))
        assertTrue(notice.startsWith("Contains information from Muslim Prayer Timetables from data.gov.sg"))
        assertTrue(notice.endsWith(Legal.LICENCE_URL))
    }

    @Test fun `the date is in English whatever the phone language is`() {
        val saved = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ms-MY"))
            assertTrue(Legal.licenceNotice(java.time.LocalDate.of(2026, 3, 9)).contains("accessed on 9 March 2026"))
        } finally {
            java.util.Locale.setDefault(saved)
        }
    }
}
