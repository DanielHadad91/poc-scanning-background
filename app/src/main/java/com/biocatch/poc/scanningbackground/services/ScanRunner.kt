package com.biocatch.poc.scanningbackground.services

import android.content.Context
import android.os.Build
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.RunStatus
import com.biocatch.poc.scanningbackground.model.ScanReport
import com.biocatch.poc.scanningbackground.model.Trigger
import com.biocatch.poc.scanningbackground.network.NetworkState
import com.biocatch.poc.scanningbackground.network.ReportUploader
import com.biocatch.poc.scanningbackground.network.WebhookSiteApi
import com.biocatch.poc.scanningbackground.scan.InstalledAppsScanner
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore
import java.util.UUID

/**
 * One run, shared by every scheduler: scan the user apps, POST the array, and record what happened
 * and how long it took. Blocking, so callers run it off the main thread.
 */
class ScanRunner(private val context: Context) {

    fun run(trigger: Trigger, scheduler: String): LogEntry {
        val startedAt = System.currentTimeMillis()
        val (status, detail, report) = scanAndUpload(startedAt)
        val entry = LogEntry(
            kind = EntryKind.SCAN,
            timestamp = startedAt,
            scheduler = scheduler,
            trigger = trigger,
            status = status,
            durationMs = System.currentTimeMillis() - startedAt,
            appCount = report?.apps?.size,
            qapGranted = report?.qapGranted,
            detail = detail,
        )
        RunStore(context).append(entry)
        return entry
    }

    private data class RunResult(val status: RunStatus, val detail: String, val report: ScanReport?)

    private fun scanAndUpload(startedAt: Long): RunResult {
        val scanner = InstalledAppsScanner(context)
        val report = try {
            ScanReport(
                scanId = UUID.randomUUID().toString(),
                scannedAt = startedAt,
                sdkInt = Build.VERSION.SDK_INT,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                qapGranted = scanner.isQapGranted(),
                apps = scanner.scanUserApps(),
            )
        } catch (e: Exception) {
            return RunResult(RunStatus.SCAN_FAILED, "${e.javaClass.simpleName}: ${e.message}", null)
        }

        val settings = SettingsStore(context)
        val serverUrl = settings.serverUrl
        if (serverUrl.isBlank()) {
            return RunResult(RunStatus.SERVER_NOT_SET, "No server URL configured", report)
        }
        if (!NetworkState.isOnline(context)) {
            return RunResult(RunStatus.NO_NETWORK, "No active network connection", report)
        }
        val payload = report.toJson()
        val upload = ReportUploader(context).upload(serverUrl, payload)
        if (shouldAutoRenew(settings, serverUrl, upload.status)) {
            autoRenewAndResend(settings, payload)?.let { return it.copy(report = report) }
        }
        return RunResult(upload.status, upload.detail, report)
    }

    /** The current URL's free quota is used up (429) or it was deleted (404/410), and auto-renew is on. */
    private fun shouldAutoRenew(settings: SettingsStore, serverUrl: String, status: RunStatus): Boolean =
        settings.autoRenew &&
            WebhookSiteApi.isWebhookSiteUrl(serverUrl) &&
            (status == RunStatus.SERVER_LIMIT_REACHED || status == RunStatus.SERVER_URL_EXPIRED)

    /** Mints a fresh webhook.site URL, saves it as the new server, and resends once. */
    private fun autoRenewAndResend(settings: SettingsStore, payload: org.json.JSONObject): RunResult? {
        val fresh = WebhookSiteApi.createToken(context) ?: return null
        settings.serverUrl = fresh
        val retry = ReportUploader(context).upload(fresh, payload)
        return RunResult(retry.status, "Quota reached; auto-renewed to a fresh URL and resent. ${retry.detail}", null)
    }
}
