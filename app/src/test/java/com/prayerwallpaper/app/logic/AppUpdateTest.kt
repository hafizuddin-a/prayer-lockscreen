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

    @Test fun `the app asks on its own at most once a month, a person's own check always asks`() {
        val last = 1_000_000_000_000L
        assertFalse(shouldCheckForUpdate(last + 29 * 24 * hour, last, force = false))
        assertTrue(shouldCheckForUpdate(last + 30 * 24 * hour, last, force = false))
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

    // --- hardening ---

    @Test fun `a final release is later than a pre-release of the same numbers, never the other way round`() {
        assertTrue(isNewerVersion("1.9.0", "1.9.0-rc1"))
        assertFalse(isNewerVersion("1.9.0-rc1", "1.9.0"))
        assertFalse(isNewerVersion("1.9.0-rc2", "1.9.0-rc1")) // cannot tell; Android still needs a higher versionCode
        assertTrue(isNewerVersion("1.9.1-rc1", "1.9.0")) // different numbers decide first
        assertFalse(isNewerVersion("1.9.0+build5", "1.9.0")) // build metadata is not a pre-release
    }

    @Test fun `an address that climbs out of the releases folder, or says more than where it goes, is not trusted`() {
        val base = "https://github.com/hafizuddin-a/prayer-lockscreen/releases/download/"
        assertTrue(isTrustedApkUrl(base + "v1.8.4/prayer-wallpaper.apk", debug = false))
        assertFalse(isTrustedApkUrl(base + "../../../someone/else/releases/download/v1/a.apk", debug = false))
        assertFalse(isTrustedApkUrl(base + "v1/%2e%2e/%2e%2e/x.apk", debug = false))
        assertFalse(isTrustedApkUrl(base + "v1/..%2Fx.apk", debug = false))
        assertFalse(isTrustedApkUrl(base + "v1\\..\\x.apk", debug = false))
        assertFalse(isTrustedApkUrl("https://evil@github.com/hafizuddin-a/prayer-lockscreen/releases/download/v1/a.apk", debug = false))
        assertFalse(isTrustedApkUrl("https://github.com:8443/hafizuddin-a/prayer-lockscreen/releases/download/v1/a.apk", debug = false))
        assertFalse(isTrustedApkUrl(base + "v1/a.apk#frag", debug = false))
        assertFalse(isTrustedApkUrl("https://github.com/hafizuddin-a/prayer-lockscreen/releases/other/a.apk", debug = false))
        assertFalse(isTrustedApkUrl("not a url", debug = false))
        assertFalse(isTrustedApkUrl("http://10.0.2.2/prayer-wallpaper.apk", debug = true)) // the emulator address needs a port
    }

    @Test fun `a download may be no larger than GitHub said, or the general limit`() {
        assertEquals(4_078_147L, downloadLimit(4_078_147L))
        assertEquals(MAX_APK_BYTES, downloadLimit(0))
        assertEquals(MAX_APK_BYTES, downloadLimit(-5))
        assertEquals(MAX_APK_BYTES, downloadLimit(MAX_APK_BYTES + 1)) // a claim bigger than the limit does not raise it
    }

    @Test fun `after a failed check the app's own checks wait, a person's own check does not`() {
        val last = 1_000_000_000_000L
        val failed = last + 31 * 24 * hour // a check is due, and the last attempt failed just now
        assertFalse(shouldCheckForUpdate(failed + hour, last, force = false, lastFailureMillis = failed))
        assertTrue(shouldCheckForUpdate(failed + 6 * hour, last, force = false, lastFailureMillis = failed))
        assertTrue(shouldCheckForUpdate(failed + hour, last, force = true, lastFailureMillis = failed))
        assertTrue(shouldCheckForUpdate(failed - hour, last, force = false, lastFailureMillis = failed)) // a failure stamped in the future (clock moved) is ignored
    }

    @Test fun `signers continue when they are the same, or when the update carries the installed one in its history`() {
        val k1 = setOf("aa"); val k2 = setOf("bb")
        assertTrue(signersContinue(k1, k1, k1))
        assertTrue(signersContinue(k1, k2, setOf("aa", "bb"))) // a rotated key
        assertFalse(signersContinue(k1, k2, k2)) // a different key with no link to the old one
        assertFalse(signersContinue(emptySet(), k1, k1))
        assertFalse(signersContinue(k1, emptySet(), emptySet()))
    }

    @Test fun `only the installer's own confirm screen for one of this app's sessions is started`() {
        val confirm = "android.content.pm.action.CONFIRM_INSTALL"
        val mine = setOf(7, 9)
        assertTrue(isSafeInstallConfirmation(confirm, "com.google.android.packageinstaller", "com.prayerwallpaper.app", false, 7, mine))
        assertTrue(isSafeInstallConfirmation(confirm, "com.android.packageinstaller", "com.prayerwallpaper.app", false, null, mine)) // no session named
        assertFalse(isSafeInstallConfirmation("android.intent.action.VIEW", "com.google.android.packageinstaller", "com.prayerwallpaper.app", false, 7, mine))
        assertFalse(isSafeInstallConfirmation(null, "com.google.android.packageinstaller", "com.prayerwallpaper.app", false, 7, mine))
        assertFalse(isSafeInstallConfirmation(confirm, "com.prayerwallpaper.app", "com.prayerwallpaper.app", false, 7, mine)) // this app's own screens
        assertFalse(isSafeInstallConfirmation(confirm, null, "com.prayerwallpaper.app", false, 7, mine))
        assertFalse(isSafeInstallConfirmation(confirm, "com.google.android.packageinstaller", "com.prayerwallpaper.app", true, 7, mine)) // carries data
        assertFalse(isSafeInstallConfirmation(confirm, "com.google.android.packageinstaller", "com.prayerwallpaper.app", false, 99, mine)) // someone else's session
    }
}
