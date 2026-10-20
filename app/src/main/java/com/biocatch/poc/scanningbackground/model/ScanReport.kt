package com.biocatch.poc.scanningbackground.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * The payload sent to the test server in one POST: a little device context plus the array of apps.
 * [qapGranted] false on API 30+ means Android filtered the package list, so [apps] is partial.
 */
data class ScanReport(
    val scanId: String,
    val scannedAt: Long,
    val sdkInt: Int,
    val deviceModel: String,
    val qapGranted: Boolean,
    val apps: List<InstalledAppInfo>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("scanId", scanId)
        put("scannedAt", scannedAt)
        put("sdkInt", sdkInt)
        put("deviceModel", deviceModel)
        put("qapGranted", qapGranted)
        put("appCount", apps.size)
        put("apps", JSONArray().apply { apps.forEach { put(it.toJson()) } })
    }
}
