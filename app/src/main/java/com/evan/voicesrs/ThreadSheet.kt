package com.evan.voicesrs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Follow-up chat for one graded card. Replies stay on that card's history. */
class ThreadSheet : BottomSheetDialogFragment() {
    private var turnId: Long = 0L
    private var list: RecyclerView? = null
    private var dictation: ChatDictation? = null
    private val adapter = MessageAdapter()

    private val listener: (List<ReviewTurn>) -> Unit = {
        view?.post { if (isAdded) bind() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        turnId = arguments?.getLong(ARG_TURN) ?: 0L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.sheet_thread, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        list = view.findViewById(R.id.threadList)
        list?.layoutManager = LinearLayoutManager(view.context).apply { stackFromEnd = true }
        list?.adapter = adapter
        // Clear the taller of the keyboard / nav bar so the input row stays visible.
        // Capture the layout's original bottom padding as the base before the listener runs.
        val root = view
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            root.setPadding(
                root.paddingLeft,
                root.paddingTop,
                root.paddingRight,
                ImeInsets.bottomPadding(baseBottom, imeBottom, navBottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
        val input = view.findViewById<EditText>(R.id.threadInput)
        val send = view.findViewById<Button>(R.id.threadSend)
        val submit = {
            val text = input.text?.toString().orEmpty()
            if (text.isNotBlank()) {
                ThreadAsk.ask(view.context, turnId, text)
                input.setText("")
            }
        }
        send.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else {
                false
            }
        }
        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                DrillService.pauseForChat(ChatPause.Trigger.FOCUS_INPUT)
                // Bring the input on screen above the keyboard using its own bounds.
                val r = ImeInsets.inputRect(input.width, input.height)
                input.post { input.requestRectangleOnScreen(Rect(r[0], r[1], r[2], r[3]), true) }
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrEmpty()) DrillService.pauseForChat(ChatPause.Trigger.TYPING)
            }
        })
        view.findViewById<Button>(R.id.threadDictate).setOnClickListener {
            val sheetView = view
            DrillService.prepareDictation {
                if (!isAdded) {
                    DrillService.dictationFinished()
                    return@prepareDictation
                }
                if (dictation == null) dictation = ChatDictation(sheetView.context)
                dictation?.start(
                    onPartial = { partial ->
                        sheetView.post {
                            if (!isAdded) return@post
                            sheetView.findViewById<EditText>(R.id.threadInput)?.setText(partial)
                        }
                    },
                    onFinal = { spoken ->
                        sheetView.post {
                            DrillService.dictationFinished()
                            if (!isAdded) return@post
                            ThreadAsk.ask(sheetView.context, turnId, spoken, byVoice = true)
                        }
                    },
                    onMiss = {
                        sheetView.post {
                            DrillService.dictationFinished()
                            if (!isAdded) return@post
                            Toast.makeText(
                                sheetView.context,
                                R.string.chat_dictate_miss,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }
        }
        DrillService.pauseForChat(ChatPause.Trigger.OPEN_THREAD)
        ThreadAsk.send(view.context, turnId)
        ReviewLog.addListener(listener)
        bind()
    }

    override fun onStart() {
        super.onStart()
        // BottomSheetDialog ignores the activity manifest soft-input mode, so force
        // the dialog window to resize for the keyboard here.
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val sheet = dialog as? BottomSheetDialog ?: return
        sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        sheet.behavior.skipCollapsed = true
        view?.layoutParams?.height = ViewGroup.LayoutParams.MATCH_PARENT
    }

    override fun onDestroyView() {
        dictation?.stop()
        dictation = null
        DrillService.dictationFinished()
        ReviewLog.removeListener(listener)
        list = null
        super.onDestroyView()
    }

    private fun bind() {
        val root = view ?: return
        val turns = ReviewLog.snapshot()
        val index = turns.indexOfFirst { it.turnId == turnId }
        val turn = if (index >= 0) turns[index] else null
        if (turn == null) {
            dismissAllowingStateLoss()
            return
        }
        val question = root.findViewById<TextView>(R.id.threadQuestion)
        val shuffle = try {
            Prefs.load(root.context).shuffleOptions
        } catch (_: Exception) {
            true
        }
        val mcq = CardFaceBinder.bindQuestion(
            root.findViewById(R.id.threadQuestionLabel),
            question,
            root.findViewById(R.id.threadOptions),
            turn.frontText,
            index + 1,
            revealed = true,
            answerPlain = turn.backText,
            cardId = turn.cardId,
            sessionStartMs = DrillUi.state.sessionStartedMs,
            shuffle = shuffle,
            correctField = turn.correctField
        )
        if (!mcq) {
            val stem = QuestionLabel.of(turn.frontText, index + 1).stem
            CardFaceBinder.setMarkup(question, BubbleModel.cardText(stem, expanded = true))
        }
        if (turn.questionLabel.isNotBlank()) {
            root.findViewById<TextView>(R.id.threadQuestionLabel).text = turn.questionLabel
        }
        CardFaceBinder.bindPills(
            root.findViewById(R.id.threadSpokenPill),
            root.findViewById(R.id.threadRatingPill),
            turn.transcript,
            turn.ease,
            root.findViewById(R.id.threadAnswerLabel)
        )
        val expl = BubbleModel.displayAi(turn.explanation, turn.frontText, turn.backText, unread = false)
        CardFaceBinder.fillSections(
            root.findViewById<LinearLayout>(R.id.threadSections),
            root.findViewById(R.id.threadExplFooter),
            expl.body,
            expl.footer
        )
        val rows = ArrayList<Row>()
        for (follow in turn.followUps) {
            rows += Row(follow.q, user = true, pending = false)
            when (val state = follow.state) {
                is ExplState.Ready -> rows += Row(state.text, user = false, pending = false)
                is ExplState.Streaming -> rows += Row(
                    state.partial.ifBlank { BubbleModel.GENERATING },
                    user = false,
                    pending = state.partial.isBlank()
                )
                ExplState.Pending, ExplState.Queued -> rows += Row(BubbleModel.GENERATING, user = false, pending = true)
                is ExplState.Error -> rows += Row(state.message, user = false, pending = true)
                ExplState.None -> Unit
            }
        }
        adapter.submit(rows)
        list?.scrollToPosition((rows.size - 1).coerceAtLeast(0))
    }

    private data class Row(val text: String, val user: Boolean, val pending: Boolean)

    private class MessageAdapter : RecyclerView.Adapter<MessageAdapter.Holder>() {
        private val rows = ArrayList<Row>()

        fun submit(next: List<Row>) {
            rows.clear()
            rows.addAll(next)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_thread_message, parent, false)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(rows[position])
        }

        override fun getItemCount(): Int = rows.size

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            private val body: TextView = view.findViewById(R.id.threadBody)

            fun bind(row: Row) {
                val lp = body.layoutParams as FrameLayout.LayoutParams
                lp.gravity = if (row.user) Gravity.END else Gravity.START
                body.layoutParams = lp
                if (row.user) {
                    body.background = itemView.context.getDrawable(R.drawable.bg_bubble_user)
                    body.setTextColor(itemView.context.getColor(R.color.bubble_user_on))
                    body.text = row.text
                } else {
                    body.background = itemView.context.getDrawable(R.drawable.bg_bubble_ai)
                    body.setTextColor(itemView.context.getColor(R.color.bubble_ai_on))
                    if (row.pending) body.text = row.text else CardFaceBinder.applySections(body, row.text)
                }
            }
        }
    }

    companion object {
        private const val ARG_TURN = "turnId"

        fun newInstance(turnId: Long): ThreadSheet {
            return ThreadSheet().apply {
                arguments = Bundle().apply { putLong(ARG_TURN, turnId) }
            }
        }
    }
}
