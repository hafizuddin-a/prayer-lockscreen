package com.prayerwallpaper.app.widgets

import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import com.prayerwallpaper.app.R
import com.prayerwallpaper.app.logic.WidgetState
import com.prayerwallpaper.app.logic.WidgetSpoken
import com.prayerwallpaper.app.logic.WidgetWords
import com.prayerwallpaper.app.logic.WidgetMetrics
import com.prayerwallpaper.app.logic.topRowFits
import com.prayerwallpaper.app.logic.chronometerBase
import com.prayerwallpaper.app.logic.formatWidgetTime
import com.prayerwallpaper.app.logic.hijriFormFor
import com.prayerwallpaper.app.logic.planNext
import com.prayerwallpaper.app.logic.text

/**
 * Widget 1, the next prayer: its name and time, the Hijri date (or "Next prayer", or "Tomorrow" where there is no date) above, and a live countdown
 * below that ticks inside the launcher, so it needs no updates from the app until the next boundary.
 */
object NextPrayerView {
    fun build(env: WidgetEnv, size: SizeF): RemoteViews {
        val state = env.state
        if (state is WidgetState.Missing) return messageView(env, WidgetWords.missing(state.reason))
        state as WidgetState.Ready
        // The last day of the data, after Isyak: there is nothing to count down to, and the widget says the data needs updating.
        val next = state.next ?: return messageView(env, WidgetWords.UPDATE)

        val entry = next.entry
        val time = formatWidgetTime(entry.time, env.is24Hour, env.locale)
        val fullFits = state.hijri?.let { topRowFits(size.width, it.text, WidgetMetrics.NEXT_TOP, env.fontScale, env.measure) } ?: false
        val hijriForm = hijriFormFor(state.hijri, fullFits, env.fontScale)
        val hijriText = state.hijri?.text(hijriForm)
        // The Hijri date stays on top after Isyak too. "Tomorrow" is only the stand-in when there is no date to show (a place without one).
        val top = hijriText ?: if (next.tomorrow) WidgetWords.TOMORROW else WidgetWords.NEXT_PRAYER
        val plan = planNext(size.width, size.height, env.fontScale, env.textMultiplier, env.look.bold, entry.label, time.text, top, env.measure)

        return RemoteViews(env.context.packageName, if (plan.stacked) R.layout.widget_next_stacked else R.layout.widget_next).apply {
            background(env)
            val theme = env.theme
            setTextViewText(R.id.name, entry.label)
            setTextViewText(R.id.time, time.text)
            visible(R.id.time, plan.showTime)
            setTextColor(R.id.name, theme.text)
            setTextColor(R.id.time, theme.text)
            setTextViewTextSize(R.id.name, TypedValue.COMPLEX_UNIT_SP, plan.nameSp)
            setTextViewTextSize(R.id.time, TypedValue.COMPLEX_UNIT_SP, plan.timeSp)

            visible(R.id.top_row, plan.showTop)
            if (plan.showTop) {
                setImageViewResource(R.id.top_icon, prayerIcon(entry.prayer))
                tint(R.id.top_icon, theme.icon)
                setTextViewText(R.id.top_text, top)
                setTextColor(R.id.top_text, theme.dimText)
                setTextViewTextSize(R.id.top_text, TypedValue.COMPLEX_UNIT_SP, plan.topSp)
            }

            visible(R.id.countdown, plan.showCountdown)
            if (plan.showCountdown) {
                setChronometerCountDown(R.id.countdown, true)
                setChronometer(R.id.countdown, chronometerBase(env.now, entry.at, env.elapsed), WidgetWords.IN_FORMAT, true)
                setTextColor(R.id.countdown, theme.dimText)
                setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, plan.countdownSp)
            }

            setContentDescription(R.id.widget_root, WidgetSpoken.next(next, time, hijriText.takeIf { hijriForm == com.prayerwallpaper.app.logic.HijriForm.FULL }))
            setOnClickPendingIntent(R.id.widget_root, env.openApp)
        }
    }
}
