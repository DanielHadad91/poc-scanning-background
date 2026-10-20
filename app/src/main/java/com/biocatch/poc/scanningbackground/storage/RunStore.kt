package com.biocatch.poc.scanningbackground.storage

import android.content.Context
import com.biocatch.poc.scanningbackground.model.LogEntry
import org.json.JSONArray
import org.json.JSONException
import java.io.File

/**
 * The local history of every scan run, boot and schedule event, kept in a private JSON file so it
 * survives the app being closed and the device rebooting. Newest entry first.
 */
class RunStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun all(): List<LogEntry> = synchronized(LOCK) { read() }

    /** Deletes the whole local history (runs, boots, schedule events, and the success/failure counts). */
    fun clear() = synchronized(LOCK) { file.delete() }

    /** The job, the alarm, the boot receiver and the screen can all write, so writes are serialized. */
    fun append(entry: LogEntry) = synchronized(LOCK) {
        val updated = (listOf(entry) + read()).take(MAX_ENTRIES)
        val array = JSONArray().apply { updated.forEach { put(it.toJson()) } }
        val temp = File(file.parentFile, "$FILE_NAME.tmp")
        temp.writeText(array.toString())
        temp.renameTo(file)
    }

    private fun read(): List<LogEntry> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { LogEntry.fromJson(array.getJSONObject(it)) }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private companion object {
        const val FILE_NAME = "history.json"
        const val MAX_ENTRIES = 500
        val LOCK = Any()
    }
}
