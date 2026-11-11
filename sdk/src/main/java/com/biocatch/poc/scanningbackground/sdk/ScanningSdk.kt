package com.biocatch.poc.scanningbackground.sdk

import android.content.Context
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.scheduling.ScanJobSchedulerContract
import com.biocatch.poc.scanningbackground.scheduling.ScanWindow
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/**
 * The host uses two calls. They schedule the scan. Collectors and the WUP transmitter stay off.
 */
object ScanningSdk {

    /**
     * Arms 1, 2, or 3 runs inside a fixed 24-hour window. A matching registration is left in place.
     * A different count replaces it. On API 19–20 a new count also picks a new alarm offset.
     * [backendDetectionUrl] null keeps the stored URL. A blank URL, or a URL that is not http or https,
     * returns without arming. Any other count leaves the armed run as it is.
     * API 19–20 calls setInexactRepeating at this call. The first fire is this call plus the offset,
     * and that delivery is the scan. API 21+ registers the periodic job at this call. The scan may
     * fall anywhere in the period. After boot, API 19–20 registers the alarm again at the boot plus
     * the stored offset. API 21+ leaves the persisted job in place.
     */
    fun scheduleScan(context: Context, intervals: Int, backendDetectionUrl: String? = null) {
        val settings = SettingsStore(context)
        val url = resolveUrl(settings, backendDetectionUrl) ?: return
        if (!ScanWindow.isValid(intervals)) {
            record(context, context.getString(R.string.log_schedule_bad_intervals))
            return
        }
        if (backendDetectionUrl != null) settings.serverUrl = url
        settings.intervals = intervals
        settings.scheduleCancelled = false
        ScanJobSchedulerContract.ensureScheduledScan(context)
    }

    /**
     * Removes the periodic job or the repeating alarm. The schedule stays off across reboot and
     * across opening the app, until the next [scheduleScan].
     */
    fun cancelScheduledScan(context: Context) {
        ScanJobSchedulerContract.from(context).cancelScheduledScan()
        val settings = SettingsStore(context)
        settings.nextRunAt = 0L
        settings.armedIntervals = 0
        settings.scheduleCancelled = true
    }

    /** POC: one scan now. The scan records its own history line. */
    fun runNow(context: Context): Boolean = ScanJobSchedulerContract.runNow(context)

    private fun resolveUrl(settings: SettingsStore, backendDetectionUrl: String?): String? {
        val candidate = backendDetectionUrl?.trim() ?: return settings.storedServerUrl()?.takeIf(::isHttp)
        if (candidate.isEmpty() || !isHttp(candidate)) return null
        return candidate
    }

    private fun isHttp(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    private fun record(context: Context, message: String) {
        RunStore(context).append(
            LogEntry(
                kind = EntryKind.SCHEDULE,
                timestamp = System.currentTimeMillis(),
                scheduler = ScanJobSchedulerContract.from(context).name,
                message = message,
            )
        )
    }
}
