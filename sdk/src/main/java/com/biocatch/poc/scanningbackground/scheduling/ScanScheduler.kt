package com.biocatch.poc.scanningbackground.scheduling

import android.content.Context
import android.os.Build
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/** Arms the repeating scan. The system keeps the repeat; a finished run does not schedule the next one. */
interface ScanScheduler {

    /** Platform mechanism, stored on each history line. "JobScheduler" or "AlarmManager". */
    val name: String

    /**
     * True on the alarm path. The first fire is this call plus the stored offset, and that delivery
     * is the scan. False for the periodic job: the period starts when [schedule] runs.
     */
    val firstFireUsesStoredOffset: Boolean

    /** True when the repeating scan is pending. The immediate scan does not count. */
    fun isPeriodicScanPending(): Boolean

    /**
     * Arms [trigger]. [Trigger.PERIODIC_SCAN] repeats every [periodMillis]. On API 19–20 the call is
     * immediate and [initialDelayMillis] is added to now as the first-fire time. That delivery is the
     * scan. After boot that delay is the stored offset counted from the boot. On API 21+ the periodic
     * job is registered at this call, [initialDelayMillis] is ignored, and the scan may fall anywhere
     * in the period. [Trigger.IMMEDIATE_SCAN] is one run. Returns false if the system refused it.
     */
    fun schedule(trigger: Trigger, initialDelayMillis: Long, periodMillis: Long): Boolean

    /** Removes the repeating scan. An immediate scan that has already started keeps running. */
    fun cancelPeriodicScan()

    companion object {
        /** JobScheduler exists from API 21; API 19-20 fall back to AlarmManager. */
        fun from(context: Context): ScanScheduler =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !SettingsStore(context).forceAlarmFallback) {
                ScanJobScheduler(context)
            } else {
                AlarmScheduler(context)
            }
    }
}
