package com.biocatch.poc.scanningbackground.model

import org.json.JSONArray
import org.json.JSONObject

/** One user-installed app: the three basic fields the POC collects. [error] marks an app that could not be read. */
data class InstalledAppInfo(
    val packageName: String,
    val firstInstallTime: Long,
    val certificatesSha256: List<String>,
    val error: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("firstInstallTime", firstInstallTime)
        put("certificatesSha256", JSONArray(certificatesSha256))
        put("error", error ?: JSONObject.NULL)
    }
}
