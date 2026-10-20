package com.evan.voicesrs

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Filter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import android.widget.TextView
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/** AnkiConnect, speech, and neural TTS settings. Test voice plays only on tap. */
class SettingsActivity : AppCompatActivity() {

    private var tts: TextToSpeech? = null
    private var destroyed = false
    private var voicesReady = false
    private var testing = false
    private var deviceVoices: List<VoiceChoice> = listOf(VoiceChoice("System default", ""))
    private var savedDeviceVoice = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        val urlView: TextInputEditText = findViewById(R.id.urlInput)
        val keyView: TextInputEditText = findViewById(R.id.keyInput)
        val deckView: MaterialAutoCompleteTextView = findViewById(R.id.deckInput)
        val deckStatus: TextView = findViewById(R.id.deckStatus)
        val rateLabel: TextView = findViewById(R.id.rateLabel)
        val rateSlider: Slider = findViewById(R.id.rateSlider)
        val voiceView: MaterialAutoCompleteTextView = findViewById(R.id.voiceSpinner)
        val voiceStatus: TextView = findViewById(R.id.voiceStatus)
        val engineView: MaterialAutoCompleteTextView = findViewById(R.id.engineInput)
        val neuralVoiceView: MaterialAutoCompleteTextView = findViewById(R.id.neuralVoiceInput)
        val neuralUrlView: TextInputEditText = findViewById(R.id.neuralUrlInput)
        val neuralTokenView: TextInputEditText = findViewById(R.id.neuralTokenInput)
        val keyStatus: TextView = findViewById(R.id.keyStatus)
        val tokenStatus: TextView = findViewById(R.id.tokenStatus)
        val neuralStatus: TextView = findViewById(R.id.neuralStatus)
        val offlineSwitch: MaterialSwitch = findViewById(R.id.offlineBatchSwitch)
        val batchSizeView: TextInputEditText = findViewById(R.id.batchSizeInput)
        val goalModeView: MaterialAutoCompleteTextView = findViewById(R.id.goalModeInput)
        val goalAmountView: TextInputEditText = findViewById(R.id.goalAmountInput)
        val testVoiceInput: TextInputEditText = findViewById(R.id.testVoiceInput)
        val testVoiceButton: Button = findViewById(R.id.testVoiceButton)
        val testVoiceStatus: TextView = findViewById(R.id.testVoiceStatus)
        val speedSwitch: MaterialSwitch = findViewById(R.id.speedModeSwitch)
        val showCardSwitch: MaterialSwitch = findViewById(R.id.showCardSwitch)

        val cfg = Prefs.load(this)
        urlView.setText(cfg.url)
        keyView.setText("")
        keyStatus.text = getString(
            if (cfg.apiKey.isNotBlank()) R.string.settings_key_saved else R.string.settings_key_missing
        )
        deckView.setText(cfg.deck)
        neuralUrlView.setText(cfg.neuralUrl)
        neuralTokenView.setText("")
        tokenStatus.text = getString(
            if (cfg.neuralToken.isNotBlank()) R.string.settings_token_saved else R.string.settings_token_missing
        )
        speedSwitch.isChecked = cfg.speedMode
        showCardSwitch.isChecked = cfg.showCard
        offlineSwitch.isChecked = cfg.offlineBatch
        batchSizeView.setText(cfg.offlineBatchSize.toString())
        val goalLabels = listOf(
            getString(R.string.settings_goal_off),
            getString(R.string.settings_goal_cards),
            getString(R.string.settings_goal_minutes)
        )
        val goalLabel = when (cfg.goalMode) {
            GoalModes.CARDS -> getString(R.string.settings_goal_cards)
            GoalModes.MINUTES -> getString(R.string.settings_goal_minutes)
            else -> getString(R.string.settings_goal_off)
        }
        bindChoices(goalModeView, goalLabels, goalLabel)
        val shownAmount = if (cfg.goalMode == GoalModes.MINUTES) cfg.goalMinutes else cfg.goalCards
        goalAmountView.setText(shownAmount.toString())
        if (testVoiceInput.text.isNullOrBlank()) {
            testVoiceInput.setText(getString(R.string.test_voice_sample))
        }

        rateSlider.value = TtsRate.toProgress(cfg.ttsRate).toFloat()
        fun showRate(value: Float) {
            rateLabel.text = getString(R.string.settings_tts_rate_value, TtsRate.fromProgress(value.roundToInt()))
        }
        showRate(rateSlider.value)
        rateSlider.addOnChangeListener { _, value, _ -> showRate(value) }

        val engineLabels = listOf(TtsEngines.CHOICE_NEURAL, TtsEngines.CHOICE_DEVICE)
        val engineLabel = if (TtsEngines.preferred(cfg.ttsEngine) == EngineChoice.ON_DEVICE) {
            TtsEngines.CHOICE_DEVICE
        } else {
            TtsEngines.CHOICE_NEURAL
        }
        bindChoices(engineView, engineLabels, engineLabel)
        bindChoices(neuralVoiceView, NeuralTts.FALLBACK_VOICES, cfg.neuralVoice)

        savedDeviceVoice = cfg.ttsVoice
        bindDeviceVoices(voiceView, deviceVoices, savedDeviceVoice)

        tts = TextToSpeech(this) { status ->
            if (destroyed) return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                voiceStatus.text = getString(R.string.settings_tts_unavailable)
                return@TextToSpeech
            }
            val voices = try {
                tts?.voices.orEmpty().sortedWith(compareBy({ it.locale?.toLanguageTag() ?: "" }, { it.name }))
            } catch (_: Exception) {
                voiceStatus.text = getString(R.string.settings_tts_unavailable)
                emptyList()
            }
            val items = ArrayList<VoiceChoice>(voices.size + 1)
            items.add(VoiceChoice(getString(R.string.settings_tts_voice_default), ""))
            for (voice in voices) {
                val locale = voice.locale?.toLanguageTag().orEmpty()
                val network = if (voice.isNetworkConnectionRequired) " · network" else ""
                val label = if (locale.isEmpty()) voice.name else "$locale — ${voice.name}$network"
                items.add(VoiceChoice(label, voice.name))
            }
            deviceVoices = items
            voicesReady = true
            bindDeviceVoices(voiceView, items, savedDeviceVoice)
            voiceStatus.text = if (savedDeviceVoice.isNotEmpty() && items.none { it.name == savedDeviceVoice }) {
                getString(R.string.settings_tts_voice_missing)
            } else {
                ""
            }
        }

        findViewById<Button>(R.id.refreshDecksButton).setOnClickListener {
            loadDecks(urlView, keyView, deckView, deckStatus)
        }
        loadDecks(urlView, keyView, deckView, deckStatus)
        loadNeural(neuralUrlView, neuralTokenView, neuralVoiceView, neuralStatus, cfg.neuralVoice)

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            val voiceName = selectedDeviceVoice(voiceView)
            savedDeviceVoice = voiceName
            val goalMode = when (goalModeView.text.toString().trim()) {
                getString(R.string.settings_goal_cards) -> GoalModes.CARDS
                getString(R.string.settings_goal_minutes) -> GoalModes.MINUTES
                else -> GoalModes.OFF
            }
            val amount = goalAmountView.text.toString().trim().toIntOrNull()
            Prefs.save(
                this,
                AnkiConfig(
                    url = urlView.text.toString().trim().ifBlank { Prefs.DEFAULT_URL },
                    apiKey = keyView.text.toString().trim().ifBlank { cfg.apiKey },
                    deck = deckView.text.toString().trim().ifBlank { Prefs.DEFAULT_DECK },
                    ttsRate = TtsRate.fromProgress(rateSlider.value.roundToInt()),
                    ttsVoice = voiceName,
                    ttsEngine = selectedEngine(engineView),
                    neuralUrl = neuralUrlView.text.toString().trim().ifBlank { Prefs.DEFAULT_NEURAL_URL },
                    neuralToken = neuralTokenView.text.toString().trim().ifBlank { cfg.neuralToken },
                    neuralVoice = neuralVoiceView.text.toString().trim().ifBlank { NeuralTts.DEFAULT_VOICE },
                    speedMode = speedSwitch.isChecked,
                    showCard = showCardSwitch.isChecked,
                    offlineBatch = offlineSwitch.isChecked,
                    offlineBatchSize = batchSizeView.text.toString().trim().toIntOrNull() ?: cfg.offlineBatchSize,
                    goalMode = goalMode,
                    goalCards = if (goalMode == GoalModes.MINUTES) cfg.goalCards else (amount ?: cfg.goalCards),
                    goalMinutes = if (goalMode == GoalModes.MINUTES) (amount ?: cfg.goalMinutes) else cfg.goalMinutes
                )
            )
            Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show()
            finish()
        }

        val testResultView: TextView = findViewById(R.id.testResultView)
        findViewById<Button>(R.id.testConnectionButton).setOnClickListener {
            testResultView.text = "Testing…"
            val url = urlView.text.toString().trim().ifBlank { Prefs.DEFAULT_URL }
            val key = keyView.text.toString().trim().ifBlank { cfg.apiKey }
            val deck = deckView.text.toString().trim().ifBlank { Prefs.DEFAULT_DECK }
            if (key.isBlank()) {
                testResultView.text = getString(R.string.settings_key_missing)
                return@setOnClickListener
            }
            Thread {
                val sb = StringBuilder()
                try {
                    val anki = AnkiConnect(url, key)
                    sb.appendLine("version: ${anki.version()}")
                    sb.appendLine("decks: ${anki.deckNames()}")
                    sb.appendLine("due in '" + deck + "': " + anki.dueCount(deck))
                    sb.append("connection OK")
                } catch (e: Exception) {
                    sb.append("FAILED: " + e.javaClass.simpleName + ": " + e.message)
                }
                runOnUiThread { if (!destroyed) testResultView.text = sb.toString() }
            }.start()
        }

        testVoiceButton.setOnClickListener {
            if (testing) return@setOnClickListener
            val phrase = testVoiceInput.text.toString().trim().ifBlank { getString(R.string.test_voice_sample) }
            val rate = TtsRate.fromProgress(rateSlider.value.roundToInt())
            val engine = selectedEngine(engineView)
            val deviceVoice = selectedDeviceVoice(voiceView)
            val neuralVoice = neuralVoiceView.text.toString().trim().ifBlank { NeuralTts.DEFAULT_VOICE }
            val neuralUrl = neuralUrlView.text.toString().trim().ifBlank { Prefs.DEFAULT_NEURAL_URL }
            val neuralToken = neuralTokenView.text.toString().trim().ifBlank { cfg.neuralToken }
            testing = true
            testVoiceButton.isEnabled = false
            testVoiceStatus.text = getString(R.string.test_voice_playing)
            Thread {
                val outcome = if (engine == TtsEngines.NEURAL) {
                    val ok = EdgeSpeaker(applicationContext).speakBlocking(
                        neuralUrl, neuralToken, neuralVoice, rate, phrase
                    ) { destroyed }
                    if (ok) {
                        "neural"
                    } else if (!destroyed && speakDeviceBlocking(phrase, rate, deviceVoice)) {
                        "fallback"
                    } else {
                        "fail"
                    }
                } else if (speakDeviceBlocking(phrase, rate, deviceVoice)) {
                    "device"
                } else {
                    "fail"
                }
                runOnUiThread {
                    testing = false
                    if (destroyed) return@runOnUiThread
                    testVoiceButton.isEnabled = true
                    testVoiceStatus.text = when (outcome) {
                        "neural" -> getString(R.string.test_voice_played_neural)
                        "device" -> getString(R.string.test_voice_played_device)
                        "fallback" -> getString(R.string.test_voice_played_fallback)
                        else -> getString(R.string.test_voice_failed)
                    }
                }
            }.start()
        }
    }

    private fun loadDecks(
        urlView: TextInputEditText,
        keyView: TextInputEditText,
        deckView: MaterialAutoCompleteTextView,
        deckStatus: TextView
    ) {
        val url = urlView.text.toString().trim().ifBlank { Prefs.DEFAULT_URL }
        val key = keyView.text.toString().trim().ifBlank { Prefs.load(this).apiKey }
        val kept = deckView.text.toString()
        deckStatus.text = getString(R.string.settings_decks_loading)
        Thread {
            val names = try {
                AnkiConnect(url, key).deckNames()
            } catch (_: Exception) {
                null
            }
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                val current = deckView.text.toString().ifBlank { kept }
                if (names.isNullOrEmpty()) {
                    deckStatus.text = getString(R.string.settings_decks_failed)
                    if (deckView.text.isNullOrBlank() && current.isNotBlank()) {
                        deckView.setText(current, false)
                    }
                } else {
                    val items = names.toMutableList()
                    if (current.isNotBlank() && current !in items) items.add(0, current)
                    bindChoices(deckView, items, current.ifBlank { items.first() })
                    deckStatus.text = ""
                }
            }
        }.start()
    }

    private fun loadNeural(
        urlView: TextInputEditText,
        tokenView: TextInputEditText,
        voiceView: MaterialAutoCompleteTextView,
        status: TextView,
        savedVoice: String
    ) {
        bindChoices(voiceView, NeuralTts.FALLBACK_VOICES, savedVoice)
        val url = urlView.text.toString().trim().ifBlank { Prefs.DEFAULT_NEURAL_URL }
        val token = tokenView.text.toString().trim().ifBlank { Prefs.load(this).neuralToken }
        Thread {
            val speaker = EdgeSpeaker(applicationContext)
            val remote = speaker.fetchVoices(url, token)
            val up = speaker.healthy(url, token)
            val names = NeuralTts.pickerNames(remote)
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                val selected = voiceView.text.toString().ifBlank { savedVoice }
                bindChoices(voiceView, names, selected)
                status.text = when {
                    up && remote.isNotEmpty() -> getString(R.string.settings_neural_ok)
                    up -> getString(R.string.settings_neural_voices_builtin)
                    else -> getString(R.string.settings_neural_down)
                }
            }
        }.start()
    }

    private fun selectedEngine(view: MaterialAutoCompleteTextView): String {
        return if (view.text.toString().trim() == TtsEngines.CHOICE_DEVICE) {
            TtsEngines.DEVICE
        } else {
            TtsEngines.NEURAL
        }
    }

    private fun selectedDeviceVoice(view: MaterialAutoCompleteTextView): String {
        if (!voicesReady) return savedDeviceVoice
        val label = view.text.toString()
        return deviceVoices.firstOrNull { it.label == label }?.name ?: savedDeviceVoice
    }

    private fun bindDeviceVoices(
        view: MaterialAutoCompleteTextView,
        items: List<VoiceChoice>,
        selectedName: String
    ) {
        val labels = items.map { it.label }
        val selected = items.firstOrNull { it.name == selectedName }?.label
            ?: items.firstOrNull()?.label.orEmpty()
        bindChoices(view, labels, selected)
    }

    private fun bindChoices(view: MaterialAutoCompleteTextView, labels: List<String>, selected: String) {
        val items = if (labels.isEmpty()) listOf(selected) else labels
        view.setAdapter(StableAdapter(this, items))
        val text = when {
            selected.isNotBlank() && selected in items -> selected
            selected.isNotBlank() -> selected
            else -> items.first()
        }
        view.setText(text, false)
    }

    private fun speakDeviceBlocking(phrase: String, rate: Float, voiceName: String): Boolean {
        val engine = tts ?: return false
        val latch = CountDownLatch(1)
        val ok = AtomicBoolean(false)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                ok.set(true)
                latch.countDown()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                latch.countDown()
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                latch.countDown()
            }
        })
        try {
            engine.setSpeechRate(TtsRate.clamp(rate))
        } catch (_: Exception) {
        }
        if (voiceName.isNotBlank()) {
            val match = try {
                engine.voices?.firstOrNull { it.name == voiceName }
            } catch (_: Exception) {
                null
            }
            if (match != null) {
                try { engine.setVoice(match) } catch (_: Exception) {}
            }
        }
        val rc = try {
            engine.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
        if (rc != TextToSpeech.SUCCESS) return false
        return try {
            latch.await(30, TimeUnit.SECONDS) && ok.get()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    override fun onDestroy() {
        destroyed = true
        if (testing) EdgeSpeaker.stopActive()
        try { tts?.stop() } catch (_: Exception) {}
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        super.onDestroy()
    }

    private data class VoiceChoice(val label: String, val name: String)

    private class StableAdapter(
        context: android.content.Context,
        private val labels: List<String>
    ) : ArrayAdapter<String>(context, android.R.layout.simple_dropdown_item_1line, labels.toMutableList()) {
        override fun getFilter(): Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?) = FilterResults().apply {
                values = labels
                count = labels.size
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                notifyDataSetChanged()
            }
        }
    }
}
