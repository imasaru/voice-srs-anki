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
 * stackFromEnd puts a short thread at the bottom. A new question follows
 * only when the user is already at the bottom. Otherwise it is held, and
 * the "Next question" pill is the way back. A late explanation does not scroll.
 */
class ChatPane(
    private val list: RecyclerView,
    private val unreadView: TextView,
    private val emptyView: TextView? = null,
    /** False when Show answer and the ratings are in a bar outside the list. */
    private val embedControls: Boolean = true,
    private val nextPill: TextView? = null
) {
    private val adapter = ChatAdapter(
        onExplExpanded = { id -> sample(id) },
        onRetry = { id -> ExplainQueue.retry(list.context, id) },
        onThread = { id -> openThread(id) }
    )
    private val tracker = ReadReceiptTracker(now = { SystemClock.elapsedRealtime() })
    private val follow = ChatFollow()
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var attached = false
    private var anchored = false
    private var followView = ChatFollow.View()
    private var lastKeys = emptyList<String>()
    private var lastOrdinal = 0

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

    private val liveListener: () -> Unit = {
        list.post { if (attached) render() }
    }

    private val scroller = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            sample(null)
            if (dy == 0) return
            val bottom = isAtBottom()
            applyFollow(follow.onScroll(followView.copy(atBottom = bottom), bottom), scrollNow = true)
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
        LiveThread.addListener(liveListener)
        nextPill?.setOnClickListener {
            applyFollow(follow.onPill(followView), scrollNow = true)
        }
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
        LiveThread.removeListener(liveListener)
        DrillService.reportChatScreen(attached = false, ordinal = followView.liveOrdinal, ready = false)
    }

    private fun render() {
        val turns = ReviewLog.snapshot()
        val showCard = try {
            Prefs.load(list.context).showCard
        } catch (_: Exception) {
            true
        }
        val liveFollows = LiveThread.snapshot()?.followUps.orEmpty()
        val rows = ChatFeed.rows(turns, DrillUi.state, embedControls, liveFollows).map { row ->
            if (row is ChatRow.Live && !showCard) row.copy(revealText = false) else row
        }
        emptyView?.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        val n = turns.count { it.unread }
        unreadView.text = if (n == 1) {
            unreadView.context.getString(R.string.chat_unread_one)
        } else {
            unreadView.context.getString(R.string.chat_unread_n, n)
        }
        val live = rows.lastOrNull() as? ChatRow.Live
        val ordinal = live?.questionOrdinal ?: 0
        val keys = rows.map { it.key }
        val explanationOnly = anchored && keys == lastKeys && ordinal == lastOrdinal
        val measured = followView.copy(
            atBottom = isAtBottom(),
            liveVisible = liveRowVisible(),
            chatPaused = DrillUi.state.chatPaused,
            silent = DrillUi.state.silentSession
        )
        val decision = when {
            ordinal > 0 && ordinal != lastOrdinal -> follow.onQuestion(measured, ordinal)
            explanationOnly -> follow.onExplanation(measured.copy(liveOrdinal = ordinal))
            ordinal > 0 -> follow.onVisible(measured.copy(liveOrdinal = ordinal), measured.liveVisible)
            else -> follow.onQuestion(measured, 0)
        }
        lastKeys = keys
        lastOrdinal = ordinal
        val preserve = decision.scroll == ChatFollow.Scroll.NONE &&
            (explanationOnly || !decision.view.atBottom || decision.view.holdingOrdinal != 0)
        val lm = list.layoutManager as? LinearLayoutManager
        val first = if (preserve) lm?.findFirstVisibleItemPosition() ?: -1 else -1
        val top = if (first >= 0) lm?.findViewByPosition(first)?.top ?: 0 else 0
        applyFollow(decision, scrollNow = false)
        adapter.submitList(rows) {
            if (preserve && first >= 0) {
                lm?.scrollToPositionWithOffset(first, top)
            } else if (decision.scroll == ChatFollow.Scroll.TO_LIVE) {
                val last = adapter.itemCount - 1
                if (last >= 0) list.smoothScrollToPosition(last)
            }
            if (list.childCount > 0) anchored = true
            list.post { publishVisibility() }
        }
    }

    private fun publishVisibility() {
        if (!attached) return
        val visible = liveRowVisible()
        val bottom = isAtBottom()
        val view = followView.copy(atBottom = bottom, liveVisible = visible)
        val decision = if (view.holdingOrdinal != 0 && bottom) {
            follow.onScroll(view, true)
        } else {
            follow.onVisible(view, visible)
        }
        applyFollow(decision, scrollNow = decision.scroll == ChatFollow.Scroll.TO_LIVE)
    }

    private fun applyFollow(decision: ChatFollow.Decision, scrollNow: Boolean) {
        followView = decision.view
        nextPill?.visibility = if (decision.showPill) View.VISIBLE else View.GONE
        if (scrollNow && decision.scroll == ChatFollow.Scroll.TO_LIVE) {
            val last = adapter.itemCount - 1
            if (last >= 0) list.smoothScrollToPosition(last)
        }
        DrillService.reportChatScreen(
            attached = attached,
            ordinal = decision.view.liveOrdinal,
            ready = decision.mayBegin
        )
    }

    private fun isAtBottom(): Boolean {
        if (!anchored || list.childCount == 0) return true
        val range = list.computeVerticalScrollRange()
        val extent = list.computeVerticalScrollExtent()
        val offset = list.computeVerticalScrollOffset()
        if (range <= extent + 1) return true
        val slop = (ChatFollow.BOTTOM_SLOP_DP * list.resources.displayMetrics.density).toInt()
        return range - (offset + extent) <= slop
    }

    private fun liveRowVisible(): Boolean {
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i) ?: continue
            val pos = list.getChildAdapterPosition(child)
            if (adapter.currentList.getOrNull(pos) !is ChatRow.Live) continue
            val rect = android.graphics.Rect()
            if (!child.getLocalVisibleRect(rect)) return false
            return rect.height() > 0
        }
        return false
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
        DrillService.pauseForChat(ChatPause.Trigger.OPEN_THREAD)
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
