package com.biocatch.poc.scanningbackground.scheduling

import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.Trigger
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Scheduled runs missed across the whole recorded history, and the longest stretch with no run
 * ([longestGapOngoing] when that stretch is still open now). [tracked] is false when there is
 * nothing to count from yet (no schedule, no scheduled run, no history reset).
 */
data class MissedRunsSummary(
    val missed: Long,
    val gaps: Int,
    val longestGapStart: Long,
    val longestGapEnd: Long,
    val longestGapOngoing: Boolean,
    val tracked: Boolean,
) {
    val longestGapMillis: Long get() = longestGapEnd - longestGapStart
}

/**
 * Sums missed runs over every gap in the history, not only the one since the last run, so a hole
 * stays counted after the chain recovers.
 *
 * The chain is anchored by scheduled scans and by successful re-schedules (first open, interval
 * change, boot, re-creation). After an anchor, slot k is due at anchor + k * interval and is missed
 * once its tolerance (slot + grace) passes before the next anchor; the open gap up to now counts the
 * same way, so a dead chain keeps counting up. Manual runs do not move the scheduled chain, so they
 * are not anchors. Each gap uses the interval in force at its anchor.
 */
object MissedRuns {

    fun summarize(
        history: List<LogEntry>,
        now: Long,
        currentIntervalMinutes: Int,
        graceMinutes: Int,
        countFrom: Long = 0L,
    ): MissedRunsSummary {
        val graceMs = TimeUnit.MINUTES.toMillis(graceMinutes.toLong())
        var intervalMs = minutesToMillis(currentIntervalMinutes)
        var anchor: Long? = countFrom.takeIf { it > 0L }
        var missed = 0L
        var gaps = 0
        var longestStart = 0L
        var longestEnd = 0L
        var longestOngoing = false

        fun closeGapAt(time: Long, ongoing: Boolean) {
            val start = anchor ?: return
            val gap = time - start
            val slots = if (gap > graceMs && intervalMs > 0L) (gap - graceMs) / intervalMs else 0L
            if (slots > 0L) {
                missed += slots
                gaps++
                if (gap > longestEnd - longestStart) {
                    longestStart = start
                    longestEnd = time
                    longestOngoing = ongoing
                }
            }
        }

        for (entry in history.sortedBy { it.timestamp }) {
            if (entry.timestamp < countFrom || !isChainAnchor(entry)) continue
            closeGapAt(entry.timestamp, ongoing = false)
            anchor = entry.timestamp
            intervalOf(entry)?.let { intervalMs = it }
        }
        closeGapAt(now, ongoing = true)
        return MissedRunsSummary(missed, gaps, longestStart, longestEnd, longestOngoing, tracked = anchor != null)
    }

    private fun isChainAnchor(entry: LogEntry): Boolean = when (entry.kind) {
        EntryKind.SCAN -> entry.trigger != Trigger.MANUAL
        EntryKind.SCHEDULE -> entry.nextRunAt != null
        EntryKind.BOOT -> false
    }

    /** Recorded on new entries; for older schedule entries it is derived from the time they armed. */
    private fun intervalOf(entry: LogEntry): Long? {
        entry.intervalMinutes?.let { return minutesToMillis(it) }
        val nextRunAt = entry.nextRunAt ?: return null
        val minutes = ((nextRunAt - entry.timestamp) / 60_000.0).roundToInt()
        return if (minutes > 0) minutesToMillis(minutes) else null
    }

    private fun minutesToMillis(minutes: Int): Long = TimeUnit.MINUTES.toMillis(minutes.toLong())
}
