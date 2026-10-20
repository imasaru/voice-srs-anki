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
        chipAgain.setOnClickListener { DrillService.submitTestGrade("again") }
        chipHard.setOnClickListener { DrillService.submitTestGrade("hard") }
        chipGood.setOnClickListener { DrillService.submitTestGrade("good") }
        chipEasy.setOnClickListener { DrillService.submitTestGrade("easy") }
        findViewById<Button>(R.id.startButton).setOnClickListener { ensurePermissionsAndStart() }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
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
        refreshCounts()
        render(DrillUi.state)
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
        val highlight = state.ratingMicOpen
        chipAgain.isChecked = highlight
        chipHard.isChecked = highlight
        chipGood.isChecked = highlight
        chipEasy.isChecked = highlight
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
