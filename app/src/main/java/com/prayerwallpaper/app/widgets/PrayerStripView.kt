package com.prayerwallpaper.app.widgets

import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import com.prayerwallpaper.app.R
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.WidgetEntry
import com.prayerwallpaper.app.logic.WidgetSpoken
import com.prayerwallpaper.app.logic.WidgetState
import com.prayerwallpaper.app.logic.WidgetWords
import com.prayerwallpaper.app.logic.formatWidgetTime
import com.prayerwallpaper.app.logic.planStrip
import com.prayerwallpaper.app.logic.windowFromCurrent

/**
 * Widget 3, all six in a row: six equal columns of icon, name and time in the compact style (`4:15 p`), the current one marked. In
 * large text, or when six do not fit, three columns: the current one and the next two. No Hijri line and no countdown.
 */
object PrayerStripView {
    fun build(env: WidgetEnv, size: SizeF): RemoteViews {
        val state = env.state
        if (state is WidgetState.Missing) return messageView(env, WidgetWords.missing(state.reason))
        state as WidgetState.Ready

        val compactTimes = state.entries.map { formatWidgetTime(it.time, env.is24Hour, env.locale, compact = true).text }
        val plan = planStrip(
            size.width, size.height, env.fontScale, env.textMultiplier, three = env.look.textSize != TextSize.STANDARD,
            labels = state.entries.map { it.label }, times = compactTimes, measure = env.measure,
        )
        val marked = state.marked()
        val shown = if (plan.columns == 6) state.entries else windowFromCurrent(state, plan.columns)

        return RemoteViews(env.context.packageName, R.layout.widget_strip).apply {
            background(env)
            removeAllViews(R.id.strip)
            for (entry in shown) addView(R.id.strip, column(env, entry, entry.prayer == marked, plan.textSp, plan.showIcon, plan.iconDp, plan.showName))
            setOnClickPendingIntent(R.id.widget_root, env.openApp)
        }
    }

    private fun column(env: WidgetEnv, entry: WidgetEntry, current: Boolean, textSp: Float, showIcon: Boolean, iconDp: Float, showName: Boolean): RemoteViews {
        val theme = env.theme
        val compact = formatWidgetTime(entry.time, env.is24Hour, env.locale, compact = true)
        val spoken = formatWidgetTime(entry.time, env.is24Hour, env.locale)
        val bold = env.look.bold
        return RemoteViews(env.context.packageName, if (current) R.layout.widget_col_current else R.layout.widget_col).apply {
            val textColor = if (current) theme.pillText else theme.text
            visible(R.id.col_icon, showIcon)
            if (showIcon) {
                setImageViewResource(R.id.col_icon, prayerIcon(entry.prayer))
                tint(R.id.col_icon, if (current) theme.pillIcon else theme.icon)
                size(R.id.col_icon, iconDp)
            }
            visible(R.id.col_name, showName)
            setTextViewText(R.id.col_name, boldIf(entry.label, bold))
            setTextViewText(R.id.col_time, boldIf(compact.text, bold))
            setTextColor(R.id.col_name, textColor)
            setTextColor(R.id.col_time, textColor)
            setTextViewTextSize(R.id.col_name, TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextViewTextSize(R.id.col_time, TypedValue.COMPLEX_UNIT_SP, textSp)
            if (current) {
                tint(R.id.col_pill, theme.pill)
            }
            setContentDescription(R.id.col_root, WidgetSpoken.row(entry.label, spoken, current))
            setOnClickPendingIntent(R.id.col_root, env.openApp)
        }
    }
}
