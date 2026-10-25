package com.prayerwallpaper.app

/** Debug builds only: force an event name beside the Hijri date, to test how a long one fits. Null means no override. */
object EventOverride {
    @Volatile
    var name: String? = null
}
