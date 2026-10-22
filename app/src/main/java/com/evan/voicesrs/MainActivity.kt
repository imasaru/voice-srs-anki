package com.evan.voicesrs

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.transition.Fade
import android.transition.TransitionManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
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
    private lateinit var errorCard: MaterialCardView
    private lateinit var errorText: TextView
    private lateinit var syncButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private var showAnswerButton: Button? = null
    private var silentSwitch: com.google.android.material.materialswitch.MaterialSwitch? = null
    private var silentBinding = false
    /** Microphone permission request is so a running drill can leave silent mode. */
    private var pendingVoice = false

    private var pendingStatus = ""
    /** Permission request in flight was for a silent start, so RECORD_AUDIO is not required. */
    private var startWithoutMic = false
    private var renderedFront = ""
    private var renderedBack = ""
    /** Last [DrillUiState.drillRunning] applied by [uiListener]; used to refresh due on session end. */
    private var lastDrillRunning = false
    private var chatPane: ChatPane? = null

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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (pendingVoice) {
            pendingVoice = false
            if (grants[Manifest.permission.RECORD_AUDIO] == true) {
                silentBinding = true
                silentSwitch?.isChecked = false
                silentBinding = false
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
        syncButton = findViewById(R.id.syncButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        showAnswerButton = findViewById(R.id.showAnswerButton)
        showAnswerButton?.setOnClickListener { DrillService.submitShowAnswer() }
        silentSwitch = findViewById(R.id.drillSilentSwitch)
        silentSwitch?.setOnCheckedChangeListener { _, checked ->
            if (!silentBinding) onSilentSwitch(checked)
        }
        findViewById<Button>(R.id.retryButton).setOnClickListener { retryDrill() }
        syncButton.setOnClickListener { syncPending() }
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
        showCrashIfAny()
        bindDeckTitle(Prefs.load(this).deck)
        val chatList = findViewById<RecyclerView>(R.id.chatList)
        val chatUnread = findViewById<TextView>(R.id.chatUnread)
        if (chatList != null && chatUnread != null) {
            val embedControls = findViewById<View>(R.id.bottomBar) == null
            chatPane = ChatPane(
                chatList,
                chatUnread,
                findViewById(R.id.chatEmpty),
                embedControls = embedControls
            ).also { it.attach() }
        }
        applyAiChrome()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val item = menu.findItem(R.id.action_history) ?: return super.onPrepareOptionsMenu(menu)
        item.isVisible = findViewById<View>(R.id.chatList) == null
        val unread = ReviewLog.unreadCount()
        item.title = if (unread > 0) {
            getString(R.string.action_history_count, unread)
        } else {
            getString(R.string.action_history)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != R.id.action_history) return super.onOptionsItemSelected(item)
        if (supportFragmentManager.findFragmentByTag("history") == null) {
            HistorySheet().show(supportFragmentManager, "history")
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        lastDrillRunning = DrillService.running
        DrillUi.addListener(uiListener)
        ReviewLog.addListener(reviewListener)
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
    }

    override fun onPause() {
        chatPane?.setResumed(false)
        super.onPause()
    }

    override fun onStop() {
        DrillUi.removeListener(uiListener)
        ReviewLog.removeListener(reviewListener)
        super.onStop()
    }

    override fun onDestroy() {
        chatPane?.detach()
        chatPane = null
        super.onDestroy()
    }

    /** The unfolded right pane is the chat. It stays full height with or without AI. */
    private fun applyAiChrome() {
        findViewById<View>(R.id.reviewPane)?.visibility = View.VISIBLE
        findViewById<View>(R.id.chatList)?.visibility = View.VISIBLE
    }

    private fun syncPending() {
        val before = GradeSync.pendingCount(filesDir)
        if (before == 0) {
            Toast.makeText(this, getString(R.string.sync_none), Toast.LENGTH_SHORT).show()
            return
        }
        syncButton.isEnabled = false
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
                syncButton.isEnabled = true
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
        crossfadeStatus(state.status.ifBlank { getString(R.string.state_ready) })
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
                frontView.text = HtmlCompat.fromHtml(state.frontHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
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
                backView.text = HtmlCompat.fromHtml(state.backHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
            }
            if (state.heard.isBlank()) {
                heardText?.visibility = View.GONE
            } else {
                heardText?.visibility = View.VISIBLE
                heardText?.text = state.heard
            }
        }
        val progressLine = SessionProgress.visibleLine(DrillService.running, state.progressLine)
        val voice = currentVoiceLine(cfg, state)
        val meta = headerMeta
        if (meta != null) {
            meta.text = VoiceStatusLine.headerLine(progressLine, voice)
            progressText.visibility = View.GONE
        } else {
            voiceLineView?.text = voice
            voiceLineView?.visibility = View.VISIBLE
            if (progressLine.isBlank()) {
                progressText.visibility = View.GONE
            } else {
                progressText.visibility = View.VISIBLE
                progressText.text = progressLine
            }
        }
        if (state.pendingSync > 0) {
            syncChip.visibility = View.VISIBLE
            syncChip.text = getString(R.string.pending_sync, state.pendingSync)
            syncButton.visibility = View.VISIBLE
        } else {
            syncChip.visibility = View.GONE
            syncButton.visibility = View.GONE
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
        val silentOn = if (drilling) state.silentSession else cfg.silentMode
        val hasQuestion = drilling && state.frontHtml.isNotBlank()
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
        chipAgain?.isChecked = sel == 1
        chipHard?.isChecked = sel == 2
        chipGood?.isChecked = sel == 3
        chipEasy?.isChecked = sel == 4
        startButton.isEnabled = !state.drillRunning
        if (headerMeta != null) {
            startButton.visibility = if (drilling) View.GONE else View.VISIBLE
        }
        stopButton.isEnabled = state.drillRunning
        if (silentSwitch?.isChecked != silentOn) {
            silentBinding = true
            silentSwitch?.isChecked = silentOn
            silentBinding = false
        }
        showAnswerButton?.visibility = if (controls.showAnswer) View.VISIBLE else View.GONE
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
     * The engine that will speak. A live drill label wins once the service has
     * probed. Before that, a selected Edge voice stays on the line until /health
     * fails. The line is not a chip and nothing here is logged.
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

    private fun onSilentSwitch(checked: Boolean) {
        val drilling = DrillUi.state.drillRunning || DrillService.running
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!checked && drilling && !mic) {
            pendingVoice = true
            silentBinding = true
            silentSwitch?.isChecked = true
            silentBinding = false
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        persistSilent(checked)
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
