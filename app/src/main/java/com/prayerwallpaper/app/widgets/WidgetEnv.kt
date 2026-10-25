package com.prayerwallpaper.app.widgets

import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import com.prayerwallpaper.app.MainActivity
import com.prayerwallpaper.app.R
import com.prayerwallpaper.app.data.Prayer
import com.prayerwallpaper.app.logic.TextMeasure
import com.prayerwallpaper.app.logic.WidgetLook
import com.prayerwallpaper.app.logic.WidgetState
import com.prayerwallpaper.app.logic.WidgetTheme
import com.prayerwallpaper.app.logic.relativeLuminance
import java.time.Instant
import java.util.Locale

/**
 * Everything a widget's RemoteViews are built from, read once per update so every widget of one update agrees: the state, the look
 * and its colours, the phone's font scale and clock style, and the intent that opens the app.
 */
data class WidgetEnv(
    val context: Context,
    val now: Instant,
    /** `SystemClock.elapsedRealtime()` at the same moment as [now], the base the launcher's chronometer counts from. */
    val elapsed: Long,
    val state: WidgetState,
    val look: WidgetLook,
    val theme: WidgetTheme,
    val fontScale: Float,
    val textMultiplier: Float,
    val is24Hour: Boolean,
    val locale: Locale,
    val openApp: PendingIntent,
    val measure: TextMeasure,
)

/** What is behind the widgets: the home screen wallpaper's mean colour and brightness. */
class Backdrop(val color: Int, val luminance: Float) {
    companion object {
        /** The default navy the app itself starts with, when the system gives no colours. */
        val Default = Backdrop(0xFF0F2A53.toInt(), relativeLuminance(0xFF0F2A53.toInt()))

        /**
         * Android's own reading of the home screen wallpaper (no permission needed): its dominant colours. Their mean brightness
         * decides, with the lock screen's own threshold, whether text on the glass is light or dark.
         */
        fun read(context: Context): Backdrop {
            val colors = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return Default
            val list = listOfNotNull(colors.primaryColor, colors.secondaryColor, colors.tertiaryColor).map { it.toArgb() }
            if (list.isEmpty()) return Default
            return Backdrop(list.first(), list.map(::relativeLuminance).average().toFloat())
        }
    }
}

/** Measures text with a Paint, as the launcher's own TextView will draw it, with a small margin for other system fonts. */
class PaintMeasure(private val density: Float) : TextMeasure {
    private val regular = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }

    override fun widthDp(text: String, sizeDp: Float, bold: Boolean): Float {
        val paint = if (bold) this.bold else regular
        paint.textSize = sizeDp * density
        return paint.measureText(text) / density * FONT_MARGIN
    }

    private companion object {
        /** A different system font can be a little wider than the default one. */
        const val FONT_MARGIN = 1.06f
    }
}

fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
)

fun prayerIcon(prayer: Prayer): Int = when (prayer) {
    Prayer.SUBUH -> R.drawable.ic_prayer_subuh
    Prayer.SYURUK -> R.drawable.ic_prayer_syuruk
    Prayer.ZOHOR -> R.drawable.ic_prayer_zohor
    Prayer.ASAR -> R.drawable.ic_prayer_asar
    Prayer.MAGHRIB -> R.drawable.ic_prayer_maghrib
    Prayer.ISYAK -> R.drawable.ic_prayer_isyak
}

fun RemoteViews.tint(viewId: Int, color: Int) = setColorStateList(viewId, "setImageTintList", ColorStateList.valueOf(color))

/** Paints the glass or card behind a widget: its fill, with the opacity in the colour's alpha, and its hairline edge. */
fun RemoteViews.background(env: WidgetEnv) {
    tint(R.id.bg_fill, env.theme.fill)
    tint(R.id.bg_border, env.theme.border)
}

fun RemoteViews.visible(viewId: Int, show: Boolean) = setViewVisibility(viewId, if (show) View.VISIBLE else View.GONE)

/** The message every widget shows when there is nothing to show. A tap still opens the app. */
fun messageView(env: WidgetEnv, text: String): RemoteViews = RemoteViews(env.context.packageName, R.layout.widget_message).apply {
    background(env)
    setTextViewText(R.id.message, text)
    setTextColor(R.id.message, env.theme.text)
    setContentDescription(R.id.widget_root, text)
    setOnClickPendingIntent(R.id.widget_root, env.openApp)
}

/** [text] in bold when the accessibility "bold text" option is on; otherwise as it is. A span, so the one TextView can be either. */
fun boldIf(text: String, bold: Boolean): CharSequence =
    if (!bold) text else android.text.SpannableString(text).apply { setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, length, 0) }

/** Sets a view's width and height in dp (API 31), for the icons whose size follows the text. */
fun RemoteViews.size(viewId: Int, dp: Float) {
    setViewLayoutWidth(viewId, dp, android.util.TypedValue.COMPLEX_UNIT_DIP)
    setViewLayoutHeight(viewId, dp, android.util.TypedValue.COMPLEX_UNIT_DIP)
}

fun RemoteViews.hidden(viewId: Int) = setViewVisibility(viewId, View.INVISIBLE)
