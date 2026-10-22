package com.evan.voicesrs

import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
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

    private fun shuffleOn(view: View): Boolean {
        return try {
            Prefs.load(view.context).shuffleOptions
        } catch (_: Exception) {
            true
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
        private val card: View = view.findViewById(R.id.cardBubble)
        private val questionLabel: TextView = view.findViewById(R.id.questionLabel)
        private val cardStem: TextView = view.findViewById(R.id.cardStem)
        private val optionRows: LinearLayout = view.findViewById(R.id.optionRows)
        private val userLabel: TextView = view.findViewById(R.id.userAnswerLabel)
        private val userAnswer: TextView = view.findViewById(R.id.userAnswerText)
        private val userPill: TextView = view.findViewById(R.id.userRatingPill)
        private val ai: View = view.findViewById(R.id.aiBubble)
        private val section: View = view.findViewById(R.id.explSection)
        private val badge: TextView = view.findViewById(R.id.explBadge)
        private val status: TextView = view.findViewById(R.id.explStatus)
        private val body: TextView = view.findViewById(R.id.explBody)
        private val blocks: LinearLayout = view.findViewById(R.id.explBlocks)
        private val footer: TextView = view.findViewById(R.id.explFooter)
        private val retry: Button = view.findViewById(R.id.explRetry)
        private val thread: TextView = view.findViewById(R.id.threadLink)

        fun bind(turn: ReviewTurn) {
            val expanded = turn.turnId in cardOpen
            val position = adapterPosition
            val ordinal = if (position == RecyclerView.NO_POSITION) 1 else position + 1
            card.setOnClickListener {
                toggle(cardOpen, turn.turnId)
                notifyTurn(turn.turnId)
            }
            ai.setOnClickListener { toggleExpl(turn.turnId) }
            retry.setOnClickListener { onRetry(turn.turnId) }
            thread.setOnClickListener { onThread(turn.turnId) }
            val session = DrillUi.state.sessionStartedMs
            val shuffle = shuffleOn(itemView)
            val chosen = TapAnswer.parseSpoken(
                turn.transcript,
                QuestionCard.parse(
                    BubbleModel.fullQuestion(turn.frontText).ifBlank { turn.frontText }
                ).options.map { it.letter }.toSet()
            )
            val mcq = CardFaceBinder.bindQuestion(
                questionLabel,
                cardStem,
                optionRows,
                turn.frontText,
                ordinal,
                revealed = true,
                answerPlain = turn.backText,
                cardId = turn.cardId,
                sessionStartMs = session,
                shuffle = shuffle,
                chosenLetter = chosen
            )
            if (mcq) {
                cardStem.maxLines = Integer.MAX_VALUE
                cardStem.ellipsize = null
            } else {
                CardFaceBinder.setMarkup(cardStem, BubbleModel.cardText(turn.frontText, expanded))
                cardStem.maxLines = if (expanded) Integer.MAX_VALUE else BubbleModel.COLLAPSED_LINES
                cardStem.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
            }
            CardFaceBinder.bindPills(userAnswer, userPill, turn.transcript, turn.ease, userLabel)
            val shown = BubbleModel.displayAi(turn.explanation, turn.frontText, turn.backText, turn.unread)
            body.visibility = View.GONE
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
                if (shown.body.isNotBlank() || shown.footer.isNotBlank()) {
                    CardFaceBinder.fillSections(blocks, footer, shown.body, shown.footer)
                } else {
                    blocks.visibility = View.GONE
                    blocks.removeAllViews()
                    footer.visibility = View.GONE
                    footer.text = ""
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

    inner class LiveHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.liveQuestionLabel)
        private val question: TextView = view.findViewById(R.id.liveQuestion)
        private val options: LinearLayout = view.findViewById(R.id.liveOptions)
        private val answer: TextView = view.findViewById(R.id.liveAnswer)
        private val answerFooter: TextView = view.findViewById(R.id.liveAnswerFooter)
        private val heard: TextView = view.findViewById(R.id.liveHeard)
        private val follows: LinearLayout = view.findViewById(R.id.liveFollows)
        private val showAnswer: Button = view.findViewById(R.id.chatShowAnswer)
        private val chips: View = view.findViewById(R.id.chatGradeChips)
        private val again: Chip = view.findViewById(R.id.chatChipAgain)
        private val hard: Chip = view.findViewById(R.id.chatChipHard)
        private val good: Chip = view.findViewById(R.id.chatChipGood)
        private val easy: Chip = view.findViewById(R.id.chatChipEasy)

        fun bind(row: ChatRow.Live) {
            if (!row.revealText) {
                label.visibility = View.GONE
                options.visibility = View.GONE
                options.removeAllViews()
                question.text = ""
                answer.visibility = View.GONE
                answer.text = ""
                answerFooter.visibility = View.GONE
                answerFooter.text = ""
            } else {
                val mcq = CardFaceBinder.bindQuestion(
                    label,
                    question,
                    options,
                    row.front,
                    row.questionOrdinal,
                    revealed = row.showBack,
                    answerPlain = row.back,
                    cardId = row.cardId,
                    sessionStartMs = DrillUi.state.sessionStartedMs,
                    shuffle = shuffleOn(itemView),
                    chosenLetter = row.chosenLetter,
                    onOption = if (row.showBack) null else { letter -> DrillService.submitLetter(letter) }
                )
                if (!mcq) CardFaceBinder.setMarkup(question, row.front)
                if (row.showBack) {
                    val parts = Citation.splitHtml(row.back)
                    answer.visibility = View.VISIBLE
                    CardFaceBinder.setMarkup(answer, parts.body)
                    if (parts.footer.isBlank()) {
                        answerFooter.visibility = View.GONE
                        answerFooter.text = ""
                    } else {
                        answerFooter.visibility = View.VISIBLE
                        answerFooter.text = parts.footer
                    }
                } else {
                    answer.visibility = View.GONE
                    answer.text = ""
                    answerFooter.visibility = View.GONE
                    answerFooter.text = ""
                }
            }
            if (!row.revealText || row.heard.isBlank()) {
                heard.visibility = View.GONE
                heard.text = ""
            } else {
                heard.visibility = View.VISIBLE
                heard.text = row.heard
            }
            showAnswer.visibility = if (row.showAnswer) View.VISIBLE else View.GONE
            showAnswer.setOnClickListener { DrillService.submitShowAnswer() }
            chips.visibility = if (row.rating) View.VISIBLE else View.GONE
            RatingSuggest.apply(again, 1, row.selectedEase, row.suggestedEase)
            RatingSuggest.apply(hard, 2, row.selectedEase, row.suggestedEase)
            RatingSuggest.apply(good, 3, row.selectedEase, row.suggestedEase)
            RatingSuggest.apply(easy, 4, row.selectedEase, row.suggestedEase)
            again.setOnClickListener { DrillService.submitTestGrade("again") }
            hard.setOnClickListener { DrillService.submitTestGrade("hard") }
            good.setOnClickListener { DrillService.submitTestGrade("good") }
            easy.setOnClickListener { DrillService.submitTestGrade("easy") }
            bindFollows(row.followUps)
        }

        private fun bindFollows(items: List<FollowUp>) {
            follows.removeAllViews()
            if (items.isEmpty()) {
                follows.visibility = View.GONE
                return
            }
            follows.visibility = View.VISIBLE
            val density = follows.resources.displayMetrics.density
            for (follow in items) {
                follows.addView(followText(follow.q, user = true, density))
                val reply = when (val state = follow.state) {
                    is ExplState.Ready -> state.text
                    is ExplState.Streaming -> state.partial.ifBlank { BubbleModel.GENERATING }
                    is ExplState.Error -> state.message
                    ExplState.Pending, ExplState.Queued -> BubbleModel.GENERATING
                    ExplState.None -> ""
                }
                if (reply.isNotBlank()) follows.addView(followText(reply, user = false, density))
            }
        }

        private fun followText(text: String, user: Boolean, density: Float): TextView {
            val view = TextView(follows.context)
            if (user) view.text = text else CardFaceBinder.applySections(view, text)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            view.setTextColor(follows.context.getColor(if (user) R.color.bubble_user_on else R.color.bubble_ai_on))
            view.setBackgroundResource(if (user) R.drawable.bg_bubble_user else R.drawable.bg_bubble_ai)
            val pad = (10 * density).toInt()
            view.setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = (6 * density).toInt()
            lp.gravity = if (user) android.view.Gravity.END else android.view.Gravity.START
            view.layoutParams = lp
            return view
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
