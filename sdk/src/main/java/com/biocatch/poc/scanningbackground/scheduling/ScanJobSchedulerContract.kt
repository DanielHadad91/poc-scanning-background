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

    /** Platform mechanism, stored on each history line. "JobScheduler" or "AlarmManager". */
    val name: String

    /** True for the alarm. Its first fire waits for the stored offset. False for the periodic job. */
    val usesAlarmOffset: Boolean

    /** True when the repeating scan is pending. */
    fun isScanExists(): Boolean

    /**
     * Arms the repeating scan. The alarm adds [initialDelayMillis] to now as the first-fire time.
     * That delivery is the scan. After boot that delay is the stored offset counted from the boot.
     * The periodic job ignores [initialDelayMillis]: the period starts when this call runs, and the
     * scan may fall anywhere in the period. Returns false if the system refused it.
     */
    fun scan(initialDelayMillis: Long, periodMillis: Long): Boolean

    /** Removes the repeating scan. An immediate scan that has already started keeps running. */
    fun cancelScan()

    /**
     * POC only. One scan now, beside the repeating scan. Not part of the production API.
     * Returns false if the system refused it.
     */
    fun runNow(): Boolean

    /** Millis until the periodic scan's first fire. The periodic job starts immediately, so this is 0. */
    fun periodicStartDelayMillis(intervals: Int, periodMillis: Long, now: Long, afterBoot: Boolean): Long = 0L

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
        fun ensureScheduled(context: Context) {
            val scheduler = from(context)
            val settings = SettingsStore(context)
            val intervals = settings.intervals
            if (scheduler.isScanExists() && settings.armedIntervals == intervals) {
                settings.scheduleOrigin = ScheduleOrigin.EXISTING_ON_OPEN
                return
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
            reschedule(context, afterBoot = false, origin, reason)
        }

        /**
         * Replaces whatever is armed. The API 19–20 boot receiver calls this so the first fire is the
         * boot plus the stored offset. A persisted periodic job is not replaced on boot.
         * The history line is recorded here. Callers do not receive it.
         */
        fun reschedule(context: Context, afterBoot: Boolean, origin: ScheduleOrigin, reason: String) {
            val scheduler = from(context)
            if (scheduler.isScanExists()) scheduler.cancelScan()
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
            val initialDelayMillis = scheduler.periodicStartDelayMillis(intervals, periodMillis, now, afterBoot)
            if (!scheduler.scan(initialDelayMillis, periodMillis)) {
                settings.nextRunAt = 0L
                settings.armedIntervals = 0
                return false
            }
            settings.nextRunAt = if (scheduler.usesAlarmOffset) now + initialDelayMillis else now + periodMillis
            settings.armedIntervals = intervals
            settings.scheduleOrigin = origin
            return true
        }
    }
}
