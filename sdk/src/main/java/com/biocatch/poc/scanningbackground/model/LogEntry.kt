package com.biocatch.poc.scanningbackground.model

import org.json.JSONObject

/** The three answers to "what happened to a run": it worked, the free service ran out, or something broke. */
enum class Outcome { SUCCEEDED, SERVICE_LIMIT, FAILED }

enum class RunStatus(val outcome: Outcome) {
    SUCCESS(Outcome.SUCCEEDED),

    /** HTTP 429: a free webhook.site URL stops accepting after 100 requests. */
    SERVER_LIMIT_REACHED(Outcome.SERVICE_LIMIT),

    /** HTTP 404/410: a free webhook.site URL is deleted 7 days after it was created. */
    SERVER_URL_EXPIRED(Outcome.SERVICE_LIMIT),

    /** HTTP 413: webhook.site rejects bodies of 10 MB or more. */
    PAYLOAD_TOO_LARGE(Outcome.SERVICE_LIMIT),

    /** No server URL is configured yet; the user must create one. */
    SERVER_NOT_SET(Outcome.FAILED),

    SERVER_ERROR(Outcome.FAILED),
    NO_NETWORK(Outcome.FAILED),
    NETWORK_ERROR(Outcome.FAILED),
    SCAN_FAILED(Outcome.FAILED),
}

enum class Trigger { SCHEDULED, MANUAL }

/** What an entry records: a scan run, a device boot, or the schedule being (re)armed. */
enum class EntryKind { SCAN, BOOT, SCHEDULE }

/**
 * One line of the local history. Scan runs fill the run fields; boot and schedule events only set
 * [message], and a schedule event also sets [nextRunAt]. [scheduler] names the mechanism involved.
 * [intervalMinutes] is the interval in force when the entry was written (absent on older entries).
 */
data class LogEntry(
    val kind: EntryKind,
    val timestamp: Long,
    val scheduler: String,
    val trigger: Trigger? = null,
    val status: RunStatus? = null,
    val durationMs: Long? = null,
    val appCount: Int? = null,
    val qapGranted: Boolean? = null,
    val detail: String? = null,
    val message: String? = null,
    val nextRunAt: Long? = null,
    val intervalMinutes: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        put("timestamp", timestamp)
        put("scheduler", scheduler)
        put("trigger", trigger?.name ?: JSONObject.NULL)
        put("status", status?.name ?: JSONObject.NULL)
        put("durationMs", durationMs ?: JSONObject.NULL)
        put("appCount", appCount ?: JSONObject.NULL)
        put("qapGranted", qapGranted ?: JSONObject.NULL)
        put("detail", detail ?: JSONObject.NULL)
        put("message", message ?: JSONObject.NULL)
        put("nextRunAt", nextRunAt ?: JSONObject.NULL)
        put("intervalMinutes", intervalMinutes ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): LogEntry = LogEntry(
            kind = EntryKind.valueOf(json.getString("kind")),
            timestamp = json.getLong("timestamp"),
            scheduler = json.getString("scheduler"),
            trigger = json.stringOrNull("trigger")?.let(Trigger::valueOf),
            status = json.stringOrNull("status")?.let(RunStatus::valueOf),
            durationMs = if (json.isNull("durationMs")) null else json.getLong("durationMs"),
            appCount = if (json.isNull("appCount")) null else json.getInt("appCount"),
            qapGranted = if (json.isNull("qapGranted")) null else json.getBoolean("qapGranted"),
            detail = json.stringOrNull("detail"),
            message = json.stringOrNull("message"),
            nextRunAt = if (json.isNull("nextRunAt")) null else json.getLong("nextRunAt"),
            // isNull is also true for a missing key, so entries written before this field still load.
            intervalMinutes = if (json.isNull("intervalMinutes")) null else json.getInt("intervalMinutes"),
        )

        private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    }
}
