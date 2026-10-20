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
import android.view.View
import android.widget.Button
import android.widget.TextView
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

    private var pendingStatus = ""
    private var renderedFront = ""
    private var renderedBack = ""

    private val uiListener: (DrillUiState) -> Unit = { snapshot ->
        runOnUiThread {
            if (!isDestroyed) render(snapshot)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            startDrillService()
        } else {
            Toast.makeText(this, "Microphone permission is required for the drill.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById<MaterialToolbar>(R.id.toolbar))
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
        findViewById<Button>(R.id.retryButton).setOnClickListener { ensurePermissionsAndStart() }
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
    }

    override fun onStart() {
        super.onStart()
        DrillUi.addListener(uiListener)
        if (DrillService.running != DrillUi.state.drillRunning) {
            DrillUi.update { it.copy(drillRunning = DrillService.running) }
        }
        render(DrillUi.state)
    }

    override fun onStop() {
        DrillUi.removeListener(uiListener)
        super.onStop()
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
        val pending = GradeSync.pendingCount(filesDir)
        if (pending != DrillUi.state.pendingSync) {
            DrillUi.update { it.copy(pendingSync = pending) }
        }
        refreshCounts()
        render(DrillUi.state)
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

    private fun refreshCounts() {
        val cfg = Prefs.load(this)
        deckNameView.text = cfg.deck
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
        val engine = state.engineLabel.ifBlank {
            if (TtsEngines.preferred(cfg.ttsEngine) == EngineChoice.NEURAL) {
                getString(R.string.engine_neural)
            } else {
                getString(R.string.engine_device)
            }
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
        if (state.progressLine.isBlank()) {
            progressText.visibility = View.GONE
        } else {
            progressText.visibility = View.VISIBLE
            progressText.text = state.progressLine
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
        findViewById<View>(R.id.gradeChips).visibility = ratingVis
        val sel = if (state.ratingVisible) state.selectedEase else null
        chipAgain.isChecked = sel == 1
        chipHard.isChecked = sel == 2
        chipGood.isChecked = sel == 3
        chipEasy.isChecked = sel == 4
        startButton.isEnabled = !state.drillRunning
        stopButton.isEnabled = state.drillRunning
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

    private fun ensurePermissionsAndStart() {
        if (DrillUi.state.drillRunning || DrillService.running) return
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
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
