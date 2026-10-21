package com.evan.voicesrs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip

/**
 * One bubble per graded turn, oldest at the top.
 * Explanation updates rebind through [PAYLOAD] so the row stays put.
 */
class ChatAdapter(
    private val onExplExpanded: (Long) -> Unit,
    private val onRetry: (Long) -> Unit
) : ListAdapter<ReviewTurn, ChatAdapter.Holder>(DIFF) {

    private val backOpen = HashSet<Long>()
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
            holder.bindBack(turn, turn.turnId in backOpen)
            holder.bindExplanation(turn, turn.turnId in explOpen)
        }
    }

    private fun bind(holder: Holder, turn: ReviewTurn) {
        holder.bindTurn(turn, turn.turnId in backOpen, turn.turnId in explOpen)
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
        private val front: TextView = view.findViewById(R.id.chatFront)
        private val back: TextView = view.findViewById(R.id.chatBack)
        private val youSaid: TextView = view.findViewById(R.id.chatYouSaid)
        private val rating: Chip = view.findViewById(R.id.chatRating)
        private val section: View = view.findViewById(R.id.explSection)
        private val badge: TextView = view.findViewById(R.id.explBadge)
        private val status: TextView = view.findViewById(R.id.explStatus)
        private val body: TextView = view.findViewById(R.id.explBody)
        private val more: TextView = view.findViewById(R.id.explMore)
        private val retry: Button = view.findViewById(R.id.explRetry)

        fun bindTurn(turn: ReviewTurn, backExpanded: Boolean, explExpanded: Boolean) {
            front.text = turn.frontText
            youSaid.text = itemView.context.getString(
                R.string.chat_you_said,
                turn.transcript.ifBlank { itemView.context.getString(R.string.chat_no_answer) }
            )
            rating.text = easeLabel(turn.ease)
            back.setOnClickListener {
                toggle(backOpen, turn.turnId)
                notifyTurn(turn.turnId)
            }
            more.setOnClickListener { toggleExpl(turn.turnId) }
            retry.setOnClickListener { onRetry(turn.turnId) }
            bindBack(turn, backExpanded)
            bindExplanation(turn, explExpanded)
        }

        fun bindBack(turn: ReviewTurn, expanded: Boolean) {
            back.text = turn.backText
            back.maxLines = if (expanded) Integer.MAX_VALUE else 2
        }

        fun bindExplanation(turn: ReviewTurn, expanded: Boolean) {
            val state = turn.explanation
            if (state is ExplState.None) {
                section.visibility = View.GONE
                return
            }
            section.visibility = View.VISIBLE
            val freshUnread = turn.unread && state is ExplState.Ready && !state.cached
            badge.visibility = if (freshUnread) View.VISIBLE else View.GONE
            when (state) {
                ExplState.None -> Unit
                ExplState.Queued -> showStatus(itemView.context.getString(R.string.expl_queued))
                ExplState.Pending -> showStatus(itemView.context.getString(R.string.expl_generating))
                is ExplState.Streaming -> {
                    status.visibility = View.VISIBLE
                    status.text = itemView.context.getString(R.string.expl_generating)
                    showBody(state.partial, expanded = false, more = false)
                    retry.visibility = View.GONE
                }
                is ExplState.Ready -> {
                    status.visibility = View.GONE
                    showBody(state.text, expanded, more = state.text.isNotBlank())
                    retry.visibility = View.GONE
                }
                is ExplState.Error -> {
                    status.visibility = View.VISIBLE
                    status.text = state.message
                    body.visibility = View.GONE
                    more.visibility = View.GONE
                    retry.visibility = if (state.retryable) View.VISIBLE else View.GONE
                }
            }
        }

        private fun showStatus(text: String) {
            status.visibility = View.VISIBLE
            status.text = text
            body.visibility = View.GONE
            more.visibility = View.GONE
            retry.visibility = View.GONE
        }

        private fun showBody(text: String, expanded: Boolean, more: Boolean) {
            body.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            body.text = text
            body.maxLines = if (expanded) Integer.MAX_VALUE else 3
            this.more.visibility = if (more) View.VISIBLE else View.GONE
            this.more.text = itemView.context.getString(
                if (expanded) R.string.expl_show_less else R.string.expl_show_more
            )
        }

        private fun toggleExpl(id: Long) {
            val open = toggle(explOpen, id)
            notifyTurn(id)
            if (open) onExplExpanded(id)
        }

        private fun easeLabel(ease: Int?): String = when (ease) {
            1 -> itemView.context.getString(R.string.grade_again)
            2 -> itemView.context.getString(R.string.grade_hard)
            3 -> itemView.context.getString(R.string.grade_good)
            4 -> itemView.context.getString(R.string.grade_easy)
            else -> ""
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
