package com.evan.voicesrs

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Filter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import android.widget.TextView
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/** AnkiConnect, speech, and neural TTS settings. Edits save themselves. Test voice plays only on tap. */
class SettingsActivity : AppCompatActivity() {

    private var tts: TextToSpeech? = null
    private var destroyed = false
    private var voicesReady = false
    private var testing = false
    private var deviceVoices: List<VoiceChoice> = listOf(VoiceChoice("System default", ""))
    private var savedDeviceVoice = ""
    private lateinit var held: AnkiConfig
    private var ready = false
    private var flushTexts: () -> Unit = {}
    private val choiceSavers = HashMap<MaterialAutoCompleteTextView, (String) -> Unit>()
    private var bindingChoices = false
    private val feedback = AutoSave.Debounce()
    private val uiHandler = Handler(Looper.getMainLooper())
    private var checkGen = 0
    private lateinit var testResultView: TextView
    private val showSavedRunnable = Runnable { showSavedNotice() }

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
        val rateCustomLink: TextView = findViewById(R.id.ttsRateCustomLink)
        val rateCustomLayout: TextInputLayout = findViewById(R.id.ttsRateCustomLayout)
        val rateCustomInput: TextInputEditText = findViewById(R.id.ttsRateCustomInput)
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
        val batchLayout: TextInputLayout = findViewById(R.id.batchSizeLayout)
        val batchSizeView: TextInputEditText = findViewById(R.id.batchSizeInput)
        val goalModeView: MaterialAutoCompleteTextView = findViewById(R.id.goalModeInput)
        val goalAmountLayout: TextInputLayout = findViewById(R.id.goalAmountLayout)
        val goalAmountView: TextInputEditText = findViewById(R.id.goalAmountInput)
        val goalBatchHint: TextView = findViewById(R.id.goalBatchHint)
        val testVoiceInput: TextInputEditText = findViewById(R.id.testVoiceInput)
        val testVoiceButton: Button = findViewById(R.id.testVoiceButton)
        val testVoiceStatus: TextView = findViewById(R.id.testVoiceStatus)
        val speedSwitch: MaterialSwitch = findViewById(R.id.speedModeSwitch)
        val showCardSwitch: MaterialSwitch = findViewById(R.id.showCardSwitch)
        val keepScreenOnSwitch: MaterialSwitch = findViewById(R.id.keepScreenOnSwitch)
        val highlightSentenceSwitch: MaterialSwitch = findViewById(R.id.highlightSentenceSwitch)
        val handsFreeSwitch: MaterialSwitch = findViewById(R.id.handsFreeSwitch)
        val handsFreeReadySwitch: MaterialSwitch = findViewById(R.id.handsFreeReadySwitch)
        val shuffleSwitch: MaterialSwitch = findViewById(R.id.shuffleOptionsSwitch)
        val detectCodeSwitch: MaterialSwitch = findViewById(R.id.detectCodeSwitch)
        val explainAgain: Chip = findViewById(R.id.autoExplainAgain)
        val explainHard: Chip = findViewById(R.id.autoExplainHard)
        val explainGood: Chip = findViewById(R.id.autoExplainGood)
        val explainEasy: Chip = findViewById(R.id.autoExplainEasy)
        val listenWaitView: MaterialAutoCompleteTextView = findViewById(R.id.listenWaitInput)
        val endSilenceView: MaterialAutoCompleteTextView = findViewById(R.id.endSilenceInput)
        val listenAttemptsView: MaterialAutoCompleteTextView = findViewById(R.id.listenAttemptsInput)
        val thinkTimeView: MaterialAutoCompleteTextView = findViewById(R.id.thinkTimeInput)

        held = Prefs.load(this)
        urlView.setText(held.url)
        keyView.setText("")
        keyStatus.text = getString(
            if (held.apiKey.isNotBlank()) R.string.settings_key_saved else R.string.settings_key_missing
        )
        val deckSeed = held.deck.ifBlank { Prefs.DEFAULT_DECK }
        bindChoices(deckView, listOf(deckSeed), deckSeed) { label ->
            val deck = label.trim().ifBlank { Prefs.DEFAULT_DECK }
            persist { it.copy(deck = deck) }
        }
        neuralUrlView.setText(held.neuralUrl)
        neuralTokenView.setText("")
        tokenStatus.text = getString(
            if (held.neuralToken.isNotBlank()) R.string.settings_token_saved else R.string.settings_token_missing
        )
        speedSwitch.isChecked = held.speedMode
        showCardSwitch.isChecked = held.showCard
        keepScreenOnSwitch.isChecked = held.keepScreenOn
        highlightSentenceSwitch.isChecked = held.highlightSentence
        handsFreeSwitch.isChecked = held.handsFree
        handsFreeReadySwitch.isChecked = held.handsFreeReady
        shuffleSwitch.isChecked = held.shuffleOptions
        detectCodeSwitch.isChecked = held.detectCode
        explainAgain.isChecked = 1 in held.autoExplainEases
        explainHard.isChecked = 2 in held.autoExplainEases
        explainGood.isChecked = 3 in held.autoExplainEases
        explainEasy.isChecked = 4 in held.autoExplainEases
        val waitLabels = ListenWindow.WAIT_SEC_CHOICES.map {
            getString(R.string.settings_listen_wait_choice, it)
        }
        val silenceLabels = ListenWindow.SILENCE_MS_CHOICES.map {
            getString(R.string.settings_end_silence_choice, it)
        }
        bindChoices(
            listenWaitView,
            waitLabels,
            getString(R.string.settings_listen_wait_choice, ListenWindow.normalizeWaitSec(held.listenWaitSec))
        ) { label ->
            persist { it.copy(listenWaitSec = ListenWindow.waitSecFromLabel(label)) }
        }
        bindChoices(
            endSilenceView,
            silenceLabels,
            getString(R.string.settings_end_silence_choice, ListenWindow.normalizeSilenceMs(held.endSilenceMs))
        ) { label ->
            persist { it.copy(endSilenceMs = ListenWindow.silenceMsFromLabel(label)) }
        }
        val attemptLabels = (ListenWindow.MIN_ATTEMPTS..ListenWindow.MAX_ATTEMPTS).map { it.toString() }
        bindChoices(
            listenAttemptsView,
            attemptLabels,
            ListenWindow.clampAttempts(held.listenAttempts).toString()
        ) { label ->
            persist { it.copy(listenAttempts = ListenWindow.attemptsFromLabel(label)) }
        }
        val thinkLabels = ThinkTime.CHOICES_SEC.map { ThinkTime.label(it) }
        bindChoices(
            thinkTimeView,
            thinkLabels,
            ThinkTime.label(held.thinkTimeSec)
        ) { label ->
            persist { it.copy(thinkTimeSec = ThinkTime.fromLabel(label)) }
        }
        offlineSwitch.isChecked = held.offlineBatch
        batchSizeView.setText(held.offlineBatchSize.toString())
        val goalLabels = listOf(
            getString(R.string.settings_goal_off),
            getString(R.string.settings_goal_cards),
            getString(R.string.settings_goal_minutes)
        )
        val goalLabel = when (held.goalMode) {
            GoalModes.CARDS -> getString(R.string.settings_goal_cards)
            GoalModes.MINUTES -> getString(R.string.settings_goal_minutes)
            else -> getString(R.string.settings_goal_off)
        }
        var commitGoalMode: () -> Unit = {}
        bindChoices(goalModeView, goalLabels, goalLabel) { commitGoalMode() }
        val shownAmount = if (held.goalMode == GoalModes.MINUTES) held.goalMinutes else held.goalCards
        goalAmountView.setText(shownAmount.toString())
        if (testVoiceInput.text.isNullOrBlank()) {
            testVoiceInput.setText(getString(R.string.test_voice_sample))
        }

        // Opaque typed color, so the disabled text color does not stack on the input alpha.
        val batchTextColor = ColorStateList.valueOf(batchSizeView.currentTextColor)
        val batchHintDefault = batchLayout.defaultHintTextColor
        val batchHintFocused = batchLayout.hintTextColor
        val batchHintBase = batchHintDefault?.defaultColor ?: batchSizeView.currentHintTextColor
        var batchUiOn: Boolean? = null
        fun goalModeFromField(): String = when (goalModeView.text.toString().trim()) {
            getString(R.string.settings_goal_cards) -> GoalModes.CARDS
            getString(R.string.settings_goal_minutes) -> GoalModes.MINUTES
            else -> GoalModes.OFF
        }
        fun refreshDrillFields() {
            val offlineOn = offlineSwitch.isChecked
            if (batchUiOn != offlineOn) {
                batchUiOn = offlineOn
                val alpha = DrillSettings.fieldAlpha(offlineOn)
                batchSizeView.isEnabled = offlineOn
                batchSizeView.setTextColor(batchTextColor)
                batchSizeView.alpha = alpha
                // Label color carries the same alpha. Layout alpha stays 1 so it does not stack.
                if (offlineOn) {
                    batchLayout.setDefaultHintTextColor(batchHintDefault)
                    batchLayout.setHintTextColor(batchHintFocused)
                } else {
                    val hintAlpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
                    val faded = ColorStateList.valueOf(
                        Color.argb(
                            hintAlpha,
                            Color.red(batchHintBase),
                            Color.green(batchHintBase),
                            Color.blue(batchHintBase)
                        )
                    )
                    batchLayout.setDefaultHintTextColor(faded)
                    batchLayout.setHintTextColor(faded)
                }
                batchLayout.findViewById<TextView>(
                    com.google.android.material.R.id.textinput_helper_text
                )?.alpha = alpha
            }

            val mode = goalModeFromField()
            goalAmountLayout.visibility = if (DrillSettings.showGoalAmount(mode)) View.VISIBLE else View.GONE
            goalAmountLayout.suffixText = DrillSettings.goalSuffix(mode).ifEmpty { null }
            val showHint = DrillSettings.showGoalBatchHint(
                mode,
                offlineOn,
                goalAmountView.text?.toString().orEmpty(),
                batchSizeView.text?.toString().orEmpty(),
                held.offlineBatchSize
            )
            goalBatchHint.visibility = if (showHint) View.VISIBLE else View.GONE
        }
        offlineSwitch.setOnCheckedChangeListener { _, checked ->
            refreshDrillFields()
            persist { it.copy(offlineBatch = checked) }
        }
        speedSwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(speedMode = checked) }
        }
        showCardSwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(showCard = checked) }
        }
        keepScreenOnSwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(keepScreenOn = checked) }
        }
        highlightSentenceSwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(highlightSentence = checked) }
        }
        handsFreeSwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(handsFree = checked) }
        }
        handsFreeReadySwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(handsFreeReady = checked) }
        }
        shuffleSwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(shuffleOptions = checked) }
        }
        detectCodeSwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(detectCode = checked) }
        }
        val explainChips = listOf(explainAgain, explainHard, explainGood, explainEasy)
        val saveExplain = {
            val selected = buildSet {
                if (explainAgain.isChecked) add(1)
                if (explainHard.isChecked) add(2)
                if (explainGood.isChecked) add(3)
                if (explainEasy.isChecked) add(4)
            }
            persist { it.copy(autoExplainEases = selected) }
        }
        for (chip in explainChips) {
            chip.setOnCheckedChangeListener { _, _ -> saveExplain() }
        }
        goalModeView.doAfterTextChanged { refreshDrillFields() }
        commitGoalMode = {
            refreshDrillFields()
            val mode = goalModeFromField()
            val amount = goalAmountView.text?.toString()?.trim()?.toIntOrNull()
            persist { cfg ->
                when (mode) {
                    GoalModes.MINUTES -> cfg.copy(goalMode = mode, goalMinutes = amount ?: cfg.goalMinutes)
                    GoalModes.CARDS -> cfg.copy(goalMode = mode, goalCards = amount ?: cfg.goalCards)
                    else -> cfg.copy(goalMode = mode)
                }
            }
        }
        batchSizeView.doAfterTextChanged { refreshDrillFields() }
        goalAmountView.doAfterTextChanged { refreshDrillFields() }
        goalBatchHint.text = DrillSettings.GOAL_BATCH_HINT
        refreshDrillFields()

        var spokenRate = TtsRate.clamp(held.ttsRate)
        var rateCommitGuard = false
        var rateFromUser = false
        // Bubble shows the speed ("1.2x"), not the step index. labelBehavior stays floating.
        rateSlider.setLabelFormatter { v -> TtsRate.label(v.roundToInt()) }
        fun showRate(rate: Float) {
            rateLabel.text = if (TtsRate.isCustom(rate)) {
                getString(R.string.settings_tts_rate_custom, rate)
            } else {
                getString(R.string.settings_tts_rate_value, rate)
            }
            val progress = TtsRate.toProgress(rate).toFloat()
            if (rateSlider.value != progress) rateSlider.value = progress
        }
        fun commitCustomRate(hideOnSuccess: Boolean): Boolean {
            val decision = AutoSave.decide(
                spokenRate,
                TtsRate.parse(rateCustomInput.text?.toString().orEmpty())
            )
            if (decision.invalid) {
                rateCustomLayout.error = getString(R.string.settings_tts_rate_error)
                return false
            }
            rateCustomLayout.error = null
            spokenRate = decision.value
            val shown = TtsRate.format(spokenRate)
            if (rateCustomInput.text?.toString() != shown) rateCustomInput.setText(shown)
            showRate(spokenRate)
            if (hideOnSuccess) {
                rateCommitGuard = true
                rateCustomLayout.visibility = View.GONE
                rateCustomInput.clearFocus()
                rateCommitGuard = false
            }
            if (decision.save) persist { it.copy(ttsRate = decision.value) }
            return true
        }
        showRate(spokenRate)
        // Opening Settings pins a saved rate above 1.5×. Only a drag writes it back.
        rateSlider.addOnChangeListener { _, value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            rateFromUser = true
            spokenRate = TtsRate.fromProgress(value.roundToInt())
            rateLabel.text = getString(R.string.settings_tts_rate_value, spokenRate)
            if (rateCustomLayout.visibility == View.VISIBLE) {
                rateCustomLayout.error = null
                rateCustomInput.setText(TtsRate.format(spokenRate))
            }
        }
        rateSlider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                rateFromUser = false
            }

            override fun onStopTrackingTouch(slider: Slider) {
                if (!rateFromUser) return
                rateFromUser = false
                spokenRate = TtsRate.fromProgress(slider.value.roundToInt())
                persist { it.copy(ttsRate = spokenRate) }
            }
        })
        rateCustomLink.setOnClickListener {
            if (rateCustomLayout.visibility == View.VISIBLE) {
                commitCustomRate(hideOnSuccess = true)
                return@setOnClickListener
            }
            rateCustomLayout.error = null
            rateCustomInput.setText(TtsRate.format(spokenRate))
            rateCustomLayout.visibility = View.VISIBLE
            rateCustomInput.requestFocus()
            rateCustomInput.setSelection(rateCustomInput.text?.length ?: 0)
        }
        rateCustomInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            if (commitCustomRate(hideOnSuccess = true)) {
                val imm = getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(rateCustomInput.windowToken, 0)
            }
            true
        }
        rateCustomInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && !rateCommitGuard && rateCustomLayout.visibility == View.VISIBLE) {
                commitCustomRate(hideOnSuccess = false)
            }
        }

        val engineLabels = listOf(TtsEngines.CHOICE_NEURAL, TtsEngines.CHOICE_DEVICE)
        val engineLabel = if (TtsEngines.preferred(held.ttsEngine) == EngineChoice.ON_DEVICE) {
            TtsEngines.CHOICE_DEVICE
        } else {
            TtsEngines.CHOICE_NEURAL
        }
        bindChoices(engineView, engineLabels, engineLabel) {
            persist { cfg -> cfg.copy(ttsEngine = selectedEngine(engineView)) }
        }
        bindChoices(neuralVoiceView, NeuralTts.FALLBACK_VOICES, held.neuralVoice) { label ->
            val name = label.trim().ifBlank { NeuralTts.DEFAULT_VOICE }
            persist { it.copy(neuralVoice = name) }
        }

        savedDeviceVoice = held.ttsVoice
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
        loadNeural(neuralUrlView, neuralTokenView, neuralVoiceView, neuralStatus, held.neuralVoice)

        fun commitUrl() {
            val url = urlView.text?.toString()?.trim().orEmpty().ifBlank { Prefs.DEFAULT_URL }
            if (urlView.text?.toString() != url) urlView.setText(url)
            if (!AutoSave.decide(held.url, url).save) return
            persist { it.copy(url = url) }
        }
        fun commitKey() {
            val typed = keyView.text?.toString()?.trim().orEmpty()
            if (typed.isEmpty()) return
            if (!AutoSave.decide(held.apiKey, typed).save) return
            if (persist { it.copy(apiKey = typed) } && !destroyed) {
                keyStatus.text = getString(R.string.settings_key_saved)
            }
        }
        fun commitDeck() {
            val typed = deckView.text?.toString()?.trim().orEmpty()
            val deck = typed.ifBlank { Prefs.DEFAULT_DECK }
            if (typed.isBlank() && deckView.text?.toString() != deck) deckView.setText(deck, false)
            if (!AutoSave.decide(held.deck, deck).save) return
            persist { it.copy(deck = deck) }
        }
        var batchClampedText: String? = null
        fun commitBatch() {
            val raw = batchSizeView.text?.toString().orEmpty()
            val batch = DrillSettings.clampBatch(raw, held.offlineBatchSize)
            if (batch.rewriteField && batchSizeView.text?.toString() != batch.text) {
                batchSizeView.setText(batch.text)
            }
            if (batch.cappedAtMax) {
                batchClampedText = batch.text
                batchLayout.helperText = DrillSettings.CLAMPED_NOTE
                Toast.makeText(this, DrillSettings.CLAMPED_NOTE, Toast.LENGTH_SHORT).show()
            } else if (batchLayout.helperText?.toString() == DrillSettings.CLAMPED_NOTE &&
                raw.trim() != batchClampedText
            ) {
                // A second commit of the clamped value (focus loss after Done, onPause) keeps the note.
                batchClampedText = null
                batchLayout.helperText = getString(R.string.settings_batch_range)
            }
            val candidate = if (raw.trim().toIntOrNull() == null) held.offlineBatchSize else batch.value
            if (!AutoSave.decide(held.offlineBatchSize, candidate).save) return
            persist { it.copy(offlineBatchSize = candidate) }
        }
        fun commitGoalAmount() {
            val mode = goalModeFromField()
            if (!DrillSettings.showGoalAmount(mode)) return
            val raw = goalAmountView.text?.toString().orEmpty()
            if (raw.isBlank()) return
            val last = if (mode == GoalModes.MINUTES) held.goalMinutes else held.goalCards
            val decision = AutoSave.decide(last, raw.trim().toIntOrNull())
            if (decision.invalid || !decision.save) return
            val wrote = persist { cfg ->
                if (mode == GoalModes.MINUTES) cfg.copy(goalMinutes = decision.value)
                else cfg.copy(goalCards = decision.value)
            }
            if (!wrote) return
            val stored = if (mode == GoalModes.MINUTES) held.goalMinutes else held.goalCards
            if (goalAmountView.text?.toString()?.trim() != stored.toString()) {
                goalAmountView.setText(stored.toString())
            }
        }
        fun commitNeuralUrl() {
            val url = neuralUrlView.text?.toString()?.trim().orEmpty().ifBlank { Prefs.DEFAULT_NEURAL_URL }
            if (neuralUrlView.text?.toString() != url) neuralUrlView.setText(url)
            if (!AutoSave.decide(held.neuralUrl, url).save) return
            persist { it.copy(neuralUrl = url) }
        }
        fun commitNeuralToken() {
            val typed = neuralTokenView.text?.toString()?.trim().orEmpty()
            if (typed.isEmpty()) return
            if (!AutoSave.decide(held.neuralToken, typed).save) return
            if (persist { it.copy(neuralToken = typed) } && !destroyed) {
                tokenStatus.text = getString(R.string.settings_token_saved)
            }
        }
        fun track(view: TextView, commit: () -> Unit) {
            view.setOnEditorActionListener { v, actionId, _ ->
                if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
                commit()
                val imm = getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                true
            }
            view.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) commit()
            }
        }
        track(urlView) { commitUrl() }
        track(keyView) { commitKey() }
        track(deckView) { commitDeck() }
        track(batchSizeView) { commitBatch() }
        track(goalAmountView) { commitGoalAmount() }
        track(neuralUrlView) { commitNeuralUrl() }
        track(neuralTokenView) { commitNeuralToken() }
        val aiEnabledSwitch: MaterialSwitch = findViewById(R.id.aiEnabledSwitch)
        val generatedDeckInput: TextInputEditText = findViewById(R.id.generatedDeckInput)
        val aiBaseUrlLayout: TextInputLayout = findViewById(R.id.aiBaseUrlLayout)
        val aiBaseUrlInput: TextInputEditText = findViewById(R.id.aiBaseUrlInput)
        val aiModelInput: MaterialAutoCompleteTextView = findViewById(R.id.aiModelInput)
        val aiKeyInput: TextInputEditText = findViewById(R.id.aiKeyInput)
        val aiKeyStatus: TextView = findViewById(R.id.aiKeyStatus)
        val aiLookaheadLayout: TextInputLayout = findViewById(R.id.aiLookaheadLayout)
        val aiLookaheadInput: TextInputEditText = findViewById(R.id.aiLookaheadInput)
        val aiBatchSwitch: MaterialSwitch = findViewById(R.id.aiBatchSwitch)
        val aiTestResult: TextView = findViewById(R.id.aiTestResult)
        aiEnabledSwitch.isChecked = held.aiEnabled
        generatedDeckInput.setText(held.generatedDeck.ifBlank { MakeCard.DEFAULT_DECK })
        aiBaseUrlInput.setText(held.aiBaseUrl.ifBlank { Prefs.DEFAULT_AI_URL })
        aiModelInput.setText(held.aiModel.ifBlank { Prefs.DEFAULT_AI_MODEL })
        aiKeyInput.setText("")
        aiKeyStatus.text = getString(
            if (held.aiApiKey.isNotBlank()) R.string.settings_ai_key_saved else R.string.settings_ai_key_missing
        )
        aiLookaheadInput.setText(held.aiLookahead.coerceIn(0, 5).toString())
        aiBatchSwitch.isChecked = held.aiBatch
        aiEnabledSwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(aiEnabled = checked) }
        }
        fun commitGeneratedDeck() {
            val deck = generatedDeckInput.text?.toString()?.trim().orEmpty()
                .ifBlank { MakeCard.DEFAULT_DECK }
            if (generatedDeckInput.text?.toString() != deck) generatedDeckInput.setText(deck)
            persist { it.copy(generatedDeck = deck) }
        }
        track(generatedDeckInput) { commitGeneratedDeck() }
        aiBatchSwitch.setOnCheckedChangeListener { _, checked ->
            persist { it.copy(aiBatch = checked) }
        }
        fun commitAiUrl() {
            val raw = aiBaseUrlInput.text?.toString()?.trim().orEmpty()
            val url = raw.ifBlank { Prefs.DEFAULT_AI_URL }
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                aiBaseUrlLayout.error = getString(R.string.settings_ai_url_error)
                return
            }
            aiBaseUrlLayout.error = null
            if (aiBaseUrlInput.text?.toString() != url) aiBaseUrlInput.setText(url)
            persist { it.copy(aiBaseUrl = url) }
        }
        fun commitAiModel() {
            val raw = aiModelInput.text?.toString()?.trim().orEmpty()
            val model = raw.ifBlank { Prefs.DEFAULT_AI_MODEL }
            if (aiModelInput.text?.toString() != model) aiModelInput.setText(model)
            persist { it.copy(aiModel = model) }
        }
        fun commitAiKey() {
            val typed = aiKeyInput.text?.toString()?.trim().orEmpty()
            if (typed.isEmpty()) return
            if (persist { it.copy(aiApiKey = typed) } && !destroyed) {
                aiKeyStatus.text = getString(R.string.settings_ai_key_saved)
            }
        }
        fun commitAiLookahead() {
            val raw = aiLookaheadInput.text?.toString()?.trim().orEmpty()
            val n = raw.toIntOrNull()
            if (n == null || n !in 0..5) {
                aiLookaheadLayout.error = getString(R.string.settings_ai_lookahead_error)
                return
            }
            aiLookaheadLayout.error = null
            persist { it.copy(aiLookahead = n) }
        }
        track(aiBaseUrlInput) { commitAiUrl() }
        track(aiModelInput) { commitAiModel() }
        track(aiKeyInput) { commitAiKey() }
        track(aiLookaheadInput) { commitAiLookahead() }
        fun loadAiModels() {
            val raw = aiBaseUrlInput.text?.toString()?.trim().orEmpty()
            val url = raw.ifBlank { Prefs.DEFAULT_AI_URL }
            if (!url.startsWith("http://") && !url.startsWith("https://")) return
            val key = aiKeyInput.text?.toString()?.trim().orEmpty().ifBlank { held.aiApiKey }
            val kept = aiModelInput.text?.toString().orEmpty()
            Thread {
                val ids = try {
                    AiClient().listModels(url, key) { destroyed }
                } catch (_: Exception) {
                    null
                }
                runOnUiThread {
                    if (destroyed || ids == null) return@runOnUiThread
                    val current = aiModelInput.text?.toString()?.ifBlank { kept }.orEmpty().ifBlank { kept }
                    val items = ArrayList(ids)
                    if (current.isNotBlank() && current !in items) items.add(0, current)
                    val selected = current.ifBlank { Prefs.DEFAULT_AI_MODEL }
                    bindChoices(aiModelInput, items, selected) { picked ->
                        val model = picked.trim().ifBlank { Prefs.DEFAULT_AI_MODEL }
                        if (aiModelInput.text?.toString() != model) aiModelInput.setText(model, false)
                        persist { it.copy(aiModel = model) }
                    }
                }
            }.start()
        }
        findViewById<View>(R.id.aiModelRefresh).setOnClickListener { loadAiModels() }
        loadAiModels()
        flushTexts = {
            commitUrl()
            commitKey()
            commitDeck()
            commitBatch()
            commitGoalAmount()
            commitNeuralUrl()
            commitNeuralToken()
            commitAiUrl()
            commitAiModel()
            commitAiKey()
            commitAiLookahead()
            if (rateCustomLayout.visibility == View.VISIBLE) commitCustomRate(hideOnSuccess = false)
        }
        findViewById<Button>(R.id.aiTestButton).setOnClickListener {
            commitAiUrl()
            commitAiModel()
            commitAiKey()
            commitAiLookahead()
            if (aiBaseUrlLayout.error != null || aiLookaheadLayout.error != null) return@setOnClickListener
            val url = aiBaseUrlInput.text?.toString()?.trim().orEmpty().ifBlank { Prefs.DEFAULT_AI_URL }
            val model = aiModelInput.text?.toString()?.trim().orEmpty().ifBlank { Prefs.DEFAULT_AI_MODEL }
            val key = aiKeyInput.text?.toString()?.trim().orEmpty().ifBlank { held.aiApiKey }
            val button = it as Button
            button.isEnabled = false
            aiTestResult.text = getString(R.string.settings_ai_testing)
            Thread {
                val message = try {
                    val client = AiClient()
                    client.probe(url, key) { destroyed }
                    val started = SystemClock.elapsedRealtime()
                    val reply = client.complete(
                        baseUrl = url,
                        apiKey = key,
                        model = model,
                        system = "Reply with the single word ok.",
                        user = "Say ok.",
                        maxTokens = AiTimeouts.TEST_MAX_TOKENS,
                        cancelled = { destroyed }
                    )
                    ConnectionSummary.aiOk(model, SystemClock.elapsedRealtime() - started, reply)
                } catch (e: Exception) {
                    ConnectionSummary.failure(e)
                }
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    button.isEnabled = true
                    aiTestResult.text = message
                }
            }.start()
        }
        findViewById<Button>(R.id.aiClearCacheButton).setOnClickListener { button ->
            button.isEnabled = false
            Thread {
                val ok = try {
                    AiCache.clear(applicationContext)
                    true
                } catch (_: Exception) {
                    false
                }
                runOnUiThread {
                    if (destroyed) return@runOnUiThread
                    button.isEnabled = true
                    Toast.makeText(
                        this,
                        getString(if (ok) R.string.settings_ai_cleared else R.string.settings_ai_clear_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.start()
        }

        testResultView = findViewById(R.id.testResultView)
        findViewById<Button>(R.id.testConnectionButton).setOnClickListener {
            val url = urlView.text.toString().trim().ifBlank { Prefs.DEFAULT_URL }
            val key = keyView.text.toString().trim().ifBlank { held.apiKey }
            val deck = deckView.text.toString().trim().ifBlank { Prefs.DEFAULT_DECK }
            runConnectionCheck(url, key, deck)
        }

        testVoiceButton.setOnClickListener {
            if (testing) return@setOnClickListener
            if (rateCustomLayout.visibility == View.VISIBLE && !commitCustomRate(hideOnSuccess = false)) {
                return@setOnClickListener
            }
            val phrase = testVoiceInput.text.toString().trim().ifBlank { getString(R.string.test_voice_sample) }
            val rate = spokenRate
            val engine = selectedEngine(engineView)
            val deviceVoice = selectedDeviceVoice(voiceView)
            val neuralVoice = neuralVoiceView.text.toString().trim().ifBlank { NeuralTts.DEFAULT_VOICE }
            val neuralUrl = neuralUrlView.text.toString().trim().ifBlank { Prefs.DEFAULT_NEURAL_URL }
            val neuralToken = neuralTokenView.text.toString().trim().ifBlank { held.neuralToken }
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
        ready = true
    }

    override fun onPause() {
        if (ready) flushTexts()
        super.onPause()
    }

    /** Load, modify, save. Unchanged configs are left alone. */
    private fun persist(transform: (AnkiConfig) -> AnkiConfig): Boolean {
        if (!ready || destroyed) return false
        val before = try {
            Prefs.load(this)
        } catch (_: Exception) {
            return false
        }
        val proposed = try {
            transform(before)
        } catch (_: Exception) {
            return false
        }
        if (!AutoSave.decide(before, proposed).save) return false
        try {
            Prefs.save(this, proposed)
        } catch (_: Exception) {
            return false
        }
        held = try {
            Prefs.load(this)
        } catch (_: Exception) {
            proposed
        }
        if (before.url != held.url || before.apiKey != held.apiKey) {
            runConnectionCheck(held.url, held.apiKey, held.deck)
        }
        armSaved()
        return true
    }

    private fun armSaved() {
        val now = SystemClock.elapsedRealtime()
        feedback.onSaved(now)
        val due = feedback.deadline() ?: return
        uiHandler.removeCallbacks(showSavedRunnable)
        uiHandler.postDelayed(showSavedRunnable, (due - now).coerceAtLeast(0L))
    }

    private fun showSavedNotice() {
        if (destroyed || isFinishing) return
        val now = SystemClock.elapsedRealtime()
        val due = feedback.deadline() ?: return
        if (now < due) {
            uiHandler.postDelayed(showSavedRunnable, due - now)
            return
        }
        if (!feedback.take(now)) return
        val root = findViewById<View>(R.id.settingsScroll) ?: return
        Snackbar.make(root, R.string.settings_saved, Snackbar.LENGTH_SHORT).show()
    }

    private fun runConnectionCheck(url: String, key: String, deck: String) {
        if (destroyed || !::testResultView.isInitialized) return
        val gen = ++checkGen
        testResultView.text = "Testing…"
        if (key.isBlank()) {
            testResultView.text = getString(R.string.settings_key_missing)
            return
        }
        Thread {
            val text = try {
                val anki = AnkiConnect(url, key)
                val version = anki.version()
                val decks = anki.deckNames()
                val due = anki.dueCount(deck)
                ConnectionSummary.ankiOk(version, decks.size, deck, due)
            } catch (e: Exception) {
                ConnectionSummary.failure(e)
            }
            runOnUiThread {
                if (destroyed || gen != checkGen) return@runOnUiThread
                testResultView.text = text
            }
        }.start()
    }

    private fun loadDecks(
        urlView: TextInputEditText,
        keyView: TextInputEditText,
        deckView: MaterialAutoCompleteTextView,
        deckStatus: TextView
    ) {
        val url = urlView.text.toString().trim().ifBlank { Prefs.DEFAULT_URL }
        val key = keyView.text.toString().trim().ifBlank { held.apiKey }
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
        val token = tokenView.text.toString().trim().ifBlank { held.neuralToken }
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
        bindChoices(view, labels, selected) { label ->
            if (!voicesReady) return@bindChoices
            val name = deviceVoices.firstOrNull { it.label == label }?.name ?: return@bindChoices
            savedDeviceVoice = name
            persist { it.copy(ttsVoice = name) }
        }
    }

    private fun bindChoices(
        view: MaterialAutoCompleteTextView,
        labels: List<String>,
        selected: String,
        onPick: ((String) -> Unit)? = null
    ) {
        if (onPick != null) choiceSavers[view] = onPick
        val items = if (labels.isEmpty()) listOf(selected) else labels
        val saver = choiceSavers[view]
        bindingChoices = true
        try {
            view.setAdapter(StableAdapter(this, items))
            val text = when {
                selected.isNotBlank() && selected in items -> selected
                selected.isNotBlank() -> selected
                else -> items.first()
            }
            view.setText(text, false)
        } finally {
            bindingChoices = false
        }
        if (saver == null) return
        view.setOnItemClickListener { _, _, position, _ ->
            if (bindingChoices || position < 0) return@setOnItemClickListener
            val label = view.adapter?.getItem(position)?.toString() ?: return@setOnItemClickListener
            saver(label)
        }
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
        uiHandler.removeCallbacks(showSavedRunnable)
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
