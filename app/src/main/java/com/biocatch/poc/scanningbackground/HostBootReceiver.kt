package com.biocatch.poc.scanningbackground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.biocatch.poc.scanningbackground.R
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.scheduling.ScanJobSchedulerContract
import com.biocatch.poc.scanningbackground.storage.RunStore

/**
 * Second BOOT_COMPLETED receiver, owned by the host. It records that it ran and does not schedule.
 * Android delivers the boot broadcast to this receiver and to the SDK [com.biocatch.poc.scanningbackground.scanjobschedulerbelow21.BootReceiver].
 * Neither cancels the other. This receiver does not schedule the periodic scan or the repeating alarm.
 */
class HostBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        RunStore(appContext).append(
            LogEntry(
                kind = EntryKind.BOOT,
                timestamp = System.currentTimeMillis(),
                scheduler = ScanJobSchedulerContract.from(appContext).name,
                message = appContext.getString(R.string.log_host_boot),
            )
        )
    }
}
