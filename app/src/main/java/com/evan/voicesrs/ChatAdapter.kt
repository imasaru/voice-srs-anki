package com.evan.voicesrs

import android.text.TextUtils
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Past exchanges, the "● New" divider, and the live question at the bottom.
 * Explanation updates rebind through [PAYLOAD] so a row does not jump.
 */
class ChatAdapter(
    private val onExplExpanded: (Long) -> Unit,
    private val onRetry: (Long) -> Unit,
    private val onThread: (Long) -> Unit,
    private val onSpeakSection: (Long, Int, String) -> Unit = { _, _, _ -> },
    private val onBookmark: (ReviewTurn) -> Unit = {}
) : ListAdapter<ChatRow, RecyclerView.ViewHolder>(DIFF) {

    private val cardOpen = HashSet<Long>()
    private val explOpen = HashSet<Long>()
    /** Letter shown on a past card while the grade-change dialog is open. */
    private val pendingPick = HashMap<Long, String>()

    fun expandedIds(): Set<Long> = explOpen.toSet()

    fun turnAt(position: Int): ReviewTurn? = (currentList.getOrNull(position) as? ChatRow.Past)?.turn

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatRow.Past -> TYPE_PAST
        ChatRow.Divider -> TYPE_DIVIDER
        is ChatRow.Live -> TYPE_LIVE
        is ChatRow.Ask -> TYPE_ASK
        is ChatRow.Notice -> TYPE_NOTICE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DIVIDER -> DividerHolder(inflater.inflate(R.layout.item_chat_divider, parent, false))
            TYPE_LIVE -> LiveHolder(inflater.inflate(R.layout.item_chat_live, parent, false))
            TYPE_ASK -> AskHolder(inflater.inflate(R.layout.item_chat_ask, parent, false))
            TYPE_NOTICE -> NoticeHolder(inflater.inflate(R.layout.item_chat_notice, parent, false))
            else -> PastHolder(inflater.inflate(R.layout.item_chat_turn, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        bind(holder, getItem(position))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val row = getItem(position)
        if (payloads.isNotEmpty() && payloads.all { it == PAYLOAD_READING }) {
            if (holder is PastHolder && row is ChatRow.Past) { holder.paintReading(row.turn); return }
            if (holder is LiveHolder && row is ChatRow.Live) { holder.paintReading(row); return }
        }
        if (payloads.isEmpty()) bind(holder, row) else bind(holder, row)
    }

    private fun bind(holder: RecyclerView.ViewHolder, row: ChatRow) {
        when {
            holder is PastHolder && row is ChatRow.Past -> holder.bind(row.turn)
            holder is LiveHolder && row is ChatRow.Live -> holder.bind(row)
            holder is AskHolder && row is ChatRow.Ask -> holder.bind(row)
            holder is NoticeHolder && row is ChatRow.Notice -> holder.bind(row)
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
        private val userReason: TextView = view.findViewById(R.id.userJudgeReason)
        private val ai: View = view.findViewById(R.id.aiBubble)
        private val section: View = view.findViewById(R.id.explSection)
        private val badge: TextView = view.findViewById(R.id.explBadge)
        private val status: TextView = view.findViewById(R.id.explStatus)
        private val body: TextView = view.findViewById(R.id.explBody)
        private val blocks: LinearLayout = view.findViewById(R.id.explBlocks)
        private val footer: TextView = view.findViewById(R.id.explFooter)
        private val retry: Button = view.findViewById(R.id.explRetry)
        private val explain: Button = view.findViewById(R.id.explAsk)
        private val thread: TextView = view.findViewById(R.id.threadLink)
        private val bookmark: TextView = view.findViewById(R.id.bookmarkLink)
        private val makeCard: Chip = view.findViewById(R.id.makeCardChip)

        /** PAYLOAD_READING: repaint only the reading span on the already-built blocks (no rebind). */
        fun paintReading(turn: ReviewTurn) {
            val highlightOn = try {
                Prefs.load(itemView.context).highlightSentence
            } catch (_: Exception) {
                HighlightSentence.SETTING_DEFAULT
            }
            CardFaceBinder.paintReadingIn(blocks, ReadingSpan.sentenceFor(turn.cardId, DrillUi.state, highlightOn))
        }

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
            bookmark.setOnClickListener { onBookmark(turn) }
            makeCard.setOnClickListener { MakeCardFlow.start(itemView.context, turn) }
            makeCard.visibility = if (turn.explanation is ExplState.Ready) View.VISIBLE else View.GONE
            val session = DrillUi.state.sessionStartedMs
            val shuffle = shuffleOn(itemView)
            val parsed = QuestionCard.parse(
                QuestionLabel.of(turn.frontText, ordinal).stem.ifBlank {
                    BubbleModel.fullQuestion(turn.frontText).ifBlank { turn.frontText }
                }
            )
            val storedLetter = TapAnswer.parseSpoken(
                turn.transcript,
                parsed.options.map { it.letter }.toSet()
            )
            val chosen = pendingPick[turn.turnId] ?: storedLetter
            val live = DrillUi.state
            val currentCard = live.drillRunning && live.cardId == turn.cardId && live.frontHtml.isNotBlank()
            val canCorrect = turn.ease != null && parsed.mcq && !currentCard
            val onRegrade: (() -> Unit)? = if (turn.ease != null) { { offerRegrade(turn) } } else null
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
                chosenLetter = chosen,
                onOption = if (canCorrect) { letter -> offerGradeChange(turn, letter, parsed) } else null,
                pastEditable = canCorrect,
                correctField = turn.correctField
            )
            if (turn.questionLabel.isNotBlank()) {
                questionLabel.visibility = View.VISIBLE
                questionLabel.text = turn.questionLabel
            }
            if (mcq) {
                cardStem.maxLines = Integer.MAX_VALUE
                cardStem.ellipsize = null
            } else {
                val stem = QuestionLabel.of(turn.frontText, ordinal).stem.ifBlank { turn.frontText }
                CardFaceBinder.setMarkup(cardStem, BubbleModel.cardText(stem, expanded))
                cardStem.maxLines = if (expanded) Integer.MAX_VALUE else BubbleModel.COLLAPSED_LINES
                cardStem.ellipsize = if (expanded) null else TextUtils.TruncateAt.END
            }
            CardFaceBinder.bindPills(userAnswer, userPill, turn.transcript, turn.ease, onRegrade, userLabel)
            BubbleMenu.attach(card)
            BubbleMenu.attach(itemView.findViewById(R.id.userBubble), userAnswer)
            BubbleMenu.attach(ai, body)
            val why = JudgeNote.shown(turn)
            userReason.text = if (why == null) "" else itemView.context.getString(R.string.own_words_reason, why)
            userReason.visibility = if (why == null) View.GONE else View.VISIBLE
            val shown = BubbleModel.displayAi(turn.explanation, turn.frontText, turn.backText, turn.unread)
            val showExplain = AutoExplain.showButton(turn.explanation)
            body.visibility = View.GONE
            explain.setOnClickListener { ExplainQueue.retry(itemView.context, turn.turnId) }
            explain.visibility = if (showExplain) View.VISIBLE else View.GONE
            explain.isEnabled = showExplain
            if (!shown.visible && !showExplain) {
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
                    val highlightOn = try {
                        Prefs.load(itemView.context).highlightSentence
                    } catch (_: Exception) {
                        HighlightSentence.SETTING_DEFAULT
                    }
                    CardFaceBinder.fillSections(
                        blocks,
                        footer,
                        shown.body,
                        shown.footer,
                        turnId = turn.turnId,
                        playback = PlaybackUi.state,
                        highlightOn = highlightOn,
                        onSpeak = { index, text -> onSpeakSection(turn.turnId, index, text) },
                        readingSentence = ReadingSpan.sentenceFor(turn.cardId, DrillUi.state, highlightOn)
                    )
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

        private fun offerGradeChange(turn: ReviewTurn, letter: String, parsed: QuestionCard.Parsed) {
            val live = DrillUi.state
            val currentCard = live.drillRunning && live.cardId == turn.cardId && live.frontHtml.isNotBlank()
            val correct = QuestionCard.correctLetter(
                QuestionCard.highlightSource(turn.correctField, turn.backText),
                parsed.options
            )
            val preview = PastGrade.preview(
                letter,
                correct,
                currentCard = currentCard,
                rated = turn.ease != null,
                mcq = parsed.mcq
            ) ?: return
            pendingPick[turn.turnId] = preview.letter
            notifyTurn(turn.turnId)
            MaterialAlertDialogBuilder(itemView.context)
                .setMessage(preview.prompt)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    pendingPick.remove(turn.turnId)
                    GradeFix.apply(itemView.context, turn, preview)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    val restored = PastGrade.cancel(pendingPick.remove(turn.turnId))
                    if (restored != null) pendingPick.remove(turn.turnId)
                    notifyTurn(turn.turnId)
                }
                .setOnCancelListener {
                    pendingPick.remove(turn.turnId)
                    notifyTurn(turn.turnId)
                }
                .show()
        }

        private fun offerRegrade(turn: ReviewTurn) {
            val live = DrillUi.state
            val currentCard = live.drillRunning && live.cardId == turn.cardId && live.frontHtml.isNotBlank()
            val choices = arrayOf("Again", "Hard", "Good", "Easy")
            MaterialAlertDialogBuilder(itemView.context)
                .setItems(choices) { _, index ->
                    PastGrade.previewEase(index + 1, currentCard, rated = turn.ease != null)
                        ?.let { GradeFix.apply(itemView.context, turn, it) }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
        }

        private fun toggleExpl(id: Long) {
            val open = toggle(explOpen, id)
            notifyTurn(id)
            if (open) onExplExpanded(id)
        }
    }

    class DividerHolder(view: View) : RecyclerView.ViewHolder(view)

    class AskHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val question: TextView = view.findViewById(R.id.askQuestion)
        private val reply: TextView = view.findViewById(R.id.askReply)

        init {
            BubbleCopy.addCopyAll(reply)
        }

        fun bind(row: ChatRow.Ask) {
            question.text = row.question
            val text = when (val state = row.state) {
                is ExplState.Ready -> state.text
                is ExplState.Streaming -> state.partial.ifBlank { BubbleModel.GENERATING }
                is ExplState.Error -> state.message
                ExplState.Pending, ExplState.Queued -> BubbleModel.GENERATING
                ExplState.None -> ""
            }
            if (text.isBlank()) {
                reply.visibility = View.GONE
                reply.text = ""
            } else {
                reply.visibility = View.VISIBLE
                if (row.state is ExplState.Ready || row.state is ExplState.Streaming) {
                    CardFaceBinder.applySections(reply, text)
                } else {
                    reply.text = text
                }
            }
            val retryable = row.state is ExplState.Error && (row.state as ExplState.Error).retryable
            BubbleCopy.selectable(question, true)
            BubbleCopy.selectable(reply, !retryable)
            if (retryable) {
                reply.setOnClickListener { GeneralChat.retry(it.context, row.id) }
            } else {
                reply.setOnClickListener(null)
            }
            reply.isClickable = retryable
        }
    }

    class NoticeHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val notice: TextView = view.findViewById(R.id.chatNotice)

        fun bind(row: ChatRow.Notice) {
            notice.text = row.message
        }
    }

    inner class LiveHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val label: TextView = view.findViewById(R.id.liveQuestionLabel)
        private val question: TextView = view.findViewById(R.id.liveQuestion)
        private val options: LinearLayout = view.findViewById(R.id.liveOptions)
        private val answer: TextView = view.findViewById(R.id.liveAnswer)
        private val answerFooter: TextView = view.findViewById(R.id.liveAnswerFooter)
        private val heard: TextView = view.findViewById(R.id.liveHeard)
        private val follows: LinearLayout = view.findViewById(R.id.liveFollows)
        private val showAnswer: Button = view.findViewById(R.id.chatShowAnswer)
        private val guideLabel: TextView = view.findViewById(R.id.chatGradeGuide)
        private val chips: View = view.findViewById(R.id.chatGradeChips)
        private val again: Chip = view.findViewById(R.id.chatChipAgain)
        private val hard: Chip = view.findViewById(R.id.chatChipHard)
        private val good: Chip = view.findViewById(R.id.chatChipGood)
        private val easy: Chip = view.findViewById(R.id.chatChipEasy)
        private val memoHint: TextView = view.findViewById(R.id.liveMemoHint)
        private val memoFront: EditText = view.findViewById(R.id.liveMemoFront)
        private val memoBack: EditText = view.findViewById(R.id.liveMemoBack)

        /** h80: the drill speaks this card's question/answer, drawn here; paint the reading sentence on them. */
        fun paintReading(row: ChatRow.Live) {
            val highlightOn = try {
                Prefs.load(itemView.context).highlightSentence
            } catch (_: Exception) {
                HighlightSentence.SETTING_DEFAULT
            }
            val sentence = if (row.revealText) ReadingSpan.sentenceFor(row.cardId, DrillUi.state, highlightOn) else ""
            CardFaceBinder.paintReadingIn(listOf(question, answer), sentence)
        }

        private fun composerBusy(v: View): Boolean {
            val root = v.rootView
            val focused = root.findFocus() as? EditText
            val chat = root.findViewById<EditText>(R.id.chatInput)
            return focused != null || !chat?.text.isNullOrEmpty()
        }

        init {
            val cut: (View) -> Unit = {
                val s = DrillUi.state
                if (TapAnswer.cutsSpeech(s.drillRunning, s.chatPaused, s.silentSession)) DrillService.skipSpeech()
            }
            question.setOnClickListener(cut)
            itemView.setOnClickListener(cut)
        }

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
                    onOption = if (row.showBack) null else { letter -> DrillService.submitLetter(letter, typing = composerBusy(itemView)) },
                    correctField = row.correctField
                )
                if (!mcq) {
                    val markup = if (row.front.contains("<img", ignoreCase = true)) {
                        row.front
                    } else {
                        QuestionLabel.of(row.front, row.questionOrdinal).stem.ifBlank { row.front }
                    }
                    CardFaceBinder.setMarkup(question, markup)
                }
                if (row.questionLabel.isNotBlank()) {
                    label.visibility = View.VISIBLE
                    label.text = row.questionLabel
                }
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
            bindMemos(row)
            val heardLine = InfoCard.heardLine(row.heard)
            if (row.heard.isBlank()) {
                heard.visibility = View.GONE
                heard.text = ""
            } else {
                heard.visibility = View.VISIBLE
                heard.text = heardLine
            }
            showAnswer.visibility = if (row.showAnswer) View.VISIBLE else View.GONE
            guideLabel.setText(when (row.guide) { 1 -> R.string.grade_guide; 2 -> R.string.grade_guide_tap; else -> R.string.answer_guide })
            guideLabel.visibility = if (row.guide == 0) View.GONE else View.VISIBLE
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
            paintReading(row)
        }

        private fun bindMemos(row: ChatRow.Live) {
            if (!row.memoAvailable) {
                memoFront.visibility = View.GONE
                memoBack.visibility = View.GONE
                memoHint.visibility = if (row.memoHint) View.VISIBLE else View.GONE
                memoHint.text = if (row.memoHint) CardMemo.MISSING_HINT else ""
                return
            }
            memoHint.visibility = View.GONE
            memoFront.visibility = View.VISIBLE
            if (memoFront.text.toString() != row.memoFront) memoFront.setText(row.memoFront)
            val showBack = CardMemo.showsBack(row.showBack)
            memoBack.visibility = if (showBack) View.VISIBLE else View.GONE
            if (showBack && memoBack.text.toString() != row.memoBack) memoBack.setText(row.memoBack)
            val commit = {
                val front = memoFront.text.toString()
                val back = memoBack.text.toString()
                DrillUi.update { it.copy(memoFront = front, memoBack = back) }
                MemoSaver.save(
                    itemView.context,
                    row.noteId,
                    row.cardId,
                    front,
                    back,
                    onError = { e ->
                        ErrorSnackbar.onMain(
                            itemView,
                            SaveErrors.message(SaveErrors.Op.MEMO, e),
                        )
                    },
                )
            }
            memoFront.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commit() }
            memoBack.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) commit() }
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
            BubbleCopy.selectable(view, true)
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
        /** Only the spoken-sentence highlight changed (DrillUi.readingSentence / readingIndex). */
        const val PAYLOAD_READING = "reading"
        private const val TYPE_PAST = 1
        private const val TYPE_DIVIDER = 2
        internal const val TYPE_LIVE = 3
        private const val TYPE_ASK = 4
        private const val TYPE_NOTICE = 5

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
