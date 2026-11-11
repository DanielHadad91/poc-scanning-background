package com.biocatch.poc.scanningbackground.scanjobschedulerbelow21

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.scheduling.ScanJobSchedulerContract
import com.biocatch.poc.scanningbackground.scheduling.ScanWindow
import com.biocatch.poc.scanningbackground.storage.SettingsStore
import java.util.Random

/**
 * API 19–20, where JobScheduler does not exist. The scheduled scan is [AlarmManager.setInexactRepeating]:
 * the system owns the repeat, and the time between fires can move. [AlarmManager.setRepeating] is the
 * same inexact mechanism since API 19 and is deprecated, so it is not used. Alarms survive swipe-away
 * but not reboot, so [BootReceiver] calls [AlarmManager.setInexactRepeating] again at the boot. The
 * first fire is the boot plus the stored offset. That delivery is the scan. A finished
 * run does not register the next fire. There is no network constraint: an offline run is recorded by
 * the upload failing, and the next repeat tries again. [AlarmManager.setInexactRepeating] has no
 * 15-minute floor.
 *
 * [cancelScheduledScan] removes the repeating alarm. A scan that has already started in the receiver keeps running.
 */
internal class ScanJobSchedulerBelow21(private val context: Context) : ScanJobSchedulerContract {

    override val name: String = NAME

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Delay until the stored alarm offset. A new count rolls a new offset and keeps it until the count changes. */
    private fun alarmOffsetDelay(
        settings: SettingsStore,
        intervals: Int,
        periodMillis: Long,
        now: Long,
        afterBoot: Boolean,
    ): Long {
        val newPhase = settings.phaseIntervals != intervals || settings.phaseOffsetMillis < 0L
        if (newPhase) {
            settings.phaseOffsetMillis = Random().nextInt(periodMillis.toInt()).toLong()
            settings.phaseIntervals = intervals
            settings.windowStartMillis = now
        } else if (afterBoot || settings.windowStartMillis < 0L) {
            // Boot keeps the stored offset. The window start becomes the boot, so the delay is that offset.
            settings.windowStartMillis = now
        }
        return ScanWindow.delayUntilPhase(
            now,
            periodMillis,
            settings.phaseOffsetMillis,
            settings.windowStartMillis,
        )
    }

    override fun isScanScheduled(): Boolean =
        pendingIntent(Trigger.PERIODIC_SCAN, PendingIntent.FLAG_NO_CREATE, oneShot = false) != null

    @SuppressLint("MissingPermission")
    override fun scheduleScan(intervals: Int, periodMillis: Long, now: Long, afterBoot: Boolean): Long? {
        val operation = pendingIntent(Trigger.PERIODIC_SCAN, PendingIntent.FLAG_UPDATE_CURRENT, oneShot = false) ?: return null
        val triggerAt = now + alarmOffsetDelay(SettingsStore(context), intervals, periodMillis, now, afterBoot)
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, triggerAt, periodMillis, operation)
        return triggerAt
    }

    @SuppressLint("MissingPermission")
    override fun runNow(): Boolean {
        val operation = pendingIntent(Trigger.IMMEDIATE_SCAN, PendingIntent.FLAG_UPDATE_CURRENT, oneShot = true) ?: return false
        val triggerAt = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, operation)
        }
        return true
    }

    override fun cancelScheduledScan() {
        pendingIntent(Trigger.PERIODIC_SCAN, PendingIntent.FLAG_NO_CREATE, oneShot = false)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(trigger: Trigger, flags: Int, oneShot: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_TRIGGER, trigger.name)
        // FLAG_IMMUTABLE exists from API 23. The design alarm is API 19–20, so it is not set there.
        // A forced alarm on API 23+ uses this PendingIntent. From API 31 the platform requires the flag, and the app targets SDK 37.
        val immutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val oneShotFlag = if (oneShot) PendingIntent.FLAG_ONE_SHOT else 0
        val requestCode = if (trigger == Trigger.PERIODIC_SCAN) PERIODIC_SCAN_REQUEST_CODE else IMMEDIATE_SCAN_REQUEST_CODE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags or immutable or oneShotFlag)
    }

    companion object {
        internal const val NAME = "AlarmManager"
        /** Repeating scan. setInexactRepeating owns the repeat. */
        private const val PERIODIC_SCAN_REQUEST_CODE = 2001
        /** POC: one scan now, beside the repeating scan. */
        private const val IMMEDIATE_SCAN_REQUEST_CODE = 2002
    }
}
