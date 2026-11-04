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
        onThread = { id -> openThread(id) },
        onSpeakSection = { turnId, index, text ->
            PlaybackSpeaker.play(list.context, turnId, index, text)
        },
        onBookmark = { turn ->
            val quote = when (val expl = turn.explanation) {
                is ExplState.Ready -> expl.text
                else -> turn.frontText
            }
            val marked = BookmarkStore.toggle(
                list.context.filesDir,
                Bookmark(
                    id = 0L,
                    at = System.currentTimeMillis(),
                    turnId = turn.turnId,
                    cardId = turn.cardId,
                    label = turn.questionLabel,
                    quote = quote.trim()
                )
            )
            android.widget.Toast.makeText(
                list.context,
                list.context.getString(if (marked) R.string.bookmark_saved else R.string.bookmark_removed),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        },
        onBookmarkLive = { row ->
            val marked = BookmarkStore.toggleLive(list.context.filesDir, row.cardId, liveQuote(row), System.currentTimeMillis())
            android.widget.Toast.makeText(list.context, list.context.getString(if (marked) R.string.bookmark_saved else R.string.bookmark_removed), android.widget.Toast.LENGTH_SHORT).show()
        },
        onAskAbout = { text -> askAboutText(text) },
        onFlag = { turn -> flagCard(turn) },
        onOpenAnki = { turn -> openInAnki(turn) },
        isBookmarked = { turn -> turn.turnId in BookmarkStore.turnIds(list.context.filesDir) },
        isBookmarkedLive = { row -> BookmarkStore.isLiveMarked(list.context.filesDir, row.cardId, liveQuote(row)) }
    )
    private fun liveQuote(row: ChatRow.Live): String = BubbleModel.fullQuestion(row.front).ifBlank { row.front }.trim()
    private val tracker = ReadReceiptTracker(now = { SystemClock.elapsedRealtime() })
    private val follow = ChatFollow()
    private val handler = Handler(Looper.getMainLooper())
    private var resumed = false
    private var attached = false
    private var anchored = false
    private var followView = ChatFollow.View()
    private var lastKeys = emptyList<String>()
    private var lastOrdinal = 0
    private var lastRows: List<ChatRow>? = null
    private var lastPaused: Boolean? = null
    private var lastSilent: Boolean? = null
    /** Extra re-pin attempts after a new question's scroll lands short on a long history. */
    private var pinRetries = 0

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

    private val generalListener: () -> Unit = {
        list.post { if (attached) render() }
    }

    private val playbackListener: (PlaybackControl.Snapshot) -> Unit = {
        list.post { if (attached) render() }
    }

    /** Top of the live question before the last layout, while follow mode is on. */
    private var anchorTop: Int? = null
    private var followAnchor = false
    private var anchorSuppress = false

    private val anchorLayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (anchorSuppress) return@OnLayoutChangeListener
        val before = anchorTop
        val after = liveQuestionTop()
        if (followAnchor && before != null && after != null) {
            val delta = ScrollAnchor.scrollDelta(following = true, beforeTop = before, afterTop = after)
            if (delta != 0) {
                anchorSuppress = true
                list.scrollBy(0, delta)
                anchorSuppress = false
                anchorTop = liveQuestionTop()
                return@OnLayoutChangeListener
            }
        }
        if (followAnchor) anchorTop = after
    }

    private val scroller = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            sample(null)
            if (dy == 0) return
            val bottom = isAtBottom()
            if (!anchorSuppress) {
                followAnchor = ScrollAnchor.following(bottom, followView.holdingOrdinal, liveRowVisible())
                anchorTop = liveQuestionTop()
            }
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
        GeneralChat.addListener(generalListener)
        PlaybackUi.addListener(playbackListener)
        list.addOnLayoutChangeListener(anchorLayout)
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
        lastRows = null
        handler.removeCallbacks(tick)
        list.removeOnScrollListener(scroller)
        ReviewLog.removeListener(listener)
        DrillUi.removeListener(uiListener)
        LiveThread.removeListener(liveListener)
        GeneralChat.removeListener(generalListener)
        PlaybackUi.removeListener(playbackListener)
        list.removeOnLayoutChangeListener(anchorLayout)
        DrillService.reportChatScreen(attached = false, ordinal = followView.liveOrdinal, ready = false)
    }

    /** The spoken sentence changed: repaint only the current drill card's turn row (payload, no full rebind). */
    fun notifyReading(state: DrillUiState) {
        val ids = adapter.currentList.map { (it as? ChatRow.Past)?.turn?.cardId }
        val pos = ReadingSpan.currentTurnIndex(ids, state)
        if (pos >= 0) adapter.notifyItemChanged(pos, ChatAdapter.PAYLOAD_READING)
        val live = adapter.currentList.indexOfLast { it is ChatRow.Live }
        if (live >= 0) adapter.notifyItemChanged(live, ChatAdapter.PAYLOAD_READING)
    }

    /** h121b: "Ask about this" — put the quote into the chat input and select it, never send. */
    private fun askAboutText(text: String) {
        val input = findChatInput() ?: run {
            android.widget.Toast.makeText(
                list.context,
                list.context.getString(R.string.chat_ask_unavailable),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        input.setText(text)
        input.setSelection(text.length)
        input.requestFocus()
    }

    /** The chat input EditText, found via the host Activity or the list's root view. */
    private fun findChatInput(): android.widget.EditText? {
        (list.context as? android.app.Activity)?.let {
            val v = it.findViewById<android.widget.EditText>(R.id.chatInput)
            if (v != null) return v
        }
        return list.rootView.findViewById<android.widget.EditText>(R.id.chatInput)
    }

    /** h121b: "Flag card" — tag the note behind the card on the Anki server (off the main thread). */
    private fun flagCard(turn: ReviewTurn) {
        val app = list.context.applicationContext
        Thread({
            val ok = try {
                val cfg = Prefs.load(app)
                AnkiConnect(cfg.url, cfg.apiKey).flagCard(turn.cardId)
            } catch (e: Exception) {
                false
            }
            val msg = if (ok) R.string.chat_flagged else R.string.chat_flag_failed
            handler.post {
                android.widget.Toast.makeText(
                    app,
                    app.getString(msg),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }, "flag-card").start()
    }

    /** h121c: "Open in Anki" — launch AnkiDroid's browser deep link for this turn's card. */
    private fun openInAnki(turn: ReviewTurn) {
        try {
            list.context.startActivity(AnkiDroidLink.intentFor(turn.cardId))
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(
                list.context,
                list.context.getString(R.string.chat_anki_missing),
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun render() {
        val turns = ReviewLog.snapshot()
        val showCard = try {
            Prefs.load(list.context).showCard
        } catch (_: Exception) {
            true
        }
        val liveFollows = LiveThread.snapshot()?.followUps.orEmpty()
        val rows = ChatFeed.rows(
            turns,
            DrillUi.state,
            embedControls,
            liveFollows,
            asks = GeneralChat.snapshot(),
            notice = GeneralChat.notice(),
            history = ChatHistoryStore.snapshot()
        ).map { row ->
            if (row is ChatRow.Live && !showCard) row.copy(revealText = false) else row
        }
        emptyView?.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        val n = turns.count { it.unread }
        unreadView.text = if (n == 1) {
            unreadView.context.getString(R.string.chat_unread_one)
        } else {
            unreadView.context.getString(R.string.chat_unread_n, n)
        }
        val paused = DrillUi.state.chatPaused
        val silent = DrillUi.state.silentSession
        if (anchored && ChatRenderGate.unchanged(lastRows, rows, lastPaused, paused, lastSilent, silent)) return
        lastRows = rows; lastPaused = paused; lastSilent = silent
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
        val followingNow = ScrollAnchor.following(
            atBottom = measured.atBottom,
            holdingOrdinal = measured.holdingOrdinal,
            liveVisible = measured.liveVisible
        )
        if (decision.scroll != ChatFollow.Scroll.TO_LIVE) {
            followAnchor = followingNow
            if (followingNow) anchorTop = liveQuestionTop()
        } else {
            followAnchor = false
        }
        val preserve = !followingNow && decision.scroll == ChatFollow.Scroll.NONE &&
            (explanationOnly || !decision.view.atBottom || decision.view.holdingOrdinal != 0)
        val lm = list.layoutManager as? LinearLayoutManager
        val first = if (preserve) lm?.findFirstVisibleItemPosition() ?: -1 else -1
        val top = if (first >= 0) lm?.findViewByPosition(first)?.top ?: 0 else 0
        applyFollow(decision, scrollNow = false)
        if (decision.scroll == ChatFollow.Scroll.TO_LIVE) pinRetries = 1
        adapter.submitList(rows) {
            if (preserve && first >= 0) {
                lm?.scrollToPositionWithOffset(first, top)
            } else if (decision.scroll == ChatFollow.Scroll.TO_LIVE) {
                pinLiveToTop()
            }
            if (list.childCount > 0) anchored = true
            list.post { publishVisibility() }
        }
    }

    private fun publishVisibility() {
        if (!attached) return
        val visible = liveRowVisible()
        val bottom = isAtBottom()
        if (!visible && pinRetries > 0 && followView.liveOrdinal > 0 && followView.holdingOrdinal == 0) {
            pinRetries--
            pinLiveToTop()
            list.post { publishVisibility() }
            return
        }
        if (visible) pinRetries = 0
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
            pinLiveToTop()
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

    private fun pinLiveToTop() {
        val last = adapter.itemCount - 1
        if (last < 0) return
        val lm = list.layoutManager as? LinearLayoutManager ?: return
        lm.scrollToPositionWithOffset(last, 0)
        list.post {
            val top = liveQuestionTop() ?: return@post
            val delta = ScrollAnchor.pinTopDelta(top, list.paddingTop)
            if (delta != 0) list.scrollBy(0, delta)
        }
    }

    private fun liveQuestionTop(): Int? {
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i) ?: continue
            val pos = list.getChildAdapterPosition(child)
            if (adapter.currentList.getOrNull(pos) is ChatRow.Live) return child.top
        }
        return null
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
