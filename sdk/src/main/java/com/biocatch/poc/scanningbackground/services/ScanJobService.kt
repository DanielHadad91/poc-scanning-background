package com.biocatch.poc.scanningbackground.services

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Build
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.scheduling.JobSchedulerScheduler
import kotlin.concurrent.thread

/**
 * API 21+. Started by the system, also when the app is closed, so it never relies on the screen.
 * The periodic job repeats by itself. A finished scan does not register the next run.
 * A registration job only calls setPeriodic once and does not scan.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class ScanJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val trigger = params.extras.getString(EXTRA_TRIGGER)?.let(Trigger::valueOf) ?: Trigger.SCHEDULED
        if (trigger == Trigger.REGISTER) {
            thread(name = "register-job") {
                val retry = !JobSchedulerScheduler(applicationContext).registerPeriodic()
                jobFinished(params, retry)
            }
            return true
        }
        thread(name = "scan-job") {
            ScanRunner(applicationContext).run(trigger, JobSchedulerScheduler.NAME)
            // Scheduling job 1001 again would restart the period. setPeriodic already repeats.
            jobFinished(params, false)
        }
        return true
    }

    /** A scheduled run stopped by the system is retried; a manual one is not. */
    override fun onStopJob(params: JobParameters): Boolean {
        val trigger = params.extras.getString(EXTRA_TRIGGER)
        if (trigger == Trigger.MANUAL.name) return false
        // Retry the registration only when the periodic job was not registered yet.
        if (trigger == Trigger.REGISTER.name) return !JobSchedulerScheduler(this).hasPeriodicJob()
        return true
    }

    companion object {
        const val EXTRA_TRIGGER = "trigger"
    }
}
