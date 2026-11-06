package com.biocatch.poc.scanningbackground.scheduling

import android.content.Context
import android.os.Build
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/** Arms the repeating scan. The system keeps the repeat; a finished run does not schedule the next one. */
interface BackgroundScheduler {

    val name: String

    fun isScheduled(): Boolean

    /**
     * Arms [trigger]. A scheduled trigger repeats every [periodMillis]. On API 19–20 the call is
     * immediate and [initialDelayMillis] is added to now as the first-fire time. That delivery is the
     * scan. On API 21+ the registration job waits [initialDelayMillis], then registers the periodic job.
     * After boot that delay is the stored offset counted from the boot. A manual trigger is one run.
     * Returns false if the system refused it.
     */
    fun schedule(trigger: Trigger, initialDelayMillis: Long, periodMillis: Long): Boolean

    fun cancelScheduled()

    companion object {
        /** JobScheduler exists from API 21; API 19-20 fall back to AlarmManager. */
        fun from(context: Context): BackgroundScheduler =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && !SettingsStore(context).forceAlarmFallback) {
                JobSchedulerScheduler(context)
            } else {
                AlarmScheduler(context)
            }
    }
}
