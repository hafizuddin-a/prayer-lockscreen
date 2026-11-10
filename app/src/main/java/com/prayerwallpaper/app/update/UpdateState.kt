package com.prayerwallpaper.app.update

import com.prayerwallpaper.app.logic.ReleaseInfo
import java.time.Instant

/** Where an update stands, as the home screen and About show it. */
sealed interface UpdateState {
    /** Nothing is known: no check has run, or the last one failed quietly. */
    data object Idle : UpdateState

    data class UpToDate(val checkedAt: Instant) : UpdateState

    data object Checking : UpdateState

    data class Available(val release: ReleaseInfo) : UpdateState

    /** Android has to be told this app may install apps (a switch in its settings) before it can update itself. */
    data class NeedsInstallPermission(val release: ReleaseInfo) : UpdateState

    data class Downloading(val release: ReleaseInfo, val bytes: Long) : UpdateState

    /** Handed to Android: it is asking the person, or installing. The app is replaced when it finishes. */
    data class Installing(val release: ReleaseInfo) : UpdateState

    data class Failed(val release: ReleaseInfo?, val message: String) : UpdateState
}
