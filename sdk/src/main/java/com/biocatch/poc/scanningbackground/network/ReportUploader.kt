package com.biocatch.poc.scanningbackground.network

import android.content.Context
import com.biocatch.poc.scanningbackground.model.RunStatus
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class UploadResult(val status: RunStatus, val detail: String)

/** Sends the whole report in a single POST and classifies the answer into an exact [RunStatus]. */
class ReportUploader(private val context: Context) {

    fun upload(serverUrl: String, report: JSONObject): UploadResult {
        val body = report.toString().toByteArray(Charsets.UTF_8)

        val connection = try {
            openConnection(serverUrl)
        } catch (e: Exception) {
            return UploadResult(RunStatus.NETWORK_ERROR, "Invalid server URL: ${e.javaClass.simpleName}: ${e.message}")
        }

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body) }

            val code = connection.responseCode
            classify(code, readResponse(connection, code), body.size)
        } catch (e: IOException) {
            UploadResult(RunStatus.NETWORK_ERROR, "${e.javaClass.simpleName}: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(serverUrl: String): HttpURLConnection {
        val url = URL(serverUrl.trim())
        require(url.protocol == "https" || url.protocol == "http") { "only http/https is supported" }
        val connection = url.openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection) {
            TlsCompat.socketFactoryOrNull(context)?.let { connection.sslSocketFactory = it }
        }
        return connection
    }

    private fun classify(code: Int, response: String, sentBytes: Int): UploadResult {
        val status = when (code) {
            in 200..299 -> RunStatus.SUCCESS
            429 -> RunStatus.SERVER_LIMIT_REACHED
            404, 410 -> RunStatus.SERVER_URL_EXPIRED
            413 -> RunStatus.PAYLOAD_TOO_LARGE
            else -> RunStatus.SERVER_ERROR
        }
        val snippet = if (status == RunStatus.SUCCESS) "" else response.trim().take(MAX_RESPONSE_CHARS)
        val detail = buildString {
            append("HTTP ").append(code).append(", sent ").append(sentBytes).append(" bytes")
            if (snippet.isNotEmpty()) append(", response: ").append(snippet)
        }
        return UploadResult(status, detail)
    }

    private fun readResponse(connection: HttpURLConnection, code: Int): String =
        try {
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        } catch (e: IOException) {
            ""
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_RESPONSE_CHARS = 300
    }
}
