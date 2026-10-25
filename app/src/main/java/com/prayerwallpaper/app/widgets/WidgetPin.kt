package com.prayerwallpaper.app.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Asks the launcher to place a widget (it shows its own "add" prompt). False when the launcher cannot be asked, in which case the
 * settings screen tells the user how to add one from the launcher instead.
 */
fun requestPin(context: Context, provider: Class<*>): Boolean {
    val manager = AppWidgetManager.getInstance(context)
    return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(ComponentName(context, provider), null, null)
}
