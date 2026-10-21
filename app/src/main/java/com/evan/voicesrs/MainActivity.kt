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
import android.os.Handler
import android.os.Looper
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
    private lateinit var engineChip: Chip
    private lateinit var currentCard: MaterialCardView
    private lateinit var frontLabel: TextView
    private lateinit var cardFront: TextView
    private lateinit var backLabel: TextView
    private lateinit var cardBack: TextView
    private lateinit var heardText: TextView
    private lateinit var chipAgain: Chip
    private lateinit var chipHard: Chip
    private lateinit var chipGood: Chip
    private lateinit var chipEasy: Chip
    private lateinit var progressText: TextView
    private lateinit var syncChip: Chip
    private lateinit var errorCard: MaterialCardView
    private lateinit var errorText: TextView
    private lateinit var syncButton: Button
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var showAnswerButton: Button
    private lateinit var statusSilentIndicator: View
    private lateinit var cardSilentIndicator: View

    private var pendingStatus = ""
    /** Permission request in flight was for a silent start, so RECORD_AUDIO is not required. */
    private var startWithoutMic = false
    private var renderedFront = ""
    private var renderedBack = ""
    /** Last [DrillUiState.drillRunning] applied by [uiListener]; used to refresh due on session end. */
    private var lastDrillRunning = false
    private var chatPane: ChatPane? = null
    /** Null while a home neural probe is in flight and no result has landed yet. */
    private var homeHealth: Boolean? = null
    private var engineProbeGen = 0
    private val engineHandler = Handler(Looper.getMainLooper())
    private val engineProbeTick = object : Runnable {
        override fun run() {
            if (isDestroyed) return
            startEngineProbe(reset = false)
            engineHandler.postDelayed(this, ENGINE_PROBE_MS)
        }
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
            if (ended) {
                refreshCounts()
                startEngineProbe(reset = true)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
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
        engineChip = findViewById(R.id.engineChip)
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
        syncChip = findViewById(R.id.syncChip)
        errorCard = findViewById(R.id.errorCard)
        errorText = findViewById(R.id.errorText)
        syncButton = findViewById(R.id.syncButton)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        showAnswerButton = findViewById(R.id.showAnswerButton)
        showAnswerButton.setOnClickListener { DrillService.submitShowAnswer() }
        bindSilentIndicators()
        findViewById<Button>(R.id.retryButton).setOnClickListener { retryDrill() }
        syncButton.setOnClickListener { syncPending() }
        chipAgain.setOnClickListener { if (DrillUi.state.ratingVisible) DrillService.submitTestGrade("again"); render(DrillUi.state) }
        chipHard.setOnClickListener { if (DrillUi.state.ratingVisible) DrillService.submitTestGrade("hard"); render(DrillUi.state) }
        chipGood.setOnClickListener { if (DrillUi.state.ratingVisible) DrillService.submitTestGrade("good"); render(DrillUi.state) }
        chipEasy.setOnClickListener { if (DrillUi.state.ratingVisible) DrillService.submitTestGrade("easy"); render(DrillUi.state) }
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
            chatPane = ChatPane(chatList, chatUnread, findViewById(R.id.chatEmpty)).also { it.attach() }
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
        applyAiChrome()
        val pending = GradeSync.pendingCount(filesDir)
        if (pending != DrillUi.state.pendingSync) {
            DrillUi.update { it.copy(pendingSync = pending) }
        }
        refreshCounts()
        engineHandler.removeCallbacks(engineProbeTick)
        startEngineProbe(reset = true)
        engineHandler.postDelayed(engineProbeTick, ENGINE_PROBE_MS)
        render(DrillUi.state)
    }

    override fun onPause() {
        engineHandler.removeCallbacks(engineProbeTick)
        engineProbeGen++
        chatPane?.setResumed(false)
        super.onPause()
    }

    override fun onStop() {
        DrillUi.removeListener(uiListener)
        ReviewLog.removeListener(reviewListener)
        super.onStop()
    }

    override fun onDestroy() {
        engineHandler.removeCallbacks(engineProbeTick)
        chatPane?.detach()
        chatPane = null
        super.onDestroy()
    }

    /**
     * Unfolded chat stays up whenever AI is on, including an empty thread.
     * The card keeps 45% so a few lines of the question stay readable, and the
     * chat keeps 55% so the latest card, answer, and reply fit together.
     * With AI off the chat hides and the card fills the pane. Folded layout
     * has no chat section.
     */
    private fun applyAiChrome() {
        val section = findViewById<View>(R.id.chatSection) ?: return
        val enabled = try {
            Prefs.load(this).aiEnabled
        } catch (_: Exception) {
            false
        }
        val vis = if (enabled) View.VISIBLE else View.GONE
        if (section.visibility != vis) section.visibility = vis
        val lp = currentCard.layoutParams as? LinearLayout.LayoutParams ?: return
        val weight = if (enabled) 45f else 1f
        if (lp.weight != weight || lp.height != 0) {
            lp.height = 0
            lp.weight = weight
            currentCard.layoutParams = lp
        }
        if (enabled) {
            val chatLp = section.layoutParams as? LinearLayout.LayoutParams
            if (chatLp != null && (chatLp.weight != 55f || chatLp.height != 0)) {
                chatLp.height = 0
                chatLp.weight = 55f
                section.layoutParams = chatLp
            }
        }
    }

    /** Edge /health on a background thread. The drill's own label wins while it runs. */
    private fun startEngineProbe(reset: Boolean) {
        val cfg = try {
            Prefs.load(this)
        } catch (_: Exception) {
            return
        }
        val configured = TtsEngines.preferred(cfg.ttsEngine)
        if (configured != EngineChoice.NEURAL) {
            homeHealth = false
            if (!DrillService.running) render(DrillUi.state)
            return
        }
        if (reset) homeHealth = null
        if (homeHealth == null && !DrillService.running) render(DrillUi.state)
        val gen = ++engineProbeGen
        val url = cfg.neuralUrl
        val token = cfg.neuralToken
        Thread {
            val ok = EdgeSpeaker(applicationContext).healthy(url, token, NeuralTts.HEALTH_TIMEOUT_MS)
            runOnUiThread {
                if (isDestroyed || gen != engineProbeGen) return@runOnUiThread
                homeHealth = ok
                if (!DrillService.running) render(DrillUi.state)
            }
        }.start()
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
        val configured = TtsEngines.preferred(cfg.ttsEngine)
        val engine = if (DrillService.running) {
            state.engineLabel.ifBlank { EngineLabel.of(configured, null) }
        } else {
            EngineLabel.of(configured, if (configured == EngineChoice.NEURAL) homeHealth else false)
        }
        if (engineChip.text.toString() != engine) engineChip.text = engine
        crossfadeStatus(state.status.ifBlank { getString(R.string.state_ready) })

        if (!cfg.showCard) {
            frontLabel.visibility = View.GONE
            cardFront.visibility = View.GONE
            backLabel.visibility = View.GONE
            cardBack.visibility = View.GONE
            heardText.visibility = View.GONE
        } else {
            frontLabel.visibility = View.VISIBLE
            cardFront.visibility = View.VISIBLE
            if (state.frontHtml.isBlank()) {
                if (renderedFront.isNotEmpty() || cardFront.text.toString() != getString(R.string.card_idle)) {
                    renderedFront = ""
                    cardFront.text = getString(R.string.card_idle)
                }
            } else if (renderedFront != state.frontHtml) {
                renderedFront = state.frontHtml
                cardFront.text = HtmlCompat.fromHtml(state.frontHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
            }
            val showBack = state.showBack && state.backHtml.isNotBlank()
            val backVis = if (showBack) View.VISIBLE else View.GONE
            if (backLabel.visibility != backVis || cardBack.visibility != backVis) {
                TransitionManager.beginDelayedTransition(currentCard, Fade())
            }
            backLabel.visibility = backVis
            cardBack.visibility = backVis
            if (showBack && renderedBack != state.backHtml) {
                renderedBack = state.backHtml
                cardBack.text = HtmlCompat.fromHtml(state.backHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
            }
            if (state.heard.isBlank()) {
                heardText.visibility = View.GONE
            } else {
                heardText.visibility = View.VISIBLE
                heardText.text = state.heard
            }
        }
        val progressLine = SessionProgress.visibleLine(DrillService.running, state.progressLine)
        if (progressLine.isBlank()) {
            progressText.visibility = View.GONE
        } else {
            progressText.visibility = View.VISIBLE
            progressText.text = progressLine
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
        // Single selection driven by service state: only the chosen grade is highlighted.
        // Rating chips appear only once the answer is revealed.
        val ratingVis = if (state.ratingVisible) View.VISIBLE else View.GONE
        findViewById<View>(R.id.gradeGuideLabel).visibility = ratingVis
        findViewById<TextView>(R.id.gradeGuideLabel).setText(
            if (state.drillRunning && state.silentSession) R.string.grade_guide_tap else R.string.grade_guide
        )
        findViewById<View>(R.id.gradeChips).visibility = ratingVis
        val sel = if (state.ratingVisible) state.selectedEase else null
        chipAgain.isChecked = sel == 1
        chipHard.isChecked = sel == 2
        chipGood.isChecked = sel == 3
        chipEasy.isChecked = sel == 4
        startButton.isEnabled = !state.drillRunning
        stopButton.isEnabled = state.drillRunning
        val drilling = state.drillRunning || DrillService.running
        val silentOn = if (drilling) state.silentSession else cfg.silentMode
        statusSilentIndicator.visibility = if (silentOn) View.VISIBLE else View.GONE
        cardSilentIndicator.visibility = if (drilling && silentOn) View.VISIBLE else View.GONE
        showAnswerButton.visibility = if (state.awaitingShowAnswer) View.VISIBLE else View.GONE
    }

    private fun bindSilentIndicators() {
        val found = ArrayList<View>(2)
        collectIds(findViewById(android.R.id.content), R.id.silentIndicator, found)
        for (view in found) {
            if (isUnder(view, R.id.currentCard)) cardSilentIndicator = view
            else statusSilentIndicator = view
        }
    }

    private fun collectIds(view: View, id: Int, out: MutableList<View>) {
        if (view.id == id) out.add(view)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) collectIds(view.getChildAt(i), id, out)
        }
    }

    private fun isUnder(view: View, ancestorId: Int): Boolean {
        var parent = view.parent
        while (parent is View) {
            if (parent.id == ancestorId) return true
            parent = parent.parent
        }
        return false
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

    companion object {
        private const val ENGINE_PROBE_MS = 60_000L
    }
}
