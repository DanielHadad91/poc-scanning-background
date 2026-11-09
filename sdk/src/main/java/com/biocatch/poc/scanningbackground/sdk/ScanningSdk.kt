package com.biocatch.poc.scanningbackground.sdk

import android.content.Context
import android.os.Build
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.scan.InstalledAppsScanner
import com.biocatch.poc.scanningbackground.scheduling.ScanJobSchedulerContract
import com.biocatch.poc.scanningbackground.scheduling.MissedRuns
import com.biocatch.poc.scanningbackground.scheduling.MissedRunsSummary
import com.biocatch.poc.scanningbackground.scheduling.ScanWindow
import com.biocatch.poc.scanningbackground.scheduling.SchedulingLimits
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/** Optional configuration applied at [ScanningSdk.initialize]. A null field keeps the current value. */
data class ScanConfig(
    val serverUrl: String? = null,
    val intervals: Int? = null,
    val autoRenew: Boolean? = null,
)

/** A read-only snapshot of the schedule and the latest scan, for a host app with no access to internals. */
data class ScanState(
    val schedulerName: String,
    val scheduleOrigin: ScheduleOrigin,
    val nextRunAt: Long,
    val intervalMinutes: Int,
    val serverUrl: String,
    val qapGranted: Boolean,
    val lastRun: LogEntry?,
)

/**
 * The single entry point for an integrating app. The host is responsible only for declaring the
 * SDK components and the permissions in its own AndroidManifest (see README) and calling
 * [initialize] (or [scanSchedule]) once; everything else — scanning, uploading, and boot recovery
 * for the alarm path — runs inside the SDK with no UI.
 */
object ScanningSdk {

    /**
     * Applies optional [config], then [scanSchedule]. A null [ScanConfig.intervals] uses the stored
     * count. Opening the app through [start] does not undo [cancelScanSchedule].
     */
    fun initialize(context: Context, config: ScanConfig? = null): LogEntry? {
        val settings = SettingsStore(context)
        config?.autoRenew?.let { settings.autoRenew = it }
        val intervals = config?.intervals ?: settings.intervals
        if (config == null) return start(context)
        return scanSchedule(context, intervals, config.serverUrl)
    }

    /**
     * Arms 1, 2, or 3 runs inside a hardcoded 24-hour window. A matching registration is left in
     * place. A different count replaces it. On API 19–20 a new count also picks a new alarm offset.
     * [backendDetectionUrl] null keeps the stored URL and leaves an existing registration running. A
     * blank URL, or a URL that is not http or https, returns without arming, clearing, or scanning.
     * API 19–20 calls setInexactRepeating at this call. The first-fire time passed in is this call plus
     * the offset, and that delivery is the scan. API 21+ registers the periodic job at this call, with
     * no offset wait, and the scan may fall anywhere in the period. After boot, API 19–20 makes the
     * same call at the boot and passes the boot plus the stored offset as the first fire. API 21+
     * leaves the persisted job in place. The boot receiver does not scan.
     * Returns the schedule entry when it changed the registration, or null when it left one in place.
     */
    fun scanSchedule(context: Context, intervals: Int, backendDetectionUrl: String? = null): LogEntry? {
        val settings = SettingsStore(context)
        val url = resolveUrl(settings, backendDetectionUrl) ?: return null
        if (!ScanWindow.isValid(intervals)) {
            return record(context, context.getString(R.string.log_schedule_bad_intervals))
        }
        if (backendDetectionUrl != null) settings.serverUrl = url
        settings.intervals = intervals
        settings.scheduleCancelled = false
        return ScanJobSchedulerContract.ensureScheduled(context)
    }

    /**
     * Re-applies the stored schedule when one is not cancelled. Does nothing after [cancelScanSchedule]
     * until the host calls [scanSchedule] again.
     */
    fun start(context: Context): LogEntry? {
        val settings = SettingsStore(context)
        if (settings.scheduleCancelled || !ScanWindow.isValid(settings.intervals)) return null
        return scanSchedule(context, settings.intervals, null)
    }

    /**
     * Removes the periodic job or the repeating alarm and keeps it off across reboot.
     * A scan that has already started is not aborted. The next [scanSchedule] arms it again.
     */
    fun cancelScanSchedule(context: Context) {
        ScanJobSchedulerContract.from(context).cancelScan()
        val settings = SettingsStore(context)
        settings.nextRunAt = 0L
        settings.armedIntervals = 0
        settings.scheduleCancelled = true
    }

    /** Runs one scan now, off-cycle. Returns false if the system refused to enqueue it. */
    fun runNow(context: Context): Boolean = ScanJobSchedulerContract.runNow(context)

    /** Re-arms a run one interval from now with the current interval, recording [reason]. */
    fun reschedule(context: Context, reason: String): LogEntry {
        SettingsStore(context).scheduleCancelled = false
        return ScanJobSchedulerContract.reschedule(context, reason, ScheduleOrigin.INTERVAL_CHANGED)
    }

    fun setServerUrl(context: Context, url: String) {
        SettingsStore(context).serverUrl = url
    }

    fun setAutoRenew(context: Context, enabled: Boolean) {
        SettingsStore(context).autoRenew = enabled
    }

    /** The recorded run history, newest first. */
    fun history(context: Context): List<LogEntry> = RunStore(context).all()

    /** Clears the recorded history and resets the missed-runs count to start from now. */
    fun clearHistory(context: Context) {
        RunStore(context).clear()
        SettingsStore(context).historyClearedAt = System.currentTimeMillis()
    }

    /**
     * Scheduled runs missed over the whole history: every gap longer than this Android version's
     * tolerance, plus the open one, so a hole stays counted after the runs resume.
     */
    fun missedRuns(context: Context, now: Long = System.currentTimeMillis()): MissedRunsSummary {
        val settings = SettingsStore(context)
        return MissedRuns.summarize(
            history = RunStore(context).all(),
            now = now,
            currentIntervalMinutes = settings.intervalMinutes,
            graceMinutes = SchedulingLimits.missGraceMinutes(Build.VERSION.SDK_INT),
            countFrom = settings.historyClearedAt,
        )
    }

    /** A snapshot the host can render without touching SDK internals. */
    fun state(context: Context): ScanState {
        val settings = SettingsStore(context)
        val lastRun = RunStore(context).all().firstOrNull { it.kind == EntryKind.SCAN }
        return ScanState(
            schedulerName = ScanJobSchedulerContract.from(context).name,
            scheduleOrigin = settings.scheduleOrigin,
            nextRunAt = settings.nextRunAt,
            intervalMinutes = settings.intervalMinutes,
            serverUrl = settings.serverUrl,
            qapGranted = InstalledAppsScanner(context).isQapGranted(),
            lastRun = lastRun,
        )
    }

    private fun resolveUrl(settings: SettingsStore, backendDetectionUrl: String?): String? {
        val candidate = backendDetectionUrl?.trim() ?: return settings.storedServerUrl()?.takeIf(::isHttp)
        if (candidate.isEmpty() || !isHttp(candidate)) return null
        return candidate
    }

    private fun isHttp(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

    private fun record(context: Context, message: String): LogEntry {
        val entry = LogEntry(
            kind = EntryKind.SCHEDULE,
            timestamp = System.currentTimeMillis(),
            scheduler = ScanJobSchedulerContract.from(context).name,
            message = message,
        )
        RunStore(context).append(entry)
        return entry
    }
}
