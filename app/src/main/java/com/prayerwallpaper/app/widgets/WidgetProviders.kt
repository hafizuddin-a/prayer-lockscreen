package com.prayerwallpaper.app.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import com.prayerwallpaper.app.PrayerApp
import com.prayerwallpaper.app.goAsyncGuarded

/**
 * The base of the three widget providers. Every event that can change what a widget shows asks the app to update all of them: the
 * work is a few reads and a handful of RemoteViews, done off the main thread under [goAsync].
 */
abstract class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = refresh(context, "onUpdate")

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) = refresh(context, "resized")

    override fun onEnabled(context: Context) = refresh(context, "first widget added")

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) = refresh(context, "restored")

    // With the last widget gone, updateAll cancels the alarms instead of setting them.
    override fun onDeleted(context: Context, appWidgetIds: IntArray) = refresh(context, "widget removed")

    override fun onDisabled(context: Context) = refresh(context, "last widget of this kind removed")

    private fun refresh(context: Context, reason: String) {
        val app = context.applicationContext as PrayerApp
        goAsyncGuarded("WidgetProvider") { app.widgets.updateAllDebounced(reason) }
    }
}

/** Widget 1: the next prayer, 2x1. */
class NextPrayerWidget : PrayerWidgetProvider()

/** Widget 2: today's times, 4x2. */
class TodayTimesWidget : PrayerWidgetProvider()

/** Widget 3: all six in a row, 4x1. */
class PrayerStripWidget : PrayerWidgetProvider()
