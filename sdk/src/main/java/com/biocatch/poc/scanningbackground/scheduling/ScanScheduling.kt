package com.biocatch.poc.scanningbackground.scheduling

import android.content.Context
import com.biocatch.poc.scanningbackground.sdk.R
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore
import java.util.Random

/** Schedule operations used by the screen, the receivers and the running job, each recorded in [RunStore]. */
object ScanScheduling {

    /**
     * Leaves a registration whose [SettingsStore.intervals] matches the armed value. Arms one when
     * nothing is registered, and replaces the registration when the count differs. A schedule that
     * was armed before but is gone now was lost (force stop or an aggressive OEM), and the
     * re-creation is marked [ScheduleOrigin.RECREATED_ON_REENTRY].
     */
    fun ensureScheduled(context: Context): LogEntry? {
        val scheduler = ScanScheduler.from(context)
        val settings = SettingsStore(context)
        val intervals = settings.intervals
        if (scheduler.isPeriodicScanPending() && settings.armedIntervals == intervals) {
            settings.scheduleOrigin = ScheduleOrigin.EXISTING_ON_OPEN
            return null
        }
        val intervalChanged = scheduler.isPeriodicScanPending()
        val recovering = settings.nextRunAt > 0L
        val origin = when {
            intervalChanged -> ScheduleOrigin.INTERVAL_CHANGED
            recovering -> ScheduleOrigin.RECREATED_ON_REENTRY
            else -> ScheduleOrigin.FIRST_OPEN
        }
        val reason = when {
            intervalChanged -> context.getString(R.string.log_schedule_interval, intervals)
            recovering -> context.getString(R.string.log_schedule_recreated)
            else -> context.getString(R.string.log_schedule_first)
        }
        return reschedule(context, reason, origin)
    }

    /**
     * Replaces whatever is armed and records the result. After boot, API 19–20 calls setInexactRepeating
     * at the boot and passes the stored offset as the first fire. That delivery is the scan. API 21+
     * registers the periodic job at the boot, with no offset wait.
     */
    fun reschedule(context: Context, reason: String, origin: ScheduleOrigin): LogEntry {
        val scheduler = ScanScheduler.from(context)
        scheduler.cancelPeriodicScan()
        val nextRunAt = armNext(context, scheduler, origin)
        val entry = LogEntry(
            kind = EntryKind.SCHEDULE,
            timestamp = System.currentTimeMillis(),
            scheduler = scheduler.name,
            message = if (nextRunAt != null) reason else context.getString(R.string.log_schedule_failed, reason),
            nextRunAt = nextRunAt,
            intervalMinutes = SettingsStore(context).intervalMinutes,
        )
        RunStore(context).append(entry)
        return entry
    }

    fun runNow(context: Context): Boolean = ScanScheduler.from(context).schedule(Trigger.IMMEDIATE_SCAN, 0, 0)

    /** Returns the recorded next-run time, or null if the system refused it. */
    private fun armNext(context: Context, scheduler: ScanScheduler, origin: ScheduleOrigin): Long? {
        val settings = SettingsStore(context)
        val intervals = settings.intervals
        if (!ScanWindow.isValid(intervals)) {
            settings.nextRunAt = 0L
            settings.armedIntervals = 0
            return null
        }
        val periodMillis = ScanWindow.periodMillis(intervals)
        val now = System.currentTimeMillis()
        // API 19–20 aims the alarm at one offset inside the period. That delivery is the scan.
        // API 21+ has no offset: setPeriodic starts now, and the scan may fall anywhere in the period.
        val initialDelayMillis = if (scheduler.firstFireUsesStoredOffset) {
            alarmOffsetDelay(settings, intervals, periodMillis, now, origin)
        } else {
            0L
        }
        if (!scheduler.schedule(Trigger.PERIODIC_SCAN, initialDelayMillis, periodMillis)) {
            settings.nextRunAt = 0L
            settings.armedIntervals = 0
            return null
        }
        val nextRunAt = if (scheduler.firstFireUsesStoredOffset) now + initialDelayMillis else now + periodMillis
        settings.nextRunAt = nextRunAt
        settings.armedIntervals = intervals
        settings.scheduleOrigin = origin
        return nextRunAt
    }

    /** Delay until the stored alarm offset. A new count rolls a new offset and keeps it until the count changes. */
    private fun alarmOffsetDelay(
        settings: SettingsStore,
        intervals: Int,
        periodMillis: Long,
        now: Long,
        origin: ScheduleOrigin,
    ): Long {
        val newPhase = settings.phaseIntervals != intervals || settings.phaseOffsetMillis < 0L
        if (newPhase) {
            settings.phaseOffsetMillis = Random().nextInt(periodMillis.toInt()).toLong()
            settings.phaseIntervals = intervals
            settings.windowStartMillis = now
        } else if (origin == ScheduleOrigin.AFTER_BOOT || settings.windowStartMillis < 0L) {
            // Boot keeps the stored offset. The window start becomes the boot, so the delay is that offset.
            settings.windowStartMillis = now
        }
        return ScanWindow.delayUntilPhase(
            now,
            periodMillis,
            settings.phaseOffsetMillis,
            settings.windowStartMillis,
        )
    }
}
