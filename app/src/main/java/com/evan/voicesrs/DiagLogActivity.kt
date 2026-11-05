package com.evan.voicesrs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/** h89: in-app diagnostic log viewer — loads recent lines, filters by level, clears. */
class DiagLogActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var footer: TextView
    private lateinit var chips: ChipGroup
    private var rows: List<DiagLogView.Row> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diag_log)

        val toolbar = findViewById<MaterialToolbar>(R.id.diagLogToolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.inflateMenu(R.menu.activity_diag_log_menu)
        toolbar.menu.findItem(R.id.diagLogClear).setOnMenuItemClickListener {
            doClear()
            true
        }

        list = findViewById(R.id.diagLogList)
        footer = findViewById(R.id.diagLogFooter)
        chips = findViewById(R.id.diagLogLevelChips)

        val adapter = RowAdapter()
        list.adapter = adapter

        chips.setOnCheckedStateChangeListener { _, _ -> reload(adapter) }

        load(adapter)
    }

    private val dir get() = File(filesDir, "logs")

    private fun load(adapter: RowAdapter) {
        rows = DiagLogView.filter(DiagLogView.readLast(dir), enabledLevels())
        adapter.submit(rows)
        footer.text = getString(R.string.settings_diag_log_footer, rows.size)
    }

    private fun reload(adapter: RowAdapter) = load(adapter)

    private fun doClear() {
        DiagLogView.clear(dir)
        load(list.adapter as RowAdapter)
    }

    private fun enabledLevels(): Set<String> {
        val s = HashSet<String>()
        for (i in 0 until chips.childCount) {
            val v = chips.getChildAt(i) as Chip
            if (v.isChecked) s.add(v.text.toString())
        }
        return s
    }

    private inner class RowAdapter : RecyclerView.Adapter<RowAdapter.RowHolder>() {
        private val items = ArrayList<DiagLogView.Row>()

        fun submit(r: List<DiagLogView.Row>) {
            items.clear()
            items.addAll(r)
            notifyDataSetChanged()
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_diag_log_row, parent, false)
            return RowHolder(v)
        }

        override fun onBindViewHolder(holder: RowHolder, position: Int) {
            val row = items[position]
            holder.time.text = row.time
            holder.level.text = row.level
            holder.message.text = row.message
        }

        inner class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val time: TextView = itemView.findViewById(R.id.diagLogRowTime)
            val level: TextView = itemView.findViewById(R.id.diagLogRowLevel)
            val message: TextView = itemView.findViewById(R.id.diagLogRowMessage)
        }
    }
}
