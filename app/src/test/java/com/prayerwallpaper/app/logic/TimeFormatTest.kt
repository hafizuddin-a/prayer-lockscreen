package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class TimeFormatTest {
    private fun fmt(h: Int, m: Int) = formatClock(LocalTime.of(h, m)).let { it.time + " " + it.suffix }

    @Test fun `morning times get am and afternoon times get pm`() {
        assertEquals("6:16 am", fmt(6, 16))
        assertEquals("7:42 am", fmt(7, 42))
        assertEquals("2:03 pm", fmt(14, 3))
        assertEquals("9:33 pm", fmt(21, 33))
    }

    @Test fun `noon and midnight use 12`() {
        assertEquals("12:00 pm", fmt(12, 0))
        assertEquals("12:55 pm", fmt(12, 55))
        assertEquals("12:05 am", fmt(0, 5))
    }

    @Test fun `minutes are always two digits and hours never padded`() {
        assertEquals("5:04 am", fmt(5, 4))
        assertEquals("1:10 pm", fmt(13, 10))
    }

    private fun fmt24(h: Int, m: Int) = formatClock(LocalTime.of(h, m), is24Hour = true).let { it.time + "|" + it.suffix }

    @Test fun `24-hour time is zero padded with no suffix`() {
        assertEquals("05:34|", fmt24(5, 34))
        assertEquals("13:10|", fmt24(13, 10))
        assertEquals("00:05|", fmt24(0, 5))
        assertEquals("12:55|", fmt24(12, 55))
        assertEquals("23:59|", fmt24(23, 59))
    }

    @Test fun `12-hour stays the default`() {
        assertEquals("1:10 pm", formatClock(LocalTime.of(13, 10)).let { it.time + " " + it.suffix })
    }
}
