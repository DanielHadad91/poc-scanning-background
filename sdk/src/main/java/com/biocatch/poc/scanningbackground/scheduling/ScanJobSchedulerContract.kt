package com.biocatch.poc.scanningbackground.scheduling

import android.content.Context
import android.os.Build
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.scanjobscheduler.ScanJobScheduler
import com.biocatch.poc.scanningbackground.scanjobschedulerbelow21.ScanJobSchedulerBelow21
import com.biocatch.poc.scanningbackground.sdk.R
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/**
 * Arms the repeating scan. The system keeps the repeat; a finished run does not schedule the next one.
 * [ScanJobScheduler] is the periodic job from API 21. [ScanJobSchedulerBelow21] is the alarm:
 * API 19–20, and the forced alarm on API 21+. The alarm's first fire uses the stored offset.
 */
interface ScanJobSchedulerContract {

    /** Platform mechanism, stored on each history line. "JobScheduler" or "AlarmManager". */
    val name: String

    /** True for the alarm, including the forced alarm on API 21+. Its first fire waits for the stored offset. False for the periodic job. */
    val usesAlarmOffset: Boolean

    /** True when the repeating scan is pending. The immediate scan does not count. */
    fun isScanExists(): Boolean

    /**
     * Arms [trigger]. [Trigger.PERIODIC_SCAN] repeats every [periodMillis]. The alarm, including the
     * forced alarm on API 21+, adds [initialDelayMillis] to now as the first-fire time. That delivery
     * is the scan. After boot that delay is the stored offset counted from the boot. The periodic job
     * ignores [initialDelayMillis]: the period starts when this call runs, and the scan may fall
     * anywhere in the period. [Trigger.IMMEDIATE_SCAN] is one run. Returns false if the system refused it.
     */
    fun scan(trigger: Trigger, initialDelayMillis: Long, periodMillis: Long): Boolean

    /** Removes the repeating scan. An immediate scan that has already started keeps running. */
    fun cancelScan()

    /** Millis until the periodic scan's first fire. The periodic job starts immediately, so this is 0. */
    fun periodicStartDelayMillis(intervals: Int, periodMillis: Long, now: Long, origin: ScheduleOrigin): Long = 0L

    companion object {
        /** Periodic job from API 21, unless [SettingsStore.forceAlarmFallback] selects the alarm. The alarm uses the stored offset on every API, including 21+. */
        fun from(context: Context): ScanJobSchedulerContract =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !SettingsStore(context).forceAlarmFallback) {
                ScanJobScheduler(context)
            } else {
                ScanJobSchedulerBelow21(context)
            }

        /**
         * Leaves a registration whose [SettingsStore.intervals] matches the armed value. Arms one when
         * nothing is registered, and replaces the registration when the count differs. A schedule that
         * was armed before but is gone now was lost (force stop or an aggressive OEM), and the
         * re-creation is marked [ScheduleOrigin.RECREATED_ON_REENTRY].
         */
        fun ensureScheduled(context: Context): LogEntry? {
            val scheduler = from(context)
            val settings = SettingsStore(context)
            val intervals = settings.intervals
            if (scheduler.isScanExists() && settings.armedIntervals == intervals) {
                settings.scheduleOrigin = ScheduleOrigin.EXISTING_ON_OPEN
                return null
            }
            val intervalChanged = scheduler.isScanExists()
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
         * Replaces whatever is armed and records the result. The API 19–20 boot receiver calls this so the
         * first fire is the boot plus the stored offset. A persisted periodic job is not replaced on boot.
         */
        fun reschedule(context: Context, reason: String, origin: ScheduleOrigin): LogEntry {
            val scheduler = from(context)
            scheduler.cancelScan()
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

        fun runNow(context: Context): Boolean = from(context).scan(Trigger.IMMEDIATE_SCAN, 0, 0)

        /** Returns the recorded next-run time, or null if the system refused it. */
        private fun armNext(context: Context, scheduler: ScanJobSchedulerContract, origin: ScheduleOrigin): Long? {
            val settings = SettingsStore(context)
            val intervals = settings.intervals
            if (!ScanWindow.isValid(intervals)) {
                settings.nextRunAt = 0L
                settings.armedIntervals = 0
                return null
            }
            val periodMillis = ScanWindow.periodMillis(intervals)
            val now = System.currentTimeMillis()
            val initialDelayMillis = scheduler.periodicStartDelayMillis(intervals, periodMillis, now, origin)
            if (!scheduler.scan(Trigger.PERIODIC_SCAN, initialDelayMillis, periodMillis)) {
                settings.nextRunAt = 0L
                settings.armedIntervals = 0
                return null
            }
            val nextRunAt = if (scheduler.usesAlarmOffset) now + initialDelayMillis else now + periodMillis
            settings.nextRunAt = nextRunAt
            settings.armedIntervals = intervals
            settings.scheduleOrigin = origin
            return nextRunAt
        }
    }
}
