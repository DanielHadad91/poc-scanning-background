package com.biocatch.poc.scanningbackground.scheduling

import android.os.Build

/**
 * The timing guarantee of the active scheduler on this Android version. The scheduled time is always
 * the earliest a run may start; this describes how late it may actually run and why.
 */
enum class TimingLimit {
    /** AlarmManager setExact on API 19-22: fires at the set time; these versions have no Doze. */
    EXACT,

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

    /**
     * The minimum interval Android enforces. It is 15 minutes only for PERIODIC jobs
     * (JobInfo.getMinPeriodMillis / WorkManager PeriodicWorkRequest). We use one-time, self-rescheduling
     * jobs, which have no such minimum, so the app imposes no per-version floor and never blocks a value.
     */
    const val PERIODIC_MIN_MINUTES = 15

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
        if (schedulerName == AlarmScheduler.NAME) {
            when {
                sdkInt < Build.VERSION_CODES.M -> TimingLimit.EXACT
                sdkInt < Build.VERSION_CODES.S -> TimingLimit.EXACT_DEFERRED_IN_DOZE
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
