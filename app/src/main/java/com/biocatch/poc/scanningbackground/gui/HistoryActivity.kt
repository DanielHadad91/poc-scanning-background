package com.biocatch.poc.scanningbackground.gui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import com.biocatch.poc.scanningbackground.R
import com.biocatch.poc.scanningbackground.model.EntryKind
import com.biocatch.poc.scanningbackground.model.LogEntry
import com.biocatch.poc.scanningbackground.model.RunStatus
import com.biocatch.poc.scanningbackground.storage.RunStore

/** Every recorded entry, newest first: scan runs with their duration, failures, boots and schedule events. */
class HistoryActivity : LocaleAwareActivity() {

    private lateinit var runStore: RunStore
    private lateinit var format: EntryFormatter
    private lateinit var summary: TextView
    private lateinit var adapter: HistoryAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        runStore = RunStore(this)
        format = EntryFormatter(this)
        summary = findViewById(R.id.historySummary)
        adapter = HistoryAdapter(format)

        val list = findViewById<ListView>(R.id.historyList)
        list.adapter = adapter
        list.emptyView = findViewById(R.id.historyEmpty)
    }

    override fun onStart() {
        super.onStart()
        handler.post(ticker)
    }

    override fun onStop() {
        handler.removeCallbacks(ticker)
        super.onStop()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun refresh() {
        val entries = runStore.all()
        val scans = entries.filter { it.kind == EntryKind.SCAN }
        summary.text = getString(
            R.string.history_summary,
            entries.size,
            scans.count { it.status == RunStatus.SUCCESS },
            scans.count { it.status != RunStatus.SUCCESS },
            entries.count { it.kind == EntryKind.BOOT },
        )
        adapter.update(entries, System.currentTimeMillis())
    }

    private class HistoryAdapter(private val format: EntryFormatter) : BaseAdapter() {

        private var entries: List<LogEntry> = emptyList()
        private var now: Long = 0L

        fun update(entries: List<LogEntry>, now: Long) {
            this.entries = entries
            this.now = now
            notifyDataSetChanged()
        }

        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): LogEntry = entries[position]
        override fun getItemId(position: Int): Long = getItem(position).timestamp

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.row_history, parent, false)
            val entry = getItem(position)
            val title = view.findViewById<TextView>(R.id.rowTitle)
            title.text = format.rowTitle(entry)
            title.setTextColor(format.rowColor(entry))
            view.findViewById<TextView>(R.id.rowBody).text = format.rowBody(entry, now)
            return view
        }
    }

    private companion object {
        const val TICK_MS = 2_000L
    }
}
