package com.biocatch.poc.scanningbackground.network

import android.content.Context
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Creates a fresh webhook.site URL through the site's public token API. Each new token is an ordinary
 * free URL with its own request quota, so this is not a way around the cap — it just rotates to a new
 * free URL, which is what the API is for.
 */
object WebhookSiteApi {

    const val HOST = "webhook.site"
    private const val TOKEN_ENDPOINT = "https://webhook.site/token"

    fun isWebhookSiteUrl(url: String): Boolean =
        runCatching { URL(url.trim()).host.equals(HOST, ignoreCase = true) }.getOrDefault(false)

    /** Returns a new "https://webhook.site/<uuid>" URL, or null if the request failed. */
    fun createToken(context: Context): String? {
        val connection = (URL(TOKEN_ENDPOINT).openConnection() as HttpURLConnection).apply {
            if (this is HttpsURLConnection) {
                TlsCompat.socketFactoryOrNull(context)?.let { sslSocketFactory = it }
            }
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        return try {
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val uuid = JSONObject(body).optString("uuid").takeIf { it.isNotBlank() } ?: return null
            "https://$HOST/$uuid"
        } catch (e: IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private const val TIMEOUT_MS = 15_000
}
