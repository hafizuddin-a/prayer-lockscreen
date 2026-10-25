package com.prayerwallpaper.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import com.prayerwallpaper.app.logic.mixColors
import com.prayerwallpaper.app.logic.relativeLuminance
import com.prayerwallpaper.app.logic.theme
import com.prayerwallpaper.app.logic.widgetTextMultiplier
import com.prayerwallpaper.app.widgets.NextPrayerWidget
import com.prayerwallpaper.app.widgets.PaintMeasure
import com.prayerwallpaper.app.widgets.WIDGET_PROVIDERS
import java.io.File
import java.util.Locale

/**
 * Debug builds only. Draws a widget's real RemoteViews at any size, font scale, locale and clock style into a PNG, so every size
 * can be checked without resizing on a launcher:
 *
 *   am start -n com.prayerwallpaper.app/.DebugClockActivity --es widgetshot next --ei w 146 --ei h 72 --ef fontscale 1.3
 *       [--es lang ar] [--ez h24 true] [--es bgcolor 0f2a53] --es out name --ez finish true
 *   adb exec-out run-as com.prayerwallpaper.app cat files/widgetshots/name.png > name.png
 */
object WidgetDebug {
    /**
     * Measures how Android delivers inexact alarms, for choosing how the widgets set theirs: three alarms for the same moment [minutes] ahead,
     * A with setAndAllowWhileIdle, B with setWindow(start at the moment, 10 minutes) and C with a window that ends at the moment. Each, when
     * delivered, is logged by the widget receiver (logcat tag WidgetReceiver). With [dump] they are set 60 minutes ahead for `dumpsys alarm`.
     */
    fun alarmTest(app: PrayerApp, minutes: Int) {
        val alarms = app.getSystemService(android.app.AlarmManager::class.java)
        val target = System.currentTimeMillis() + minutes * 60_000L
        fun pi(code: Int, action: String) = android.app.PendingIntent.getBroadcast(
            app, 100 + code, android.content.Intent(app, com.prayerwallpaper.app.widgets.WidgetReceiver::class.java).setAction(action),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarms.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, target, pi(1, "TEST_A_ALLOW_WHILE_IDLE"))
        alarms.setWindow(android.app.AlarmManager.RTC_WAKEUP, target, 10 * 60_000L, pi(2, "TEST_B_WINDOW_FROM_T"))
        val windowStart = maxOf(System.currentTimeMillis(), target - 10 * 60_000L)
        alarms.setWindow(android.app.AlarmManager.RTC_WAKEUP, windowStart, target - windowStart, pi(3, "TEST_C_WINDOW_TO_T"))
        alarms.setWindow(android.app.AlarmManager.RTC_WAKEUP, target, 60_000L, pi(4, "TEST_D_WINDOW_1MIN"))
        alarms.setWindow(android.app.AlarmManager.RTC_WAKEUP, target, 5 * 60_000L, pi(5, "TEST_E_WINDOW_5MIN"))
        Log.i("WidgetDebug", "alarm test: target=${java.time.Instant.ofEpochMilli(target)} (A idle, B window from T, C window to T)")
    }

    private fun chronometers(view: View): List<android.widget.Chronometer> =
        if (view is android.widget.Chronometer) listOf(view) else (view as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).flatMap { chronometers(g.getChildAt(it)) } }.orEmpty()

    private val kinds: Map<String, Class<*>> = mapOf("next" to NextPrayerWidget::class.java, "today" to com.prayerwallpaper.app.widgets.TodayTimesWidget::class.java, "strip" to com.prayerwallpaper.app.widgets.PrayerStripWidget::class.java)

    fun shot(app: PrayerApp, context: Context, kind: String, widthDp: Float, heightDp: Float, fontScale: Float?, lang: String?, h24: Boolean?, bgColor: Int, out: String): File? {
        val provider = kinds[kind] ?: WIDGET_PROVIDERS.firstOrNull { it.simpleName.startsWith(kind, ignoreCase = true) } ?: return null
        val base = app.widgets.environment()
        val cfg = Configuration(app.resources.configuration)
        fontScale?.let { cfg.fontScale = it }
        lang?.let { cfg.setLocale(Locale.forLanguageTag(it)); cfg.setLayoutDirection(Locale.forLanguageTag(it)) }
        val ctx = app.createConfigurationContext(cfg)
        val scale = cfg.fontScale
        val env = base.copy(
            context = ctx, fontScale = scale, textMultiplier = widgetTextMultiplier(base.look.textSize, scale),
            is24Hour = h24 ?: base.is24Hour, locale = cfg.locales[0],
            theme = base.look.theme(bgColor, relativeLuminance(bgColor)), measure = PaintMeasure(ctx.resources.displayMetrics.density),
        )
        val views = app.widgets.build(provider, env, SizeF(widthDp, heightDp)) ?: return null
        val density = ctx.resources.displayMetrics.density
        val wPx = (widthDp * density).toInt()
        val hPx = (heightDp * density).toInt()
        val parent = FrameLayout(ctx)
        // A launcher attaches the widget to a window, which gives it the language's direction. This view is not attached, so say it.
        parent.layoutDirection = if (android.text.TextUtils.getLayoutDirectionFromLocale(cfg.locales[0]) == View.LAYOUT_DIRECTION_RTL) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val view = views.apply(ctx, parent)
        parent.addView(view, FrameLayout.LayoutParams(wPx, hPx))
        // A chronometer that is not attached never writes its first text with the format, so do it by hand.
        chronometers(view).forEach { it.base = it.base }
        // A little margin of "wallpaper" around the widget, as a launcher leaves, so the glass edge shows.
        val margin = (8 * density).toInt()
        val canvasW = wPx + 2 * margin
        val canvasH = hPx + 2 * margin
        parent.measure(View.MeasureSpec.makeMeasureSpec(wPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(hPx, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, wPx, hPx)
        val bitmap = Bitmap.createBitmap(canvasW, canvasH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, canvasH.toFloat(), bgColor, mixColors(bgColor, 0xFF000000.toInt(), 0.35f), Shader.TileMode.CLAMP) })
        canvas.translate(margin.toFloat(), margin.toFloat())
        parent.draw(canvas)
        val dir = File(app.filesDir, "widgetshots").apply { mkdirs() }
        val file = File(dir, "$out.png")
        // Written under another name and renamed, so a script that waits for the file never reads half of it.
        val partial = File(dir, "$out.png.part")
        partial.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        partial.renameTo(file)
        Log.i("WidgetDebug", "shot $kind ${widthDp}x$heightDp fontScale=$scale lang=${cfg.locales[0]} -> ${file.name} (${canvasW}x$canvasH px)")
        return file
    }
}
