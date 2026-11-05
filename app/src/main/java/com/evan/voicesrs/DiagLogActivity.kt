package com.evan.voicesrs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diag_log)
        findViewById<MaterialToolbar>(R.id.diagLogToolbar).setNavigationOnClickListener { finish() }
        findViewById<ImageView>(R.id.diagLogClear).setOnClickListener { doClear() }
        list = findViewById(R.id.diagLogList)
        footer = findViewById(R.id.diagLogFooter)
        chips = findViewById(R.id.diagLogLevelChips)
        chips.setOnCheckedStateChangeListener { _, _ -> reload() }
        (list.adapter ?: RowAdapter()).also { list.adapter = it }
        load()
    }

    private val dir get() = File(filesDir, "logs")
    private val adapter get() = (list.adapter as RowAdapter)

    private fun load() {
        val rows = DiagLogView.filter(DiagLogView.readLast(dir), enabledLevels())
        adapter.submit(rows)
        footer.text = getString(R.string.settings_diag_log_footer, rows.size)
    }

    private fun reload() = load()

    private fun doClear() {
        DiagLogView.clear(dir)
        load()
    }

    /** Checked chip labels (TRACE/DEBUG/INFO/WARNING/ERROR); empty set → no rows shown. */
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

        override fun onCreateViewHolder(p: ViewGroup, v: Int): RowHolder =
            RowHolder(LayoutInflater.from(p.context).inflate(R.layout.item_diag_log_row, p, false))

        override fun onBindViewHolder(h: RowHolder, position: Int) {
            val r = items[position]
            h.time.text = r.time
            h.level.text = r.level
            h.message.text = r.message
        }

        inner class RowHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val time: TextView = itemView.findViewById(R.id.diagLogRowTime)
            val level: TextView = itemView.findViewById(R.id.diagLogRowLevel)
            val message: TextView = itemView.findViewById(R.id.diagLogRowMessage)
        }
    }
}
