package com.biocatch.poc.scanningbackground.gui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.biocatch.poc.scanningbackground.R
import com.biocatch.poc.scanningbackground.model.ScheduleOrigin
import com.biocatch.poc.scanningbackground.scheduling.BackgroundScheduler
import com.biocatch.poc.scanningbackground.storage.SettingsStore

/**
 * A reference screen: our components' background abilities, the limitations per Android version and
 * per manufacturer (with links to dontkillmyapp.com for the screenshots), and the live schedule state.
 */
class LimitsActivity : LocaleAwareActivity() {

    private lateinit var container: LinearLayout
    private lateinit var inflater: LayoutInflater
    private lateinit var format: EntryFormatter
    private var deviceCard: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_limits)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.limits_title)
        container = findViewById(R.id.limitsContainer)
        inflater = LayoutInflater.from(this)
        format = EntryFormatter(this)
        buildGuide()
        if (savedInstanceState == null && intent.getBooleanExtra(EXTRA_FOCUS_DEVICE, false)) scrollToDeviceCard()
    }

    /** Opened from the main screen's device card: jump straight to this manufacturer's instructions. */
    private fun scrollToDeviceCard() {
        val card = deviceCard ?: return
        val scroll = container.parent as? ScrollView ?: return
        scroll.post { scroll.scrollTo(0, (card.top - PADDING).coerceAtLeast(0)) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun buildGuide() {
        val heb = isHebrew()
        addBanner(getString(R.string.guide_intro))

        addSection(getString(R.string.section_schedule_state))
        addCard(currentScheduleStateItem())

        addSection(getString(R.string.section_components))
        BackgroundLimitsContent.components(heb).forEach(::addCard)

        addSection(getString(R.string.section_versions))
        BackgroundLimitsContent.androidVersions(heb).forEach(::addCard)

        addSection(getString(R.string.section_frequency))
        BackgroundLimitsContent.frequency(heb).forEach(::addCard)

        addSection(getString(R.string.section_screen_off))
        BackgroundLimitsContent.screenOff(heb).forEach(::addCard)

        addSection(getString(R.string.section_manufacturers))
        BackgroundLimitsContent.manufacturers(heb, Build.MANUFACTURER).forEach(::addCard)

        addSection(getString(R.string.section_quick_actions))
        addCard(BackgroundLimitsContent.quickActions(heb, Build.VERSION.SDK_INT))

        addSection(getString(R.string.section_glossary))
        BackgroundLimitsContent.glossary(heb).forEach(::addCard)
    }

    /** The live answer to "does it notice and remake the schedule after a problem". */
    private fun currentScheduleStateItem(): GuideItem {
        val settings = SettingsStore(this)
        val origin = settings.scheduleOrigin
        val lines = mutableListOf(
            getString(R.string.state_origin, format.scheduleOriginText(origin)),
            if (settings.nextRunAt > 0L) {
                getString(R.string.state_next_run, format.dateTime(settings.nextRunAt))
            } else {
                getString(R.string.state_not_scheduled)
            },
            getString(R.string.state_mechanism, BackgroundScheduler.from(this).name),
            getString(R.string.state_selfheal),
        )
        val tone = when (origin) {
            ScheduleOrigin.RECREATED_ON_REENTRY -> Tone.WARN
            ScheduleOrigin.NONE -> Tone.INFO
            else -> Tone.OK
        }
        return GuideItem(getString(R.string.state_title), tone, lines)
    }

    private fun addBanner(text: String) {
        val view = inflater.inflate(R.layout.guide_line, container, false) as TextView
        view.text = text
        view.setBackgroundResource(R.drawable.bg_banner_neutral)
        view.setPadding(PADDING, PADDING, PADDING, PADDING)
        container.addView(view)
    }

    private fun addSection(title: String) {
        val view = inflater.inflate(R.layout.guide_section, container, false) as TextView
        view.text = title
        container.addView(view)
    }

    private fun addCard(item: GuideItem) {
        val card = inflater.inflate(R.layout.guide_card, container, false)
        if (item.isCurrentDevice) deviceCard = card
        val title = card.findViewById<TextView>(R.id.cardTitle)
        title.text = item.title
        title.setTextColor(toneColor(item.tone))
        val body = card.findViewById<LinearLayout>(R.id.cardBody)

        item.lines.forEach { addLine(body, it) }
        if (item.steps.isNotEmpty()) {
            addLine(body, getString(R.string.guide_steps_header), bold = true)
            item.steps.forEachIndexed { index, step -> addStep(body, index + 1, step) }
        }
        item.actions.forEach { addActionButton(body, it) }
        container.addView(card)
    }

    private fun addLine(parent: LinearLayout, text: String, bold: Boolean = false) {
        val view = inflater.inflate(R.layout.guide_line, parent, false) as TextView
        view.text = text
        if (bold) view.setTypeface(view.typeface, android.graphics.Typeface.BOLD)
        parent.addView(view)
    }

    private fun addStep(parent: LinearLayout, number: Int, text: String) {
        val view = inflater.inflate(R.layout.guide_step, parent, false) as TextView
        view.text = getString(R.string.guide_step, number, text)
        parent.addView(view)
    }

    private fun addActionButton(parent: LinearLayout, action: GuideAction) {
        val button = inflater.inflate(R.layout.guide_button, parent, false) as Button
        button.text = action.label
        button.setOnClickListener { runAction(action) }
        parent.addView(button)
    }

    private fun runAction(action: GuideAction) {
        val opened = when (action.type) {
            ActionType.BATTERY_OPTIMIZATION -> {
                if (SystemSettings.isIgnoringBatteryOptimizations(this) == true) {
                    Toast.makeText(this, R.string.toast_battery_already_exempt, Toast.LENGTH_LONG).show()
                }
                SystemSettings.openBatteryOptimization(this)
            }
            else -> SystemSettings.openFirst(this, listOf(intentFor(action), SystemSettings.appDetailsIntent(this)))
        }
        if (!opened) Toast.makeText(this, R.string.toast_no_settings, Toast.LENGTH_LONG).show()
    }

    private fun intentFor(action: GuideAction): Intent = when (action.type) {
        ActionType.OPEN_URL -> Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
        ActionType.BATTERY_SAVER ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            } else {
                SystemSettings.appDetailsIntent(this)
            }
        ActionType.EXACT_ALARM ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, SystemSettings.appUri(this))
            } else {
                SystemSettings.appDetailsIntent(this)
            }
        ActionType.APP_DETAILS, ActionType.BATTERY_OPTIMIZATION -> SystemSettings.appDetailsIntent(this)
    }

    private fun toneColor(tone: Tone): Int = format.color(
        when (tone) {
            Tone.INFO -> R.color.info
            Tone.OK -> R.color.ok
            Tone.WARN -> R.color.warn
            Tone.BAD -> R.color.bad
        }
    )

    companion object {
        /** Boolean extra: open scrolled to the card for this device's manufacturer. */
        const val EXTRA_FOCUS_DEVICE = "focus_device"
        private const val PADDING = 36
    }
}
