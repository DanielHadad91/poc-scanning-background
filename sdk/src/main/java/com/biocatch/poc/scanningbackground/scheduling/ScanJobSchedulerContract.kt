package com.biocatch.poc.scanningbackground.scheduling

import android.content.Context
import android.os.Build
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.scanjobscheduler.ScanJobScheduler
import com.biocatch.poc.scanningbackground.scanjobschedulerbelow21.ScanJobSchedulerBelow21
import com.biocatch.poc.scanningbackground.sdk.R
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/**
 * Arms the repeating scan. The system keeps the repeat; a finished run does not schedule the next one.
 * [ScanJobScheduler] is the periodic job from API 21. [ScanJobSchedulerBelow21] is the alarm on API 19–20.
 * The alarm's first fire uses the stored offset.
 */
internal interface ScanJobSchedulerContract {

    /** POC only. Mechanism name stored on each history line. "JobScheduler" or "AlarmManager". */
    val name: String

    /** True when the repeating scan is pending. */
    fun isScanScheduled(): Boolean

    /**
     * Arms the repeating scan. Returns the recorded next-run time, or null if the system refused it.
     * The alarm chooses its first-fire offset. That delivery is the scan. After boot the offset is the
     * stored one, counted from [now]. The periodic job starts its period when this call runs. The scan
     * may fall anywhere in that period, and the returned time is the end of the period.
     */
    fun scheduleScan(intervals: Int, periodMillis: Long, now: Long, afterBoot: Boolean): Long?

    /** Removes the repeating scan. An immediate scan that has already started keeps running. */
    fun cancelScheduledScan()

    /**
     * POC only. One scan now, beside the repeating scan. Not part of the production API.
     * Returns false if the system refused it.
     */
    fun runNow(): Boolean

    companion object {
        /** Periodic job from API 21. API 19–20 uses the alarm. */
        fun from(context: Context): ScanJobSchedulerContract =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ScanJobScheduler(context)
            } else {
                ScanJobSchedulerBelow21(context)
            }

        /**
         * Leaves a registration whose intervals match the armed value. Arms one when nothing is
         * registered, and replaces the registration when the count differs.
         */
        fun ensureScheduledScan(context: Context) {
            val scheduler = from(context)
            val settings = SettingsStore(context)
            val intervals = settings.intervals
            if (scheduler.isScanScheduled() && settings.armedIntervals == intervals) {
                settings.scheduleOrigin = ScheduleOrigin.EXISTING_ON_OPEN
                return
            }
            // A same-count registration already returned. Pending here means the count changed.
            val intervalChanged = scheduler.isScanScheduled()
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
            rescheduleScan(context, afterBoot = false, origin, reason)
        }

        /**
         * Replaces whatever is armed. The API 19–20 boot receiver calls this so the first fire is the
         * boot plus the stored offset. A persisted periodic job is not replaced on boot.
         * The history line is recorded here. Callers do not receive it.
         */
        fun rescheduleScan(context: Context, afterBoot: Boolean, origin: ScheduleOrigin, reason: String) {
            val scheduler = from(context)
            if (scheduler.isScanScheduled()) scheduler.cancelScheduledScan()
            val armed = armNext(context, scheduler, afterBoot, origin)
            RunStore(context).append(
                LogEntry(
                    kind = EntryKind.SCHEDULE,
                    timestamp = System.currentTimeMillis(),
                    scheduler = scheduler.name,
                    message = if (armed) reason else context.getString(R.string.log_schedule_failed, reason),
                    nextRunAt = SettingsStore(context).nextRunAt.takeIf { armed && it > 0L },
                    intervalMinutes = SettingsStore(context).intervalMinutes,
                )
            )
        }

        /** POC only. One scan now. Not part of the production API. The history line is written by the scan. */
        fun runNow(context: Context): Boolean = from(context).runNow()

        private fun armNext(
            context: Context,
            scheduler: ScanJobSchedulerContract,
            afterBoot: Boolean,
            origin: ScheduleOrigin,
        ): Boolean {
            val settings = SettingsStore(context)
            val intervals = settings.intervals
            if (!ScanWindow.isValid(intervals)) {
                settings.nextRunAt = 0L
                settings.armedIntervals = 0
                return false
            }
            val periodMillis = ScanWindow.periodMillis(intervals)
            val now = System.currentTimeMillis()
            val nextRunAt = scheduler.scheduleScan(intervals, periodMillis, now, afterBoot)
            if (nextRunAt == null) {
                settings.nextRunAt = 0L
                settings.armedIntervals = 0
                return false
            }
            settings.nextRunAt = nextRunAt
            settings.armedIntervals = intervals
            settings.scheduleOrigin = origin
            return true
        }
    }
}
