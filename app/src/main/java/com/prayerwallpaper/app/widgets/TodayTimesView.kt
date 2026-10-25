package com.prayerwallpaper.app.widgets

import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import com.prayerwallpaper.app.R
import com.prayerwallpaper.app.logic.WidgetEntry
import com.prayerwallpaper.app.logic.WidgetSpoken
import com.prayerwallpaper.app.logic.WidgetState
import com.prayerwallpaper.app.logic.WidgetWords
import com.prayerwallpaper.app.logic.TextSize
import com.prayerwallpaper.app.logic.chronometerBase
import com.prayerwallpaper.app.logic.formatWidgetTime
import com.prayerwallpaper.app.logic.planToday
import com.prayerwallpaper.app.logic.windowFromCurrent

/**
 * Widget 2, today's times: the Hijri date on top, the six entries in two columns (or one, for large text or a narrow widget) with
 * the current one marked, and a footer naming the next prayer with a live countdown. The list changes only at midnight.
 */
object TodayTimesView {
    fun build(env: WidgetEnv, size: SizeF): RemoteViews {
        val state = env.state
        if (state is WidgetState.Missing) return messageView(env, WidgetWords.missing(state.reason))
        state as WidgetState.Ready

        val next = state.next
        fun timeText(entry: WidgetEntry) = formatWidgetTime(entry.time, env.is24Hour, env.locale).text
        val header = state.hijri?.text
        val footerWorst = next?.let { "${it.entry.label}${if (it.tomorrow) " tomorrow" else ""} in 20:00:00" }
        val plan = planToday(
            size.width, size.height, env.fontScale, env.textMultiplier, wantSingle = env.look.textSize != TextSize.STANDARD,
            dot = env.theme.dot, header = header, footerWorst = footerWorst,
            rowWords = { rows -> (if (rows < state.entries.size) windowFromCurrent(state, rows) else state.entries).map { it.label to timeText(it) } },
            measure = env.measure,
        )

        val marked = state.marked()
        val shown = if (plan.rows < state.entries.size) windowFromCurrent(state, plan.rows) else state.entries
        val perColumn = if (plan.singleColumn) shown.size else (shown.size + 1) / 2

        return RemoteViews(env.context.packageName, R.layout.widget_today).apply {
            background(env)
            val theme = env.theme

            visible(R.id.today_header, plan.showHeader)
            if (plan.showHeader) {
                setTextViewText(R.id.today_header, header)
                setTextColor(R.id.today_header, theme.text)
                setTextViewTextSize(R.id.today_header, TypedValue.COMPLEX_UNIT_SP, plan.headerSp)
            }

            removeAllViews(R.id.col_left)
            removeAllViews(R.id.col_right)
            visible(R.id.col_right, !plan.singleColumn)
            shown.forEachIndexed { i, entry ->
                val column = if (plan.singleColumn || i < perColumn) R.id.col_left else R.id.col_right
                addView(column, row(env, entry, entry.prayer == marked, plan.textSp, plan.showIcon, plan.iconDp))
            }

            visible(R.id.today_footer, plan.showFooter)
            if (plan.showFooter && next != null) {
                setChronometerCountDown(R.id.today_footer, true)
                setChronometer(R.id.today_footer, chronometerBase(env.now, next.entry.at, env.elapsed), WidgetWords.nextFooterFormat(next.entry.label, next.tomorrow), true)
                setTextColor(R.id.today_footer, theme.dimText)
                setTextViewTextSize(R.id.today_footer, TypedValue.COMPLEX_UNIT_SP, plan.footerSp)
                setOnClickPendingIntent(R.id.today_footer, env.openApp)
            }
            // What a screen reader says for the widget as a whole: the Hijri date and the next prayer, never the countdown (it would be wrong a
            // moment later). Each row has its own description and is its own stop.
            val nextSpoken = next?.let { WidgetSpoken.footer(it, formatWidgetTime(it.entry.time, env.is24Hour, env.locale)) }
            setContentDescription(R.id.widget_root, listOfNotNull(header, nextSpoken).joinToString(". ").ifEmpty { null })
            setOnClickPendingIntent(R.id.widget_root, env.openApp)
        }
    }

    private fun row(env: WidgetEnv, entry: WidgetEntry, current: Boolean, textSp: Float, showIcon: Boolean, iconDp: Float): RemoteViews {
        val theme = env.theme
        val time = formatWidgetTime(entry.time, env.is24Hour, env.locale)
        val bold = env.look.bold
        return RemoteViews(env.context.packageName, if (current) R.layout.widget_row_current else R.layout.widget_row).apply {
            val textColor = if (current) theme.pillText else theme.text
            visible(R.id.row_icon, showIcon)
            if (showIcon) {
                setImageViewResource(R.id.row_icon, prayerIcon(entry.prayer))
                tint(R.id.row_icon, if (current) theme.pillIcon else theme.icon)
                size(R.id.row_icon, iconDp)
            }
            setTextViewText(R.id.row_name, boldIf(entry.label, bold))
            setTextViewText(R.id.row_time, boldIf(time.text, bold))
            setTextColor(R.id.row_name, textColor)
            setTextColor(R.id.row_time, textColor)
            setTextViewTextSize(R.id.row_name, TypedValue.COMPLEX_UNIT_SP, textSp)
            setTextViewTextSize(R.id.row_time, TypedValue.COMPLEX_UNIT_SP, textSp)
            if (current) {
                tint(R.id.row_pill, theme.pill)
                if (theme.dot) {
                    visible(R.id.row_dot, true)
                    tint(R.id.row_dot, theme.pillText)
                }
            } else if (theme.dot) {
                // A gutter as wide as the dot, so the icons line up down the column.
                hidden(R.id.row_dot)
            }
            setContentDescription(R.id.row_root, WidgetSpoken.row(entry.label, time, current))
            setOnClickPendingIntent(R.id.row_root, env.openApp)
        }
    }
}
