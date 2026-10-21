package com.evan.voicesrs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Folded-layout history. The list opens on the newest turn once, via stackFromEnd. */
class HistorySheet : BottomSheetDialogFragment() {
    private var pane: ChatPane? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = view.findViewById<RecyclerView>(R.id.chatList)
        val unread = view.findViewById<TextView>(R.id.chatUnread)
        pane = ChatPane(list, unread).also { it.attach() }
    }

    override fun onStart() {
        super.onStart()
        val sheet = dialog as? BottomSheetDialog ?: return
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        view?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
    }

    override fun onResume() {
        super.onResume()
        pane?.setResumed(true)
    }

    override fun onPause() {
        pane?.setResumed(false)
        super.onPause()
    }

    override fun onDestroyView() {
        pane?.detach()
        pane = null
        super.onDestroyView()
    }
}
