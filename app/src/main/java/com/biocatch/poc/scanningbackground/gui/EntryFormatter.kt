package com.biocatch.poc.scanningbackground.gui

import android.content.Context
import android.os.Build
import com.biocatch.poc.scanningbackground.R
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.Outcome
import com.biocatch.poc.scanningbackground.model.RunStatus
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.scheduling.SchedulingLimits
import com.biocatch.poc.scanningbackground.scheduling.TimingLimit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Turns history entries into the text and colors both screens show. */
class EntryFormatter(private val context: Context) {

    private val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    private val shortDateTimeFormat = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    private val clockFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("EEEE, dd/MM/yyyy", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val hourMinuteFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dayHourMinuteFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

    fun dateTime(millis: Long): String = dateTimeFormat.format(Date(millis))
    fun shortDateTime(millis: Long): String = shortDateTimeFormat.format(Date(millis))
    fun clock(millis: Long): String = clockFormat.format(Date(millis))
    fun date(millis: Long): String = dateFormat.format(Date(millis))

    /** How long a run took. */
    fun duration(millis: Long): String =
        if (millis < 1_000) {
            context.getString(R.string.duration_ms, millis.toInt())
        } else {
            context.getString(R.string.duration_seconds, String.format(Locale.US, "%.1f", millis / 1_000.0))
        }

    /** The length of a stretch with no runs, in hours and minutes. */
    fun gapDuration(millis: Long): String {
        val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(millis.coerceAtLeast(0))
        return if (totalMinutes < 60) {
            context.getString(R.string.gap_minutes, totalMinutes.toInt())
        } else {
            context.getString(R.string.gap_hours_minutes, (totalMinutes / 60).toInt(), (totalMinutes % 60).toInt())
        }
    }

    /** A stretch's start or end: the time alone when it is today, otherwise with the date. */
    fun gapPoint(millis: Long, now: Long): String =
        (if (dayFormat.format(Date(millis)) == dayFormat.format(Date(now))) hourMinuteFormat else dayHourMinuteFormat)
            .format(Date(millis))

    /** Time remaining until the next run, as a clock: HH:MM:SS. */
    fun countdown(millis: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
        return String.format(Locale.US, "%02d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
    }

    fun ago(millis: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0))
        return when {
            seconds < 60 -> context.getString(R.string.ago_seconds, seconds.toInt())
            seconds < 3_600 -> context.getString(R.string.ago_minutes, (seconds / 60).toInt())
            seconds < 86_400 -> context.getString(R.string.ago_hours, (seconds / 3_600).toInt())
            else -> context.getString(R.string.ago_days, (seconds / 86_400).toInt())
        }
    }

    fun timingLimitText(limit: TimingLimit): String = when (limit) {
        TimingLimit.EXACT -> context.getString(R.string.limit_exact)
        TimingLimit.EXACT_DEFERRED_IN_DOZE -> context.getString(R.string.limit_exact_doze)
        TimingLimit.INEXACT_DEFERRED_IN_DOZE -> context.getString(R.string.limit_inexact_doze)
        TimingLimit.JOB_NEAR_TIME -> context.getString(R.string.limit_job_near)
        TimingLimit.JOB_DOZE -> context.getString(R.string.limit_job_doze)
        TimingLimit.JOB_BATCHED ->
            context.getString(R.string.limit_job_batched, SchedulingLimits.CONNECTIVITY_BATCH_MAX_DELAY_MINUTES.toInt())
    }

    fun outcomeLabel(outcome: Outcome): String = context.getString(
        when (outcome) {
            Outcome.SUCCEEDED -> R.string.outcome_succeeded
            Outcome.SERVICE_LIMIT -> R.string.outcome_service_limit
            Outcome.FAILED -> R.string.outcome_failed
        }
    )

    fun outcomeColor(outcome: Outcome): Int = color(
        when (outcome) {
            Outcome.SUCCEEDED -> R.color.ok
            Outcome.SERVICE_LIMIT -> R.color.warn
            Outcome.FAILED -> R.color.bad
        }
    )

    fun scheduleOriginText(origin: ScheduleOrigin): String = context.getString(
        when (origin) {
            ScheduleOrigin.NONE -> R.string.origin_none
            ScheduleOrigin.FIRST_OPEN -> R.string.origin_first_open
            ScheduleOrigin.CONTINUED_AFTER_RUN -> R.string.origin_continued
            ScheduleOrigin.AFTER_BOOT -> R.string.origin_after_boot
            ScheduleOrigin.INTERVAL_CHANGED -> R.string.origin_interval
            ScheduleOrigin.RECREATED_ON_REENTRY -> R.string.origin_recreated
            ScheduleOrigin.EXISTING_ON_OPEN -> R.string.origin_existing
        }
    )

    fun triggerLabel(trigger: Trigger?): String =
        context.getString(if (trigger == Trigger.MANUAL) R.string.trigger_manual else R.string.trigger_scheduled)

    fun explanation(status: RunStatus): String = context.getString(
        when (status) {
            RunStatus.SUCCESS -> R.string.status_success
            RunStatus.SERVER_LIMIT_REACHED -> R.string.status_server_limit_reached
            RunStatus.SERVER_URL_EXPIRED -> R.string.status_server_url_expired
            RunStatus.PAYLOAD_TOO_LARGE -> R.string.status_payload_too_large
            RunStatus.SERVER_NOT_SET -> R.string.status_server_not_set
            RunStatus.SERVER_ERROR -> R.string.status_server_error
            RunStatus.NO_NETWORK -> R.string.status_no_network
            RunStatus.NETWORK_ERROR -> R.string.status_network_error
            RunStatus.SCAN_FAILED -> R.string.status_scan_failed
        }
    )

    /** The lines under a scan run's title: when, how long, how many apps, why, and the raw detail. */
    fun scanBody(entry: LogEntry, now: Long): String {
        val lines = mutableListOf(
            context.getString(R.string.run_when, dateTime(entry.timestamp), ago(now - entry.timestamp), duration(entry.durationMs ?: 0)),
        )
        entry.appCount?.let { count ->
            var apps = context.getString(R.string.run_apps, count)
            if (entry.qapGranted == false) apps += " · " + context.getString(R.string.run_partial)
            lines += apps
        }
        lines += context.getString(R.string.run_via, entry.scheduler)
        entry.status?.let { lines += explanation(it) }
        entry.detail?.let { lines += context.getString(R.string.detail_label, it) }
        return lines.joinToString("\n")
    }

    fun rowTitle(entry: LogEntry): String = when (entry.kind) {
        EntryKind.SCAN -> context.getString(
            R.string.row_scan_title,
            outcomeLabel(entry.status?.outcome ?: Outcome.FAILED),
            triggerLabel(entry.trigger),
            dateTime(entry.timestamp),
        )
        EntryKind.BOOT -> context.getString(R.string.row_boot_title, dateTime(entry.timestamp))
        EntryKind.SCHEDULE -> context.getString(
            if (entry.nextRunAt != null) R.string.row_schedule_title else R.string.row_schedule_failed_title,
            dateTime(entry.timestamp),
        )
    }

    fun rowBody(entry: LogEntry, now: Long): String = when (entry.kind) {
        EntryKind.SCAN -> scanBody(entry, now)
        EntryKind.BOOT, EntryKind.SCHEDULE -> listOfNotNull(
            entry.message,
            entry.nextRunAt?.let { context.getString(R.string.row_next_run, dateTime(it)) },
            context.getString(R.string.run_via, entry.scheduler),
        ).joinToString("\n")
    }

    fun rowColor(entry: LogEntry): Int = when (entry.kind) {
        EntryKind.SCAN -> outcomeColor(entry.status?.outcome ?: Outcome.FAILED)
        EntryKind.BOOT -> color(R.color.info)
        EntryKind.SCHEDULE -> color(if (entry.nextRunAt != null) R.color.info else R.color.bad)
    }

    fun color(resId: Int): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            context.getColor(resId)
        } else {
            @Suppress("DEPRECATION")
            context.resources.getColor(resId)
        }
}
