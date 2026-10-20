package com.biocatch.poc.scanningbackground.storage

import android.content.Context
import android.content.SharedPreferences
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin

/** User settings plus the time the scheduled run is armed for. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The last URL the user saved, or a working default so the app sends out of the box. */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, null)?.trim()?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString(KEY_SERVER_URL, value.trim()).apply()

    /** When on, a run that hits the free quota mints a fresh webhook.site URL and resends automatically. */
    var autoRenew: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RENEW, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_RENEW, value).apply()

    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
        set(value) = prefs.edit().putInt(KEY_INTERVAL_MINUTES, value.coerceAtLeast(MIN_INTERVAL_MINUTES)).apply()

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
        /** A pre-created webhook.site URL so a fresh install works immediately; rotated by auto-renew when full. */
        const val DEFAULT_SERVER_URL = "https://webhook.site/4274ca33-d7f7-4ae5-bc29-6c2defd8148a"
        const val DEFAULT_INTERVAL_MINUTES = 60
        const val MIN_INTERVAL_MINUTES = 1

        private const val PREFS_NAME = "settings"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_INTERVAL_MINUTES = "interval_minutes"
        private const val KEY_NEXT_RUN_AT = "next_run_at"
        private const val KEY_ORIGIN = "schedule_origin"
        private const val KEY_HISTORY_CLEARED_AT = "history_cleared_at"
        private const val KEY_AUTO_RENEW = "auto_renew"
        private const val KEY_FORCE_ALARM = "force_alarm_fallback"
    }
}
