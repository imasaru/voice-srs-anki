package com.evan.voicesrs

import android.os.Bundle
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class BookmarksSheet : BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_bookmarks, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.bookmarksList)
        val empty = view.findViewById<TextView>(R.id.bookmarksEmpty)
        list.layoutManager = LinearLayoutManager(view.context)
        fun bind() {
            val marks = BookmarkStore.load(view.context.filesDir)
            empty.visibility = if (marks.isEmpty()) View.VISIBLE else View.GONE
            list.adapter = Adapter(marks) { id ->
                BookmarkStore.remove(view.context.filesDir, id)
                bind()
            }
        }
        bind()
    }

    override fun onStart() {
        super.onStart()
        val sheet = dialog as? BottomSheetDialog ?: return
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        view?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? MainActivity)?.onBookmarksChanged()
    }

    private class Adapter(
        private val marks: List<Bookmark>,
        private val onDelete: (Long) -> Unit
    ) : RecyclerView.Adapter<Holder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_bookmark, parent, false)
            return Holder(view)
        }

        override fun getItemCount(): Int = marks.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val mark = marks[position]
            holder.label.text = mark.label.ifBlank { holder.itemView.context.getString(R.string.bookmarks_title) }
            holder.quote.text = mark.quote
            holder.delete.setOnClickListener { onDelete(mark.id) }
        }
    }

    private class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.bookmarkLabel)
        val quote: TextView = view.findViewById(R.id.bookmarkQuote)
        val delete: TextView = view.findViewById(R.id.bookmarkDelete)
    }
}
