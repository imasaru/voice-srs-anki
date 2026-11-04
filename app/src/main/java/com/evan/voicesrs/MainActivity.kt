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
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
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
import java.time.LocalDate
import java.time.ZoneId
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Deck, due count, current card, and Start / Stop / Settings. */
class MainActivity : AppCompatActivity() {

    private lateinit var deckPathView: TextView
    private lateinit var deckNameView: TextView
    private lateinit var dueCountView: TextView
    private var examCountdown: TextView? = null
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
    /** Narrow header: "Done N · Left N · Voice: …". Absent on the wide layout. */
    private var headerMeta: TextView? = null
    /** Null until /health returns. Unknown health still shows Edge when it is selected. */
    @Volatile private var edgeHealthy: Boolean? = null
    private var edgeProbeGeneration = 0
    private lateinit var syncChip: Chip
    /** Last pending-sync count applied by [render]; drives menu invalidation. */
    private var renderedPendingSync = -1
    /** Deck the running drill was started against; null when no drill has run here. */
    private var drillDeck: String? = null
    private lateinit var errorCard: MaterialCardView
    private lateinit var errorText: TextView
    private lateinit var startButton: Button
    private var showAnswerButton: Button? = null
    private var voiceChip: MaterialButton? = null
    private var timerMinus: Button? = null
    private var timerValue: TextView? = null
    private var timerPlus: Button? = null
    private var timerGoalChip: MaterialButton? = null
    /** Last goal minutes from refreshTimerRow; tickElapsed reads this, never Prefs. */
    private var cachedGoalMinutes = 0
    /** Goal-bar fill 0..1000, set each tick from [cachedGoalMinutes]. */
    private var timerGoalBar: LinearProgressIndicator? = null
    private var voiceChipListener: MaterialButton.OnCheckedChangeListener? = null
    private var timerChipListener: MaterialButton.OnCheckedChangeListener? = null
    /** "Elapsed m:ss" chip beside the countdown. Null when the timer row is not inflated. */
    private var elapsedChip: TextView? = null
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
    private var readingSpan: android.text.style.BackgroundColorSpan? = null
    private var paintedReading = ""
    /** Last [DrillUiState.drillRunning] applied by [uiListener]; used to refresh due on session end. */
    private var lastDrillRunning = false
    private var chatPane: ChatPane? = null
    /** readingSentence|readingIndex last sent to the chat list (h80). */
    private var lastReadingKey = ""
    private var chatDictation: ChatDictation? = null
    private var chatInputView: EditText? = null
    private var appliedHintRes = 0
    /** Timer-row visibility captured before the IME opens; restored when it closes. */
    private var timerRowWantedVisible = false
    /** Session-cue visibility captured before the wide-layout IME opens; restored when it closes. */
    private var cueWantedVisible = false
    /** Last IME state we acted on, so visibility only changes when it flips. */
    private var lastImeVisible = false

    private val elapsedHandler = Handler(Looper.getMainLooper())
    /** Ticks the elapsed chip once a second while a drill runs. Never touches the chat pane. */
    private val elapsedRunnable: Runnable = Runnable { tickElapsed() }
    /** True between onResume and onPause; the ticker only runs while resumed. */
    private var elapsedResumed = false
    /** True while the ticker is scheduled, so render() can start it when a drill begins. */
    private var elapsedTicking = false

    private fun tickElapsed() {
        val chip = elapsedChip ?: return
        val state = DrillUi.state
        if (ElapsedChip.visible(state.drillRunning)) {
            val ms = ElapsedChip.elapsed(
                state.elapsedStartMs,
                state.elapsedPausedTotalMs,
                state.elapsedPausedSinceMs,
                SystemClock.elapsedRealtime()
            )
            chip.text = ElapsedChip.short(ms)
            chip.contentDescription = ElapsedChip.format(ms)
            chip.visibility = View.VISIBLE
            val bar = timerGoalBar
            if (bar != null) bar.progress = TimerRowModel.progress(ms, cachedGoalMinutes)
            elapsedHandler.postDelayed(elapsedRunnable, 1_000L)
        } else {
            elapsedHandler.removeCallbacks(elapsedRunnable)
            elapsedTicking = false
            val bar = timerGoalBar
            if (bar != null) bar.progress = 0
            // Idle: keep the chip visible at 0:00 so the row does not jump height.
            chip.visibility = View.VISIBLE
            chip.text = ElapsedChip.short(0)
            chip.contentDescription = null
        }
    }

    private fun startElapsedTicker() {
        elapsedTicking = true
        elapsedHandler.removeCallbacks(elapsedRunnable)
        elapsedHandler.post(elapsedRunnable)
    }

    private fun stopElapsedTicker() {
        elapsedTicking = false
        elapsedHandler.removeCallbacks(elapsedRunnable)
    }

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
            val readingKey = snapshot.readingSentence + "|" + snapshot.readingIndex
            if (readingKey != lastReadingKey) {
                lastReadingKey = readingKey
                chatPane?.notifyReading(snapshot)
            }
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Fold/unfold recreates the activity; keep the drill's deck so the notice still works.
        outState.putString(KEY_DRILL_DECK, drillDeck)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drillDeck = savedInstanceState?.getString(KEY_DRILL_DECK)
        runCatching { ChatHistoryStore.load(filesDir) }
        runCatching { AnswerTimeLog.init(filesDir) }
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
        deckPathView = findViewById(R.id.deckPath)
        deckNameView = findViewById(R.id.deckName)
        dueCountView = findViewById(R.id.dueCount)
        examCountdown = findViewById(R.id.examCountdown)
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
        showAnswerButton = findViewById(R.id.showAnswerButton)
        showAnswerButton?.setOnClickListener { DrillService.submitShowAnswer() }
        voiceChip = findViewById<MaterialButton>(R.id.voiceChip)
        voiceChipListener = MaterialButton.OnCheckedChangeListener { _, checked -> onSilentSwitch(ToggleChips.silentFromVoice(checked)) }
        voiceChip?.addOnCheckedChangeListener(voiceChipListener!!)
        timerMinus = findViewById(R.id.timerMinus)
        timerValue = findViewById(R.id.timerValue)
        timerPlus = findViewById(R.id.timerPlus)
        timerGoalChip = findViewById<MaterialButton>(R.id.timerGoalChip)
        timerGoalBar = findViewById<LinearProgressIndicator>(R.id.timerGoalBar)
        elapsedChip = findViewById<TextView>(R.id.elapsedChip)
        timerMinus?.setOnClickListener { onTimerStep(-TimerRowModel.STEP_MINUTES) }
        timerPlus?.setOnClickListener { onTimerStep(TimerRowModel.STEP_MINUTES) }
        timerChipListener = MaterialButton.OnCheckedChangeListener { _, checked -> onTimerSwitch(checked) }
        timerGoalChip?.addOnCheckedChangeListener(timerChipListener!!)
        findViewById<Button>(R.id.retryButton).setOnClickListener { retryDrill() }
        syncChip.setOnClickListener { syncPending() }
        chipAgain?.setOnClickListener { if (ratingOpen()) DrillService.submitTestGrade("again") }
        chipHard?.setOnClickListener { if (ratingOpen()) DrillService.submitTestGrade("hard") }
        chipGood?.setOnClickListener { if (ratingOpen()) DrillService.submitTestGrade("good") }
        chipEasy?.setOnClickListener { if (ratingOpen()) DrillService.submitTestGrade("easy") }
        startButton.setOnClickListener {
            if (PlaybackControl.drillButtonStops(DrillUi.state.drillRunning, DrillService.running)) {
                stopService(Intent(this, DrillService::class.java))
            } else {
                ensurePermissionsAndStart()
            }
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.playbackPauseButton)?.setOnClickListener {
            val drilling = DrillUi.state.drillRunning || DrillService.running
            when (PlaybackControl.pauseTarget(PlaybackUi.state, drilling, DrillUi.state.chatPaused)) {
                PlaybackControl.PauseTarget.PLAYBACK -> PlaybackSpeaker.togglePause(this)
                PlaybackControl.PauseTarget.DRILL -> DrillService.pauseForChat(ChatPause.Trigger.MANUAL)
                PlaybackControl.PauseTarget.DRILL_RESUME -> DrillService.resumeFromChat()
                PlaybackControl.PauseTarget.NONE -> Unit
            }
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
        // Hide the header meta + timer row + bottom bar while the IME is up so
        // the chat clears the keyboard. The deck line stays visible.
        val imeRoot = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(imeRoot) { _, insets ->
            applyImeLayout(insets.isVisible(WindowInsetsCompat.Type.ime()))
            insets
        }
        ViewCompat.requestApplyInsets(imeRoot)
        refreshTimerRow()
        paintSwitch(switchModel.on)
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
        val now = Prefs.load(this).deck
        if (DeckName.changedMidDrill(drillDeck, now, DrillService.running)) {
            Toast.makeText(this, "Deck changed to " + DeckName.split(now).second.ifBlank { now } + ". Stop and start the drill to switch.", Toast.LENGTH_LONG).show()
            drillDeck = now
        }
        if (!DrillService.running) drillDeck = null
        render(DrillUi.state)
        refreshTimerRow()
        elapsedResumed = true
        startElapsedTicker()
    }

    override fun onPause() {
        chatPane?.setResumed(false)
        elapsedResumed = false
        stopElapsedTicker()
        super.onPause()
    }

    override fun onStop() {
        DrillUi.removeListener(uiListener)
        ReviewLog.removeListener(reviewListener)
        PlaybackUi.removeListener(playbackListener)
        super.onStop()
    }

    override fun onDestroy() {
        // ChatDictation holds this Activity as its Context and its recognizer,
        // so dictation always stops, even on a configuration-change recreate.
        chatDictation?.stop()
        chatDictation = null
        DrillService.dictationFinished()
        stopResumePulse(findViewById(R.id.chatResumeButton))
        chatPane?.detach()
        chatPane = null
        // Split-screen resize / fold recreate, or a running drill (DrillService owns its read-aloud): keep read-aloud playing.
        if (ActivityTeardown.shouldStopPlayback(isChangingConfigurations, DrillService.running)) PlaybackSpeaker.stop()
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
                    val wasEmpty = !hadComposerText
                    hadComposerText = true
                    if (wasEmpty) hint?.visibility = View.GONE
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
        paintExamCountdown(cfg)
        dueCountView.text = "Checking Anki…"
        Thread {
            val anki = try {
                AnkiConnect(cfg.url, cfg.apiKey)
            } catch (_: Exception) {
                null
            }
            val text = try {
                val counts = anki?.dueCounts(cfg.deck)
                    ?: throw IllegalStateException("AnkiConnect unavailable")
                getString(R.string.due_split_format, counts.due, counts.new)
            } catch (e: Exception) {
                "Anki unreachable: ${e.message}"
            }
            val coverage = if (anki != null) {
                try {
                    refreshExamCoverage(anki, cfg)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                dueCountView.text = text
                paintExamCountdown(Prefs.load(this), coverage)
            }
        }.start()
    }

    private fun refreshExamCoverage(anki: AnkiConnect, cfg: AnkiConfig): Int? {
        val state = Prefs.loadExam(this, cfg.deck)
        val exam = state.date ?: return null
        val days = ExamPlan.daysLeft(LocalDate.now(), exam)
        if (days < 0) return null
        val cov = ExamSync.coverage(anki, cfg.deck, days)
        try {
            Prefs.saveExam(this, cfg.deck, state.copy(coverage = cov.percent))
        } catch (_: Exception) {
        }
        return cov.percent
    }

    private fun paintExamCountdown(cfg: AnkiConfig, coverage: Int? = null) {
        val view = examCountdown ?: return
        val state = try {
            Prefs.loadExam(this, cfg.deck)
        } catch (_: Exception) {
            view.visibility = View.GONE
            view.text = ""
            return
        }
        val exam = state.date
        if (exam == null) {
            view.visibility = View.GONE
            view.text = ""
            return
        }
        val days = ExamPlan.daysLeft(LocalDate.now(), exam)
        if (days < 0) {
            view.visibility = View.GONE
            view.text = ""
            return
        }
        val coveragePercent = coverage ?: state.coverage ?: 0
        val line = if (cfg.examApplyToAnki) {
            ExamApply.countdownLine(
                days,
                ExamApply.retentionOf(days, state.target),
                coveragePercent
            )
        } else {
            getString(R.string.settings_exam_countdown_readonly, days, coveragePercent)
        }
        if (line.isBlank()) {
            view.visibility = View.GONE
            view.text = ""
        } else {
            view.visibility = View.VISIBLE
            view.text = line
        }
    }

    private fun render(state: DrillUiState) {
        // A drill that starts after onResume must start the elapsed ticker too.
        if (elapsedResumed && !elapsedTicking && ElapsedChip.visible(state.drillRunning)) startElapsedTicker()
        val cfg = Prefs.load(this)
        paintExamCountdown(cfg)
        val drillingNow = state.drillRunning || DrillService.running
        crossfadeStatus(
            InfoCard.compose(state.heard, state.status, drillingNow,
                silent = state.silentSession, micOpen = state.ratingMicOpen)
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
                val markup = if (state.frontHtml.contains("<img", ignoreCase = true)) {
                    state.frontHtml
                } else {
                    val face = QuestionLabel.of(state.frontHtml, state.questionOrdinal.coerceAtLeast(1))
                    val shown = if (face.fromCard) face.stem else state.frontHtml
                    shown.ifBlank { state.frontHtml }
                }
                CardFaceBinder.setMarkup(frontView, markup)
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
            paintReading(frontView, if (showBack) backView else null, if (cfg.highlightSentence) state.readingSentence else "")
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
            voiceLineView?.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_volume_up, 0, 0, 0)
            voiceLineView?.compoundDrawablePadding = (6 * resources.displayMetrics.density).toInt()
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
        val chipScroll = findViewById<View>(R.id.promptChipScroll)
        val chipRow = findViewById<ChipGroup>(R.id.promptChipRow)
        if (chipScroll != null && chipRow != null) {
            val showChips = !hasQuestion && cfg.aiEnabled
            val promptChips = if (showChips) Prefs.loadPromptChips(this) else emptyList()
            chipScroll.visibility = if (showChips) View.VISIBLE else View.GONE
            val sig = PromptChips.toJson(promptChips)
            if (showChips && chipRow.tag != sig) {
                chipRow.tag = sig
                chipRow.removeAllViews()
                for (pc in promptChips) {
                    val chipView = layoutInflater.inflate(R.layout.item_prompt_chip, chipRow, false) as Chip
                    chipView.text = pc.name
                    chipView.setOnClickListener { runPromptChip(pc) }
                    chipRow.addView(chipView)
                }
                val addChip = layoutInflater.inflate(R.layout.item_prompt_chip, chipRow, false) as Chip
                addChip.id = R.id.promptChipRowAdd
                addChip.text = ""
                addChip.chipIcon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_add)
                addChip.isChipIconVisible = true
                addChip.textEndPadding = 0f
                addChip.contentDescription = getString(R.string.prompt_chip_add_desc)
                addChip.setOnClickListener { startActivity(Intent(this, PromptChipEditActivity::class.java)) }
                chipRow.addView(addChip)
            }
        }
        val controls = AnswerControls.of(
            hasQuestion = hasQuestion,
            answerShown = state.showBack,
            silent = silentOn
        )
        val ratingVis = if (controls.rating) View.VISIBLE else View.GONE
        val guide = SkipQueue.guide(hasQuestion, state.showBack, silentOn)
        findViewById<View>(R.id.gradeGuideLabel)?.visibility = if (guide != 0) View.VISIBLE else View.GONE
        if (guide != 0) {
            findViewById<TextView>(R.id.gradeGuideLabel)?.setText(when (guide) {
                1 -> R.string.grade_guide
                2 -> R.string.grade_guide_tap
                else -> R.string.answer_guide
            })
        }
        findViewById<View>(R.id.gradeChips)?.visibility = ratingVis
        val sel = if (controls.rating) state.selectedEase else null
        val suggested = if (controls.rating) state.suggestedEase else null
        val ringStep = if (controls.rating) state.autoAcceptStep else null
        RatingSuggest.apply(chipAgain, 1, sel, suggested, ringStep)
        RatingSuggest.apply(chipHard, 2, sel, suggested, ringStep)
        RatingSuggest.apply(chipGood, 3, sel, suggested, ringStep)
        RatingSuggest.apply(chipEasy, 4, sel, suggested, ringStep)
        startButton.isEnabled = true
        startButton.visibility = View.VISIBLE
        val stops = PlaybackControl.drillButtonStops(state.drillRunning, DrillService.running)
        startButton.setText(if (stops) R.string.app_stop else R.string.app_start)
        (startButton as? com.google.android.material.button.MaterialButton)?.icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, if (stops) R.drawable.ic_drill_stop else R.drawable.ic_play_arrow)
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

    /** The pause button's own tonal tint and text colours, kept so the pink Resume can be undone. */
    private var pauseBaseTint: ColorStateList? = null
    private var pauseBaseText: ColorStateList? = null

    private fun paintPlayback(snap: PlaybackControl.Snapshot) {
        val pause = findViewById<Button>(R.id.playbackPauseButton) ?: return
        val mb = pause as? com.google.android.material.button.MaterialButton
        val drilling = DrillUi.state.drillRunning || DrillService.running
        pause.visibility = if (PlaybackControl.pauseVisible(snap, drilling)) View.VISIBLE else View.GONE
        val drillPaused = DrillUi.state.chatPaused
        pause.isEnabled = PlaybackControl.pauseEnabled(snap, drilling, drillPaused)
        if (pauseBaseTint == null) pauseBaseTint = pause.backgroundTintList
        if (pauseBaseText == null) pauseBaseText = pause.textColors
        if (PlaybackControl.pauseShowsDrillPause(snap, drilling, drillPaused)) {
            mb?.icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_drill_pause)
            pause.text = getString(R.string.drill_pause)
            pause.contentDescription = null
            pause.backgroundTintList = pauseBaseTint
            pauseBaseText?.let { pause.setTextColor(it) }
        } else if (PlaybackControl.bottomShowsDrillResume(snap, drilling, drillPaused)) {
            val typed = TypedValue()
            pause.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typed, true)
            mb?.icon = androidx.appcompat.content.res.AppCompatResources.getDrawable(this, R.drawable.ic_play_arrow)
            pause.text = getString(R.string.drill_resume)
            pause.contentDescription = null
            pause.backgroundTintList = ColorStateList.valueOf(typed.data)
            val onPrimary = TypedValue()
            pause.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, onPrimary, true)
            pause.setTextColor(onPrimary.data)
        } else {
            mb?.icon = null
            pause.contentDescription = null
            pause.backgroundTintList = pauseBaseTint
            pauseBaseText?.let { pause.setTextColor(it) }
            pause.text = if (PlaybackControl.pauseShowsResume(snap, drilling, drillPaused)) {
                getString(R.string.playback_resume)
            } else {
                getString(R.string.playback_pause)
            }
        }
        val status = findViewById<TextView>(R.id.playbackStatus)
        status?.text = when (snap.state) {
            PlaybackControl.State.LOADING -> getString(R.string.playback_loading)
            PlaybackControl.State.READING -> getString(R.string.playback_reading)
            PlaybackControl.State.PAUSED -> getString(R.string.playback_resume)
            PlaybackControl.State.IDLE -> ""
        }
        // renderCue now depends on PlaybackUi.state (the bottom toggle reads Resume
        // only for an idle paused drill), so repaint the cue when playback changes.
        renderCue(DrillUi.state, drilling, switchModel.on)
    }

    /** One-tap prompt chip tap: the card on screen and saved card chats are sent only when the chip asks for them. */
    private fun runPromptChip(pc: PromptChip) {
        val fullDeck = try {
            Prefs.load(this).deck
        } catch (_: Exception) {
            ""
        }
        if (fullDeck.isBlank()) {
            GeneralChat.showNotice(ChatSend.visible(ChatSend.Fail.NO_DECK))
            return
        }
        val cardBlob = if (pc.source == ChipSource.CURRENT_CARD) {
            PromptChipRow.cardBlob(renderedFront, renderedBack)
        } else {
            ""
        }
        val chatsBlob = if (pc.source == ChipSource.CARD_CHATS) {
            val text = try {
                val f = ThreadStore.file(filesDir)
                if (f.isFile) f.readText() else ""
            } catch (_: Exception) {
                ""
            }
            PromptChipRow.chatsBlob(text)
        } else {
            ""
        }
        GeneralChat.runChip(this, fullDeck, pc, cardBlob = cardBlob, chatsBlob = chatsBlob)
    }

    /** 58C: one background span over the sentence being spoken, on the question or the answer view. */
    // #59 hook (58E): a word span from ReadingTimeline.wordAt, offset by spanIn(...).first, can be added here later. Do not implement now.
    private fun paintReading(front: TextView, back: TextView?, sentence: String) {
        val key = sentence + "|" + renderedFront + "|" + renderedBack + "|" + (back != null)
        if (key == paintedReading) return
        paintedReading = key
        val old = readingSpan
        readingSpan = null
        if (old != null) {
            for (view in listOfNotNull(front, back)) {
                val cur = view.text
                if (cur is android.text.Spanned && cur.getSpanStart(old) >= 0) {
                    val sp = android.text.SpannableString(cur)
                    sp.removeSpan(old)
                    view.text = sp
                }
            }
        }
        if (sentence.isBlank()) return
        for (view in listOfNotNull(front, back)) {
            val range = SentenceReading.spanIn(view.text.toString(), sentence) ?: continue
            val span = android.text.style.BackgroundColorSpan(getColor(R.color.reading_highlight))
            val sp = android.text.SpannableString(view.text)
            sp.setSpan(span, range.first, range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            view.text = sp
            readingSpan = span
            return
        }
    }

    /** Countdown while thinking. The pause row replaces it and stays until Resume or a rating. */
    private fun renderCue(state: DrillUiState, drilling: Boolean, silentOn: Boolean) {
        val pauseRow = findViewById<View>(R.id.chatPauseRow)
        val countdown = findViewById<TextView>(R.id.thinkCountdown)
        val seconds = state.thinkSecondsLeft
        val resume = findViewById<Button>(R.id.chatResumeButton)
        val bottomIsResume = PlaybackControl.bottomShowsDrillResume(PlaybackUi.state, drilling, state.chatPaused)
        if (ChatPause.pauseRowVisible(drilling, state.chatPaused)) {
            pauseRow?.visibility = View.VISIBLE
            countdown?.visibility = View.GONE
            findViewById<TextView>(R.id.chatPauseLabel)?.text = ChatPause.label(state.manualPause)
            resume?.visibility = if (ChatPause.inlineResumeVisible(state.manualPause, state.chatShowResume, bottomIsResume)) View.VISIBLE else View.GONE
            pulseResume(resume, ChatPause.inlineResumeVisible(state.manualPause, state.chatShowResume, bottomIsResume) && state.chatResumePulse)
        } else if (drilling && seconds != null && seconds > 0 && !state.showBack) {
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

    /**
     * Hide the header meta line, timer row, and bottom bar while the IME is up
     * so the chat clears the keyboard. The deck line stays visible. Called only
     * when the IME state flips; the timer row is restored to its pre-IME state.
     */
    private fun applyImeLayout(imeVisible: Boolean) {
        if (imeVisible == lastImeVisible) return
        lastImeVisible = imeVisible
        // Wide (unfolded / w600dp) layout has no bottomBar; the chat clears the
        // keyboard by hiding the toolbar and session cue instead. The chat stays
        // visible either way.
        if (findViewById<View>(R.id.bottomBar) == null) {
            if (imeVisible) {
                cueWantedVisible = findViewById<View>(R.id.sessionCue)?.visibility == View.VISIBLE
            }
            val w = ImeLayoutDecision.decideWide(imeVisible, cueWantedVisible)
            findViewById<View>(R.id.toolbar)?.visibility = if (w.toolbar) View.VISIBLE else View.GONE
            findViewById<View>(R.id.sessionCue)?.visibility = if (w.sessionCue) View.VISIBLE else View.GONE
            return
        }
        if (imeVisible) {
            timerRowWantedVisible = findViewById<View>(R.id.timerRowInclude)?.visibility == View.VISIBLE
        }
        val d = ImeLayoutDecision.decide(imeVisible, timerRowWantedVisible)
        headerMeta?.visibility = if (d.headerMeta) View.VISIBLE else View.GONE
        findViewById<View>(R.id.timerRowInclude)?.visibility = if (d.timerRow) View.VISIBLE else View.GONE
        findViewById<View>(R.id.bottomBar)?.visibility = if (d.bottomBar) View.VISIBLE else View.GONE
    }

    private fun refreshTimerRow() {
        val value = timerValue ?: return
        val cfg = try {
            Prefs.load(this)
        } catch (_: Exception) {
            return
        }
        cachedGoalMinutes = TimerRowModel.minutes(cfg)
        value.text = TimerRowModel.label(cfg)
        paintTimerSwitch(TimerRowModel.enabled(cfg))
    }

    private fun paintTimerSwitch(on: Boolean) {
        val chip = timerGoalChip ?: return
        val listener = timerChipListener ?: return
        if (chip.isChecked != on) {
            chip.clearOnCheckedChangeListeners()
            chip.isChecked = on
            chip.addOnCheckedChangeListener(listener)
        }
        ToggleChips.bindSwitchA11y(chip, R.string.timer_switch_label)
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
        val chip = voiceChip ?: return
        val listener = voiceChipListener ?: return
        val wantOn = ToggleChips.voiceOn(on)
        if (chip.isChecked != wantOn) {
            chip.clearOnCheckedChangeListeners()
            chip.isChecked = wantOn
            chip.addOnCheckedChangeListener(listener)
        }
        ToggleChips.paintVoiceIcon(chip)
        ToggleChips.bindSwitchA11y(chip, R.string.voice_chip)
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

    /** Asks Continue/New when today has an unfinished study block (StudyBlockPrompt.offer), else starts at once. */
    private fun startDrillService() {
        val offer = runCatching {
            StudyBlockPrompt.offer(Prefs.loadStudyBlock(this), System.currentTimeMillis(), ZoneId.systemDefault())
        }.getOrNull()
        if (offer == null || isFinishing) {
            launchDrillService(newBlock = false)
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Study block")
            .setPositiveButton(offer.continueLabel) { _, _ -> launchDrillService(newBlock = false) }
            .setNegativeButton(offer.newLabel) { _, _ -> launchDrillService(newBlock = true) }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun launchDrillService(newBlock: Boolean) {
        try {
            val intent = Intent(this, DrillService::class.java).putExtra(DrillService.EXTRA_NEW_BLOCK, newBlock)
            ContextCompat.startForegroundService(this, intent)
            drillDeck = Prefs.load(this).deck
            Toast.makeText(this, "Drill starting…", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "startForegroundService failed", e)
            Toast.makeText(this, "Could not start drill: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private companion object {
        const val KEY_DRILL_DECK = "drillDeck"
    }
}
