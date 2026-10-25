package com.prayerwallpaper.app

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Debug builds only. A clock that follows the system clock until a fake time is set, and then
 * runs in real time from that moment, so a boundary can be parked a minute away and watched.
 */
class FakeableClock(private val nanoTime: () -> Long = System::nanoTime) : Clock() {
    private class Anchor(val fake: Instant, val atNanos: Long)

    @Volatile
    private var anchor: Anchor? = null

    val isFake: Boolean get() = anchor != null

    fun set(instant: Instant) {
        anchor = Anchor(instant, nanoTime())
    }

    fun reset() {
        anchor = null
    }

    override fun instant(): Instant {
        val a = anchor ?: return Instant.now()
        return a.fake.plusNanos(nanoTime() - a.atNanos)
    }

    /** No zone of its own, like the release clock: readers call [withZone] with the place's zone. */
    override fun getZone(): ZoneId = ZoneOffset.UTC

    /** The same fake instant seen in [zone]. It shares this clock's anchor, so setting a time moves every view. */
    override fun withZone(zone: ZoneId): Clock = ZonedView(zone)

    private inner class ZonedView(private val zone: ZoneId) : Clock() {
        override fun instant(): Instant = this@FakeableClock.instant()

        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = this@FakeableClock.withZone(zone)
    }
}

object AppClock {
    private val fakeable = FakeableClock()
    val clock: Clock = fakeable
    val isFake: Boolean get() = fakeable.isFake

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun addChangeListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeChangeListener(listener: () -> Unit) {
        listeners -= listener
    }

    fun setFakeTime(instant: Instant) {
        fakeable.set(instant)
        listeners.forEach { it() }
    }

    fun useRealTime() {
        fakeable.reset()
        listeners.forEach { it() }
    }
}
