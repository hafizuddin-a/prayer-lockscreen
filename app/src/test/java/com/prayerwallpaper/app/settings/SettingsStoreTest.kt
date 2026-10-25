package com.prayerwallpaper.app.settings

import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Place
import com.prayerwallpaper.app.logic.AsrRule
import com.prayerwallpaper.app.logic.CalcMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    private val karachi = Place(1174872, "Karachi", "", "PK", 24.8608, 67.0104, "Asia/Karachi")
    private val london = Place(2643743, "London", "ENG", "GB", 51.50853, -0.12574, "Europe/London")

    @Test fun `a fresh install is Singapore on the official timetable with Malay names`() {
        val s = SettingsStore(FakePrefs()).read()
        assertEquals(Place.SINGAPORE, s.place)
        assertTrue(s.place.usesMuis)
        assertNull(s.method)
        assertEquals(NameSet.MALAY, s.effectiveNames)
        assertEquals(List(6) { 0 }, s.adjustments)
    }

    @Test fun `the place and every choice survive a write and a read`() {
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        val chosen = AppSettings(
            place = karachi, method = CalcMethod.EGYPTIAN, asr = AsrRule.HANAFI, names = NameSet.MALAY,
            adjustments = listOf(1, 0, -2, 3, 0, 5),
        )
        store.write(chosen)
        val back = SettingsStore(prefs).read()
        assertEquals(karachi, back.place)
        assertEquals(24.8608, back.place.latitude, 0.0) // exact, not rounded through a float
        assertEquals(67.0104, back.place.longitude, 0.0)
        assertEquals(CalcMethod.EGYPTIAN, back.method)
        assertEquals(AsrRule.HANAFI, back.asr)
        assertEquals(NameSet.MALAY, back.names)
        assertEquals(listOf(1, 0, -2, 3, 0, 5), back.adjustments)
    }

    @Test fun `choices that were cleared stay cleared`() {
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        store.write(AppSettings(place = karachi, method = CalcMethod.EGYPTIAN, asr = AsrRule.HANAFI, names = NameSet.MALAY))
        store.write(AppSettings(place = karachi))
        val back = store.read()
        assertNull(back.method)
        assertNull(back.asr)
        assertNull(back.names)
    }

    @Test fun `unset choices follow the country`() {
        val karachiDefaults = AppSettings(place = karachi)
        assertEquals(CalcMethod.KARACHI, karachiDefaults.effectiveMethod)
        assertEquals(AsrRule.HANAFI, karachiDefaults.effectiveAsr)
        assertEquals(NameSet.ENGLISH, karachiDefaults.effectiveNames)
        val londonDefaults = AppSettings(place = london)
        assertEquals(CalcMethod.MUSLIM_WORLD_LEAGUE, londonDefaults.effectiveMethod)
        assertEquals(AsrRule.STANDARD, londonDefaults.effectiveAsr)
    }

    @Test fun `a choice overrides the country default`() {
        val s = AppSettings(place = karachi, method = CalcMethod.UMM_AL_QURA, asr = AsrRule.STANDARD, names = NameSet.MALAY)
        assertEquals(CalcMethod.UMM_AL_QURA, s.effectiveMethod)
        assertEquals(AsrRule.STANDARD, s.effectiveAsr)
        assertEquals(NameSet.MALAY, s.effectiveNames)
    }

    @Test fun `moving to another place resets the method and the nudges but keeps Asr and names`() {
        val before = AppSettings(place = karachi, method = CalcMethod.EGYPTIAN, asr = AsrRule.HANAFI, names = NameSet.MALAY, adjustments = listOf(0, 0, 0, 0, 5, 0))
        val after = before.withPlace(london)
        assertEquals(london, after.place)
        assertNull(after.method)
        assertEquals(List(6) { 0 }, after.adjustments)
        assertEquals(AsrRule.HANAFI, after.asr)
        assertEquals(NameSet.MALAY, after.names)
    }

    @Test fun `the calculation request carries the place's zone and coordinates`() {
        val r = AppSettings(place = london, asr = AsrRule.HANAFI, adjustments = listOf(0, 0, 0, 0, 2, 0)).calcRequest()
        assertEquals(51.50853, r.latitude, 0.0)
        assertEquals("Europe/London", r.zone.id)
        assertEquals(CalcMethod.MUSLIM_WORLD_LEAGUE, r.method)
        assertEquals(AsrRule.HANAFI, r.asr)
        assertEquals(2, r.adjustments[4])
    }

    @Test fun `a stored zone this phone does not know falls back to Singapore, not a crash`() {
        val prefs = FakePrefs(
            mapOf("place_id" to 5L, "place_name" to "Nowhere", "place_country" to "ZZ", "place_zone" to "Not/AZone", "place_lat" to 1.0.toRawBits(), "place_lon" to 2.0.toRawBits()),
        )
        assertEquals(Place.SINGAPORE, SettingsStore(prefs).read().place)
    }

    @Test fun `stored coordinates out of range or missing fields fall back to Singapore`() {
        val base = mapOf("place_id" to 5L, "place_name" to "X", "place_country" to "GB", "place_zone" to "Europe/London")
        assertEquals(Place.SINGAPORE, SettingsStore(FakePrefs(base + mapOf("place_lat" to 91.0.toRawBits(), "place_lon" to 0.0.toRawBits()))).read().place)
        assertEquals(Place.SINGAPORE, SettingsStore(FakePrefs(base + mapOf("place_lat" to Double.NaN.toRawBits(), "place_lon" to 0.0.toRawBits()))).read().place)
        assertEquals(Place.SINGAPORE, SettingsStore(FakePrefs(base - "place_name" + mapOf("place_lat" to 1.0.toRawBits(), "place_lon" to 1.0.toRawBits()))).read().place)
    }

    @Test fun `unknown method names and damaged adjustments are ignored`() {
        val prefs = FakePrefs(mapOf("calc_method" to "HELLO", "asr_rule" to "x", "name_set" to "y", "adjustments" to "1,2,three,4,5,6"))
        val s = SettingsStore(prefs).read()
        assertNull(s.method)
        assertNull(s.asr)
        assertNull(s.names)
        assertEquals(List(6) { 0 }, s.adjustments)
    }

    @Test fun `too few or too many adjustments are ignored, and big ones are limited`() {
        assertEquals(List(6) { 0 }, SettingsStore(FakePrefs(mapOf("adjustments" to "1,2,3"))).read().adjustments)
        assertEquals(List(6) { 0 }, SettingsStore(FakePrefs(mapOf("adjustments" to "1,2,3,4,5,6,7"))).read().adjustments)
        assertEquals(listOf(30, -30, 0, 0, 0, 1), SettingsStore(FakePrefs(mapOf("adjustments" to "99,-99,0,0,0,1"))).read().adjustments)
    }

    @Test fun `settings from before places existed read as Singapore and keep their look`() {
        // An older install has only the look settings stored.
        val prefs = FakePrefs(mapOf("mode" to "SOLID", "solid" to -16316665, "list_top" to 0.55f))
        val s = SettingsStore(prefs).read()
        assertEquals(BackgroundMode.SOLID, s.mode)
        assertEquals(0.55f, s.listTop, 0f)
        assertEquals(Place.SINGAPORE, s.place)
        assertFalse(prefs.contains("place_id")) // reading does not write anything
    }

    @Test fun `text colour follows the background unless the user chose otherwise`() {
        assertEquals(TextColorSource.BACKGROUND, SettingsStore(FakePrefs()).read().textColors)
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        store.write(AppSettings(textColors = TextColorSource.SYSTEM))
        assertEquals(TextColorSource.SYSTEM, SettingsStore(prefs).read().textColors)
        store.update { it.copy(textColors = TextColorSource.CUSTOM, customTextColor = 0xFF123456.toInt()) }
        assertEquals(TextColorSource.CUSTOM, SettingsStore(prefs).read().textColors)
        assertEquals(0xFF123456.toInt(), SettingsStore(prefs).read().customTextColor)
        store.update { it.copy(textColors = TextColorSource.BACKGROUND) }
        assertEquals(TextColorSource.BACKGROUND, store.read().textColors)
    }

    @Test fun `an unknown stored text colour source falls back to the default`() {
        assertEquals(TextColorSource.BACKGROUND, SettingsStore(FakePrefs(mapOf("text_colors" to "RAINBOW"))).read().textColors)
    }

    @Test fun `the custom text colour starts white and is stored opaque`() {
        assertEquals(0xFFFFFFFF.toInt(), SettingsStore(FakePrefs()).read().customTextColor)
        val prefs = FakePrefs()
        SettingsStore(prefs).write(AppSettings(customTextColor = 0x00ABCDEF))
        assertEquals(0xFFABCDEF.toInt(), SettingsStore(prefs).read().customTextColor)
    }

    @Test fun `the earlier PLAIN choice reads as a custom colour, which starts white`() {
        val s = SettingsStore(FakePrefs(mapOf("text_colors" to "PLAIN"))).read()
        assertEquals(TextColorSource.CUSTOM, s.textColors)
        assertEquals(0xFFFFFFFF.toInt(), s.customTextColor)
    }

    @Test fun `the Hijri line is on by default and the choice is kept`() {
        assertTrue(SettingsStore(FakePrefs()).read().showHijri)
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        store.update { it.copy(showHijri = false) }
        assertFalse(SettingsStore(prefs).read().showHijri)
        store.update { it.copy(showHijri = true) }
        assertTrue(SettingsStore(prefs).read().showHijri)
    }

    @Test fun `settings from before the Hijri line existed show it`() {
        assertTrue(SettingsStore(FakePrefs(mapOf("mode" to "SOLID"))).read().showHijri)
    }

    @Test fun `the Hijri date changes at Maghrib by default and the choice is kept`() {
        assertTrue(SettingsStore(FakePrefs()).read().hijriAtMaghrib)
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        store.update { it.copy(hijriAtMaghrib = false) }
        assertFalse(SettingsStore(prefs).read().hijriAtMaghrib)
        store.update { it.copy(hijriAtMaghrib = true) }
        assertTrue(SettingsStore(prefs).read().hijriAtMaghrib)
    }

    @Test fun `the Hijri date is centred by default and the choice is kept`() {
        assertEquals(com.prayerwallpaper.app.logic.HijriAlign.CENTER, SettingsStore(FakePrefs()).read().hijriAlign)
        val prefs = FakePrefs()
        SettingsStore(prefs).update { it.copy(hijriAlign = com.prayerwallpaper.app.logic.HijriAlign.LEFT) }
        assertEquals(com.prayerwallpaper.app.logic.HijriAlign.LEFT, SettingsStore(prefs).read().hijriAlign)
    }

    @Test fun `an unknown stored Hijri position falls back to centred`() {
        assertEquals(com.prayerwallpaper.app.logic.HijriAlign.CENTER, SettingsStore(FakePrefs(mapOf("hijri_align" to "DIAGONAL"))).read().hijriAlign)
    }

    @Test fun `Islamic events are shown by default and the choice is kept`() {
        assertTrue(SettingsStore(FakePrefs()).read().showHijriEvents)
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        store.update { it.copy(showHijriEvents = false) }
        assertFalse(SettingsStore(prefs).read().showHijriEvents)
        store.update { it.copy(showHijriEvents = true) }
        assertTrue(SettingsStore(prefs).read().showHijriEvents)
    }

    @Test fun `the countdown is shown by default and the choice is kept`() {
        assertTrue(SettingsStore(FakePrefs()).read().showCountdown)
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        store.update { it.copy(showCountdown = false) }
        assertFalse(SettingsStore(prefs).read().showCountdown)
        store.update { it.copy(showCountdown = true) }
        assertTrue(SettingsStore(prefs).read().showCountdown)
    }

    @Test fun `settings from before the countdown existed show it`() {
        assertTrue(SettingsStore(FakePrefs(mapOf("mode" to "SOLID", "show_hijri" to false))).read().showCountdown)
    }

    @Test fun `the countdown choice does not disturb the Hijri choices or the position`() {
        val prefs = FakePrefs()
        val store = SettingsStore(prefs)
        store.update { it.copy(showHijri = false, showCountdown = false, hijriAlign = com.prayerwallpaper.app.logic.HijriAlign.LEFT) }
        val read = SettingsStore(prefs).read()
        assertFalse(read.showHijri)
        assertFalse(read.showCountdown)
        assertEquals(com.prayerwallpaper.app.logic.HijriAlign.LEFT, read.hijriAlign)
    }

    // --- accessibility ---

    @Test fun `accessibility follows the system by default with everything standard`() {
        val read = SettingsStore(FakePrefs()).read()
        assertTrue(read.followSystem)
        assertEquals(com.prayerwallpaper.app.logic.TextSize.STANDARD, read.textSize)
        assertFalse(read.highContrast)
        assertEquals(com.prayerwallpaper.app.logic.CardStyle.AUTO, read.cardStyle)
        assertFalse(read.boldText)
    }

    @Test fun `every accessibility choice is kept`() {
        val prefs = FakePrefs()
        SettingsStore(prefs).update {
            it.copy(
                followSystem = false, textSize = com.prayerwallpaper.app.logic.TextSize.EXTRA_LARGE, highContrast = true,
                cardStyle = com.prayerwallpaper.app.logic.CardStyle.DARK, boldText = true,
            )
        }
        val read = SettingsStore(prefs).read()
        assertFalse(read.followSystem)
        assertEquals(com.prayerwallpaper.app.logic.TextSize.EXTRA_LARGE, read.textSize)
        assertTrue(read.highContrast)
        assertEquals(com.prayerwallpaper.app.logic.CardStyle.DARK, read.cardStyle)
        assertTrue(read.boldText)
    }

    @Test fun `unknown stored accessibility values fall back to the defaults`() {
        val read = SettingsStore(FakePrefs(mapOf("access_text_size" to "HUGE", "access_card_style" to "NEON"))).read()
        assertEquals(com.prayerwallpaper.app.logic.TextSize.STANDARD, read.textSize)
        assertEquals(com.prayerwallpaper.app.logic.CardStyle.AUTO, read.cardStyle)
    }

    @Test fun `settings from before accessibility existed follow the system`() {
        assertTrue(SettingsStore(FakePrefs(mapOf("mode" to "SOLID", "show_countdown" to true))).read().followSystem)
    }

    @Test fun `the larger text question has not been asked on a new install, and once asked it stays asked`() {
        assertFalse(SettingsStore(FakePrefs()).read().accessAsked)
        val prefs = FakePrefs()
        SettingsStore(prefs).update { it.copy(accessAsked = true) }
        assertTrue(SettingsStore(prefs).read().accessAsked)
    }

    @Test fun `the first-run setup is not done on a new install, and once done it stays done`() {
        assertFalse(SettingsStore(FakePrefs()).read().setupDone)
        val prefs = FakePrefs()
        SettingsStore(prefs).update { it.copy(setupDone = true) }
        assertTrue(SettingsStore(prefs).read().setupDone)
    }

    @Test fun `settings from before the setup existed read as not done and keep everything else`() {
        val read = SettingsStore(FakePrefs(mapOf("mode" to "SOLID", "access_asked" to true))).read()
        assertFalse(read.setupDone)
        assertTrue(read.accessAsked)
        assertEquals(BackgroundMode.SOLID, read.mode)
    }

    // --- reminders ---

    @Test fun `reminders are off by default, with every prayer but Syuruk chosen and no pre-reminder`() {
        val read = SettingsStore(FakePrefs()).read()
        assertFalse(read.remindersOn)
        assertEquals(com.prayerwallpaper.app.data.Prayer.entries.filter { it != com.prayerwallpaper.app.data.Prayer.SYURUK }.toSet(), read.reminderPrayers)
        assertEquals(0, read.preReminderMinutes)
    }

    @Test fun `every reminder choice is kept`() {
        val prefs = FakePrefs()
        SettingsStore(prefs).update {
            it.copy(remindersOn = true, reminderPrayers = setOf(com.prayerwallpaper.app.data.Prayer.SYURUK, com.prayerwallpaper.app.data.Prayer.ASAR), preReminderMinutes = 10)
        }
        val read = SettingsStore(prefs).read()
        assertTrue(read.remindersOn)
        assertEquals(setOf(com.prayerwallpaper.app.data.Prayer.SYURUK, com.prayerwallpaper.app.data.Prayer.ASAR), read.reminderPrayers)
        assertEquals(10, read.preReminderMinutes)
    }

    @Test fun `choosing no prayer at all is kept as no prayer, not as the default`() {
        val prefs = FakePrefs()
        SettingsStore(prefs).update { it.copy(reminderPrayers = emptySet()) }
        assertTrue(SettingsStore(prefs).read().reminderPrayers.isEmpty())
    }

    @Test fun `an unknown prayer name or pre-reminder size is ignored`() {
        val read = SettingsStore(FakePrefs(mapOf("reminder_prayers" to "ASAR,PRAYER_X", "pre_reminder_minutes" to 7))).read()
        assertEquals(setOf(com.prayerwallpaper.app.data.Prayer.ASAR), read.reminderPrayers)
        assertEquals(0, read.preReminderMinutes)
    }

    @Test fun `settings from before reminders existed have them off`() {
        assertFalse(SettingsStore(FakePrefs(mapOf("mode" to "SOLID", "access_asked" to true))).read().remindersOn)
    }

    // --- widgets ---

    @Test fun `widgets are glass at 12 percent, an auto card, and follow the lock screen, by default`() {
        val read = SettingsStore(FakePrefs()).read()
        assertEquals(com.prayerwallpaper.app.logic.WidgetStyle.GLASS, read.widgetStyle)
        assertEquals(com.prayerwallpaper.app.logic.CardStyle.AUTO, read.widgetCard)
        assertEquals(0.12f, read.widgetGlassOpacity, 0.0001f)
        assertTrue(read.widgetFollowAccess)
    }

    @Test fun `the widget choices survive a write and a read`() {
        val prefs = FakePrefs()
        SettingsStore(prefs).update {
            it.copy(widgetStyle = com.prayerwallpaper.app.logic.WidgetStyle.CARD, widgetCard = com.prayerwallpaper.app.logic.CardStyle.LIGHT, widgetGlassOpacity = 0.4f, widgetFollowAccess = false)
        }
        val read = SettingsStore(prefs).read()
        assertEquals(com.prayerwallpaper.app.logic.WidgetStyle.CARD, read.widgetStyle)
        assertEquals(com.prayerwallpaper.app.logic.CardStyle.LIGHT, read.widgetCard)
        assertEquals(0.4f, read.widgetGlassOpacity, 0.0001f)
        assertFalse(read.widgetFollowAccess)
    }

    @Test fun `the glass is a white tint by default, a dark tint keeps its own opacity, and the tint in force picks it`() {
        val fresh = SettingsStore(FakePrefs()).read()
        assertEquals(com.prayerwallpaper.app.logic.GlassTint.LIGHT, fresh.widgetGlass)
        assertEquals(0.55f, fresh.widgetDarkGlassOpacity, 0.0001f)
        assertEquals(0.12f, fresh.glassOpacity, 0.0001f)
        val prefs = FakePrefs()
        SettingsStore(prefs).update { it.copy(widgetGlass = com.prayerwallpaper.app.logic.GlassTint.DARK, widgetDarkGlassOpacity = 0.6f, widgetGlassOpacity = 0.2f) }
        val read = SettingsStore(prefs).read()
        assertEquals(com.prayerwallpaper.app.logic.GlassTint.DARK, read.widgetGlass)
        assertEquals(0.6f, read.glassOpacity, 0.0001f)
        assertEquals(0.2f, read.widgetGlassOpacity, 0.0001f)
    }

    @Test fun `an unknown glass tint reads as the white one`() {
        assertEquals(com.prayerwallpaper.app.logic.GlassTint.LIGHT, SettingsStore(FakePrefs(mapOf("widget_glass_tint" to "SMOKED"))).read().widgetGlass)
    }

    @Test fun `an out-of-range opacity or an unknown style is put right`() {
        val read = SettingsStore(FakePrefs(mapOf("widget_glass_opacity" to 7f, "widget_style" to "FROSTED", "widget_card" to "PURPLE"))).read()
        assertEquals(1f, read.widgetGlassOpacity, 0f)
        assertEquals(com.prayerwallpaper.app.logic.WidgetStyle.GLASS, read.widgetStyle)
        assertEquals(com.prayerwallpaper.app.logic.CardStyle.AUTO, read.widgetCard)
    }

    @Test fun `settings from before widgets existed have the defaults`() {
        val read = SettingsStore(FakePrefs(mapOf("mode" to "SOLID", "access_asked" to true))).read()
        assertEquals(com.prayerwallpaper.app.logic.WidgetStyle.GLASS, read.widgetStyle)
        assertTrue(read.widgetFollowAccess)
    }
}
