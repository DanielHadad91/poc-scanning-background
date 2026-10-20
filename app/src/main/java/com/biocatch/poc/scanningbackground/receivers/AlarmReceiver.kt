package com.biocatch.poc.scanningbackground.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.scheduling.AlarmScheduler
import com.biocatch.poc.scanningbackground.scheduling.ScanScheduling
import com.biocatch.poc.scanningbackground.services.ScanRunner
import kotlin.concurrent.thread

/**
 * Fired by [AlarmScheduler] on API 19-20. goAsync() keeps the receiver alive after onReceive, but the
 * system still ends it at the broadcast timeout (about 10-60 s), which bounds how long a run may take.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val trigger = intent.getStringExtra(EXTRA_TRIGGER)?.let(Trigger::valueOf) ?: Trigger.SCHEDULED
        // Armed before the work, so the cycle continues even if the process is killed mid-run.
        if (trigger == Trigger.SCHEDULED) ScanScheduling.armNextScheduledRun(appContext)

        val pending = goAsync()
        thread(name = "scan-alarm") {
            try {
                ScanRunner(appContext).run(trigger, AlarmScheduler.NAME)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TRIGGER = "trigger"
    }
}
