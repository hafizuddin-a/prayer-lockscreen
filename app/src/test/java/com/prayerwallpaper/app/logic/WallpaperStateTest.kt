package com.prayerwallpaper.app.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperStateTest {
    @Test fun `a separate live wallpaper on the lock screen decides it`() {
        assertTrue(lockScreenIsOurs(lockIsOurs = true, lockId = 12, homeIsOurs = false))
        assertFalse(lockScreenIsOurs(lockIsOurs = false, lockId = 12, homeIsOurs = true))
    }

    @Test fun `with one shared wallpaper for both screens the lock screen follows the home one`() {
        assertTrue(lockScreenIsOurs(lockIsOurs = null, lockId = -1, homeIsOurs = true))
        assertFalse(lockScreenIsOurs(lockIsOurs = null, lockId = -1, homeIsOurs = false))
    }

    @Test fun `a separate static image on the lock screen is never ours even if home is`() {
        assertFalse(lockScreenIsOurs(lockIsOurs = null, lockId = 7, homeIsOurs = true))
    }
}
