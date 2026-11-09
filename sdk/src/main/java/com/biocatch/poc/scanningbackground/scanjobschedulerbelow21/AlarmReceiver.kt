package com.biocatch.poc.scanningbackground.scanjobschedulerbelow21

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.scan.ScanRunner
import kotlin.concurrent.thread

/**
 * Fired by [ScanJobSchedulerBelow21] on API 19-20. goAsync() keeps the receiver alive after onReceive, but the
 * system still ends it at the broadcast timeout (about 10-60 s), which bounds how long a run may take.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val trigger = intent.getStringExtra(EXTRA_TRIGGER)?.let(Trigger::fromStored) ?: Trigger.PERIODIC_SCAN
        // A scheduled alarm is setInexactRepeating, so the next fire is already registered.

        val pending = goAsync()
        thread(name = "scan-alarm") {
            try {
                ScanRunner(appContext).run(trigger, ScanJobSchedulerBelow21.NAME)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TRIGGER = "trigger"
    }
}
