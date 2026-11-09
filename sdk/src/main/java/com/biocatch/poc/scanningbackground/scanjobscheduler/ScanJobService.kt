package com.biocatch.poc.scanningbackground.scanjobscheduler

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.scan.ScanRunner
import kotlin.concurrent.thread

/**
 * API 21+. Started by the system, also when the app is closed, so it never relies on the screen.
 * The periodic scan repeats by itself. A finished scan does not register the next run.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class ScanJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val trigger = params.extras.getString(EXTRA_TRIGGER)?.let(Trigger::fromStored) ?: Trigger.PERIODIC_SCAN
        thread(name = "scan-job") {
            ScanRunner(applicationContext).run(trigger, ScanJobScheduler.NAME)
            // Scheduling the periodic scan again would restart the period. setPeriodic already repeats.
            jobFinished(params, false)
        }
        return true
    }

    /** A periodic scan stopped by the system is retried. An immediate scan is not. */
    override fun onStopJob(params: JobParameters): Boolean {
        val stored = params.extras.getString(EXTRA_TRIGGER)
        return stored?.let(Trigger::fromStored) != Trigger.IMMEDIATE_SCAN
    }

    companion object {
        const val EXTRA_TRIGGER = "trigger"
    }
}
