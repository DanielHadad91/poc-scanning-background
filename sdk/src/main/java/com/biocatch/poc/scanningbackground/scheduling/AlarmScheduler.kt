package com.biocatch.poc.scanningbackground.scheduling

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.receivers.AlarmReceiver

/**
 * API 19-20, where JobScheduler does not exist. Alarms survive the app being swiped away but not a
 * reboot, so [com.biocatch.poc.scanningbackground.receivers.BootReceiver] re-arms them. There is
 * no network constraint: an offline run is recorded and the next interval tries again.
 */
class AlarmScheduler(private val context: Context) : BackgroundScheduler {

    override val name: String = NAME

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun isScheduled(): Boolean = pendingIntent(Trigger.SCHEDULED, PendingIntent.FLAG_NO_CREATE) != null

    @SuppressLint("MissingPermission")
    override fun schedule(trigger: Trigger, delayMillis: Long): Boolean {
        val operation = pendingIntent(trigger, PendingIntent.FLAG_UPDATE_CURRENT) ?: return false
        val triggerAt = System.currentTimeMillis() + delayMillis
        // Exact alarms need SCHEDULE_EXACT_ALARM from API 31; below that setExact needs no permission.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
        return true
    }

    override fun cancelScheduled() {
        pendingIntent(Trigger.SCHEDULED, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /** One-shot, so after an alarm fires [isScheduled] reports false until the run arms the next one. */
    private fun pendingIntent(trigger: Trigger, flags: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_TRIGGER, trigger.name)
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val requestCode = if (trigger == Trigger.SCHEDULED) REQUEST_SCHEDULED else REQUEST_MANUAL
        return PendingIntent.getBroadcast(context, requestCode, intent, flags or immutable or PendingIntent.FLAG_ONE_SHOT)
    }

    companion object {
        const val NAME = "AlarmManager"
        private const val REQUEST_SCHEDULED = 2001
        private const val REQUEST_MANUAL = 2002
    }
}
