package com.biocatch.poc.scanningbackground.scanjobschedulerbelow21

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.scheduling.ScanJobSchedulerContract
import com.biocatch.poc.scanningbackground.sdk.R
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/**
 * API 19–20. Alarms are lost on reboot, so this registers setInexactRepeating again. The first fire
 * is the boot plus the stored offset, and that delivery is the scan. A persisted JobScheduler job is
 * left as it was. A cancelled schedule stays off. This receiver does not scan.
 * Public because the host manifest names this class.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        val scheduler = ScanJobSchedulerContract.from(appContext)
        if (!scheduler.usesAlarmOffset) return
        RunStore(appContext).append(
            LogEntry(
                kind = EntryKind.BOOT,
                timestamp = System.currentTimeMillis(),
                scheduler = scheduler.name,
                message = appContext.getString(R.string.log_boot),
            )
        )
        if (SettingsStore(appContext).scheduleCancelled) {
            RunStore(appContext).append(
                LogEntry(
                    kind = EntryKind.SCHEDULE,
                    timestamp = System.currentTimeMillis(),
                    scheduler = scheduler.name,
                    message = appContext.getString(R.string.log_boot_cancelled),
                )
            )
            return
        }
        ScanJobSchedulerContract.reschedule(
            appContext,
            afterBoot = true,
            origin = ScheduleOrigin.AFTER_BOOT,
            reason = appContext.getString(R.string.log_schedule_after_boot),
        )
    }
}
