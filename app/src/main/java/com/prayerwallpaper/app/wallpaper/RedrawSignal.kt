package com.prayerwallpaper.app.wallpaper

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Lets other parts of the app ask the live wallpaper to redraw, for example when a reminder alarm arrives and the highlighted
 * prayer may have moved. The wallpaper engine runs in the app's own process, so a plain in-process list is enough: with no
 * engine showing, there is nobody to tell. The engine redraws only if it is visible, and does so on its own thread.
 */
object RedrawSignal {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun fire() = listeners.forEach { it() }
}
