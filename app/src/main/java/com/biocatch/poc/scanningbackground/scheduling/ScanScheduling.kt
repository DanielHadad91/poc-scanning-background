package com.biocatch.poc.scanningbackground.scheduling

import android.content.Context
import com.biocatch.poc.scanningbackground.R
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore
import java.util.concurrent.TimeUnit

/** Schedule operations used by the screen, the receivers and the running job, each recorded in [RunStore]. */
object ScanScheduling {

    /**
     * Arms the cycle only if nothing is armed, so opening the app does not reset the timer. If a
     * schedule was armed before but is gone now, it was lost (force stop or an aggressive OEM), and
     * the re-creation is marked [ScheduleOrigin.RECREATED_ON_REENTRY].
     */
    fun ensureScheduled(context: Context): LogEntry? {
        val scheduler = BackgroundScheduler.from(context)
        val settings = SettingsStore(context)
        if (scheduler.isScheduled()) {
            settings.scheduleOrigin = ScheduleOrigin.EXISTING_ON_OPEN
            return null
        }
        val recovering = settings.nextRunAt > 0L
        val origin = if (recovering) ScheduleOrigin.RECREATED_ON_REENTRY else ScheduleOrigin.FIRST_OPEN
        val reason = context.getString(if (recovering) R.string.log_schedule_recreated else R.string.log_schedule_first)
        return reschedule(context, reason, origin)
    }

    /** Replaces whatever is armed with a run one interval from now and records the result. */
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
        )
        RunStore(context).append(entry)
        return entry
    }

    /** Continues the cycle from a scheduled run; only a refusal is recorded, to keep the history readable. */
    fun armNextScheduledRun(context: Context) {
        val scheduler = BackgroundScheduler.from(context)
        if (armNext(context, scheduler, ScheduleOrigin.CONTINUED_AFTER_RUN) == null) {
            RunStore(context).append(
                LogEntry(
                    kind = EntryKind.SCHEDULE,
                    timestamp = System.currentTimeMillis(),
                    scheduler = scheduler.name,
                    message = context.getString(R.string.log_schedule_after_run_failed),
                )
            )
        }
    }

    fun runNow(context: Context): Boolean = BackgroundScheduler.from(context).schedule(Trigger.MANUAL, 0)

    /** Returns the time the next scheduled run is armed for, or null if the system refused it. */
    private fun armNext(context: Context, scheduler: BackgroundScheduler, origin: ScheduleOrigin): Long? {
        val settings = SettingsStore(context)
        val delayMillis = TimeUnit.MINUTES.toMillis(settings.intervalMinutes.toLong())
        val nextRunAt = System.currentTimeMillis() + delayMillis
        if (!scheduler.schedule(Trigger.SCHEDULED, delayMillis)) {
            settings.nextRunAt = 0L
            return null
        }
        settings.nextRunAt = nextRunAt
        settings.scheduleOrigin = origin
        return nextRunAt
    }
}
