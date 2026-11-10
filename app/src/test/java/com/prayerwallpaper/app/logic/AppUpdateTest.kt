package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    // --- versions ---

    @Test fun `a version is read from a tag with or without the v, ignoring a suffix`() {
        assertEquals(listOf(1, 8, 1), parseVersion("v1.8.1"))
        assertEquals(listOf(1, 8, 1), parseVersion("1.8.1"))
        assertEquals(listOf(1, 8, 1), parseVersion("1.8.1-beta.2"))
        assertEquals(listOf(2, 0), parseVersion(" V2.0 "))
        assertNull(parseVersion("latest"))
        assertNull(parseVersion(""))
        assertNull(parseVersion("1.x"))
    }

    @Test fun `a later version is newer, an equal or earlier one is not, and zeros at the end change nothing`() {
        assertTrue(isNewerVersion("1.8.1", "1.8.0"))
        assertTrue(isNewerVersion("v1.9.0", "1.8.9"))
        assertTrue(isNewerVersion("2.0.0", "1.99.99"))
        assertTrue(isNewerVersion("1.10.0", "1.9.0")) // numbers, not text: 10 is more than 9
        assertTrue(isNewerVersion("1.8.0.1", "1.8.0"))
        assertFalse(isNewerVersion("1.8.0", "1.8.0"))
        assertFalse(isNewerVersion("1.8", "1.8.0"))
        assertFalse(isNewerVersion("1.7.9", "1.8.0"))
    }

    @Test fun `something that is not a version is never newer`() {
        assertFalse(isNewerVersion("nightly", "1.8.0"))
        assertFalse(isNewerVersion("1.9.0", "unknown"))
    }

    // --- GitHub's answer ---

    private fun release(tag: String = "v1.8.1", assets: String, extra: String = "") =
        """{"tag_name":"$tag","name":"Prayer Live Wallpaper","draft":false,"prerelease":false,"body":"notes",$extra"assets":[$assets]}"""

    private fun asset(name: String, size: Long = 4_060_855, url: String = "https://github.com/hafizuddin-a/prayer-lockscreen/releases/download/v1.8.1/$name") =
        """{"name":"$name","size":$size,"content_type":"application/vnd.android.package-archive","browser_download_url":"$url"}"""

    @Test fun `the release is read from what GitHub sends`() {
        val info = parseLatestRelease(release(assets = asset("prayer-wallpaper.apk")))
        assertEquals(ReleaseInfo("1.8.1", "https://github.com/hafizuddin-a/prayer-lockscreen/releases/download/v1.8.1/prayer-wallpaper.apk", 4_060_855), info)
    }

    @Test fun `the APK with the fixed name wins over other files`() {
        val info = parseLatestRelease(release(assets = asset("notes.txt") + "," + asset("prayer-wallpaper-1.8.1.apk") + "," + asset("prayer-wallpaper.apk")))
        assertTrue(info!!.apkUrl.endsWith("/prayer-wallpaper.apk"))
    }

    @Test fun `with no fixed name the one APK is used, and with several there is no guess`() {
        assertTrue(parseLatestRelease(release(assets = asset("prayer-wallpaper-1.8.1.apk")))!!.apkUrl.endsWith("prayer-wallpaper-1.8.1.apk"))
        assertNull(parseLatestRelease(release(assets = asset("a.apk") + "," + asset("b.apk"))))
    }

    @Test fun `a release with no APK, a draft, a pre-release or an odd tag is not an update`() {
        assertNull(parseLatestRelease(release(assets = asset("notes.txt"))))
        assertNull(parseLatestRelease(release(assets = "")))
        assertNull(parseLatestRelease("""{"tag_name":"v1.9.0","draft":true,"assets":[${asset("prayer-wallpaper.apk")}]}"""))
        assertNull(parseLatestRelease("""{"tag_name":"v1.9.0","prerelease":true,"assets":[${asset("prayer-wallpaper.apk")}]}"""))
        assertNull(parseLatestRelease(release(tag = "nightly", assets = asset("prayer-wallpaper.apk"))))
    }

    @Test fun `an answer that is not a release is not an update and does not crash`() {
        assertNull(parseLatestRelease("""{"message":"Not Found","documentation_url":"https://docs.github.com/rest"}"""))
        assertNull(parseLatestRelease("not json at all"))
        assertNull(parseLatestRelease("[]"))
        assertNull(parseLatestRelease(""))
    }

    @Test fun `a missing size is zero rather than a failure`() {
        val info = parseLatestRelease("""{"tag_name":"v1.8.1","assets":[{"name":"prayer-wallpaper.apk","browser_download_url":"https://github.com/hafizuddin-a/prayer-lockscreen/releases/download/v1.8.1/prayer-wallpaper.apk"}]}""")
        assertNotNull(info)
        assertEquals(0L, info!!.apkBytes)
    }

    // --- where an APK may come from ---

    @Test fun `only this app's GitHub releases are trusted in a release build`() {
        assertTrue(isTrustedApkUrl("https://github.com/hafizuddin-a/prayer-lockscreen/releases/download/v1.8.1/prayer-wallpaper.apk", debug = false))
        assertFalse(isTrustedApkUrl("https://github.com/someone-else/prayer-lockscreen/releases/download/v1.8.1/prayer-wallpaper.apk", debug = false))
        assertFalse(isTrustedApkUrl("http://github.com/hafizuddin-a/prayer-lockscreen/releases/download/v1.8.1/prayer-wallpaper.apk", debug = false))
        assertFalse(isTrustedApkUrl("https://github.com.evil.example/hafizuddin-a/prayer-lockscreen/releases/download/x.apk", debug = false))
        assertFalse(isTrustedApkUrl("https://example.com/prayer-wallpaper.apk", debug = false))
        assertFalse(isTrustedApkUrl("http://10.0.2.2:8000/prayer-wallpaper.apk", debug = false))
    }

    @Test fun `a debug build also trusts the emulator's computer, so an update can be tried without publishing`() {
        assertTrue(isTrustedApkUrl("http://10.0.2.2:8000/prayer-wallpaper.apk", debug = true))
        assertFalse(isTrustedApkUrl("http://192.168.1.5:8000/prayer-wallpaper.apk", debug = true))
        assertFalse(isTrustedApkUrl("https://example.com/prayer-wallpaper.apk", debug = true))
    }

    // --- when to ask ---

    private val hour = 3_600_000L

    @Test fun `the app asks on its own at most every few hours, a person's own check always asks`() {
        val last = 1_000_000_000_000L
        assertFalse(shouldCheckForUpdate(last + hour, last, force = false))
        assertTrue(shouldCheckForUpdate(last + 6 * hour, last, force = false))
        assertTrue(shouldCheckForUpdate(last + hour, last, force = true))
    }

    @Test fun `never having asked, or a clock that went backwards, means ask`() {
        assertTrue(shouldCheckForUpdate(5_000_000_000_000L, 0, force = false))
        assertTrue(shouldCheckForUpdate(1_000_000_000_000L, 2_000_000_000_000L, force = false))
    }

    @Test fun `an update installs without asking only when all three things hold`() {
        assertTrue(mayInstallWithoutAsking(autoUpdate = true, canInstallPackages = true, installedByThisApp = true))
        assertFalse(mayInstallWithoutAsking(autoUpdate = false, canInstallPackages = true, installedByThisApp = true))
        assertFalse(mayInstallWithoutAsking(autoUpdate = true, canInstallPackages = false, installedByThisApp = true))
        assertFalse(mayInstallWithoutAsking(autoUpdate = true, canInstallPackages = true, installedByThisApp = false))
    }

    // --- when Android says no ---

    @Test fun `saying no to the prompt is not an error, and a different signature says what to do`() {
        assertNull(installFailureMessage(3))
        assertTrue(installFailureMessage(5)!!.contains("Uninstall the app"))
        assertTrue(installFailureMessage(7)!!.contains("download page"))
        assertTrue(installFailureMessage(6)!!.contains("space"))
        assertTrue(installFailureMessage(2)!!.contains("Allow"))
        assertTrue(installFailureMessage(1)!!.isNotBlank())
    }
}
