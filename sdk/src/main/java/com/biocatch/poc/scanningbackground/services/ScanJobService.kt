package com.biocatch.poc.scanningbackground.services

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.scheduling.JobSchedulerScheduler
import com.biocatch.poc.scanningbackground.scheduling.ScanScheduling
import kotlin.concurrent.thread

/** API 21+. Started by the system, also when the app is closed, so it never relies on the screen. */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class ScanJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val trigger = params.extras.getString(EXTRA_TRIGGER)?.let(Trigger::valueOf) ?: Trigger.SCHEDULED
        thread(name = "scan-job") {
            ScanRunner(applicationContext).run(trigger, JobSchedulerScheduler.NAME)
            // Finish first: scheduling the same job id while it is still running would stop it.
            jobFinished(params, false)
            if (trigger == Trigger.SCHEDULED) ScanScheduling.armNextScheduledRun(applicationContext)
        }
        return true
    }

    /** A scheduled run stopped by the system is retried; a manual one is not. */
    override fun onStopJob(params: JobParameters): Boolean =
        params.extras.getString(EXTRA_TRIGGER) != Trigger.MANUAL.name

    companion object {
        const val EXTRA_TRIGGER = "trigger"
    }
}
