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

/**
 * API 21+. The scheduled scan is one periodic job. The system starts [ScanJobService] in a fresh
 * process when a period is due, including after the app was swiped away, and [JobInfo.Builder.setPersisted]
 * restores that job after reboot. [com.biocatch.poc.scanningbackground.receivers.BootReceiver] then
 * replaces it by registering the periodic job again at the boot. There is no offset wait.
 *
 * The period starts when [schedule] runs. The scan may fall anywhere inside that period.
 * [com.biocatch.poc.scanningbackground.storage.SettingsStore.nextRunAt] is the end of the current
 * period, not the clock time the system will pick. From API 24 the platform refuses a
 * periodic job shorter than 15 minutes ([JobInfo.getMinPeriodMillis]). This schedule is
 * 1, 2, or 3 runs in 24 hours, so the period is 24, 12, or 8 hours and cannot reach that
 * floor. The SDK does not check it. From Android 15 (API 35)
 * a ready network job can also wait for the connectivity batch, up to about 31 minutes, and while the
 * screen is on it may wait until the screen turns off.
 *
 * Calling [schedule] again on [PERIODIC_SCAN_JOB_ID] restarts the period. A finished run must not call it.
 * The repeat belongs to [JobInfo.Builder.setPeriodic]. [cancelPeriodicScan] removes the periodic scan.
 * If that job is executing, [android.app.job.JobScheduler.cancel] stops it and ignores
 * [android.app.job.JobService.onStopJob]'s return value, so the job is not put back.
 *
 * WorkManager's periodic work is the same JobScheduler job plus an AndroidX dependency, so it is not
 * used. Exact repeating alarms are not a JobScheduler feature.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class ScanJobScheduler(private val context: Context) : ScanScheduler {

    override val name: String = NAME

    override val firstFireUsesStoredOffset: Boolean = false

    private val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    override fun isPeriodicScanPending(): Boolean =
        jobScheduler.allPendingJobs.any { it.id == PERIODIC_SCAN_JOB_ID }

    // setPersisted requires RECEIVE_BOOT_COMPLETED, declared by the host app (an SDK declares no permissions).
    @SuppressLint("MissingPermission")
    override fun schedule(trigger: Trigger, initialDelayMillis: Long, periodMillis: Long): Boolean {
        val extras = PersistableBundle().apply { putString(ScanJobService.EXTRA_TRIGGER, trigger.name) }
        val builder = JobInfo.Builder(jobId(trigger), ComponentName(context, ScanJobService::class.java))
            .setExtras(extras)
        when (trigger) {
            // No override deadline: a deadline would start the job without network and lose "send when online".
            // setPeriodic cannot take a delay. The period starts when this call runs, including after boot.
            Trigger.PERIODIC_SCAN -> builder
                .setPeriodic(periodMillis)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
            // No network constraint, so being offline shows up right away as a failed run.
            Trigger.IMMEDIATE_SCAN -> builder.setOverrideDeadline(0)
        }
        return try {
            jobScheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun cancelPeriodicScan() {
        jobScheduler.cancel(PERIODIC_SCAN_JOB_ID)
    }

    private fun jobId(trigger: Trigger): Int = when (trigger) {
        Trigger.PERIODIC_SCAN -> PERIODIC_SCAN_JOB_ID
        Trigger.IMMEDIATE_SCAN -> IMMEDIATE_SCAN_JOB_ID
    }

    companion object {
        const val NAME = "JobScheduler"
        /** Repeating scan. setPeriodic owns the repeat. */
        private const val PERIODIC_SCAN_JOB_ID = 1001
        /** One scan now, beside the periodic scan. */
        private const val IMMEDIATE_SCAN_JOB_ID = 1002
    }
}
