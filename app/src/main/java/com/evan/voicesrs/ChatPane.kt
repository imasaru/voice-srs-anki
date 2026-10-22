package com.evan.voicesrs

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Shared chat list for the unfolded pane and the folded history sheet.
 * stackFromEnd puts a short thread at the bottom and shows the newest rows
 * on the first layout. Later appends restore the layout state, so the
 * viewport does not follow. There is no scrollToPosition and no jump pill.
 */
class ChatPane(
    private val list: RecyclerView,
    private val unreadView: TextView,
    private val emptyView: TextView? = null,
    /** False when Show answer and the ratings are in a bar outside the list. */
    private val embedControls: Boolean = true
) {
    private val adapter = ChatAdapter(
        onExplExpanded = { id -> sample(id) },
        onRetry = { id -> ExplainQueue.retry(list.context, id) },
        onThread = { id -> openThread(id) }
    )
    private val tracker = ReadReceiptTracker(now = { SystemClock.elapsedRealtime() })
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var attached = false
    private var anchored = false

    private val tick = object : Runnable {
        override fun run() {
            if (!resumed || !attached) return
            sample(null)
            handler.postDelayed(this, 250L)
        }
    }

    private val listener: (List<ReviewTurn>) -> Unit = {
        list.post { if (attached) render() }
    }

    private val uiListener: (DrillUiState) -> Unit = {
        list.post { if (attached) render() }
    }

    private val scroller = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            sample(null)
        }
    }

    fun attach() {
        if (attached) return
        attached = true
        list.layoutManager = LinearLayoutManager(list.context).apply { stackFromEnd = true }
        list.adapter = adapter
        list.itemAnimator = null
        list.addOnScrollListener(scroller)
        ReviewLog.addListener(listener)
        DrillUi.addListener(uiListener)
        render()
    }

    fun setResumed(value: Boolean) {
        resumed = value
        handler.removeCallbacks(tick)
        if (!attached) return
        if (value) {
            sample(null)
            handler.postDelayed(tick, 250L)
        } else {
            tracker.onSample(emptyMap(), resumed = false, emptySet())
        }
    }

    fun detach() {
        if (!attached) return
        attached = false
        resumed = false
        handler.removeCallbacks(tick)
        list.removeOnScrollListener(scroller)
        ReviewLog.removeListener(listener)
        DrillUi.removeListener(uiListener)
    }

    private fun render() {
        val turns = ReviewLog.snapshot()
        val showCard = try {
            Prefs.load(list.context).showCard
        } catch (_: Exception) {
            true
        }
        val rows = ChatFeed.rows(turns, DrillUi.state, embedControls).map { row ->
            if (row is ChatRow.Live && !showCard) row.copy(revealText = false) else row
        }
        emptyView?.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        val n = turns.count { it.unread }
        unreadView.text = if (n == 1) {
            unreadView.context.getString(R.string.chat_unread_one)
        } else {
            unreadView.context.getString(R.string.chat_unread_n, n)
        }
        val lm = list.layoutManager
        val adding = rows.size != adapter.currentList.size
        val state = if (anchored && adding) lm?.onSaveInstanceState() else null
        adapter.submitList(rows) {
            if (state != null) {
                try {
                    lm?.onRestoreInstanceState(state)
                } catch (_: Exception) {
                }
            }
            if (list.childCount > 0) anchored = true
        }
    }

    private fun sample(forceExpanded: Long?) {
        if (!attached || !resumed) return
        val visible = HashMap<Long, Float>()
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i) ?: continue
            val pos = list.getChildAdapterPosition(child)
            if (pos == RecyclerView.NO_POSITION) continue
            val turn = adapter.turnAt(pos) ?: continue
            // Only an unread explanation can start the dwell timer. Otherwise a turn seen
            // while still Queued/Generating is marked read early and its badge never clears.
            if (!turn.unread) continue
            val section = child.findViewById<View>(R.id.explSection)
                ?: child.findViewById<View>(R.id.aiBubble)
                ?: continue
            if (section.visibility != View.VISIBLE) continue
            visible[turn.turnId] = visibleFraction(section)
        }
        val unreadIds = adapter.currentList.mapNotNull { (it as? ChatRow.Past)?.turn }
            .filter { it.unread }
            .map { it.turnId }
            .toSet()
        val expanded = adapter.expandedIds().filter { it in unreadIds }.toMutableSet()
        if (forceExpanded != null && forceExpanded in unreadIds) expanded += forceExpanded
        val newly = tracker.onSample(visible, resumed = true, expanded)
        for (id in newly) {
            if (ReviewLog.markRead(id)) ExplainLog.badgeCleared(id)
        }
    }

    private fun openThread(turnId: Long) {
        val activity = list.context as? FragmentActivity ?: return
        if (activity.supportFragmentManager.findFragmentByTag(THREAD_TAG) != null) return
        ThreadSheet.newInstance(turnId).show(activity.supportFragmentManager, THREAD_TAG)
    }

    private fun visibleFraction(view: View): Float {
        if (view.visibility != View.VISIBLE || view.height <= 0) return 0f
        val rect = Rect()
        if (!view.getGlobalVisibleRect(rect)) return 0f
        return rect.height().toFloat() / view.height.toFloat()
    }

    companion object {
        private const val THREAD_TAG = "thread"
    }
}
