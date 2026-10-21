package com.evan.voicesrs

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip

/**
 * Past exchanges, the "● New" divider, and the live question at the bottom.
 * Explanation updates rebind through [PAYLOAD] so a row does not jump.
 */
class ChatAdapter(
    private val onExplExpanded: (Long) -> Unit,
    private val onRetry: (Long) -> Unit,
    private val onThread: (Long) -> Unit
) : ListAdapter<ChatRow, RecyclerView.ViewHolder>(DIFF) {

    private val cardOpen = HashSet<Long>()
    private val explOpen = HashSet<Long>()

    fun expandedIds(): Set<Long> = explOpen.toSet()

    fun turnAt(position: Int): ReviewTurn? = (currentList.getOrNull(position) as? ChatRow.Past)?.turn

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatRow.Past -> TYPE_PAST
        ChatRow.Divider -> TYPE_DIVIDER
        is ChatRow.Live -> TYPE_LIVE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DIVIDER -> DividerHolder(inflater.inflate(R.layout.item_chat_divider, parent, false))
            TYPE_LIVE -> LiveHolder(inflater.inflate(R.layout.item_chat_live, parent, false))
            else -> PastHolder(inflater.inflate(R.layout.item_chat_turn, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bind(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) bind(holder, getItem(position)) else bind(holder, getItem(position))
    }

    private fun bind(holder: RecyclerView.ViewHolder, row: ChatRow) {
        when {
            holder is PastHolder && row is ChatRow.Past -> holder.bind(row.turn)
            holder is LiveHolder && row is ChatRow.Live -> holder.bind(row)
            holder is DividerHolder -> Unit
        }
    }

    private fun toggle(set: HashSet<Long>, id: Long): Boolean {
        return if (set.add(id)) true else {
            set.remove(id)
            false
        }
    }

    private fun notifyTurn(id: Long) {
        val index = currentList.indexOfFirst { it is ChatRow.Past && it.turn.turnId == id }
        if (index >= 0) notifyItemChanged(index, PAYLOAD)
    }

    inner class PastHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: TextView = view.findViewById(R.id.cardBubble)
        private val user: TextView = view.findViewById(R.id.userBubble)
        private val ai: View = view.findViewById(R.id.aiBubble)
        private val section: View = view.findViewById(R.id.explSection)
        private val badge: TextView = view.findViewById(R.id.explBadge)
        private val status: TextView = view.findViewById(R.id.explStatus)
        private val body: TextView = view.findViewById(R.id.explBody)
        private val retry: Button = view.findViewById(R.id.explRetry)
        private val thread: TextView = view.findViewById(R.id.threadLink)

        fun bind(turn: ReviewTurn) {
            val expanded = turn.turnId in cardOpen
            card.setOnClickListener {
                toggle(cardOpen, turn.turnId)
                notifyTurn(turn.turnId)
            }
            ai.setOnClickListener { toggleExpl(turn.turnId) }
            retry.setOnClickListener { onRetry(turn.turnId) }
            thread.setOnClickListener { onThread(turn.turnId) }
            card.text = BubbleModel.cardText(turn.frontText, expanded)
            card.maxLines = if (expanded) Integer.MAX_VALUE else BubbleModel.COLLAPSED_LINES
            card.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
            user.text = BubbleModel.userLabel(turn.transcript, turn.ease)
            val shown = BubbleModel.displayAi(turn.explanation, turn.frontText, turn.backText, turn.unread)
            if (!shown.visible) {
                ai.visibility = View.GONE
                section.visibility = View.GONE
            } else {
                ai.visibility = View.VISIBLE
                section.visibility = View.VISIBLE
                badge.visibility = if (shown.unread) View.VISIBLE else View.GONE
                if (shown.status.isNotEmpty()) {
                    status.visibility = View.VISIBLE
                    status.text = shown.status
                } else {
                    status.visibility = View.GONE
                }
                val explExpanded = turn.turnId in explOpen
                if (shown.body.isNotBlank()) {
                    body.visibility = View.VISIBLE
                    MarkdownSpans.apply(body, shown.body)
                    body.maxLines = if (explExpanded) Integer.MAX_VALUE else 8
                    body.ellipsize = if (explExpanded) null else TextUtils.TruncateAt.END
                } else {
                    body.visibility = View.GONE
                    body.text = ""
                }
                retry.visibility = if (shown.retry) View.VISIBLE else View.GONE
            }
            val replies = turn.followUps.count { it.q.isNotBlank() }
            val last = ThreadLabel.lastAt(turn.followUps)
            thread.visibility = View.VISIBLE
            thread.text = ThreadLabel.text(replies, last)
        }

        private fun toggleExpl(id: Long) {
            val open = toggle(explOpen, id)
            notifyTurn(id)
            if (open) onExplExpanded(id)
        }
    }

    class DividerHolder(view: View) : RecyclerView.ViewHolder(view)

    class LiveHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val question: TextView = view.findViewById(R.id.liveQuestion)
        private val answer: TextView = view.findViewById(R.id.liveAnswer)
        private val heard: TextView = view.findViewById(R.id.liveHeard)
        private val showAnswer: Button = view.findViewById(R.id.chatShowAnswer)
        private val chips: View = view.findViewById(R.id.chatGradeChips)
        private val again: Chip = view.findViewById(R.id.chatChipAgain)
        private val hard: Chip = view.findViewById(R.id.chatChipHard)
        private val good: Chip = view.findViewById(R.id.chatChipGood)
        private val easy: Chip = view.findViewById(R.id.chatChipEasy)

        fun bind(row: ChatRow.Live) {
            question.text = html(row.front)
            if (row.showBack) {
                answer.visibility = View.VISIBLE
                answer.text = html(row.back)
            } else {
                answer.visibility = View.GONE
                answer.text = ""
            }
            if (row.heard.isBlank()) {
                heard.visibility = View.GONE
            } else {
                heard.visibility = View.VISIBLE
                heard.text = row.heard
            }
            showAnswer.visibility = if (row.showAnswer) View.VISIBLE else View.GONE
            showAnswer.setOnClickListener { DrillService.submitShowAnswer() }
            chips.visibility = if (row.rating) View.VISIBLE else View.GONE
            again.isChecked = row.selectedEase == 1
            hard.isChecked = row.selectedEase == 2
            good.isChecked = row.selectedEase == 3
            easy.isChecked = row.selectedEase == 4
            again.setOnClickListener { DrillService.submitTestGrade("again") }
            hard.setOnClickListener { DrillService.submitTestGrade("hard") }
            good.setOnClickListener { DrillService.submitTestGrade("good") }
            easy.setOnClickListener { DrillService.submitTestGrade("easy") }
        }

        private fun html(value: String): CharSequence {
            if (!value.contains('<')) return value
            return HtmlCompat.fromHtml(value, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    }

    companion object {
        const val PAYLOAD = "expl"
        private const val TYPE_PAST = 1
        private const val TYPE_DIVIDER = 2
        private const val TYPE_LIVE = 3

        private val DIFF = object : DiffUtil.ItemCallback<ChatRow>() {
            override fun areItemsTheSame(a: ChatRow, b: ChatRow): Boolean = a.key == b.key
            override fun areContentsTheSame(a: ChatRow, b: ChatRow): Boolean = a == b
            override fun getChangePayload(a: ChatRow, b: ChatRow): Any? {
                if (a is ChatRow.Past && b is ChatRow.Past) {
                    val left = a.turn
                    val right = b.turn
                    val explChanged = left.explanation != right.explanation ||
                        left.unread != right.unread ||
                        left.followUps != right.followUps
                    val restSame = left.copy(
                        explanation = right.explanation,
                        unread = right.unread,
                        followUps = right.followUps
                    ) == right
                    if (explChanged && restSame) return PAYLOAD
                }
                return null
            }
        }
    }
}
