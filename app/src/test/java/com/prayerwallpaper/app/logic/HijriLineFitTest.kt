package com.prayerwallpaper.app.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HijriLineFitTest {
    // A fake font: every character is 10 wide, and the moon with its gap is 60.
    private val widthOf: (String) -> Float = { it.length * 10f }
    private val fixed = 60f
    private val date = "12 Rabiulawal 1449H" // 19 characters
    private val sep = HIJRI_EVENT_SEPARATOR

    private fun fit(event: String?, available: Float, eventFloor: Float = 0.75f, dateFloor: Float = 0.7f) =
        fitHijriLine(widthOf, fixed, date, event, available, eventFloor, dateFloor)

    @Test fun `a date on its own that fits is drawn as it is`() {
        val f = fit(null, available = 500f)
        assertEquals(1f, f.scale, 0f)
        assertEquals(date, f.text)
    }

    @Test fun `a short event that fits is drawn in full at the usual size`() {
        val f = fit("Asyura Day", available = 600f) // 60 + 19*10 + 3*10 + 10*10 = 380
        assertEquals(1f, f.scale, 0f)
        assertEquals("$date$sep" + "Asyura Day", f.text)
    }

    @Test fun `an event that only just fits is not shrunk`() {
        val f = fit("Asyura Day", available = 380f)
        assertEquals(1f, f.scale, 0f)
    }

    @Test fun `a long event shrinks the whole line to fit, keeping the full name`() {
        val name = "Prophet Muhammad’s Birthday" // 27 characters: 60 + 190 + 30 + 270 = 550
        val f = fit(name, available = 500f)
        assertEquals(500f / 550f, f.scale, 1e-6f)
        assertEquals("$date$sep$name", f.text)
        assertTrue(f.scale < 1f && f.scale >= 0.75f)
    }

    @Test fun `shrinking stops at the floor, and then the name is shortened with an ellipsis`() {
        val name = "Prophet Muhammad’s Birthday"
        val f = fit(name, available = 400f) // 550 needs 0.727 of the size, below the 0.75 floor
        assertEquals(0.75f, f.scale, 0f)
        assertTrue(f.text.endsWith(HIJRI_ELLIPSIS))
        assertTrue(f.text.startsWith("$date$sep"))
        // It fits at the floor, and one more letter would not.
        assertTrue(0.75f * (fixed + widthOf(f.text)) <= 400f)
        val letters = f.text.removePrefix("$date$sep").removeSuffix(HIJRI_ELLIPSIS).length
        val onemore = date + sep + name.take(letters + 1).trimEnd() + HIJRI_ELLIPSIS
        assertTrue(0.75f * (fixed + widthOf(onemore)) > 400f)
    }

    @Test fun `the date is never cut, whatever the event`() {
        for (available in listOf(250f, 300f, 350f, 400f, 450f, 500f)) {
            val f = fit("Hari Raya Aidiladha", available)
            assertTrue("$available: ${f.text}", f.text.startsWith(date))
        }
    }

    @Test fun `a trimmed name does not leave a space before the ellipsis`() {
        val f = fit("Hari Raya Aidilfitri", available = 330f) // forces a cut through a space somewhere
        assertTrue(f.text, !f.text.endsWith(" $HIJRI_ELLIPSIS"))
    }

    @Test fun `when not even one letter of the event fits, the date is drawn alone`() {
        val f = fit("Hari Raya Aidiladha", available = 150f) // the date needs 250 even at full size
        assertEquals(date, f.text)
        assertEquals(0.7f, f.scale, 0f) // and only shrinks as far as a date may
    }

    @Test fun `a date alone shrinks to fit, down to its own floor`() {
        assertEquals(200f / 250f, fit(null, available = 200f).scale, 1e-6f)
        assertEquals(0.7f, fit(null, available = 100f).scale, 0f)
    }

    @Test fun `no event and an empty event name are not the same, but a blank name never reaches here`() {
        assertNotEquals(fit(null, 600f).text, fit("X", 600f).text)
    }

    // Real names, with Roboto-ish widths: about 22 px a character at 43 px text, a 70 px moon and gap, 994 px to use.
    private val real = listOf(
        "Asyura Day", "Nuzul Quran", "Israk Mikraj", "Nisfu Syaaban", "Islamic New Year", "Beginning of Fasting",
        "Hari Raya Aidilfitri", "Hari Raya Aidiladha", "Prophet Muhammad’s Birthday",
    )
    private val dates = listOf("27 Rejab 1448H", "15 Syaaban 1448H", "1 Ramadhan 1448H", "17 Ramadhan 1448H", "1 Syawal 1448H", "10 Zulhijjah 1447H", "1 Muharram 1449H", "10 Muharram 1449H", "12 Rabiulawal 1449H")

    private fun real(widthFactor: Float, available: Float) = real.zip(dates).map { (event, d) ->
        fitHijriLine({ it.length * 22f * widthFactor }, 70f, d, event, available, 0.70f, 0.70f)
    }

    @Test fun `every real event name is shown in full on an ordinary font, the longest only a little smaller`() {
        val fits = real(widthFactor = 1f, available = 994f)
        fits.zip(real).forEach { (f, event) -> assertTrue("$event: ${f.text}", f.text.endsWith(event)) }
        assertTrue("longest is ${fits.last().scale}", fits.last().scale in 0.70f..1f)
        assertTrue(fits.first().scale == 1f) // the shortest is not shrunk at all
    }

    @Test fun `a font 20 percent wider still shows every name in full`() {
        real(widthFactor = 1.2f, available = 994f).zip(real).forEach { (f, event) -> assertTrue("$event: ${f.text}", f.text.endsWith(event)) }
    }

    @Test fun `a font 40 percent wider shortens only the longest name, and the date is kept`() {
        val fits = real(widthFactor = 1.4f, available = 994f)
        assertTrue(fits.last().text.endsWith(HIJRI_ELLIPSIS))
        assertTrue(fits.dropLast(1).zip(real).all { (f, event) -> f.text.endsWith(event) || f.text.endsWith(HIJRI_ELLIPSIS) })
    }

    @Test fun `a very wide font shortens the longest names but keeps the date and never overflows`() {
        val fits = real(widthFactor = 1.9f, available = 994f)
        fits.zip(dates).forEach { (f, d) ->
            assertTrue(f.text, f.text.startsWith(d))
            assertTrue("${f.text} is too wide", f.scale * (70f + f.text.length * 22f * 1.9f) <= 994f + 1f)
        }
        assertTrue(fits.last().text.endsWith(HIJRI_ELLIPSIS))
    }

    @Test fun `from the left there is a little less room, so the longest name shrinks a little more`() {
        val centred = real(1f, 994f).last().scale
        val left = real(1f, 918f).last().scale
        assertTrue(left < centred)
    }

    // --- an event name on a line of its own (Large and Extra large text) ---

    private val perLetter = { text: String -> text.length * 10f }

    @Test fun `an event name that fits on its own line is drawn as it is`() {
        val fit = fitEventName(perLetter, "Asyura Day", 200f, 0.7f)
        assertEquals(1f, fit.scale, 0f)
        assertEquals("Asyura Day", fit.text)
    }

    @Test fun `a longer one shrinks, down to the floor`() {
        val fit = fitEventName(perLetter, "Prophet Muhammad's Birthday", 220f, 0.7f) // 27 letters: 270 wide
        assertEquals(220f / 270f, fit.scale, 1e-4f)
        assertEquals("Prophet Muhammad's Birthday", fit.text)
    }

    @Test fun `one that cannot fit even at the floor is shortened with an ellipsis, never run off the edge`() {
        val fit = fitEventName(perLetter, "Prophet Muhammad's Birthday", 100f, 0.7f)
        assertEquals(0.7f, fit.scale, 0f)
        assertTrue(fit.text.endsWith(HIJRI_ELLIPSIS))
        assertTrue(0.7f * perLetter(fit.text) <= 100f)
    }

    @Test fun `with no room at all it is just the ellipsis`() {
        assertEquals(HIJRI_ELLIPSIS, fitEventName(perLetter, "Asyura Day", 1f, 0.7f).text)
    }
}
