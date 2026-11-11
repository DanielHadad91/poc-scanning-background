package com.biocatch.poc.scanningbackground.scheduling

import java.util.concurrent.TimeUnit

/** Hardcoded 24-hour window. `intervals` is how many runs fall inside it: 1, 2, or 3. */
object ScanWindow {

    const val HOURS = 24
    const val MIN_INTERVALS = 1
    const val MAX_INTERVALS = 3

    fun isValid(intervals: Int): Boolean = intervals in MIN_INTERVALS..MAX_INTERVALS

    /**
     * 24 h, 12 h, or 8 h. The shortest is 8 hours, so none of these reach the 15-minute
     * periodic-job floor. The SDK does not check that floor.
     */
    fun periodMillis(intervals: Int): Long = TimeUnit.HOURS.toMillis(HOURS.toLong()) / intervals

    /**
     * Millis from [now] until the next [phaseOffsetMillis] inside [periodMillis].
     * The period is counted from [windowStartMillis]: the scheduleScan call that chose this offset, or the boot.
     */
    fun delayUntilPhase(now: Long, periodMillis: Long, phaseOffsetMillis: Long, windowStartMillis: Long): Long {
        val into = floorMod(now - windowStartMillis, periodMillis)
        val phase = floorMod(phaseOffsetMillis, periodMillis)
        val delay = floorMod(phase - into, periodMillis)
        return delay
    }

    private fun floorMod(value: Long, modulus: Long): Long {
        val remainder = value % modulus
        return if (remainder >= 0) remainder else remainder + modulus
    }
}
