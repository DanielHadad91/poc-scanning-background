package com.biocatch.poc.scanningbackground.scheduling

import android.content.Context
import android.os.Build
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/** Arms one scan at a time; the run itself arms the next, so any interval in minutes is possible. */
interface BackgroundScheduler {

    val name: String

    fun isScheduled(): Boolean

    /** Arms a single [trigger] run [delayMillis] from now. Returns false if the system refused it. */
    fun schedule(trigger: Trigger, delayMillis: Long): Boolean

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
