package com.biocatch.poc.scanningbackground.storage

import android.content.Context
import android.content.SharedPreferences
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.scheduling.ScanWindow

/** User settings plus the time the scheduled run is armed for. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The last URL the host saved. Empty when nothing is stored. A blank URL does not change the schedule or scan. */
    var serverUrl: String
        get() = storedServerUrl().orEmpty()
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    /** Null when no URL is stored or the stored value is blank. */
    fun storedServerUrl(): String? =
        prefs.getString(KEY_SERVER_URL, null)?.trim()?.takeIf { it.isNotEmpty() }

    /** When on, a run that hits the free quota mints a fresh webhook.site URL and resends automatically. */
    var autoRenew: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RENEW, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RENEW, value).apply()

    /** How many runs fall in the 24-hour window. 0 until a schedule call is given 1, 2, or 3. */
    var intervals: Int
        get() = prefs.getInt(KEY_INTERVALS, 0)
        set(value) = prefs.edit().putInt(KEY_INTERVALS, value).apply()

    /** Gap in minutes derived from [intervals], for history and missed-run math. 0 when unset. */
    val intervalMinutes: Int
        get() = if (ScanWindow.isValid(intervals)) (ScanWindow.HOURS * 60) / intervals else 0

    /** The intervals value the registered job or alarm was armed with. 0 when nothing is armed. */
    var armedIntervals: Int
        get() = prefs.getInt(KEY_ARMED_INTERVALS, 0)
        set(value) = prefs.edit().putInt(KEY_ARMED_INTERVALS, value).apply()

    /** Offset inside the period, counted from [windowStartMillis]. Kept until [intervals] changes. API 19–20 waits this long and the first alarm is the scan. API 21+ waits this long, then registers the periodic job, including after boot. -1 until the first arm. */
    var phaseOffsetMillis: Long
        get() = prefs.getLong(KEY_PHASE_OFFSET, -1L)
        set(value) = prefs.edit().putLong(KEY_PHASE_OFFSET, value).apply()

    /** When this window started: the scanSchedule call that chose the offset, or the latest boot. -1 until the first arm. */
    var windowStartMillis: Long
        get() = prefs.getLong(KEY_WINDOW_START, -1L)
        set(value) = prefs.edit().putLong(KEY_WINDOW_START, value).apply()

    /** The [intervals] value [phaseOffsetMillis] was chosen for. */
    var phaseIntervals: Int
        get() = prefs.getInt(KEY_PHASE_INTERVALS, 0)
        set(value) = prefs.edit().putInt(KEY_PHASE_INTERVALS, value).apply()

    /** Set by [com.biocatch.poc.scanningbackground.sdk.ScanningSdk.cancelScanSchedule]. Boot leaves this off. */
    var scheduleCancelled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE_CANCELLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULE_CANCELLED, value).apply()

    /** 0 when nothing is armed. Written by the scheduler; JobScheduler itself does not expose it. */
    var nextRunAt: Long
        get() = prefs.getLong(KEY_NEXT_RUN_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_NEXT_RUN_AT, value).apply()

    /**
     * When the history was last cleared, or 0. Missed runs are counted from here, so a reset zeroes the
     * counter yet a chain that is dead at reset time still starts counting up again.
     */
    var historyClearedAt: Long
        get() = prefs.getLong(KEY_HISTORY_CLEARED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_HISTORY_CLEARED_AT, value).apply()

    var scheduleOrigin: ScheduleOrigin
        get() = prefs.getString(KEY_ORIGIN, null)
            ?.let { name -> runCatching { ScheduleOrigin.valueOf(name) }.getOrNull() }
            ?: ScheduleOrigin.NONE
        set(value) = prefs.edit().putString(KEY_ORIGIN, value.name).apply()

    /**
     * Test hook: forces the API 19-20 AlarmManager path on a newer device, since no API 19-20 emulator
     * runs on Apple Silicon. Off by default; set only through adb.
     */
    val forceAlarmFallback: Boolean
        get() = prefs.getBoolean(KEY_FORCE_ALARM, false)

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_INTERVALS = "intervals"
        private const val KEY_ARMED_INTERVALS = "armed_intervals"
        private const val KEY_PHASE_OFFSET = "phase_offset_millis"
        private const val KEY_WINDOW_START = "window_start_millis"
        private const val KEY_PHASE_INTERVALS = "phase_intervals"
        private const val KEY_SCHEDULE_CANCELLED = "schedule_cancelled"
        private const val KEY_NEXT_RUN_AT = "next_run_at"
        private const val KEY_ORIGIN = "schedule_origin"
        private const val KEY_HISTORY_CLEARED_AT = "history_cleared_at"
        private const val KEY_AUTO_RENEW = "auto_renew"
        private const val KEY_FORCE_ALARM = "force_alarm_fallback"
    }
}
