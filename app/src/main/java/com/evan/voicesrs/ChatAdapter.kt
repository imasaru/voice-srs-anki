package com.evan.voicesrs

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * One exchange per graded card: card bubble, user bubble, AI bubble.
 * Oldest at the top; [ChatPane] uses stackFromEnd so the newest sits at the
 * bottom. Explanation updates rebind through [PAYLOAD] so the row stays put.
 */
class ChatAdapter(
    private val onExplExpanded: (Long) -> Unit,
    private val onRetry: (Long) -> Unit
) : ListAdapter<ReviewTurn, ChatAdapter.Holder>(DIFF) {

    private val cardOpen = HashSet<Long>()
    private val explOpen = HashSet<Long>()

    fun expandedIds(): Set<Long> = explOpen.toSet()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_turn, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        bind(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            bind(holder, getItem(position))
        } else {
            val turn = getItem(position)
            holder.bindCard(turn, turn.turnId in cardOpen)
            holder.bindUser(turn)
            holder.bindExplanation(turn, turn.turnId in explOpen)
        }
    }

    private fun bind(holder: Holder, turn: ReviewTurn) {
        holder.bindTurn(turn, turn.turnId in cardOpen, turn.turnId in explOpen)
    }

    private fun toggle(set: HashSet<Long>, id: Long): Boolean {
        return if (set.add(id)) true else {
            set.remove(id)
            false
        }
    }

    private fun notifyTurn(id: Long) {
        val index = currentList.indexOfFirst { it.turnId == id }
        if (index >= 0) notifyItemChanged(index, PAYLOAD)
    }

    inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: TextView = view.findViewById(R.id.cardBubble)
        private val user: TextView = view.findViewById(R.id.userBubble)
        private val ai: View = view.findViewById(R.id.aiBubble)
        private val section: View = view.findViewById(R.id.explSection)
        private val badge: TextView = view.findViewById(R.id.explBadge)
        private val status: TextView = view.findViewById(R.id.explStatus)
        private val body: TextView = view.findViewById(R.id.explBody)
        private val retry: Button = view.findViewById(R.id.explRetry)

        fun bindTurn(turn: ReviewTurn, cardExpanded: Boolean, explExpanded: Boolean) {
            card.setOnClickListener {
                toggle(cardOpen, turn.turnId)
                notifyTurn(turn.turnId)
            }
            ai.setOnClickListener { toggleExpl(turn.turnId) }
            retry.setOnClickListener { onRetry(turn.turnId) }
            bindCard(turn, cardExpanded)
            bindUser(turn)
            bindExplanation(turn, explExpanded)
        }

        fun bindCard(turn: ReviewTurn, expanded: Boolean) {
            card.text = BubbleModel.cardText(turn.frontText, expanded)
            card.maxLines = if (expanded) Integer.MAX_VALUE else 1
            card.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
        }

        fun bindUser(turn: ReviewTurn) {
            user.text = BubbleModel.userLabel(turn.transcript, turn.ease)
        }

        fun bindExplanation(turn: ReviewTurn, expanded: Boolean) {
            val shown = BubbleModel.displayAi(
                turn.explanation,
                turn.frontText,
                turn.backText,
                turn.unread
            )
            if (!shown.visible) {
                ai.visibility = View.GONE
                section.visibility = View.GONE
                return
            }
            ai.visibility = View.VISIBLE
            section.visibility = View.VISIBLE
            badge.visibility = if (shown.unread) View.VISIBLE else View.GONE
            if (shown.status.isNotEmpty()) {
                status.visibility = View.VISIBLE
                status.text = shown.status
            } else {
                status.visibility = View.GONE
            }
            if (shown.body.isNotBlank()) {
                body.visibility = View.VISIBLE
                body.text = shown.body
                body.maxLines = if (expanded) Integer.MAX_VALUE else 8
                body.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
            } else {
                body.visibility = View.GONE
                body.text = ""
            }
            retry.visibility = if (shown.retry) View.VISIBLE else View.GONE
        }

        private fun toggleExpl(id: Long) {
            val open = toggle(explOpen, id)
            notifyTurn(id)
            if (open) onExplExpanded(id)
        }
    }

    companion object {
        const val PAYLOAD = "expl"

        private val DIFF = object : DiffUtil.ItemCallback<ReviewTurn>() {
            override fun areItemsTheSame(a: ReviewTurn, b: ReviewTurn): Boolean = a.turnId == b.turnId

            override fun areContentsTheSame(a: ReviewTurn, b: ReviewTurn): Boolean = a == b

            override fun getChangePayload(a: ReviewTurn, b: ReviewTurn): Any? {
                val explChanged = a.explanation != b.explanation || a.unread != b.unread
                val restSame = a.copy(explanation = b.explanation, unread = b.unread) == b
                return if (explChanged && restSame) PAYLOAD else null
            }
        }
    }
}
