package com.biocatch.poc.scanningbackground.scheduling

import android.os.Build
import com.biocatch.poc.scanningbackground.scanjobschedulerbelow21.ScanJobSchedulerBelow21

/**
 * The timing guarantee of the active scheduler on this Android version. The scheduled time is always
 * the earliest a run may start; this describes how late it may actually run and why.
 */
enum class TimingLimit {
    /** AlarmManager setInexactRepeating on API 19-22: the repeat can drift; these versions have no Doze. */
    INEXACT,

    /** AlarmManager setExact on API 23-30: exact while awake, deferred to a maintenance window in Doze. */
    EXACT_DEFERRED_IN_DOZE,

    /** AlarmManager inexact set() on API 31+ (reached only via the forced-fallback test hook). */
    INEXACT_DEFERRED_IN_DOZE,

    /** JobScheduler on API 21-22: usually within seconds, no hard upper bound, no Doze. */
    JOB_NEAR_TIME,

    /** JobScheduler on API 23-34: near the time while awake, deferred to a maintenance window in Doze. */
    JOB_DOZE,

    /** JobScheduler on API 35+: network jobs are batched, so there is a concrete maximum delay. */
    JOB_BATCHED,
}

object SchedulingLimits {

    /**
     * conn_max_connectivity_job_batch_delay_ms (31 minutes), the cap JobScheduler applies when it
     * batches a ready network job. It is a system DeviceConfig value an ordinary app cannot read, so
     * it is hardcoded from the value measured on Android 17 (API 37); other devices may differ.
     */
    const val CONNECTIVITY_BATCH_MAX_DELAY_MINUTES = 31L

    /*
     * From API 24 a periodic JobScheduler job cannot be shorter than 15 minutes
     * (JobInfo.getMinPeriodMillis). setInexactRepeating has no matching floor.
     * One, two, or three runs in 24 hours is 24, 12, or 8 hours, so this schedule
     * cannot reach that floor and the SDK does not check it.
     */

    /**
     * How late a run may be before we count it as missed. Android does not guarantee a maximum delay
     * for deferrable work, so this is a tolerance, not a guarantee: the measured ~31 min batching cap on
     * API 35+, a Doze-window allowance on API 23-34, and near-exact on older versions.
     */
    fun missGraceMinutes(sdkInt: Int): Int = when {
        sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> CONNECTIVITY_BATCH_MAX_DELAY_MINUTES.toInt()
        sdkInt >= Build.VERSION_CODES.M -> 15
        else -> 2
    }

    fun forScheduler(schedulerName: String, sdkInt: Int): TimingLimit =
        if (schedulerName == ScanJobSchedulerBelow21.NAME) {
            when {
                sdkInt < Build.VERSION_CODES.M -> TimingLimit.INEXACT
                else -> TimingLimit.INEXACT_DEFERRED_IN_DOZE
            }
        } else {
            when {
                sdkInt >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> TimingLimit.JOB_BATCHED
                sdkInt >= Build.VERSION_CODES.M -> TimingLimit.JOB_DOZE
                else -> TimingLimit.JOB_NEAR_TIME
            }
        }

    /** The maximum delay after the scheduled time, when it is bounded; null when only qualitative. */
    fun maxDelayMinutes(limit: TimingLimit): Long? =
        if (limit == TimingLimit.JOB_BATCHED) CONNECTIVITY_BATCH_MAX_DELAY_MINUTES else null
}
