package com.evan.voicesrs

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.content.pm.PackageManager
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.transition.Fade
import android.transition.TransitionManager
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Deck, due count, current card, and Start / Stop / Settings. */
class MainActivity : AppCompatActivity() {

    private lateinit var deckPathView: TextView
    private lateinit var deckNameView: TextView
    private lateinit var dueCountView: TextView
    private lateinit var stateText: TextView
    private var currentCard: MaterialCardView? = null
    private var frontLabel: TextView? = null
    private var cardFront: TextView? = null
    private var backLabel: TextView? = null
    private var cardBack: TextView? = null
    private var heardText: TextView? = null
    private var resumePulse: ObjectAnimator? = null
    private var chipAgain: Chip? = null
    private var chipHard: Chip? = null
    private var chipGood: Chip? = null
    private var chipEasy: Chip? = null
    private lateinit var progressText: TextView
    /** Plain voice line on the wide status card. Absent on the narrow header. */
    private var voiceLineView: TextView? = null
    /** Narrow header: "Done N · Left N · 🔊 Voice: …". Absent on the wide layout. */
    private var headerMeta: TextView? = null
    /** Null until /health returns. Unknown health still shows Edge when it is selected. */
    @Volatile private var edgeHealthy: Boolean? = null
    private var edgeProbeGeneration = 0
    private lateinit var syncChip: Chip
    /** Last pending-sync count applied by [render]; drives menu invalidation. */
    private var renderedPendingSync = -1
    private lateinit var errorCard: MaterialCardView
    private lateinit var errorText: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var showAnswerButton: Button? = null
    private var silentSwitch: com.google.android.material.materialswitch.MaterialSwitch? = null
    private var timerMinus: Button? = null
    private var timerValue: TextView? = null
    private var timerPlus: Button? = null
    private var timerSwitch: com.google.android.material.materialswitch.MaterialSwitch? = null
    /** Last switch position the user or a fresh service snapshot committed to. */
    private var switchModel = SwitchBoard.Model(on = false, generation = 0)
    /** Microphone permission request is so a running drill can leave silent mode. */
    private var pendingVoice = false
    /** A sync is in flight; disables the sync menu action to avoid double taps. */
    private var syncing = false

    private var pendingStatus = ""
    /** Permission request in flight was for a silent start, so RECORD_AUDIO is not required. */
    private var startWithoutMic = false
    private var renderedFront = ""
    private var renderedBack = ""
    /** Last [DrillUiState.drillRunning] applied by [uiListener]; used to refresh due on session end. */
    private var lastDrillRunning = false
    private var chatPane: ChatPane? = null
    private var chatDictation: ChatDictation? = null
    private var chatInputView: EditText? = null
    private var appliedHintRes = 0

    private val reviewListener: (List<ReviewTurn>) -> Unit = {
        runOnUiThread {
            if (!isDestroyed) invalidateOptionsMenu()
        }
    }

    private val uiListener: (DrillUiState) -> Unit = { snapshot ->
        runOnUiThread {
            if (isDestroyed) return@runOnUiThread
            val ended = lastDrillRunning && !snapshot.drillRunning
            lastDrillRunning = snapshot.drillRunning
            render(snapshot)
            if (ended) refreshCounts()
        }
    }

    private val playbackListener: (PlaybackControl.Snapshot) -> Unit = {
        runOnUiThread {
            if (!isDestroyed) paintPlayback(it)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (pendingVoice) {
            pendingVoice = false
            if (grants[Manifest.permission.RECORD_AUDIO] == true) {
                switchModel = SwitchBoard.userTap(switchModel, false)
                paintSwitch(switchModel.on)
                persistSilent(false)
            }
            return@registerForActivityResult
        }
        val micOk = startWithoutMic || grants[Manifest.permission.RECORD_AUDIO] == true
        if (micOk) {
            startDrillService()
        } else {
            Toast.makeText(this, "Microphone permission is required for the drill.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        deckPathView = findViewById(R.id.deckPath)
        deckNameView = findViewById(R.id.deckName)
        dueCountView = findViewById(R.id.dueCount)
        stateText = findViewById(R.id.stateText)
        currentCard = findViewById(R.id.currentCard)
        frontLabel = findViewById(R.id.frontLabel)
        cardFront = findViewById(R.id.cardFront)
        backLabel = findViewById(R.id.backLabel)
        cardBack = findViewById(R.id.cardBack)
        heardText = findViewById(R.id.heardText)
        chipAgain = findViewById(R.id.chipAgain)
        chipHard = findViewById(R.id.chipHard)
        chipGood = findViewById(R.id.chipGood)
        chipEasy = findViewById(R.id.chipEasy)
        progressText = findViewById(R.id.progressText)
        voiceLineView = findViewById(R.id.voiceLine)
        headerMeta = findViewById(R.id.headerMeta)
        syncChip = findViewById(R.id.syncChip)
        errorCard = findViewById(R.id.errorCard)
        errorText = findViewById(R.id.errorText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        showAnswerButton = findViewById(R.id.showAnswerButton)
        showAnswerButton?.setOnClickListener { DrillService.submitShowAnswer() }
        silentSwitch = findViewById(R.id.drillSilentSwitch)
        silentSwitch?.setOnCheckedChangeListener { _, checked -> onSilentSwitch(checked) }
        timerMinus = findViewById(R.id.timerMinus)
        timerValue = findViewById(R.id.timerValue)
        timerPlus = findViewById(R.id.timerPlus)
        timerSwitch = findViewById(R.id.timerSwitch)
        timerMinus?.setOnClickListener { onTimerStep(-TimerRowModel.STEP_MINUTES) }
        timerPlus?.setOnClickListener { onTimerStep(TimerRowModel.STEP_MINUTES) }
        timerSwitch?.setOnCheckedChangeListener { _, checked -> onTimerSwitch(checked) }
        findViewById<Button>(R.id.retryButton).setOnClickListener { retryDrill() }
        syncChip.setOnClickListener { syncPending() }
        chipAgain?.setOnClickListener { if (ratingOpen()) DrillService.submitTestGrade("again") }
        chipHard?.setOnClickListener { if (ratingOpen()) DrillService.submitTestGrade("hard") }
        chipGood?.setOnClickListener { if (ratingOpen()) DrillService.submitTestGrade("good") }
        chipEasy?.setOnClickListener { if (ratingOpen()) DrillService.submitTestGrade("easy") }
        startButton.setOnClickListener { ensurePermissionsAndStart() }
        stopButton.setOnClickListener {
            stopService(Intent(this, DrillService::class.java))
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.playbackPauseButton)?.setOnClickListener {
            PlaybackSpeaker.togglePause(this)
        }
        showCrashIfAny()
        bindDeckTitle(Prefs.load(this).deck)
        wireChatComposer()
        chatInputView = findViewById(R.id.chatInput)
        val chatList = findViewById<RecyclerView>(R.id.chatList)
        val chatUnread = findViewById<TextView>(R.id.chatUnread)
        if (chatList != null && chatUnread != null) {
            val embedControls = findViewById<View>(R.id.bottomBar) == null
            chatPane = ChatPane(
                chatList,
                chatUnread,
                findViewById(R.id.chatEmpty),
                embedControls = embedControls,
                nextPill = findViewById(R.id.chatNextPill)
            ).also { it.attach() }
        }
        applyAiChrome()
        refreshTimerRow()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_sync)?.apply {
            isVisible = MenuSync.visible(DrillUi.state.pendingSync, syncing)
            isEnabled = !syncing
        }
        menu.findItem(R.id.action_history)?.apply {
            isVisible = findViewById<View>(R.id.chatList) == null
            val unread = ReviewLog.unreadCount()
            title = if (unread > 0) {
                getString(R.string.action_history_count, unread)
            } else {
                getString(R.string.action_history)
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_sync -> {
                syncPending()
                return true
            }
            R.id.action_history -> {
                if (supportFragmentManager.findFragmentByTag("history") == null) {
                    HistorySheet().show(supportFragmentManager, "history")
                }
                return true
            }
            R.id.action_bookmarks -> {
                if (supportFragmentManager.findFragmentByTag("bookmarks") == null) {
                    BookmarksSheet().show(supportFragmentManager, "bookmarks")
                }
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        lastDrillRunning = DrillService.running
        DrillUi.addListener(uiListener)
        ReviewLog.addListener(reviewListener)
        PlaybackUi.addListener(playbackListener)
        invalidateOptionsMenu()
        if (!DrillService.running) {
            if (DrillUi.state.drillRunning || DrillUi.state.progressLine.isNotEmpty()) {
                DrillUi.update { it.copy(drillRunning = false, progressLine = "") }
            }
        } else if (!DrillUi.state.drillRunning) {
            DrillUi.update { it.copy(drillRunning = true) }
        }
        render(DrillUi.state)
    }

    /** If the previous run died, surface the persisted crash report. */
    private fun showCrashIfAny() {
        val f = CrashReporter.latestCrash(this) ?: return
        val full = try { f.readText() } catch (_: Exception) { return }
        val preview = full.take(4000)
        MaterialAlertDialogBuilder(this)
            .setTitle("Voice SRS crashed last run")
            .setMessage(preview)
            .setPositiveButton("Copy report") { _, _ ->
                try {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("voicesrs-crash", full))
                    Toast.makeText(this, "Crash report copied.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Copy failed.", Toast.LENGTH_LONG).show()
                }
            }
            .setNeutralButton("Clear") { _, _ -> CrashReporter.clearCrashes(this) }
            .setNegativeButton("Close", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        chatPane?.setResumed(true)
        probeEdgeHealth()
        applyAiChrome()
        val pending = GradeSync.pendingCount(filesDir)
        if (pending != DrillUi.state.pendingSync) {
            DrillUi.update { it.copy(pendingSync = pending) }
        }
        refreshCounts()
        render(DrillUi.state)
        refreshTimerRow()
    }

    override fun onPause() {
        chatPane?.setResumed(false)
        super.onPause()
    }

    override fun onStop() {
        DrillUi.removeListener(uiListener)
        ReviewLog.removeListener(reviewListener)
        PlaybackUi.removeListener(playbackListener)
        super.onStop()
    }

    override fun onDestroy() {
        chatDictation?.stop()
        chatDictation = null
        DrillService.dictationFinished()
        stopResumePulse(findViewById(R.id.chatResumeButton))
        chatPane?.detach()
        chatPane = null
        PlaybackSpeaker.stop()
        super.onDestroy()
    }

    /**
     * Focus, typing, and dictation pause the drill. Sending does not resume.
     * The listener is attached after the start button takes focus so opening
     * the screen does not pause a running drill.
     */
    private fun wireChatComposer() {
        val input = findViewById<EditText>(R.id.chatInput) ?: return
        val shell = findViewById<View>(R.id.chatInputShell)
        val dictate = findViewById<ImageButton>(R.id.chatDictate)
        val send = findViewById<ImageButton>(R.id.chatSend)
        val hint = findViewById<TextView>(R.id.chatDictateHint)
        val resume = findViewById<Button>(R.id.chatResumeButton)
        input.clearFocus()
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        )
        startButton.requestFocus()
        val paintShell = {
            shell?.isActivated = input.hasFocus() || !input.text.isNullOrEmpty()
        }
        input.setOnFocusChangeListener { _, hasFocus ->
            paintShell()
            if (hasFocus) {
                DrillService.pauseForChat(ChatPause.Trigger.FOCUS_INPUT)
                DrillService.noteChatReengage()
            } else {
                DrillService.noteChatDisengage(ChatPause.Disengage.UNFOCUSED)
            }
        }
        var hadComposerText = !input.text.isNullOrEmpty()
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                paintShell()
                if (!s.isNullOrEmpty()) {
                    hadComposerText = true
                    hint?.visibility = View.GONE
                    DrillService.pauseForChat(ChatPause.Trigger.TYPING)
                    DrillService.noteChatReengage()
                } else if (hadComposerText) {
                    hadComposerText = false
                    DrillService.noteChatCleared()
                }
            }
        })
        val submit = {
            val sent = sendChatQuestion(input.text?.toString().orEmpty(), byVoice = false)
            if (sent) input.setText("")
        }
        send?.setOnClickListener { submit() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit()
                true
            } else {
                false
            }
        }
        dictate?.setOnClickListener {
            hint?.visibility = View.GONE
            DrillService.noteChatReengage()
            DrillService.prepareDictation {
                if (isDestroyed) {
                    DrillService.dictationFinished()
                    return@prepareDictation
                }
                if (chatDictation == null) chatDictation = ChatDictation(this)
                chatDictation?.start(
                    onPartial = { partial ->
                        runOnUiThread {
                            if (isDestroyed) return@runOnUiThread
                            input.setText(partial)
                            input.setSelection(input.text?.length ?: 0)
                        }
                    },
                    onFinal = { spoken ->
                        runOnUiThread {
                            if (isDestroyed) return@runOnUiThread
                            DrillService.dictationFinished()
                            input.setText(spoken)
                            input.setSelection(input.text?.length ?: 0)
                            val sent = sendChatQuestion(spoken, byVoice = true)
                            if (sent) input.setText("")
                        }
                    },
                    onMiss = {
                        runOnUiThread {
                            if (isDestroyed) return@runOnUiThread
                            DrillService.dictationFinished()
                            hint?.visibility = View.VISIBLE
                            DrillService.noteChatDisengage(ChatPause.Disengage.DICTATION_FAILED)
                        }
                    }
                )
            }
        }
        resume?.setOnClickListener { DrillService.resumeFromChat() }
    }

    /**
     * The main box targets the card on screen, the same one named above the field.
     * No card is a general question about the selected deck. Failures are visible.
     * Asking does not grade, and it does not pause a drill that is not running.
     * @return true when the question was accepted.
     */
    private fun sendChatQuestion(raw: String, byVoice: Boolean): Boolean {
        val state = DrillUi.state
        val drilling = state.drillRunning || DrillService.running
        val screen = if (drilling && state.questionOrdinal > 0 && state.frontHtml.isNotBlank()) {
            ChatTarget.Screen(state.cardId, state.questionOrdinal, state.showBack)
        } else {
            null
        }
        val deck = try {
            val cfg = Prefs.load(this)
            DeckName.split(cfg.deck).second.ifBlank { cfg.deck }
        } catch (_: Exception) {
            ""
        }
        return when (val outcome = ChatSend.resolve(raw, screen, deck)) {
            is ChatSend.Outcome.Visible -> {
                GeneralChat.showNotice(outcome.message)
                false
            }
            is ChatSend.Outcome.General -> {
                GeneralChat.ask(this, outcome.deck, outcome.question)
                DrillService.noteQuestionSent()
                true
            }
            is ChatSend.Outcome.Card -> {
                val accepted = LiveThread.ask(this, outcome.question, byVoice)
                if (!accepted) {
                    GeneralChat.showNotice(ChatSend.visible(ChatSend.Fail.AI))
                    false
                } else {
                    DrillService.noteQuestionSent()
                    true
                }
            }
        }
    }

    /** The unfolded right pane is the chat. It stays full height with or without AI. */
    private fun applyAiChrome() {
        findViewById<View>(R.id.reviewPane)?.visibility = View.VISIBLE
        findViewById<View>(R.id.chatList)?.visibility = View.VISIBLE
    }

    private fun syncPending() {
        if (syncing) return
        val before = GradeSync.pendingCount(filesDir)
        if (before == 0) {
            Toast.makeText(this, getString(R.string.sync_none), Toast.LENGTH_SHORT).show()
            return
        }
        syncing = true
        val cfg = Prefs.load(this)
        Thread {
            val left = try {
                GradeSync.sync(filesDir, AnkiConnect(cfg.url, cfg.apiKey), attempts = 4)
            } catch (_: Exception) {
                GradeSync.pendingCount(filesDir)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                DrillUi.update { it.copy(pendingSync = left) }
                syncing = false
                invalidateOptionsMenu()
                val message = if (left == 0) {
                    getString(R.string.sync_done)
                } else {
                    getString(R.string.sync_still, left)
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun bindDeckTitle(full: String) {
        val (path, leaf) = DeckName.split(full)
        deckNameView.text = leaf.ifEmpty { full }
        if (path.isEmpty()) {
            deckPathView.visibility = View.GONE
            deckPathView.text = ""
        } else {
            deckPathView.text = path
            deckPathView.visibility = View.VISIBLE
        }
    }

    private fun refreshCounts() {
        val cfg = Prefs.load(this)
        bindDeckTitle(cfg.deck)
        dueCountView.text = "Checking Anki…"
        Thread {
            val text = try {
                val due = AnkiConnect(cfg.url, cfg.apiKey).dueCount(cfg.deck)
                getString(R.string.due_format, due)
            } catch (e: Exception) {
                "Anki unreachable: ${e.message}"
            }
            runOnUiThread { if (!isDestroyed) dueCountView.text = text }
        }.start()
    }

    private fun render(state: DrillUiState) {
        val cfg = Prefs.load(this)
        val drillingNow = state.drillRunning || DrillService.running
        crossfadeStatus(
            InfoCard.compose(state.heard, state.status, drillingNow)
                .ifBlank { getString(R.string.state_ready) }
        )
        val frontView = cardFront
        val backView = cardBack
        val card = currentCard
        if (frontView == null || card == null) {
            // Unfolded layout has no pinned card. The chat row is the question.
        } else if (!cfg.showCard) {
            frontLabel?.visibility = View.GONE
            frontView.visibility = View.GONE
            backLabel?.visibility = View.GONE
            backView?.visibility = View.GONE
            heardText?.visibility = View.GONE
        } else {
            frontLabel?.visibility = View.VISIBLE
            frontView.visibility = View.VISIBLE
            if (state.frontHtml.isBlank()) {
                if (renderedFront.isNotEmpty() || frontView.text.toString() != getString(R.string.card_idle)) {
                    renderedFront = ""
                    frontView.text = getString(R.string.card_idle)
                }
            } else if (renderedFront != state.frontHtml) {
                renderedFront = state.frontHtml
                val face = QuestionLabel.of(state.frontHtml, state.questionOrdinal.coerceAtLeast(1))
                val shown = if (face.fromCard) face.stem else state.frontHtml
                CardFaceBinder.setMarkup(frontView, shown.ifBlank { state.frontHtml })
            }
            val showBack = state.showBack && state.backHtml.isNotBlank()
            val backVis = if (showBack) View.VISIBLE else View.GONE
            if (backLabel?.visibility != backVis || backView?.visibility != backVis) {
                TransitionManager.beginDelayedTransition(card, Fade())
            }
            backLabel?.visibility = backVis
            backView?.visibility = backVis
            if (showBack && renderedBack != state.backHtml && backView != null) {
                renderedBack = state.backHtml
                CardFaceBinder.setMarkup(backView, state.backHtml)
            }
            heardText?.visibility = View.GONE
            heardText?.text = ""
        }
        val progressLine = SessionProgress.visibleLine(DrillService.running, state.progressLine)
        val voice = currentVoiceLine(cfg, state)
        val goal = SessionProgress.goalLabel(cfg.goalMode, cfg.goalCards, cfg.goalMinutes)
        val meta = headerMeta
        if (meta != null) {
            meta.text = VoiceStatusLine.headerLine(progressLine, voice, goal)
            progressText.visibility = View.GONE
        } else {
            voiceLineView?.text = voice
            voiceLineView?.visibility = View.VISIBLE
            val shown = SessionProgress.withGoal(progressLine, goal)
            if (shown.isBlank()) {
                progressText.visibility = View.GONE
            } else {
                progressText.visibility = View.VISIBLE
                progressText.text = shown
            }
        }
        if (MenuSync.shouldInvalidate(renderedPendingSync, state.pendingSync)) {
            renderedPendingSync = state.pendingSync
            invalidateOptionsMenu()
        }
        if (state.pendingSync > 0) {
            syncChip.visibility = View.VISIBLE
            syncChip.text = getString(R.string.pending_sync, state.pendingSync)
        } else {
            syncChip.visibility = View.GONE
        }
        if (state.showError && state.errorMessage.isNotBlank()) {
            errorCard.visibility = View.VISIBLE
            errorText.text = state.errorMessage
            findViewById<TextView>(R.id.errorTitle).text =
                state.errorTitle.ifBlank { getString(R.string.error_title) }
        } else {
            errorCard.visibility = View.GONE
        }
        val drilling = state.drillRunning || DrillService.running
        if (drilling) {
            switchModel = SwitchBoard.reconcile(
                switchModel,
                SwitchBoard.Paint(state.silentSession, state.silentGeneration)
            )
        } else {
            switchModel = SwitchBoard.Model(cfg.silentMode, switchModel.generation)
        }
        paintSwitch(switchModel.on)
        val silentOn = switchModel.on
        val hasQuestion = drilling && state.frontHtml.isNotBlank()
        val askAbout = findViewById<TextView>(R.id.chatAskAbout)
        val leaf = DeckName.split(cfg.deck).second.ifBlank { cfg.deck }
        val askLine = if (hasQuestion) {
            val label = state.questionLabel.ifBlank {
                QuestionLabel.of(state.frontHtml, state.questionOrdinal).label
            }
            ChatAskLabel.line(label, leaf)
        } else {
            ChatAskLabel.general(leaf)
        }
        if (askAbout != null) {
            if (askLine == null) {
                askAbout.visibility = View.GONE
            } else {
                askAbout.visibility = View.VISIBLE
                askAbout.text = askLine
            }
        }
        val hintRes = ChatAskLabel.hint(hasQuestion)
        if (chatInputView != null && appliedHintRes != hintRes) {
            chatInputView?.hint = getString(hintRes)
            appliedHintRes = hintRes
        }
        val reportChip = findViewById<Chip>(R.id.todayReportChip)
        if (reportChip != null) {
            val showReport = !hasQuestion && cfg.aiEnabled
            reportChip.visibility = if (showReport) View.VISIBLE else View.GONE
            if (showReport && reportChip.tag == null) {
                reportChip.tag = "wired"
                reportChip.setOnClickListener {
                    val fullDeck = try {
                        Prefs.load(this).deck
                    } catch (_: Exception) {
                        ""
                    }
                    if (fullDeck.isBlank()) {
                        GeneralChat.showNotice(ChatSend.visible(ChatSend.Fail.NO_DECK))
                    } else {
                        GeneralChat.todayReport(this, fullDeck)
                    }
                }
            }
        }
        val controls = AnswerControls.of(
            hasQuestion = hasQuestion,
            answerShown = state.showBack,
            silent = silentOn
        )
        val ratingVis = if (controls.rating) View.VISIBLE else View.GONE
        findViewById<View>(R.id.gradeGuideLabel)?.visibility = ratingVis
        findViewById<TextView>(R.id.gradeGuideLabel)?.setText(
            if (silentOn) R.string.grade_guide_tap else R.string.grade_guide
        )
        findViewById<View>(R.id.gradeChips)?.visibility = ratingVis
        val sel = if (controls.rating) state.selectedEase else null
        val suggested = if (controls.rating) state.suggestedEase else null
        RatingSuggest.apply(chipAgain, 1, sel, suggested)
        RatingSuggest.apply(chipHard, 2, sel, suggested)
        RatingSuggest.apply(chipGood, 3, sel, suggested)
        RatingSuggest.apply(chipEasy, 4, sel, suggested)
        startButton.isEnabled = !state.drillRunning
        if (headerMeta != null) {
            startButton.visibility = if (drilling) View.GONE else View.VISIBLE
        }
        stopButton.isEnabled = state.drillRunning
        showAnswerButton?.visibility = if (controls.showAnswer) View.VISIBLE else View.GONE
        renderCue(state, drilling, silentOn)
        paintPlayback(PlaybackUi.state)
        val keepOn = KeepScreenOn.apply(drilling, cfg.keepScreenOn)
        if (keepOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun paintPlayback(snap: PlaybackControl.Snapshot) {
        val pause = findViewById<Button>(R.id.playbackPauseButton) ?: return
        pause.visibility = if (PlaybackControl.pauseVisible()) View.VISIBLE else View.GONE
        pause.isEnabled = PlaybackControl.pauseEnabled(snap)
        pause.text = if (snap.state == PlaybackControl.State.PAUSED) {
            getString(R.string.playback_resume)
        } else {
            getString(R.string.playback_pause)
        }
        val status = findViewById<TextView>(R.id.playbackStatus)
        status?.text = when (snap.state) {
            PlaybackControl.State.LOADING -> getString(R.string.playback_loading)
            PlaybackControl.State.READING -> getString(R.string.playback_reading)
            PlaybackControl.State.PAUSED -> getString(R.string.playback_resume)
            PlaybackControl.State.IDLE -> ""
        }
    }

    /** Countdown while thinking. The pause row replaces it and stays until Resume or a rating. */
    private fun renderCue(state: DrillUiState, drilling: Boolean, silentOn: Boolean) {
        val pauseRow = findViewById<View>(R.id.chatPauseRow)
        val countdown = findViewById<TextView>(R.id.thinkCountdown)
        val seconds = state.thinkSecondsLeft
        val resume = findViewById<Button>(R.id.chatResumeButton)
        if (drilling && state.chatPaused) {
            pauseRow?.visibility = View.VISIBLE
            countdown?.visibility = View.GONE
            resume?.visibility = if (state.chatShowResume) View.VISIBLE else View.GONE
            pulseResume(resume, state.chatShowResume && state.chatResumePulse)
        } else if (drilling && !silentOn && seconds != null && seconds > 0 && !state.showBack) {
            pauseRow?.visibility = View.GONE
            countdown?.visibility = View.VISIBLE
            countdown?.text = ThinkTime.countdownText(seconds)
            stopResumePulse(resume)
        } else {
            pauseRow?.visibility = View.GONE
            countdown?.visibility = View.GONE
            stopResumePulse(resume)
        }
    }

    /** Soft accent-pink pulse after a question was sent. Idle resume stays still. */
    private fun pulseResume(button: Button?, on: Boolean) {
        if (button == null) return
        if (!on) {
            stopResumePulse(button)
            return
        }
        val typed = TypedValue()
        button.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typed, true)
        button.backgroundTintList = ColorStateList.valueOf(typed.data)
        if (resumePulse?.isRunning == true) return
        resumePulse = ObjectAnimator.ofFloat(button, View.ALPHA, 1f, 0.45f).apply {
            duration = 700L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun stopResumePulse(button: Button?) {
        resumePulse?.cancel()
        resumePulse = null
        button?.alpha = 1f
        button?.backgroundTintList = null
    }

    private fun ratingOpen(): Boolean {
        val state = DrillUi.state
        val drilling = state.drillRunning || DrillService.running
        val silentOn = if (drilling) state.silentSession else Prefs.load(this).silentMode
        return AnswerControls.of(
            hasQuestion = drilling && state.frontHtml.isNotBlank(),
            answerShown = state.showBack,
            silent = silentOn
        ).rating
    }

    /**
     * The engine that will speak. A live drill label wins once an utterance
     * has actually started. Before that, a selected Edge voice stays on the
     * line until /health fails. The line is not a chip and nothing here is logged.
     */
    private fun currentVoiceLine(cfg: AnkiConfig, state: DrillUiState): String {
        val preferred = TtsEngines.preferred(cfg.ttsEngine)
        val drilling = state.drillRunning || DrillService.running
        val engine = when {
            drilling && state.engineLabel == EngineLabel.NEURAL -> EngineChoice.NEURAL
            drilling && state.engineLabel == EngineLabel.DEVICE -> EngineChoice.ON_DEVICE
            else -> VoiceStatusLine.resolve(preferred, edgeHealthy)
        }
        val name = if (engine == EngineChoice.NEURAL) cfg.neuralVoice else cfg.ttsVoice
        return VoiceStatusLine.format(engine, name)
    }

    /** Background /health. The token and URL are not written to the log. */
    private fun probeEdgeHealth() {
        val cfg = try {
            Prefs.load(this)
        } catch (_: Exception) {
            return
        }
        if (TtsEngines.preferred(cfg.ttsEngine) != EngineChoice.NEURAL) {
            edgeHealthy = null
            return
        }
        val generation = ++edgeProbeGeneration
        val app = applicationContext
        val url = cfg.neuralUrl
        val token = cfg.neuralToken
        Thread {
            val ok = try {
                EdgeSpeaker(app).healthy(url, token)
            } catch (_: Exception) {
                false
            }
            if (generation != edgeProbeGeneration) return@Thread
            edgeHealthy = ok
            runOnUiThread {
                if (!isDestroyed) render(DrillUi.state)
            }
        }.start()
    }

    private fun onTimerStep(delta: Int) {
        val cfg = try {
            Prefs.load(this)
        } catch (_: Exception) {
            return
        }
        Prefs.save(this, TimerRowModel.step(cfg, delta))
        refreshTimerRow()
    }

    private fun onTimerSwitch(checked: Boolean) {
        val cfg = try {
            Prefs.load(this)
        } catch (_: Exception) {
            return
        }
        Prefs.save(this, TimerRowModel.setEnabled(cfg, checked))
        refreshTimerRow()
    }

    private fun refreshTimerRow() {
        val value = timerValue ?: return
        val cfg = try {
            Prefs.load(this)
        } catch (_: Exception) {
            return
        }
        value.text = TimerRowModel.label(cfg)
        paintTimerSwitch(TimerRowModel.enabled(cfg))
    }

    private fun paintTimerSwitch(on: Boolean) {
        val sw = timerSwitch ?: return
        if (sw.isChecked == on) return
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, checked -> onTimerSwitch(checked) }
    }

    private fun onSilentSwitch(checked: Boolean) {
        val drilling = DrillUi.state.drillRunning || DrillService.running
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!checked && drilling && !mic) {
            pendingVoice = true
            paintSwitch(true)
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        // Commit the tap before any snapshot render can run, including one
        // nested inside setSilentMode on this same thread.
        switchModel = SwitchBoard.userTap(switchModel, checked)
        paintSwitch(switchModel.on)
        persistSilent(checked)
    }

    /** Programmatic paints detach the listener so they cannot undo a tap. */
    private fun paintSwitch(on: Boolean) {
        val sw = silentSwitch ?: return
        if (sw.isChecked == on) return
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = on
        sw.setOnCheckedChangeListener { _, checked -> onSilentSwitch(checked) }
    }

    private fun persistSilent(checked: Boolean) {
        val cfg = try {
            Prefs.load(this)
        } catch (_: Exception) {
            return
        }
        if (cfg.silentMode != checked) Prefs.save(this, cfg.copy(silentMode = checked))
        DrillService.setSilentMode(checked)
    }

    private fun crossfadeStatus(next: String) {
        if (next.isEmpty() || stateText.text.toString() == next) return
        pendingStatus = next
        stateText.animate().cancel()
        stateText.alpha = 1f
        stateText.animate().alpha(0f).setDuration(90).withEndAction {
            stateText.text = pendingStatus
            stateText.animate().alpha(1f).setDuration(160).start()
        }.start()
    }

    /**
     * Retry is on the error card, including while the failed session is still
     * speaking. A plain start is ignored until that session exits, so the tap
     * does nothing for the whole utterance. Ask the service to abandon it and
     * start a new one. If the service is already gone, start normally.
     */
    private fun retryDrill() {
        if (DrillService.running) {
            try {
                val intent = Intent(this, DrillService::class.java).setAction(DrillService.ACTION_RETRY)
                ContextCompat.startForegroundService(this, intent)
            } catch (e: Exception) {
                Log.e("MainActivity", "retry failed", e)
                Toast.makeText(this, "Could not retry: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            ensurePermissionsAndStart()
        }
    }

    private fun ensurePermissionsAndStart() {
        if (DrillUi.state.drillRunning || DrillService.running) return
        val mode = SilentPolicy.of(Prefs.load(this).silentMode)
        startWithoutMic = !mode.requestRecordAudio
        val needed = mutableListOf<String>()
        if (mode.requestRecordAudio) needed += Manifest.permission.RECORD_AUDIO
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startDrillService()
        else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun startDrillService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, DrillService::class.java))
            Toast.makeText(this, "Drill starting…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "startForegroundService failed", e)
            Toast.makeText(this, "Could not start drill: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
