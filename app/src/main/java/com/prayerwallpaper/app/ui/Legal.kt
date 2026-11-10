package com.prayerwallpaper.app.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Wording required by the project rules and by the data licence. Kept as constants so a test can pin it. */
object Legal {
    const val ATTRIBUTION =
        "Prayer times data: Majlis Ugama Islam Singapura (MUIS), via data.gov.sg, under the Open Data Licence. " +
            "This app is not affiliated with or endorsed by MUIS."

    const val DISCLAIMER =
        "Times shown here may differ from official announcements. Please check MUIS for the official timetable."

    /** The Hijri month start dates come from MUIS's calendar, not the data.gov.sg dataset, so they are credited on their own. */
    const val HIJRI_CREDIT = "Hijri month start dates and Islamic events follow the Muis Calendar published by MUIS."

    const val LICENCE_URL = "https://data.gov.sg/open-data-licence"

    /** Where to talk to the developer (bugs, feature requests). Opened only when the user taps it; the app sends nothing itself. */
    const val FEEDBACK_HANDLE = "@madebyhafiz"
    const val FEEDBACK_URL = "https://www.instagram.com/madebyhafiz/"

    /**
     * The notice the Singapore Open Data Licence asks for, in its own form: dataset, date accessed, source,
     * licence version and link. [accessedOn] is when the timetable was last downloaded; before the first
     * download there is no date to give, so that part is left out.
     */
    fun licenceNotice(accessedOn: LocalDate?): String {
        val accessed = accessedOn?.let { " accessed on ${it.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))}" }.orEmpty()
        return "Contains information from Muslim Prayer Timetables$accessed from data.gov.sg which is made available under " +
            "the terms of the Singapore Open Data Licence version 1.0 $LICENCE_URL"
    }

    /** Said wherever calculated times are chosen or explained. They are not an official timetable. */
    const val CALCULATED_DISCLAIMER =
        "These times are calculated on your phone from your location. They can differ from your local mosque or " +
            "religious authority by a few minutes. Check with them if you need the exact time."

    const val GEONAMES_CREDIT =
        "City names, positions and time zones come from GeoNames (geonames.org), licensed under Creative Commons " +
            "Attribution 4.0 (CC BY 4.0). The list was shortened and reformatted for this app."
    const val GEONAMES_URL = "https://www.geonames.org"
    const val CC_BY_URL = "https://creativecommons.org/licenses/by/4.0/"

    const val ADHAN_CREDIT =
        "Times for places other than Singapore are calculated with Adhan (github.com/batoulapps/adhan-java), " +
            "used under the MIT licence:"

    /** The MIT licence's own text, which it asks to travel with copies of the software. */
    const val ADHAN_LICENCE =
        "The MIT License (MIT)\n\nCopyright (c) 2016 Batoul Apps\n\n" +
            "Permission is hereby granted, free of charge, to any person obtaining a copy of this software and " +
            "associated documentation files (the \"Software\"), to deal in the Software without restriction, including " +
            "without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell " +
            "copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the " +
            "following conditions:\n\n" +
            "The above copyright notice and this permission notice shall be included in all copies or substantial " +
            "portions of the Software.\n\n" +
            "THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT " +
            "LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO " +
            "EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER " +
            "IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE " +
            "USE OR OTHER DEALINGS IN THE SOFTWARE."

    /** The hosted privacy policy (GitHub Pages, source in `site/`). The About screen shows the link only when it is not blank. */
    const val PRIVACY_POLICY_URL = "https://hafizuddin-a.github.io/prayer-lockscreen/"

    const val PRIVACY_SUMMARY =
        "This app collects no personal data. It has no accounts, no analytics and no ads. It downloads public " +
            "prayer time data from data.gov.sg. The colours and photo you choose stay on your device. If you tap " +
            "Use my location, your phone's approximate location is read once, on the phone, to find the nearest " +
            "city; it is not saved and not sent anywhere. Prayer reminders are optional and off until you turn them on: " +
            "they are set and shown on your phone only, and a short log of when each one arrived stays on the phone. " +
            "The version downloaded from GitHub also asks GitHub once a day whether a new version is out, and sends nothing about you; " +
            "you can turn that off in About. " +
            "Links in the app (the licences and the feedback link) open in your browser or Instagram only when you tap them."

    const val OPEN_SOURCE =
        "Built with AndroidX, Jetpack Compose, WorkManager, Kotlin and kotlinx.serialization, all under the Apache " +
            "License 2.0, with Adhan (MIT) and GeoNames data (CC BY 4.0), credited above. The icons are drawn for this app."
}
