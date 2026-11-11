package com.prayerwallpaper.app

import android.content.BroadcastReceiver
import android.util.Log

/**
 * Runs [block] on a thread of its own and never lets it throw. An exception that nobody catches on such a thread ends the whole app, and
 * Android resets a live wallpaper whose app crashes, so background work logs what went wrong and carries on. The next trigger (an alarm,
 * the screen coming on, the app opening) simply tries again.
 */
fun inBackground(tag: String, block: () -> Unit) {
    Thread { runGuarded(tag, ::logFailure, block) }.start()
}

/** For a broadcast receiver: the same, inside [goAsync], so Android lets the work finish and the receiver is released afterwards. */
fun BroadcastReceiver.goAsyncGuarded(tag: String, block: () -> Unit) {
    val pending = goAsync()
    Thread {
        try {
            runGuarded(tag, ::logFailure, block)
        } finally {
            pending.finish()
        }
    }.start()
}

/** The part that matters, on its own so it can be tried without Android: runs [block], and hands any exception to [report] instead of throwing it. */
internal fun runGuarded(tag: String, report: (String, Exception) -> Unit, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        report(tag, e)
    }
}

private fun logFailure(tag: String, e: Exception) {
    Log.w(tag, "background work failed: $e", e)
}
