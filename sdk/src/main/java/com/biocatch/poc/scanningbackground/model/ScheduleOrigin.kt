package com.biocatch.poc.scanningbackground.model

/** Why the current next run is armed — lets the UI show whether the schedule survived or had to be remade. */
enum class ScheduleOrigin {
    NONE,

    /** First time the app was opened; nothing was scheduled before. */
    FIRST_OPEN,

    /** A scheduled run finished and armed the next one. This is the healthy steady state. */
    CONTINUED_AFTER_RUN,

    /** The boot receiver re-armed it after the device restarted. */
    AFTER_BOOT,

    /** The user changed the interval. */
    INTERVAL_CHANGED,

    /**
     * The app opened and found no pending schedule even though one had been armed before, so it was
     * remade. This means the previous schedule was lost — usually a force stop or an aggressive OEM.
     */
    RECREATED_ON_REENTRY,

    /** The app opened and the schedule was already pending; it kept running in the background. */
    EXISTING_ON_OPEN,
}
