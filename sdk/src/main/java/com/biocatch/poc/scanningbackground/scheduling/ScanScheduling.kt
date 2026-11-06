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
        val scheduler = BackgroundScheduler.from(context)
        val settings = SettingsStore(context)
        val intervals = settings.intervals
        if (scheduler.isScheduled() && settings.armedIntervals == intervals) {
            settings.scheduleOrigin = ScheduleOrigin.EXISTING_ON_OPEN
            return null
        }
        val intervalChanged = scheduler.isScheduled()
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
     * at the boot and passes the stored offset as the first fire. API 21+ waits that offset, then
     * registers the periodic job.
     */
    fun reschedule(context: Context, reason: String, origin: ScheduleOrigin): LogEntry {
        val scheduler = BackgroundScheduler.from(context)
        scheduler.cancelScheduled()
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

    fun runNow(context: Context): Boolean = BackgroundScheduler.from(context).schedule(Trigger.MANUAL, 0, 0)

    /** Returns the recorded next-run time, or null if the system refused it. */
    private fun armNext(context: Context, scheduler: BackgroundScheduler, origin: ScheduleOrigin): Long? {
        val settings = SettingsStore(context)
        val intervals = settings.intervals
        if (!ScanWindow.isValid(intervals)) {
            settings.nextRunAt = 0L
            settings.armedIntervals = 0
            return null
        }
        val periodMillis = ScanWindow.periodMillis(intervals)
        val now = System.currentTimeMillis()
        val newPhase = settings.phaseIntervals != intervals || settings.phaseOffsetMillis < 0L
        if (newPhase) {
            settings.phaseOffsetMillis = Random().nextInt(periodMillis.toInt()).toLong()
            settings.phaseIntervals = intervals
            settings.windowStartMillis = now
        } else if (origin == ScheduleOrigin.AFTER_BOOT || settings.windowStartMillis < 0L) {
            // Boot keeps the stored offset. The window start becomes the boot, so the delay is that offset.
            settings.windowStartMillis = now
        }
        // A scanSchedule call and a boot both pass this delay into the scheduler.
        // API 19–20 calls setInexactRepeating now. The first-fire time is now plus this delay.
        // API 21+ waits this delay, then registers the periodic job.
        val initialDelayMillis = ScanWindow.delayUntilPhase(
            now,
            periodMillis,
            settings.phaseOffsetMillis,
            settings.windowStartMillis,
        )
        val deferRegistration = scheduler.name == JobSchedulerScheduler.NAME
        val trigger = if (deferRegistration) Trigger.REGISTER else Trigger.SCHEDULED
        if (!scheduler.schedule(trigger, initialDelayMillis, periodMillis)) {
            settings.nextRunAt = 0L
            settings.armedIntervals = 0
            return null
        }
        // AlarmManager registers now and the first fire is this delay later. That fire is the scan.
        // The periodic job starts when the wait ends, and its run
        // can fall anywhere before the end of that period.
        val nextRunAt = if (deferRegistration) now + initialDelayMillis + periodMillis else now + initialDelayMillis
        settings.nextRunAt = nextRunAt
        settings.armedIntervals = intervals
        settings.scheduleOrigin = origin
        return nextRunAt
    }
}
