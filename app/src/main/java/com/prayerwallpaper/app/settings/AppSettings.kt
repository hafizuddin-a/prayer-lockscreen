package com.prayerwallpaper.app.settings

import android.content.SharedPreferences
import com.prayerwallpaper.app.data.NameSet
import com.prayerwallpaper.app.data.Place
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.logic.AccessOptions
import com.prayerwallpaper.app.logic.AsrRule
import com.prayerwallpaper.app.logic.CardStyle
import com.prayerwallpaper.app.logic.CalcMethod
import com.prayerwallpaper.app.logic.CalcRequest
import com.prayerwallpaper.app.logic.DEFAULT_DARK_GLASS_OPACITY
import com.prayerwallpaper.app.logic.DEFAULT_GLASS_OPACITY
import com.prayerwallpaper.app.logic.GlassTint
import com.prayerwallpaper.app.logic.WidgetStyle
import com.prayerwallpaper.app.logic.HIJRI_DAY_ROLLS_AT_MAGHRIB
import com.prayerwallpaper.app.logic.HijriAlign
import com.prayerwallpaper.app.logic.MAX_ADJUSTMENT_MINUTES
import com.prayerwallpaper.app.logic.MAX_PHOTO_ZOOM
import com.prayerwallpaper.app.logic.MIN_PHOTO_ZOOM
import com.prayerwallpaper.app.logic.MethodDefaults
import com.prayerwallpaper.app.logic.PRE_REMINDER_CHOICES
import com.prayerwallpaper.app.logic.SystemAccessibility
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.toOptions
import com.prayerwallpaper.app.wallpaper.ListLayout
import java.time.DateTimeException
import java.time.ZoneId

/** What the list sits on. [title] is how the settings and the home screen's summary name it. */
enum class BackgroundMode(val title: String) {
    MATCH("Match my wallpaper"), PHOTO("Photo"), SOLID("Solid colour"), GRADIENT("Gradient"),
}

/**
 * Where the list's text colour comes from: tinted to suit the background; a colour the user picks; or the tones of
 * the system's Material You palette, which is how a Pixel draws its clock.
 */
enum class TextColorSource { BACKGROUND, CUSTOM, SYSTEM }

/** The three colours read from the user's wallpaper before this one replaced it. Just ints, no image. */
data class CapturedColors(val primary: Int, val secondary: Int, val tertiary: Int?)

/** Everything the user can change. The wallpaper engine reads this from app-private storage. */
data class AppSettings(
    val mode: BackgroundMode = BackgroundMode.GRADIENT,
    val captured: CapturedColors? = null,
    val captureSkipped: Boolean = false,
    /** 0 when no photo has been chosen, otherwise a stamp that changes whenever the photo does. */
    val photoVersion: Long = 0,
    /** How the photo is framed: zoom 1 to [MAX_PHOTO_ZOOM], and where it sits (0 to 1, 0.5 is centred). */
    val photoZoom: Float = 1f,
    val photoPanX: Float = 0.5f,
    val photoPanY: Float = 0.5f,
    val solid: Int = DEFAULT_SOLID,
    val gradientTop: Int = DEFAULT_GRADIENT_TOP,
    val gradientBottom: Int = DEFAULT_GRADIENT_BOTTOM,
    /** Black scrim over the background, 0 to [MAX_DIM]. */
    val dim: Float = 0f,
    /** Top of the prayer list as a fraction of the screen height. */
    val listTop: Float = ListLayout.DEFAULT_TOP,
    val textColors: TextColorSource = TextColorSource.BACKGROUND,
    /** Used when [textColors] is [TextColorSource.CUSTOM]. Opaque. */
    val customTextColor: Int = DEFAULT_CUSTOM_TEXT,
    /** The Hijri date line above the list. Drawn only for Singapore, whose MUIS calendar the dates come from. */
    val showHijri: Boolean = true,
    /** Whether the Hijri date moves on at Maghrib (true) or at midnight (false). */
    val hijriAtMaghrib: Boolean = HIJRI_DAY_ROLLS_AT_MAGHRIB,
    /** Whether the lines around the list, the Hijri date above it and the countdown under it, are centred or start at its left edge. */
    val hijriAlign: HijriAlign = HijriAlign.CENTER,
    /** Whether an Islamic event (Hari Raya, Nuzul Quran...) is named beside the Hijri date on its day. */
    val showHijriEvents: Boolean = true,
    /** The line under the list naming the next prayer and the time left, redrawn once a minute while the lock screen shows. */
    val showCountdown: Boolean = true,
    /**
     * Accessibility. While [followSystem] is on the three options below the list's text size, high contrast and bold are taken
     * from the phone's own settings and the stored choices are not used; changing one by hand turns it off. [cardStyle] is
     * always the user's own.
     */
    val followSystem: Boolean = true,
    val textSize: TextSize = TextSize.STANDARD,
    val highContrast: Boolean = false,
    val cardStyle: CardStyle = CardStyle.AUTO,
    val boldText: Boolean = false,
    /** Whether the one-time "Would you like larger text?" question has been shown. */
    val accessAsked: Boolean = false,
    /** Whether the first-run setup was finished. An update never shows it (see [com.prayerwallpaper.app.logic.shouldShowSetup]). */
    val setupDone: Boolean = false,
    /** Whether the app looks for a new version on GitHub about once a month and installs it when Android allows. Only builds with self-update have it. */
    val autoUpdate: Boolean = true,
    /** Notifications at prayer times. Off until the user turns them on, which starts the permission flow. */
    val remindersOn: Boolean = false,
    /** The prayers to remind about: all but Syuruk (sunrise is not a prayer) by default. */
    val reminderPrayers: Set<Prayer> = DEFAULT_REMINDER_PRAYERS,
    /** A heads-up this many minutes before each selected prayer, one of [PRE_REMINDER_CHOICES]; 0 is off. */
    val preReminderMinutes: Int = 0,
    /** Reminders vibrate but make no sound: they are posted on the silent channel. */
    val silentReminders: Boolean = false,
    /** Home screen widgets: glass over the wallpaper or a solid card, the card's colour, and how opaque the glass is (0 to 1). */
    val widgetStyle: WidgetStyle = WidgetStyle.GLASS,
    val widgetCard: CardStyle = CardStyle.AUTO,
    val widgetGlassOpacity: Float = DEFAULT_GLASS_OPACITY,
    /** The glass is a white tint (the default) or a dark one, which has its own opacity so switching back and forth keeps each. */
    val widgetGlass: GlassTint = GlassTint.LIGHT,
    val widgetDarkGlassOpacity: Float = DEFAULT_DARK_GLASS_OPACITY,
    /** The widgets take the lock screen's text size, bold text and high contrast (which means the card style). */
    val widgetFollowAccess: Boolean = true,
    /** Where the times are for. Singapore uses the official MUIS timetable; every other place is calculated. */
    val place: Place = Place.SINGAPORE,
    /** The user's own choices for a calculated place. Null means the default for the place's country. */
    val method: CalcMethod? = null,
    val asr: AsrRule? = null,
    val names: NameSet? = null,
    /** Minutes to add to each prayer, in [com.prayerwallpaper.app.data.Prayer] order. */
    val adjustments: List<Int> = NO_ADJUSTMENTS,
) {
    /** The opacity of the glass in force: that of the tint chosen. */
    val glassOpacity: Float get() = if (widgetGlass == GlassTint.DARK) widgetDarkGlassOpacity else widgetGlassOpacity

    val effectiveMethod: CalcMethod get() = method ?: MethodDefaults.forCountry(place.country).method
    val effectiveAsr: AsrRule get() = asr ?: MethodDefaults.forCountry(place.country).asr
    val effectiveNames: NameSet get() = names ?: MethodDefaults.namesForCountry(place.country)

    /** The accessibility options to draw with: the phone's own settings while following them, otherwise the user's choices. */
    fun accessOptions(system: SystemAccessibility): AccessOptions =
        if (followSystem) system.toOptions(cardStyle) else AccessOptions(textSize, highContrast, cardStyle, boldText)

    fun calcRequest() = CalcRequest(place.latitude, place.longitude, place.zone, effectiveMethod, effectiveAsr, adjustments)

    /**
     * Moving to another place. The method and the minute adjustments belong to the place they were chosen for,
     * so they start again from that country's defaults. The Asr rule and the names are the user's own
     * preference and stay.
     */
    fun withPlace(next: Place) = copy(place = next, method = null, adjustments = NO_ADJUSTMENTS)

    companion object {
        val NO_ADJUSTMENTS = List(6) { 0 }
        val DEFAULT_REMINDER_PRAYERS: Set<Prayer> = Prayer.entries.filter { it != Prayer.SYURUK }.toSet()
        const val DEFAULT_CUSTOM_TEXT = 0xFFFFFFFF.toInt()
        const val MAX_DIM = 0.6f
        const val MIN_LIST_TOP = 0.05f
        const val MAX_LIST_TOP = 0.85f
        const val DEFAULT_SOLID = 0xFF0F2A53.toInt()
        const val DEFAULT_GRADIENT_TOP = 0xFF5B5E9E.toInt()
        const val DEFAULT_GRADIENT_BOTTOM = 0xFF0A0E2C.toInt()
    }
}

/** SharedPreferences in app-private storage. Both the settings screen and the engine share one process. */
class SettingsStore(private val prefs: SharedPreferences) {
    fun read(): AppSettings {
        val d = AppSettings()
        val captured = if (prefs.getBoolean(CAPTURED, false)) {
            CapturedColors(
                prefs.getInt(CAP_PRIMARY, 0),
                prefs.getInt(CAP_SECONDARY, 0),
                if (prefs.getBoolean(CAP_HAS_TERTIARY, false)) prefs.getInt(CAP_TERTIARY, 0) else null,
            )
        } else {
            null
        }
        return AppSettings(
            mode = prefs.getString(MODE, null)?.let { name -> BackgroundMode.entries.firstOrNull { it.name == name } }
                // "Match my wallpaper" is no longer offered: whoever had it gets the gradient.
                ?.takeIf { it != BackgroundMode.MATCH } ?: d.mode,
            captured = captured,
            captureSkipped = prefs.getBoolean(CAPTURE_SKIPPED, false),
            photoVersion = prefs.getLong(PHOTO_VERSION, 0),
            photoZoom = prefs.getFloat(PHOTO_ZOOM, d.photoZoom).coerceIn(MIN_PHOTO_ZOOM, MAX_PHOTO_ZOOM),
            photoPanX = prefs.getFloat(PHOTO_PAN_X, d.photoPanX).coerceIn(0f, 1f),
            photoPanY = prefs.getFloat(PHOTO_PAN_Y, d.photoPanY).coerceIn(0f, 1f),
            solid = prefs.getInt(SOLID, d.solid),
            gradientTop = prefs.getInt(GRADIENT_TOP, d.gradientTop),
            gradientBottom = prefs.getInt(GRADIENT_BOTTOM, d.gradientBottom),
            dim = prefs.getFloat(DIM, d.dim).coerceIn(0f, AppSettings.MAX_DIM),
            listTop = prefs.getFloat(LIST_TOP, d.listTop).coerceIn(AppSettings.MIN_LIST_TOP, AppSettings.MAX_LIST_TOP),
            // "PLAIN" (white) was an earlier name for what is now CUSTOM with the default colour, which is white.
            textColors = prefs.getString(TEXT_COLORS, null)?.let { name -> TextColorSource.entries.firstOrNull { it.name == name } ?: if (name == "PLAIN") TextColorSource.CUSTOM else null } ?: d.textColors,
            customTextColor = prefs.getInt(CUSTOM_TEXT_COLOR, d.customTextColor) or (0xFF shl 24),
            showHijri = prefs.getBoolean(SHOW_HIJRI, d.showHijri),
            hijriAtMaghrib = prefs.getBoolean(HIJRI_AT_MAGHRIB, d.hijriAtMaghrib),
            hijriAlign = prefs.getString(HIJRI_ALIGN, null)?.let { name -> HijriAlign.entries.firstOrNull { it.name == name } } ?: d.hijriAlign,
            showHijriEvents = prefs.getBoolean(SHOW_HIJRI_EVENTS, d.showHijriEvents),
            showCountdown = prefs.getBoolean(SHOW_COUNTDOWN, d.showCountdown),
            followSystem = prefs.getBoolean(ACCESS_FOLLOW_SYSTEM, d.followSystem),
            textSize = prefs.getString(ACCESS_TEXT_SIZE, null)?.let { name -> TextSize.entries.firstOrNull { it.name == name } } ?: d.textSize,
            highContrast = prefs.getBoolean(ACCESS_HIGH_CONTRAST, d.highContrast),
            cardStyle = prefs.getString(ACCESS_CARD_STYLE, null)?.let { name -> CardStyle.entries.firstOrNull { it.name == name } } ?: d.cardStyle,
            boldText = prefs.getBoolean(ACCESS_BOLD, d.boldText),
            accessAsked = prefs.getBoolean(ACCESS_ASKED, d.accessAsked),
            setupDone = prefs.getBoolean(SETUP_DONE, d.setupDone),
            autoUpdate = prefs.getBoolean(AUTO_UPDATE, d.autoUpdate),
            remindersOn = prefs.getBoolean(REMINDERS_ON, d.remindersOn),
            reminderPrayers = readReminderPrayers() ?: d.reminderPrayers,
            preReminderMinutes = prefs.getInt(PRE_REMINDER_MINUTES, d.preReminderMinutes).takeIf { it in PRE_REMINDER_CHOICES } ?: d.preReminderMinutes,
            silentReminders = prefs.getBoolean(REMINDERS_SILENT, d.silentReminders),
            widgetStyle = prefs.getString(WIDGET_STYLE, null)?.let { name -> WidgetStyle.entries.firstOrNull { it.name == name } } ?: d.widgetStyle,
            widgetCard = prefs.getString(WIDGET_CARD, null)?.let { name -> CardStyle.entries.firstOrNull { it.name == name } } ?: d.widgetCard,
            widgetGlassOpacity = prefs.getFloat(WIDGET_GLASS_OPACITY, d.widgetGlassOpacity).coerceIn(0f, 1f),
            widgetGlass = prefs.getString(WIDGET_GLASS, null)?.let { name -> GlassTint.entries.firstOrNull { it.name == name } } ?: d.widgetGlass,
            widgetDarkGlassOpacity = prefs.getFloat(WIDGET_DARK_GLASS_OPACITY, d.widgetDarkGlassOpacity).coerceIn(0f, 1f),
            widgetFollowAccess = prefs.getBoolean(WIDGET_FOLLOW_ACCESS, d.widgetFollowAccess),
            place = readPlace() ?: d.place,
            method = prefs.getString(METHOD, null)?.let { name -> CalcMethod.entries.firstOrNull { it.name == name } },
            asr = prefs.getString(ASR, null)?.let { name -> AsrRule.entries.firstOrNull { it.name == name } },
            names = prefs.getString(NAMES, null)?.let { name -> NameSet.entries.firstOrNull { it.name == name } },
            adjustments = readAdjustments(),
        )
    }

    /** Null when no place was ever chosen, or what is stored cannot be used (the caller then keeps Singapore). */
    private fun readPlace(): Place? {
        if (!prefs.contains(PLACE_ID)) return null
        val zone = prefs.getString(PLACE_ZONE, null) ?: return null
        try {
            ZoneId.of(zone)
        } catch (_: DateTimeException) {
            return null
        }
        val lat = Double.fromBits(prefs.getLong(PLACE_LAT, 0))
        val lon = Double.fromBits(prefs.getLong(PLACE_LON, 0))
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Place(
            prefs.getLong(PLACE_ID, 0),
            prefs.getString(PLACE_NAME, null) ?: return null,
            prefs.getString(PLACE_REGION, "").orEmpty(),
            prefs.getString(PLACE_COUNTRY, null) ?: return null,
            lat, lon, zone,
        )
    }

    /** Null when never chosen. An empty choice is kept as empty: the user may switch every prayer off. Unknown names are ignored. */
    private fun readReminderPrayers(): Set<Prayer>? {
        val stored = prefs.getString(REMINDER_PRAYERS, null) ?: return null
        val names = stored.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return Prayer.entries.filter { it.name in names }.toSet()
    }

    private fun readAdjustments(): List<Int> {
        val parts = prefs.getString(ADJUSTMENTS, null)?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
        return if (parts != null && parts.size == 6) parts.map { it.coerceIn(-MAX_ADJUSTMENT_MINUTES, MAX_ADJUSTMENT_MINUTES) } else AppSettings.NO_ADJUSTMENTS
    }

    /** [sync] blocks until the write is on disk. Only scripted tests need it; the UI uses the default. */
    @android.annotation.SuppressLint("ApplySharedPref")
    fun write(settings: AppSettings, sync: Boolean = false) {
        val editor = prefs.edit().apply {
            putString(MODE, settings.mode.name)
            putBoolean(CAPTURED, settings.captured != null)
            settings.captured?.let {
                putInt(CAP_PRIMARY, it.primary)
                putInt(CAP_SECONDARY, it.secondary)
                putBoolean(CAP_HAS_TERTIARY, it.tertiary != null)
                putInt(CAP_TERTIARY, it.tertiary ?: 0)
            }
            putBoolean(CAPTURE_SKIPPED, settings.captureSkipped)
            putLong(PHOTO_VERSION, settings.photoVersion)
            putFloat(PHOTO_ZOOM, settings.photoZoom.coerceIn(MIN_PHOTO_ZOOM, MAX_PHOTO_ZOOM))
            putFloat(PHOTO_PAN_X, settings.photoPanX.coerceIn(0f, 1f))
            putFloat(PHOTO_PAN_Y, settings.photoPanY.coerceIn(0f, 1f))
            putInt(SOLID, settings.solid)
            putInt(GRADIENT_TOP, settings.gradientTop)
            putInt(GRADIENT_BOTTOM, settings.gradientBottom)
            putFloat(DIM, settings.dim.coerceIn(0f, AppSettings.MAX_DIM))
            putFloat(LIST_TOP, settings.listTop.coerceIn(AppSettings.MIN_LIST_TOP, AppSettings.MAX_LIST_TOP))
            putString(TEXT_COLORS, settings.textColors.name)
            putInt(CUSTOM_TEXT_COLOR, settings.customTextColor or (0xFF shl 24))
            putBoolean(SHOW_HIJRI, settings.showHijri)
            putBoolean(HIJRI_AT_MAGHRIB, settings.hijriAtMaghrib)
            putString(HIJRI_ALIGN, settings.hijriAlign.name)
            putBoolean(SHOW_HIJRI_EVENTS, settings.showHijriEvents)
            putBoolean(SHOW_COUNTDOWN, settings.showCountdown)
            putBoolean(ACCESS_FOLLOW_SYSTEM, settings.followSystem)
            putString(ACCESS_TEXT_SIZE, settings.textSize.name)
            putBoolean(ACCESS_HIGH_CONTRAST, settings.highContrast)
            putString(ACCESS_CARD_STYLE, settings.cardStyle.name)
            putBoolean(ACCESS_BOLD, settings.boldText)
            putBoolean(ACCESS_ASKED, settings.accessAsked)
            putBoolean(SETUP_DONE, settings.setupDone)
            putBoolean(AUTO_UPDATE, settings.autoUpdate)
            putBoolean(REMINDERS_ON, settings.remindersOn)
            putString(REMINDER_PRAYERS, Prayer.entries.filter { it in settings.reminderPrayers }.joinToString(",") { it.name })
            putInt(PRE_REMINDER_MINUTES, settings.preReminderMinutes.takeIf { it in PRE_REMINDER_CHOICES } ?: 0)
            putBoolean(REMINDERS_SILENT, settings.silentReminders)
            putString(WIDGET_STYLE, settings.widgetStyle.name)
            putString(WIDGET_CARD, settings.widgetCard.name)
            putFloat(WIDGET_GLASS_OPACITY, settings.widgetGlassOpacity.coerceIn(0f, 1f))
            putString(WIDGET_GLASS, settings.widgetGlass.name)
            putFloat(WIDGET_DARK_GLASS_OPACITY, settings.widgetDarkGlassOpacity.coerceIn(0f, 1f))
            putBoolean(WIDGET_FOLLOW_ACCESS, settings.widgetFollowAccess)
            settings.place.let {
                putLong(PLACE_ID, it.id)
                putString(PLACE_NAME, it.name)
                putString(PLACE_REGION, it.region)
                putString(PLACE_COUNTRY, it.country)
                putLong(PLACE_LAT, it.latitude.toRawBits())
                putLong(PLACE_LON, it.longitude.toRawBits())
                putString(PLACE_ZONE, it.zoneId)
            }
            putOrRemove(METHOD, settings.method?.name)
            putOrRemove(ASR, settings.asr?.name)
            putOrRemove(NAMES, settings.names?.name)
            putString(ADJUSTMENTS, settings.adjustments.joinToString(","))
        }
        if (sync) editor.commit() else editor.apply()
    }

    inline fun update(sync: Boolean = false, change: (AppSettings) -> AppSettings) = write(change(read()), sync)

    /** Listeners are held weakly by SharedPreferences, so the caller must keep a reference. */
    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.registerOnSharedPreferenceChangeListener(listener)

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) = prefs.unregisterOnSharedPreferenceChangeListener(listener)

    private companion object {
        const val MODE = "mode"
        const val CAPTURED = "captured"
        const val CAP_PRIMARY = "cap_primary"
        const val CAP_SECONDARY = "cap_secondary"
        const val CAP_TERTIARY = "cap_tertiary"
        const val CAP_HAS_TERTIARY = "cap_has_tertiary"
        const val CAPTURE_SKIPPED = "capture_skipped"
        const val PHOTO_VERSION = "photo_version"
        const val PHOTO_ZOOM = "photo_zoom"
        const val PHOTO_PAN_X = "photo_pan_x"
        const val PHOTO_PAN_Y = "photo_pan_y"
        const val SOLID = "solid"
        const val GRADIENT_TOP = "gradient_top"
        const val GRADIENT_BOTTOM = "gradient_bottom"
        const val DIM = "dim"
        const val LIST_TOP = "list_top"
        const val TEXT_COLORS = "text_colors"
        const val CUSTOM_TEXT_COLOR = "custom_text_color"
        const val SHOW_HIJRI = "show_hijri"
        const val HIJRI_AT_MAGHRIB = "hijri_at_maghrib"
        const val HIJRI_ALIGN = "hijri_align"
        const val SHOW_HIJRI_EVENTS = "show_hijri_events"
        const val SHOW_COUNTDOWN = "show_countdown"
        const val ACCESS_FOLLOW_SYSTEM = "access_follow_system"
        const val ACCESS_TEXT_SIZE = "access_text_size"
        const val ACCESS_HIGH_CONTRAST = "access_high_contrast"
        const val ACCESS_CARD_STYLE = "access_card_style"
        const val ACCESS_BOLD = "access_bold"
        const val ACCESS_ASKED = "access_asked"
        const val SETUP_DONE = "setup_done"
        const val AUTO_UPDATE = "auto_update"
        const val REMINDERS_ON = "reminders_on"
        const val REMINDER_PRAYERS = "reminder_prayers"
        const val PRE_REMINDER_MINUTES = "pre_reminder_minutes"
        const val REMINDERS_SILENT = "reminders_silent"
        const val WIDGET_STYLE = "widget_style"
        const val WIDGET_CARD = "widget_card"
        const val WIDGET_GLASS_OPACITY = "widget_glass_opacity"
        const val WIDGET_GLASS = "widget_glass_tint"
        const val WIDGET_DARK_GLASS_OPACITY = "widget_dark_glass_opacity"
        const val WIDGET_FOLLOW_ACCESS = "widget_follow_access"
        const val PLACE_ID = "place_id"
        const val PLACE_NAME = "place_name"
        const val PLACE_REGION = "place_region"
        const val PLACE_COUNTRY = "place_country"
        const val PLACE_LAT = "place_lat"
        const val PLACE_LON = "place_lon"
        const val PLACE_ZONE = "place_zone"
        const val METHOD = "calc_method"
        const val ASR = "asr_rule"
        const val NAMES = "name_set"
        const val ADJUSTMENTS = "adjustments"

        fun SharedPreferences.Editor.putOrRemove(key: String, value: String?) = if (value == null) remove(key) else putString(key, value)
    }
}
