package com.prayerwallpaper.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundTest {
    @Test fun `work that succeeds runs and reports nothing`() {
        var ran = false
        val reports = mutableListOf<String>()
        runGuarded("T", { tag, _ -> reports += tag }) { ran = true }
        assertTrue(ran)
        assertTrue(reports.isEmpty())
    }

    @Test fun `work that fails is reported with its tag and never thrown`() {
        var failure: Exception? = null
        var tag = ""
        runGuarded("Alarm", { t, e -> tag = t; failure = e }) { throw IllegalStateException("disk full") }
        assertEquals("Alarm", tag)
        assertEquals("disk full", failure?.message)
    }
}
