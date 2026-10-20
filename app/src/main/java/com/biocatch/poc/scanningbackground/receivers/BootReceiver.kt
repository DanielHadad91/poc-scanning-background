package com.biocatch.poc.scanningbackground.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.biocatch.poc.scanningbackground.R
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.scheduling.BackgroundScheduler
import com.biocatch.poc.scanningbackground.scheduling.ScanScheduling
import com.biocatch.poc.scanningbackground.storage.RunStore

/**
 * Wakes the app after a reboot without the user opening it, records the boot, and re-arms the
 * schedule. Android delivers BOOT_COMPLETED only to an app opened at least once and not force-stopped.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext

        RunStore(appContext).append(
            LogEntry(
                kind = EntryKind.BOOT,
                timestamp = System.currentTimeMillis(),
                scheduler = BackgroundScheduler.from(appContext).name,
                message = appContext.getString(R.string.log_boot),
            )
        )
        // JobScheduler restores a persisted job by itself, but AlarmManager loses alarms on reboot;
        // re-arming in both cases gives one clear "next run" measured from the boot.
        ScanScheduling.reschedule(appContext, appContext.getString(R.string.log_schedule_after_boot), ScheduleOrigin.AFTER_BOOT)
    }
}
