package com.biocatch.poc.scanningbackground.gui

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.biocatch.poc.scanningbackground.R
import com.biocatch.poc.scanningbackground.model.AppLanguage
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.Outcome
import com.biocatch.poc.scanningbackground.model.RunStatus
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.network.WebhookSiteApi
import com.biocatch.poc.scanningbackground.scan.InstalledAppsScanner
import com.biocatch.poc.scanningbackground.scheduling.BackgroundScheduler
import com.biocatch.poc.scanningbackground.scheduling.ScanScheduling
import com.biocatch.poc.scanningbackground.scheduling.SchedulingLimits
import com.biocatch.poc.scanningbackground.sdk.ScanningSdk
import com.biocatch.poc.scanningbackground.storage.RunStore
import com.biocatch.poc.scanningbackground.storage.SettingsStore
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class MainActivity : LocaleAwareActivity() {

    private lateinit var settings: SettingsStore
    private lateinit var runStore: RunStore
    private lateinit var format: EntryFormatter

    private lateinit var statusBanner: TextView
    private lateinit var nextClock: TextView
    private lateinit var nextDate: TextView
    private lateinit var nextCountdown: TextView
    private lateinit var nextRange: TextView
    private lateinit var nextLimit: TextView
    private lateinit var scheduleConfirmation: TextView
    private lateinit var scheduleOrigin: TextView
    private lateinit var missedRuns: TextView
    private lateinit var deviceBattery: TextView
    private lateinit var deviceAllowBackground: Button
    private lateinit var lastRunTitle: TextView
    private lateinit var lastRunBody: TextView
    private lateinit var successfulRuns: TextView
    private lateinit var viewHistory: Button
    private lateinit var serverUrl: EditText
    private lateinit var intervalMinutes: EditText
    private lateinit var footer: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = SettingsStore(this)
        runStore = RunStore(this)
        format = EntryFormatter(this)

        statusBanner = findViewById(R.id.statusBanner)
        nextClock = findViewById(R.id.nextClock)
        nextDate = findViewById(R.id.nextDate)
        nextCountdown = findViewById(R.id.nextCountdown)
        nextRange = findViewById(R.id.nextRange)
        nextLimit = findViewById(R.id.nextLimit)
        scheduleConfirmation = findViewById(R.id.scheduleConfirmation)
        scheduleOrigin = findViewById(R.id.scheduleOrigin)
        missedRuns = findViewById(R.id.missedRuns)
        lastRunTitle = findViewById(R.id.lastRunTitle)
        lastRunBody = findViewById(R.id.lastRunBody)
        successfulRuns = findViewById(R.id.successfulRuns)
        viewHistory = findViewById(R.id.viewHistory)
        serverUrl = findViewById(R.id.serverUrl)
        intervalMinutes = findViewById(R.id.intervalMinutes)
        footer = findViewById(R.id.footer)

        serverUrl.setText(settings.serverUrl)
        intervalMinutes.setText(String.format(Locale.US, "%d", settings.intervalMinutes))

        findViewById<Button>(R.id.runNow).setOnClickListener { runNow() }
        findViewById<Button>(R.id.saveUrl).setOnClickListener { saveServerUrl() }
        findViewById<Button>(R.id.openViewer).setOnClickListener { openViewer() }
        findViewById<Button>(R.id.createFresh).setOnClickListener { createFreshLink() }

        val autoRenew = findViewById<CheckBox>(R.id.autoRenew)
        autoRenew.isChecked = settings.autoRenew
        autoRenew.setOnCheckedChangeListener { _, checked -> settings.autoRenew = checked }
        findViewById<Button>(R.id.applyInterval).setOnClickListener { applyInterval() }
        viewHistory.setOnClickListener { startActivity(Intent(this, HistoryActivity::class.java)) }
        findViewById<Button>(R.id.clearHistory).setOnClickListener { confirmClearHistory() }
        findViewById<Button>(R.id.openLimits).setOnClickListener { startActivity(Intent(this, LimitsActivity::class.java)) }
        setUpDeviceCard()

        ScanningSdk.start(this)?.let(::confirmSchedule)
    }

    override fun onStart() {
        super.onStart()
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_LANGUAGE, Menu.NONE, R.string.language_button)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_LANGUAGE) {
            showLanguageDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /** Wipes the local history so a test run can start from a clean slate; confirmed first. */
    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setPositiveButton(R.string.clear_history_confirm) { _, _ ->
                ScanningSdk.clearHistory(this)
                render()
                toast(getString(R.string.toast_history_cleared))
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /** Default is the system language; the user can force Hebrew or English. Changing it recreates the screen. */
    private fun showLanguageDialog() {
        val values = arrayOf(AppLanguage.SYSTEM, AppLanguage.HEBREW, AppLanguage.ENGLISH)
        val labels = arrayOf(getString(R.string.lang_system), getString(R.string.lang_hebrew), getString(R.string.lang_english))
        val languageStore = LanguageStore(this)
        val current = values.indexOf(languageStore.language).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                dialog.dismiss()
                if (values[which] != languageStore.language) {
                    languageStore.language = values[which]
                    recreate()
                }
            }
            .show()
    }

    private fun render() {
        val now = System.currentTimeMillis()
        // Reflect a background auto-renew in the field, but never while the user is editing it.
        if (!serverUrl.hasFocus() && serverUrl.text.toString() != settings.serverUrl) {
            serverUrl.setText(settings.serverUrl)
        }
        val entries = runStore.all()
        val scans = entries.filter { it.kind == EntryKind.SCAN }
        val schedulerName = BackgroundScheduler.from(this).name

        renderBanner(scans, entries.firstOrNull { it.kind == EntryKind.SCHEDULE }, now)
        renderNextRun(now, schedulerName)
        renderMissedRuns(now)
        renderDeviceBattery()
        renderLastRun(scans.firstOrNull(), now)
        renderSuccessfulRuns(scans)
        viewHistory.text = getString(R.string.view_history, entries.size)
        footer.text = listOf(
            getString(if (InstalledAppsScanner(this).isQapGranted()) R.string.footer_qap_granted else R.string.footer_qap_missing),
            getString(R.string.footer_mechanism, schedulerName, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            getString(R.string.footer_storage),
        ).joinToString("\n")
    }

    /** Failures stay visible here after the fact: a schedule refusal, the last run, or any failure in the last 24 h. */
    private fun renderBanner(scans: List<LogEntry>, lastSchedule: LogEntry?, now: Long) {
        val lastScan = scans.firstOrNull()
        val status = lastScan?.status
        val (text, background) = when {
            settings.nextRunAt == 0L && lastSchedule != null && lastSchedule.nextRunAt == null ->
                getString(R.string.banner_schedule_failed, lastSchedule.message.orEmpty()) to R.drawable.bg_banner_bad
            status == null -> getString(R.string.banner_no_runs) to R.drawable.bg_banner_neutral
            status.outcome == Outcome.SERVICE_LIMIT ->
                getString(R.string.banner_service_limit, format.explanation(status)) to R.drawable.bg_banner_warn
            status.outcome == Outcome.FAILED ->
                getString(R.string.banner_failed, format.explanation(status)) to R.drawable.bg_banner_bad
            else -> {
                val dayAgo = now - TimeUnit.DAYS.toMillis(1)
                val recentFailures = scans.count { it.timestamp >= dayAgo && it.status != RunStatus.SUCCESS }
                if (recentFailures > 0) {
                    getString(R.string.banner_ok_with_failures, recentFailures) to R.drawable.bg_banner_warn
                } else {
                    getString(R.string.banner_ok) to R.drawable.bg_banner_ok
                }
            }
        }
        statusBanner.text = text
        statusBanner.setBackgroundResource(background)
    }

    private fun renderNextRun(now: Long, schedulerName: String) {
        val nextRunAt = settings.nextRunAt
        if (nextRunAt <= 0L) {
            nextClock.setText(R.string.next_no_time)
            nextDate.text = ""
            nextCountdown.text = ""
            nextRange.visibility = View.GONE
            nextLimit.visibility = View.GONE
            scheduleConfirmation.setText(R.string.next_not_scheduled)
            scheduleConfirmation.setTextColor(format.color(R.color.bad))
            scheduleOrigin.visibility = View.GONE
            return
        }
        nextClock.text = format.clock(nextRunAt)
        nextDate.text = format.date(nextRunAt)
        val remaining = nextRunAt - now
        if (remaining > 0) {
            nextCountdown.text = getString(R.string.next_countdown, format.countdown(remaining))
            nextCountdown.setTextColor(format.color(R.color.primary))
        } else {
            nextCountdown.setText(R.string.next_overdue)
            nextCountdown.setTextColor(format.color(R.color.warn))
        }
        renderTimingLimit(nextRunAt, schedulerName)
        scheduleConfirmation.text = getString(R.string.next_confirmed, settings.intervalMinutes, schedulerName)
        scheduleConfirmation.setTextColor(format.color(R.color.ok))

        val origin = settings.scheduleOrigin
        scheduleOrigin.text = getString(R.string.next_origin, format.scheduleOriginText(origin))
        scheduleOrigin.setTextColor(format.color(if (origin == ScheduleOrigin.RECREATED_ON_REENTRY) R.color.warn else R.color.text_muted))
        scheduleOrigin.visibility = View.VISIBLE
    }

    /** The scheduled time is the earliest run; this shows the bounded range and the limitation behind it. */
    private fun renderTimingLimit(nextRunAt: Long, schedulerName: String) {
        val limit = SchedulingLimits.forScheduler(schedulerName, Build.VERSION.SDK_INT)
        val maxDelayMinutes = SchedulingLimits.maxDelayMinutes(limit)
        if (maxDelayMinutes != null) {
            val latest = nextRunAt + TimeUnit.MINUTES.toMillis(maxDelayMinutes)
            nextRange.text = getString(R.string.next_range, format.clock(nextRunAt), format.clock(latest))
            nextRange.visibility = View.VISIBLE
        } else {
            nextRange.visibility = View.GONE
        }
        nextLimit.text = format.timingLimitText(limit)
        nextLimit.visibility = View.VISIBLE
    }

    /**
     * Total scheduled runs missed over the whole history (every gap past this version's tolerance, plus
     * the open one), so a hole stays counted after the runs resume. Clearing the history resets it.
     */
    private fun renderMissedRuns(now: Long) {
        val graceMinutes = SchedulingLimits.missGraceMinutes(Build.VERSION.SDK_INT)
        val summary = ScanningSdk.missedRuns(this, now)
        if (!summary.tracked) {
            missedRuns.visibility = View.GONE
            return
        }
        if (summary.missed > 0L) {
            val end = if (summary.longestGapOngoing) getString(R.string.gap_now) else format.gapPoint(summary.longestGapEnd, now)
            missedRuns.text = resources.getQuantityString(
                R.plurals.missed_runs, summary.gaps,
                summary.missed, format.gapPoint(summary.longestGapStart, now), end,
                format.gapDuration(summary.longestGapMillis), graceMinutes, summary.gaps,
            )
            missedRuns.setTextColor(format.color(R.color.bad))
            missedRuns.setBackgroundResource(R.drawable.bg_banner_bad)
        } else {
            missedRuns.text = getString(R.string.missed_none, graceMinutes)
            missedRuns.setTextColor(format.color(R.color.ok))
            missedRuns.setBackgroundResource(R.drawable.bg_banner_ok)
        }
        missedRuns.visibility = View.VISIBLE
    }

    /** The manufacturer's key steps (from the guide), with a link that opens the guide at this device's card. */
    private fun setUpDeviceCard() {
        val tip = BackgroundLimitsContent.deviceTip(isHebrew(), Build.MANUFACTURER)
        val title = findViewById<TextView>(R.id.deviceTitle)
        title.text = getString(R.string.device_title, tip.display)
        title.setTextColor(format.color(if (tip.aggressive) R.color.warn else R.color.ok))
        findViewById<TextView>(R.id.deviceTips).text =
            getString(if (tip.aggressive) R.string.device_tips else R.string.device_tips_clean, tip.highlights)
        findViewById<Button>(R.id.deviceOpenGuide).apply {
            text = getString(R.string.device_open_guide, tip.display)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, LimitsActivity::class.java).putExtra(LimitsActivity.EXTRA_FOCUS_DEVICE, true))
            }
        }
        deviceBattery = findViewById(R.id.deviceBattery)
        deviceAllowBackground = findViewById(R.id.deviceAllowBackground)
        deviceAllowBackground.setOnClickListener {
            if (!SystemSettings.openBatteryOptimization(this)) toast(getString(R.string.toast_no_settings))
        }
    }

    /** Refreshed on every tick, so it updates as soon as the user returns from the system dialog. */
    private fun renderDeviceBattery() {
        val exempt = SystemSettings.isIgnoringBatteryOptimizations(this)
        if (exempt == null) {
            deviceBattery.visibility = View.GONE
            deviceAllowBackground.visibility = View.GONE
            return
        }
        deviceBattery.setText(if (exempt) R.string.device_battery_exempt else R.string.device_battery_optimized)
        deviceBattery.setTextColor(format.color(if (exempt) R.color.ok else R.color.bad))
        deviceBattery.visibility = View.VISIBLE
        deviceAllowBackground.visibility = if (exempt) View.GONE else View.VISIBLE
    }

    private fun renderLastRun(lastScan: LogEntry?, now: Long) {
        val status = lastScan?.status
        if (status == null) {
            lastRunTitle.setText(R.string.no_runs_yet)
            lastRunTitle.setTextColor(format.color(R.color.text_muted))
            lastRunBody.text = ""
            return
        }
        lastRunTitle.text = getString(R.string.run_title, format.outcomeLabel(status.outcome), format.triggerLabel(lastScan.trigger))
        lastRunTitle.setTextColor(format.outcomeColor(status.outcome))
        lastRunBody.text = format.scanBody(lastScan, now)
    }

    private fun renderSuccessfulRuns(scans: List<LogEntry>) {
        val successful = scans.filter { it.status == RunStatus.SUCCESS }.take(MAX_SUCCESSFUL_SHOWN)
        successfulRuns.text = if (successful.isEmpty()) {
            getString(R.string.no_successful_runs)
        } else {
            successful.joinToString("\n") {
                getString(R.string.successful_row, format.shortDateTime(it.timestamp), format.duration(it.durationMs ?: 0), it.appCount ?: 0)
            }
        }
    }

    private fun runNow() {
        toast(getString(if (ScanScheduling.runNow(this)) R.string.toast_run_enqueued else R.string.toast_run_refused))
    }

    private fun applyInterval() {
        val minutes = intervalMinutes.text.toString().trim().toIntOrNull()
        if (minutes == null || minutes < SettingsStore.MIN_INTERVAL_MINUTES) {
            toast(getString(R.string.toast_interval_invalid))
            return
        }
        hideKeyboard()
        settings.intervalMinutes = minutes
        confirmSchedule(ScanScheduling.reschedule(this, getString(R.string.log_schedule_interval, minutes), ScheduleOrigin.INTERVAL_CHANGED))
        render()
    }

    /** The confirmation the user sees after arming: the exact next-run time, or that the system refused. */
    private fun confirmSchedule(entry: LogEntry) {
        val nextRunAt = entry.nextRunAt
        toast(if (nextRunAt != null) getString(R.string.toast_schedule_confirmed, format.clock(nextRunAt)) else getString(R.string.toast_schedule_failed))
    }

    private fun saveServerUrl() {
        val url = serverUrl.text.toString().trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            toast(getString(R.string.toast_url_invalid))
            return
        }
        hideKeyboard()
        settings.serverUrl = url
        toast(getString(R.string.toast_url_saved))
    }

    /** webhook.site shows what a URL received at /#!/view/<uuid>; any other server opens as is. */
    private fun openViewer() {
        val serverUrl = settings.serverUrl
        if (serverUrl.isBlank()) {
            toast(getString(R.string.toast_set_url_first))
            return
        }
        val uri = Uri.parse(serverUrl)
        val token = uri.lastPathSegment
        val target = if (uri.host == WEBHOOK_SITE_HOST && token != null) {
            Uri.parse("https://$WEBHOOK_SITE_HOST/#!/view/$token")
        } else {
            uri
        }
        openUrl(target)
    }

    /**
     * One tap: mints a brand-new webhook.site URL (with its own quota) and saves it as the server,
     * off the main thread — no need to press Save URL afterwards.
     */
    private fun createFreshLink() {
        toast(getString(R.string.toast_creating_link))
        thread(name = "create-link") {
            val url = WebhookSiteApi.createToken(applicationContext)
            runOnUiThread {
                if (url != null) {
                    settings.serverUrl = url
                    serverUrl.setText(url)
                    toast(getString(R.string.toast_link_created))
                } else {
                    toast(getString(R.string.toast_link_failed))
                }
            }
        }
    }

    private fun openUrl(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.toast_no_browser))
        }
    }

    private fun hideKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { manager.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val TICK_MS = 1_000L
        const val MAX_SUCCESSFUL_SHOWN = 10
        const val WEBHOOK_SITE_HOST = "webhook.site"
        const val MENU_LANGUAGE = 1
    }
}
