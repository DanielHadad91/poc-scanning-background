package com.biocatch.poc.scanningbackground.scanjobscheduler

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.scheduling.ScanJobSchedulerContract

/**
 * API 21+. The scheduled scan is one periodic job. The system starts [ScanJobService] in a fresh
 * process when a period is due, including after the app was swiped away, and [JobInfo.Builder.setPersisted]
 * restores that job after reboot. Boot does not register it again. There is no offset wait.
 *
 * The period starts when [scheduleScan] runs. The scan may fall anywhere inside that period.
 * From API 24 the platform refuses a periodic job shorter than 15 minutes ([JobInfo.getMinPeriodMillis]).
 * This schedule is 1, 2, or 3 runs in 24 hours, so the period is 24, 12, or 8 hours and cannot reach
 * that floor. The SDK does not check it. From Android 15 (API 35) a ready network job can also wait
 * for the connectivity batch, up to about 31 minutes, and while the screen is on it may wait until
 * the screen turns off.
 *
 * Calling [scheduleScan] again on [PERIODIC_SCAN_JOB_ID] restarts the period. A finished run must not call it.
 * The repeat belongs to [JobInfo.Builder.setPeriodic]. [cancelScheduledScan] removes the periodic scan.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
internal class ScanJobScheduler(private val context: Context) : ScanJobSchedulerContract {

    override val name: String = NAME

    private val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    override fun isScanScheduled(): Boolean =
        jobScheduler.allPendingJobs.any { it.id == PERIODIC_SCAN_JOB_ID }

    // setPersisted requires RECEIVE_BOOT_COMPLETED, declared by the host app (an SDK declares no permissions).
    // intervals and afterBoot belong to the alarm offset. setPeriodic cannot take a delay.
    @SuppressLint("MissingPermission")
    @Suppress("UNUSED_PARAMETER")
    override fun scheduleScan(intervals: Int, periodMillis: Long, now: Long, afterBoot: Boolean): Long? {
        val accepted = schedule(PERIODIC_SCAN_JOB_ID, Trigger.PERIODIC_SCAN) {
            // No override deadline: a deadline would start the job without network.
            it.setPeriodic(periodMillis)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
        }
        return if (accepted) now + periodMillis else null
    }

    override fun runNow(): Boolean =
        schedule(IMMEDIATE_SCAN_JOB_ID, Trigger.IMMEDIATE_SCAN) {
            it.setOverrideDeadline(0)
        }

    private fun schedule(jobId: Int, trigger: Trigger, configure: (JobInfo.Builder) -> Unit): Boolean {
        val extras = PersistableBundle().apply { putString(ScanJobService.EXTRA_TRIGGER, trigger.name) }
        val builder = JobInfo.Builder(jobId, ComponentName(context, ScanJobService::class.java)).setExtras(extras)
        configure(builder)
        return try {
            jobScheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun cancelScheduledScan() {
        jobScheduler.cancel(PERIODIC_SCAN_JOB_ID)
    }

    companion object {
        internal const val NAME = "JobScheduler"
        /** Repeating scan. setPeriodic owns the repeat. */
        private const val PERIODIC_SCAN_JOB_ID = 1001
        /** POC: one scan now, beside the periodic scan. */
        private const val IMMEDIATE_SCAN_JOB_ID = 1002
    }
}
