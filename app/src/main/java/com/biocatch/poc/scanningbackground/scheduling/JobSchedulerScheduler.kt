package com.biocatch.poc.scanningbackground.scheduling

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
 * API 21+. The system starts [ScanJobService] in a fresh process when the job is due, so it runs
 * after the app was swiped away, and a persisted job is restored by the system after a reboot.
 *
 * Timing is "not before", never exact:
 * - The minimum latency is enforced by a non-wakeup, windowed system alarm, so a run can start a
 *   few minutes late, and while the device sleeps it waits for the next wakeup.
 * - From Android 15 (API 35), behind a system flag, background jobs that need network are batched
 *   even for apps in the ACTIVE standby bucket: a ready job can be held up to
 *   `conn_max_connectivity_job_batch_delay_ms` (31 minutes; measured on Android 17 / API 37), and
 *   while the screen is on it may wait until the screen turns off. Not seen on API 24, where runs
 *   started within seconds. Jobs of an app that is on screen are not batched.
 *
 * Exact timing, if it is ever required, is not possible with JobScheduler or WorkManager (which
 * runs on top of it). It needs AlarmManager.setExactAndAllowWhileIdle firing [AlarmScheduler]'s
 * receiver, which runs the scan itself:
 * - API 19-30: no permission needed; [AlarmScheduler] already uses setExact there.
 * - API 31+: the SCHEDULE_EXACT_ALARM permission. On Android 14+ (API 34) it is denied by default
 *   for new installs, so the user must allow "Alarms & reminders" in Settings; check
 *   AlarmManager.canScheduleExactAlarms() and open Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM.
 *   USE_EXACT_ALARM is granted automatically, but Google Play allows it only for alarm, clock and
 *   calendar apps.
 * - In Doze, Android rate-limits allow-while-idle alarms (the Doze docs give about once per 9
 *   minutes per app).
 *
 * Alternative, not used: a periodic job, where the system owns the cycle instead of each run arming
 * the next one-shot job. The SCHEDULED branch would become:
 *
 * ```
 * builder
 *     .setPeriodic(delayMillis)
 *     .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
 *     .setPersisted(true)
 * ```
 *
 * Compared with the one-shot job used here:
 * - The cycle cannot break. [ScanJobService] arms the next run after jobFinished, so a process killed
 *   between the two leaves nothing scheduled until the app is opened again. A periodic job stays
 *   scheduled across runs, process death and, when persisted, reboot.
 * - Retries are kept. A run stopped by the system (onStopJob returning true) or finished with
 *   jobFinished(params, true) is retried with backoff and then returns to its period. With one-shot
 *   jobs that retry is replaced when the run arms the next interval under the same job id.
 * - Offline is the same: the run waits for network, and periods missed while offline are not
 *   accumulated, so one run happens when the network returns.
 * - Limits: from API 24 the period is at least 15 minutes ([SchedulingLimits.PERIODIC_MIN_MINUTES]);
 *   shorter values are raised silently. With setPeriodic(interval) the run may fall anywhere in the
 *   period; setPeriodic(interval, flex) narrows it to the last flex part (at least 5 minutes). It
 *   cannot be combined with setMinimumLatency or setOverrideDeadline. One-shot jobs have no minimum
 *   interval, which is why they are used here.
 * - Switching also needs [ScanJobService] to stop calling ScanScheduling.armNextScheduledRun, since
 *   scheduling the same job id again restarts the period on every run, and SettingsStore.nextRunAt
 *   becomes an estimate, because the system picks the time inside each period.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class JobSchedulerScheduler(private val context: Context) : BackgroundScheduler {

    override val name: String = NAME

    private val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

    override fun isScheduled(): Boolean = jobScheduler.allPendingJobs.any { it.id == SCHEDULED_JOB_ID }

    override fun schedule(trigger: Trigger, delayMillis: Long): Boolean {
        val extras = PersistableBundle().apply { putString(ScanJobService.EXTRA_TRIGGER, trigger.name) }
        val builder = JobInfo.Builder(jobId(trigger), ComponentName(context, ScanJobService::class.java))
            .setExtras(extras)
        when (trigger) {
            // No override deadline here: a deadline would start the job without network and lose "send when online".
            Trigger.SCHEDULED -> builder
                .setMinimumLatency(delayMillis)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
            // No network constraint, so being offline shows up right away as a failed run.
            Trigger.MANUAL -> builder.setOverrideDeadline(0)
        }
        return try {
            jobScheduler.schedule(builder.build()) == JobScheduler.RESULT_SUCCESS
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    override fun cancelScheduled() = jobScheduler.cancel(SCHEDULED_JOB_ID)

    private fun jobId(trigger: Trigger): Int = if (trigger == Trigger.SCHEDULED) SCHEDULED_JOB_ID else MANUAL_JOB_ID

    companion object {
        const val NAME = "JobScheduler"
        private const val SCHEDULED_JOB_ID = 1001
        private const val MANUAL_JOB_ID = 1002
    }
}
