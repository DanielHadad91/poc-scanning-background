package com.biocatch.poc.scanningbackground.sdk

import android.content.Context
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.scan.InstalledAppsScanner
import com.biocatch.poc.scanningbackground.scheduling.BackgroundScheduler
import com.biocatch.poc.scanningbackground.scheduling.ScanScheduling
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/** Optional configuration applied at [ScanningSdk.initialize]. A null field keeps the current value. */
data class ScanConfig(
    val serverUrl: String? = null,
    val intervalMinutes: Int? = null,
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
 * [initialize] (or [start]) once; everything else — scanning, uploading, self-rescheduling, boot
 * recovery — runs inside the SDK with no UI.
 */
object ScanningSdk {

    /** Applies optional [config], then arms the schedule if nothing is armed. Call once on app start. */
    fun initialize(context: Context, config: ScanConfig? = null): LogEntry? {
        config?.let { applyConfig(context, it) }
        return ScanScheduling.ensureScheduled(context)
    }

    /** Arms the schedule only if nothing is armed (idempotent); does not reset a running timer. */
    fun start(context: Context): LogEntry? = ScanScheduling.ensureScheduled(context)

    /** Cancels the armed run. The schedule revives on the next [start] or after a device boot. */
    fun stop(context: Context) {
        BackgroundScheduler.from(context).cancelScheduled()
        SettingsStore(context).nextRunAt = 0L
    }

    /** Runs one scan now, off-cycle. Returns false if the system refused to enqueue it. */
    fun runNow(context: Context): Boolean = ScanScheduling.runNow(context)

    /** Re-arms a run one interval from now with the current interval, recording [reason]. */
    fun reschedule(context: Context, reason: String): LogEntry =
        ScanScheduling.reschedule(context, reason, ScheduleOrigin.INTERVAL_CHANGED)

    fun setServerUrl(context: Context, url: String) {
        SettingsStore(context).serverUrl = url
    }

    fun setIntervalMinutes(context: Context, minutes: Int) {
        SettingsStore(context).intervalMinutes = minutes
    }

    fun setAutoRenew(context: Context, enabled: Boolean) {
        SettingsStore(context).autoRenew = enabled
    }

    /** The recorded run history, newest first. */
    fun history(context: Context): List<LogEntry> = RunStore(context).all()

    /** Clears the recorded history (and resets any derived counters the host computes from it). */
    fun clearHistory(context: Context) = RunStore(context).clear()

    /** A snapshot the host can render without touching SDK internals. */
    fun state(context: Context): ScanState {
        val settings = SettingsStore(context)
        val lastRun = RunStore(context).all().firstOrNull { it.kind == EntryKind.SCAN }
        return ScanState(
            schedulerName = BackgroundScheduler.from(context).name,
            scheduleOrigin = settings.scheduleOrigin,
            nextRunAt = settings.nextRunAt,
            intervalMinutes = settings.intervalMinutes,
            serverUrl = settings.serverUrl,
            qapGranted = InstalledAppsScanner(context).isQapGranted(),
            lastRun = lastRun,
        )
    }

    private fun applyConfig(context: Context, config: ScanConfig) {
        val settings = SettingsStore(context)
        config.serverUrl?.let { settings.serverUrl = it }
        config.intervalMinutes?.let { settings.intervalMinutes = it }
        config.autoRenew?.let { settings.autoRenew = it }
    }
}
