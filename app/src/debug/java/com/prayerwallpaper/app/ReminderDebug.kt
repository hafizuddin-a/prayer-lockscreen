package com.prayerwallpaper.app

import android.content.Context
import android.content.SharedPreferences
import com.prayerwallpaper.app.data.PrayerDay
import com.prayerwallpaper.app.logic.AlarmMethod
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Debug builds only. Two switches for testing reminders on a phone without waiting for real prayer times: a fast schedule that
 * replaces today's six prayer times with ones two minutes apart starting two minutes from now, and a way to force the alarm method
 * (exact, inexact or the alarm clock one) to compare how reliably Android delivers each. Both are kept in a small file of their own
 * so they survive the app's process being killed between alarms, which is the case being tested. None of this exists in release.
 */
object ReminderDebug {
    private var prefs: SharedPreferences? = null

    fun install(context: Context) {
        prefs = context.getSharedPreferences("reminder_debug", Context.MODE_PRIVATE)
    }

    /** The forced alarm method, or null to let the app choose. */
    fun method(): AlarmMethod? = prefs?.getString(METHOD, null)?.let { name -> AlarmMethod.entries.firstOrNull { it.name == name } }

    fun setMethod(method: AlarmMethod?) {
        prefs?.edit()?.apply { if (method == null) remove(METHOD) else putString(METHOD, method.name) }?.apply()
    }

    /** The fast schedule's day, or null when it is off. It is the only day, so after its last alarm nothing is planned. */
    fun days(zone: ZoneId): Map<LocalDate, PrayerDay>? {
        val p = prefs ?: return null
        val date = p.getString(FAST_DATE, null)?.let(LocalDate::parse) ?: return null
        val times = p.getString(FAST_TIMES, null)?.split(',')?.map(LocalTime::parse) ?: return null
        return mapOf(date to PrayerDay(date, times))
    }

    /**
     * Turns the fast schedule on: six prayers two minutes apart, the first two minutes from the next whole minute. Returns false when
     * that would run past midnight, which a single day cannot hold.
     */
    fun startFast(zone: ZoneId, now: Instant, stepMinutes: Long = 2): Boolean {
        val first = now.atZone(zone).truncatedTo(ChronoUnit.MINUTES).plusMinutes(1 + stepMinutes)
        val times = (0 until 6).map { first.plusMinutes(it * stepMinutes) }
        if (times.last().toLocalDate() != first.toLocalDate()) return false
        prefs?.edit()?.putString(FAST_DATE, first.toLocalDate().toString())?.putString(FAST_TIMES, times.joinToString(",") { it.toLocalTime().toString() })?.apply()
        return true
    }

    fun stopFast() {
        prefs?.edit()?.remove(FAST_DATE)?.remove(FAST_TIMES)?.apply()
    }

    private const val METHOD = "method"
    private const val FAST_DATE = "fast_date"
    private const val FAST_TIMES = "fast_times"
}
