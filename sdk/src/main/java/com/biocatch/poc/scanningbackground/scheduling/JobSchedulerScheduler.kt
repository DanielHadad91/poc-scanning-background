package com.biocatch.poc.scanningbackground.scheduling

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.services.ScanJobService
import com.biocatch.poc.scanningbackground.storage.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * API 21+. The scheduled scan is one periodic job. The system starts [ScanJobService] in a fresh
 * process when a period is due, including after the app was swiped away, and [JobInfo.Builder.setPersisted]
 * restores that job after reboot. [com.biocatch.poc.scanningbackground.receivers.BootReceiver] then
 * replaces it, waits the stored offset from the boot, and then registers the periodic job again.
 *
 * [setPeriodic] cannot take a start delay, so a normal arm first posts job [REGISTER_JOB_ID]. That
 * job does not scan. It waits until the same offset the alarm would use, then registers the periodic
 * job, and the period starts at that registration. The run may still fall anywhere inside the period.
 * [com.biocatch.poc.scanningbackground.storage.SettingsStore.nextRunAt] is the end of the current
 * period, not the clock time the system will pick. From API 24 a period
 * shorter than [SchedulingLimits.PERIODIC_MIN_MINUTES] is raised to that floor. From Android 15 (API 35)
 * a ready network job can also wait for the connectivity batch, up to about 31 minutes, and while the
 * screen is on it may wait until the screen turns off.
 *
 * Calling [schedule] again on [SCHEDULED_JOB_ID] restarts the period. A finished run must not call it.
 * The repeat belongs to [JobInfo.Builder.setPeriodic]. [cancelScheduled] removes the periodic job and the registration job. If the periodic job is
 * executing, [android.app.job.JobScheduler.cancel] stops it and ignores
 * [android.app.job.JobService.onStopJob]'s return value, so the job is not put back.
 *
 * WorkManager's periodic work is the same JobScheduler job plus an AndroidX dependency, so it is not
 * used. Exact repeating alarms are not a JobScheduler feature.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class JobSchedulerScheduler(private val context: Context) : BackgroundScheduler {

    override val name: String = NAME

    private val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    override fun isScheduled(): Boolean = jobScheduler.allPendingJobs.any {
        it.id == SCHEDULED_JOB_ID || it.id == REGISTER_JOB_ID
    }

    /** True when the periodic scan job itself is pending. The registration job does not count. */
    fun hasPeriodicJob(): Boolean = jobScheduler.allPendingJobs.any { it.id == SCHEDULED_JOB_ID }

    /**
     * Called by the registration job. Registers the periodic scan at this moment.
     * Returns false only when the system refused and the registration should be retried.
     */
    fun registerPeriodic(): Boolean {
        val settings = SettingsStore(context)
        if (settings.scheduleCancelled || !ScanWindow.isValid(settings.intervals)) return true
        if (hasPeriodicJob()) return true
        val periodMillis = ScanWindow.periodMillis(settings.intervals)
        if (!schedule(Trigger.SCHEDULED, 0L, periodMillis)) return false
        if (SettingsStore(context).scheduleCancelled) {
            cancelScheduled()
            return true
        }
        settings.nextRunAt = System.currentTimeMillis() + periodMillis
        return true
    }

    // setPersisted requires RECEIVE_BOOT_COMPLETED, declared by the host app (an SDK declares no permissions).
    @SuppressLint("MissingPermission")
    override fun schedule(trigger: Trigger, initialDelayMillis: Long, periodMillis: Long): Boolean {
        val extras = PersistableBundle().apply { putString(ScanJobService.EXTRA_TRIGGER, trigger.name) }
        val builder = JobInfo.Builder(jobId(trigger), ComponentName(context, ScanJobService::class.java))
            .setExtras(extras)
        when (trigger) {
            // No override deadline: a deadline would start the job without network and lose "send when online".
            // setPeriodic cannot take a delay. The period starts when this call runs.
            // The registration job makes this call at the stored offset, including after boot.
            Trigger.SCHEDULED -> builder
                .setPeriodic(enforcedPeriodMillis(periodMillis))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
            // Waits until the alarm offset, then registers the periodic job. It does not scan.
            // Not persisted: a reboot drops it, and the boot receiver schedules the wait again.
            Trigger.REGISTER -> builder
                .setMinimumLatency(initialDelayMillis)
                .setOverrideDeadline(initialDelayMillis)
            // No network constraint, so being offline shows up right away as a failed run.
            Trigger.MANUAL -> builder.setOverrideDeadline(0)
        }
        return try {
            jobScheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun cancelScheduled() {
        jobScheduler.cancel(SCHEDULED_JOB_ID)
        jobScheduler.cancel(REGISTER_JOB_ID)
    }

    /** API 24+ raises a shorter period to 15 minutes. Earlier releases accept the given period. */
    private fun enforcedPeriodMillis(periodMillis: Long): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return periodMillis
        val floor = TimeUnit.MINUTES.toMillis(SchedulingLimits.PERIODIC_MIN_MINUTES.toLong())
        return periodMillis.coerceAtLeast(floor)
    }

    private fun jobId(trigger: Trigger): Int = when (trigger) {
        Trigger.SCHEDULED -> SCHEDULED_JOB_ID
        Trigger.MANUAL -> MANUAL_JOB_ID
        Trigger.REGISTER -> REGISTER_JOB_ID
    }

    companion object {
        const val NAME = "JobScheduler"
        private const val SCHEDULED_JOB_ID = 1001
        private const val MANUAL_JOB_ID = 1002
        private const val REGISTER_JOB_ID = 1003
    }
}
