package com.biocatch.poc.scanningbackground.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.biocatch.poc.scanningbackground.sdk.R
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.scheduling.ScanScheduler
import com.biocatch.poc.scanningbackground.scheduling.ScanScheduling
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/**
 * Wakes the app after a reboot without the user opening it and records the boot. A schedule that
 * was not cancelled is armed again. API 19–20 calls setInexactRepeating at the boot and passes the
 * stored offset as the first fire. That delivery is the scan. API 21+ registers the periodic job at
 * the boot, with no offset wait. This receiver does not scan. A cancelled
 * schedule stays off. Android delivers BOOT_COMPLETED only to an app opened at least once and
 * not force-stopped.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext

        RunStore(appContext).append(
            LogEntry(
                kind = EntryKind.BOOT,
                timestamp = System.currentTimeMillis(),
                scheduler = ScanScheduler.from(appContext).name,
                message = appContext.getString(R.string.log_boot),
            )
        )
        if (SettingsStore(appContext).scheduleCancelled) {
            RunStore(appContext).append(
                LogEntry(
                    kind = EntryKind.SCHEDULE,
                    timestamp = System.currentTimeMillis(),
                    scheduler = ScanScheduler.from(appContext).name,
                    message = appContext.getString(R.string.log_boot_cancelled),
                )
            )
            return
        }
        // Alarms are dropped on reboot. A persisted job can return on its previous period, so this
        // replaces it. API 19–20 calls setInexactRepeating at the boot and passes the stored offset as
        // the first fire. API 21+ registers the periodic job at the boot. This does not scan.
        ScanScheduling.reschedule(
            appContext,
            appContext.getString(R.string.log_schedule_after_boot),
            ScheduleOrigin.AFTER_BOOT,
        )
    }
}
