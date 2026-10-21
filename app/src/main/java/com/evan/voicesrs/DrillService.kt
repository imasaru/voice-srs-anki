package com.evan.voicesrs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Foreground-service drill loop (hands-free, screen-off safe).
 * A session reads silent mode once, before startForeground. Silent sessions
 * use the media-playback type, speak the question and the answer, and wait
 * for taps. They do not create a recognizer or open the microphone.
 * Thin remote: Anki (via AnkiConnect) is the brain; this service only
 * drives the loop:
 *
 *   sync -> guiDeckReview -> per card:
 *     guiCurrentCard -> TTS question -> beep -> listen (answer discarded) ->
 *     guiShowAnswer -> TTS answer -> Grader listens for grade ->
 *     guiAnswerCard(ease) -> next card
 *   ... until guiCurrentCard is empty -> "Drill complete" -> sync -> stop.
 *
 * SpeechRecognizer is created on the main thread and destroyed after every
 * listen. Reusing one instance wedges it: the next startListening returns
 * ERROR_CLIENT immediately, and calling stopListening() from onError feeds
 * that error back into itself (a tight ERROR_CLIENT loop). Each session is a
 * new recognizer. ERROR_CLIENT and ERROR_RECOGNIZER_BUSY destroy it and wait
 * before one recreate. A silent NO_MATCH or SPEECH_TIMEOUT before speech
 * starts posts another recognizer for the same token until the listen
 * deadline. Neither path calls startListening from the error callback, and
 * nothing calls stopListening().
 *
 * The listen-cue tone is played and stopped BEFORE startListening so it
 * cannot leak into the open microphone.
 *
 * Threading: recognizer create/start/cancel/destroy run on the main thread.
 * A HandlerThread owns listen timeouts and TTS init. The drill blocks on a
 * session thread; recognizer callbacks arrive on main, so latch waits cannot
 * deadlock. Network calls run on the session thread.
 */
class DrillService : Service(), VoiceIo {

    companion object {
        const val ACTION_STOP = "com.evan.voicesrs.action.STOP"
        /** Abandon the in-flight session (usually still speaking an error) and start another. */
        const val ACTION_RETRY = "com.evan.voicesrs.action.RETRY"
        const val ACTION_TEST_GRADE = "com.evan.voicesrs.TEST_GRADE"
        const val ACTION_TEST_SHOW_ANSWER = "com.evan.voicesrs.TEST_SHOW_ANSWER"
        const val EXTRA_GRADE = "grade"
        const val CHANNEL_ID = "drill"
        const val NOTIF_ID = 1
        private const val TAG = "DrillService"
        private const val WAKELOCK_TIMEOUT_MS = 45 * 60 * 1000L
        private const val SPEAK_CAP_MS = 120_000L
        /** Tone length. The mic stays closed until [LISTEN_CUE_WAIT_MS] elapses. */
        private const val LISTEN_CUE_TONE_MS = 150
        private const val LISTEN_CUE_WAIT_MS = 400L
        /** One delayed recreate after CLIENT/BUSY. Not an immediate re-listen. */
        private const val CLIENT_BACKOFF_MS = 700L
        /** Latch wait past the listen deadline: client backoff plus main-thread slack. */
        private const val LISTEN_LATCH_SLACK_MS = CLIENT_BACKOFF_MS + 5_000L
        private const val NEXT_LISTEN_BACKOFF_MS = 1000L

        @Volatile var running = false
            private set

        /**
         * Process-wide queue of debug grade words. The debug receiver calls
         * [submitTestGrade]; release builds have no receiver, so this stays empty.
         */
        private val pendingGrades = ConcurrentLinkedQueue<String>()

        @Volatile private var active: DrillService? = null

        /** In-drill statuses that must not be left on screen once the session ends. */
        private val TRANSIENT_STATUS = setOf(
            "Checking Anki…", "Reading the question", "Answer", "Rate this card",
            "Listening for your answer", "Listening for your grade",
            "Tap to show the answer"
        )

        /** First grade per card wins (tap or voice). */
        val gradeGate = GradeGate()

        /**
         * Rating chip and debug [com.evan.voicesrs.TestGradeReceiver] hook.
         * The first claim after the answer is revealed wins. Speech in progress
         * is cut so the drill can grade from the tap without a prompt or hear-back.
         * A tap that lands in an open grade listen is still returned from [listen]
         * (logged `listen ok: TEST_GRADE`) but is not spoken back.
         */
        fun submitTestGrade(raw: String?) {
            val word = GradeMapper.canonicalGradeWord(raw)
            if (word == null) {
                Log.w(TAG, "TEST_GRADE ignored")
                return
            }
            val ease = GradeGate.easeOf(word)
            synchronized(gradeGate) {
                if (!gradeGate.tryClaim(ease)) {
                    Log.i(TAG, "TEST_GRADE ignored: no open card or already graded")
                    return
                }
                runCatching { DrillUi.update { it.copy(selectedEase = ease) } }
                pendingGrades.clear()
                pendingGrades.add(word)
            }
            // Claims only succeed once the answer is revealed, so the question is never cut.
            active?.interruptSpeech()
            Log.i(TAG, "tap interrupt speech")
            active?.releaseSilentGradeWait()
            // Deliver straight into an open recognizer listen when there is one.
            val svc = active
            if (svc != null && svc.deliverQueuedTap(word)) {
                Log.i(TAG, "TEST_GRADE delivered: $word")
                return
            }
            Log.i(TAG, "TEST_GRADE queued: $word")
        }

        /** Show answer button and [ACTION_TEST_SHOW_ANSWER]. Wakes a silent question wait. */
        fun submitShowAnswer() {
            val svc = active
            if (svc == null) {
                Log.i(TAG, "show answer ignored")
                return
            }
            svc.releaseShowAnswer()
        }
    }

    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private var tts: TextToSpeech? = null
    private val ttsReady = CountDownLatch(1)
    private var recognizer: SpeechRecognizer? = null
    /** Bumped on every destroy so late callbacks from the old instance are ignored. */
    private var recognizerGen = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var toneGen: ToneGenerator? = null
    private lateinit var mainHandler: Handler
    @Volatile private var recognitionAvailable = false
    @Volatile private var destroyed = false
    /** Captured once in [onStartCommand], before the session thread starts. */
    @Volatile private var sessionSilent = false

    private var sessionThread: Thread? = null
    private val stopRequested = AtomicBoolean(false)
    /** Set by [ACTION_RETRY] so the session thread loops instead of stopping the service. */
    private val pendingRestart = AtomicBoolean(false)
    /** Per utterance. [speak] clears it; a rating tap sets it so TTS stops. */
    @Volatile private var speechInterrupted = false
    /** This card was claimed by a tap. Cleared when that grade is taken or the next question starts. */
    @Volatile private var tapGrade = false
    /** The listen result in hand came from a tap, not the microphone. */
    @Volatile private var tapTranscript = false

    /** Grading path is pluggable: v1 self-grading, v2 (future) LLM judge. */
    var grader: Grader = VoiceSelfGrader()

    /** Diagnostic hear-back after every listen ("I heard: ..."). On in v0.1.3. */
    var verboseHearBack = true
    /** Tracks consecutive null listens; trips the fail-loud mic abort. Silent sessions never touch it. */
    private val listenHealth by lazy { ListenHealthTracker() }

    // Latches for the blocking VoiceIo calls (touched from worker/binder threads).
    @Volatile private var speakLatch: CountDownLatch? = null
    @Volatile private var listenLatch: CountDownLatch? = null
    private val showAnswerLatch = AtomicReference<CountDownLatch?>(null)
    private val silentGradeLatch = AtomicReference<CountDownLatch?>(null)
    private val listenResult = AtomicReference<String?>(null)
    @Volatile private var listenToken = 0
    @Volatile private var listenActive = false
    /** True while [grader] is inside grade(); TEST_GRADE applies only then. */
    @Volatile private var grading = false
    /** A TEST_GRADE already claimed the in-flight grade listen. */
    private var inFlightClaimed = false
    /** At most one delayed recreate per listen session. */
    private var clientRetryUsed = false
    /** True after onBeginningOfSpeech for this listen. Silence restarts require this to stay false. */
    @Volatile private var speechBegan = false
    private var silenceRestarts = 0
    private var listenDeadlineMs = 0L
    private var listenWaitMs = ListenWindow.DEFAULT_WAIT_SEC * 1000L
    private var listenSilenceMs = ListenWindow.DEFAULT_SILENCE_MS
    private var listenExtrasLogged = false
    /** End-of-speech silence from the settings snapshot taken at session start. */
    @Volatile private var configuredSilenceMs = ListenWindow.DEFAULT_SILENCE_MS
    private val errorGate = AtomicBoolean(false)
    private val injectLock = Any()
    /** elapsedRealtime deadline: do not open the mic before this. */
    private var backoffUntilElapsed = 0L
    /** Set after the first /health probe this session. */
    private var neuralHealthKnown = false
    /** Review log and explanation prefetch for this drill. Null outside a session. */
    private var explainSession: ExplainSession? = null
    /** True when the last probe failed. Speak stays on-device until a later probe passes. */
    private var neuralBlocked = false
    private var lastNeuralHealthAt = 0L

    // ---------- Service lifecycle ----------

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        pendingGrades.clear()
        active = this
        mainHandler = Handler(Looper.getMainLooper())
        worker = HandlerThread("drill-worker").apply { start() }
        handler = Handler(worker.looper)
        wakeLock = try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "voicesrs:drill").also {
                it.acquire(WAKELOCK_TIMEOUT_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "wake lock failed; continuing without it", e)
            null
        }
        toneGen = try { ToneGenerator(AudioManager.STREAM_MUSIC, 80) } catch (e: Exception) { null }

        handler.post {
            try {
                initTts()
            } catch (e: Exception) {
                // The worker thread dying would kill the whole process.
                Log.e(TAG, "TTS init failed", e)
                ttsReady.countDown()
            }
        }
    }

    /** Runs on the worker thread. Initializes TTS; must never throw out. */
    private fun initTts() {
        try {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    applyTtsPrefs()
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String) {}
                        override fun onDone(utteranceId: String) { speakLatch?.countDown() }
                        override fun onError(utteranceId: String) { speakLatch?.countDown() }
                        override fun onStop(utteranceId: String, interrupted: Boolean) {
                            speakLatch?.countDown()
                        }
                    })
                } else {
                    Log.e(TAG, "TTS init failed: $status")
                }
                ttsReady.countDown()
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS constructor failed", e)
            ttsReady.countDown()
        }
    }

    /** Rate and voice from [Prefs]. Call only after the engine reports SUCCESS. */
    private fun applyTtsPrefs() {
        val engine = tts ?: return
        val cfg = Prefs.load(this)
        val rate = TtsRate.clamp(cfg.ttsRate)
        engine.setSpeechRate(rate)
        val voiceName = cfg.ttsVoice.trim()
        if (voiceName.isEmpty()) {
            Log.i(TAG, "TTS rate=$rate voice=system default")
            return
        }
        val match = try {
            engine.voices?.firstOrNull { it.name == voiceName }
        } catch (e: Exception) {
            Log.w(TAG, "TTS voice list failed", e)
            null
        }
        if (match == null) {
            Log.w(TAG, "TTS voice not in engine; using system default")
            Log.i(TAG, "TTS rate=$rate voice=system default")
            return
        }
        val rc = try {
            engine.setVoice(match)
        } catch (e: Exception) {
            Log.w(TAG, "TTS setVoice failed", e)
            TextToSpeech.ERROR
        }
        Log.i(TAG, "TTS rate=$rate voiceSet=$rc")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=" + intent?.action)
        if (intent?.action == ACTION_STOP) {
            pendingRestart.set(false)
            requestStop()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_RETRY) {
            val restarting = synchronized(this) {
                if (sessionThread?.isAlive == true) {
                    pendingRestart.set(true)
                    stopRequested.set(true)
                    speechInterrupted = true
                    speakLatch?.countDown()
                    try { tts?.stop() } catch (_: Exception) {}
                    sessionThread?.interrupt()
                    Log.i(TAG, "retry: restarting session")
                    true
                } else {
                    false
                }
            }
            if (restarting) return START_NOT_STICKY
            Log.i(TAG, "retry: no session, starting")
        }
        // Main-thread service starts are serialized. Snapshot only when a new session will start,
        // so a settings change during a running drill waits until the next session.
        val starting = synchronized(this) { sessionThread?.isAlive != true }
        if (starting) {
            sessionSilent = try {
                Prefs.load(this).silentMode
            } catch (e: Exception) {
                Log.w(TAG, "prefs load failed; voice session", e)
                false
            }
        }
        try {
            startForegroundWithNotification()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed; stopping service", e)
            stopSelf()
            return START_NOT_STICKY
        }
        synchronized(this) {
            publishDrillRunning(true)
            if (sessionThread?.isAlive != true) {
                stopRequested.set(false)
                neuralHealthKnown = false
                neuralBlocked = false
                sessionThread = Thread(::runSession, "drill-session").also { it.start() }
            }
        }
        // NOT_STICKY: a startup crash must never resurrect-loop the service.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        val wasActive = active === this
        if (wasActive) active = null
        mainHandler.removeCallbacksAndMessages(null)
        requestStop()
        destroyRecognizerOnMain()
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        try { toneGen?.release() } catch (_: Exception) {}
        if (wakeLock?.isHeld == true) wakeLock?.release()
        handler.removeCallbacksAndMessages(null)
        worker.quitSafely()
        if (wasActive && (active == null || active === this)) publishDrillRunning(false)
        super.onDestroy()
    }

    private fun requestStop() {
        stopRequested.set(true)
        showAnswerLatch.get()?.countDown()
        silentGradeLatch.get()?.countDown()
        runCatching { explainSession?.stop() }
        try { EdgeSpeaker.stopActive() } catch (_: Exception) {}
        sessionThread?.interrupt()
        // Release any blocking VoiceIo waits so the session thread can exit.
        speakLatch?.countDown()
        finishListen(null)
        stopSelf()
    }

    // ---------- Foreground notification ----------

    private fun startForegroundWithNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Drill", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, DrillService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_stop), stopIntent)
            .build()
        val mode = SilentPolicy.of(sessionSilent)
        Log.i(TAG, "fgs type=${mode.fgsType.logName}")
        if (mode.fgsType == FgsType.MEDIA_PLAYBACK && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // ---------- Drill session (session thread) ----------

    private sealed class CardStep {
        data class Graded(
            val ease: Int,
            val elapsedMs: Long,
            val transcript: String = "",
            val answerHtml: String = "",
            /** Question HTML from the same card object as [answerHtml]. */
            val questionHtml: String = ""
        ) : CardStep()
        data object Skipped : CardStep()
        data object Paused : CardStep()
        data object Blank : CardStep()
        /** Reviewer card changed. The loop fetches guiCurrentCard again. */
        data object Resync : CardStep()
    }

    private sealed class Revealed {
        data class Answer(
            val questionHtml: String,
            val answerHtml: String,
            val buttons: List<Int>
        ) : Revealed()
        data object Changed : Revealed()
    }

    private fun publishDrillRunning(active: Boolean) {
        running = active
        runCatching {
            DrillUi.update {
                if (active) it.copy(drillRunning = true, silentSession = sessionSilent)
                else it.copy(drillRunning = false, progressLine = "", awaitingShowAnswer = false)
            }
        }
    }

    /**
     * GET /health before any clip is played, including cached ones.
     * A miss keeps the session on-device until [NeuralTts.HEALTH_RECHECK_MS] elapses
     * and a later probe succeeds. Logs "neural health ok" or "neural health fail".
     */
    private fun refreshNeuralHealth(cfg: AnkiConfig, force: Boolean) {
        val preferred = TtsEngines.preferred(cfg.ttsEngine)
        val due = NeuralTts.shouldProbeNeural(
            preferred,
            neuralHealthKnown,
            neuralBlocked,
            SystemClock.elapsedRealtime() - lastNeuralHealthAt
        )
        if (!force && !due) return
        if (preferred != EngineChoice.NEURAL) {
            neuralHealthKnown = true
            neuralBlocked = true
            showEngine(EngineLabel.of(preferred, false))
            return
        }
        if (!neuralHealthKnown) showEngine(EngineLabel.of(preferred, null))
        lastNeuralHealthAt = SystemClock.elapsedRealtime()
        val ok = try {
            EdgeSpeaker(this).healthy(cfg.neuralUrl, cfg.neuralToken, NeuralTts.HEALTH_TIMEOUT_MS)
        } catch (_: Exception) {
            false
        }
        neuralHealthKnown = true
        neuralBlocked = !ok
        if (NeuralTts.sessionEngine(preferred, ok) == EngineChoice.NEURAL) {
            Log.i(TAG, "neural health ok")
            showEngine(EngineLabel.of(preferred, true))
        } else {
            Log.i(TAG, "neural health fail")
            showEngine(EngineLabel.of(preferred, false))
        }
    }

    private fun useNeuralNow(cfg: AnkiConfig): Boolean {
        refreshNeuralHealth(cfg, force = false)
        return TtsEngines.preferred(cfg.ttsEngine) == EngineChoice.NEURAL && !neuralBlocked
    }

    private fun runSession() {
        while (!destroyed) {
            Thread.interrupted()
            stopRequested.set(false)
            pendingRestart.set(false)
            runSessionOnce()
            if (!pendingRestart.get() || destroyed) break
            Log.i(TAG, "retry: new session")
        }
    }

    private fun runSessionOnce() {
        Log.i(TAG, "runSession start")
        if (sessionSilent) Log.i(TAG, "silent mode: mic disabled")
        if (stopRequested.get()) return
        if (!sessionSilent) initRecognizerOnMain()
        publishDrillRunning(true)
        val store = LocalQueueStore(filesDir)
        try {
            if (!ttsReady.await(30, TimeUnit.SECONDS) || tts == null) {
                Log.e(TAG, "TTS not ready; aborting drill")
                return
            }
            val cfg = Prefs.load(this)
            explainSession = ExplainSession.start(this, cfg)
            configuredSilenceMs = ListenWindow.normalizeSilenceMs(cfg.endSilenceMs)
            refreshNeuralHealth(cfg, force = true)
            DrillUi.update {
                it.copy(
                    showError = false,
                    errorMessage = "",
                    status = "Checking Anki…",
                    progressLine = "",
                    pendingSync = store.readGrades().size
                )
            }
            if (cfg.apiKey.isBlank()) {
                val message = "No AnkiConnect API key is saved. Open Settings and enter it."
                showFailure(message)
                speak(message)
                return
            }
            val anki = AnkiConnect(cfg.url, cfg.apiKey)
            val report = anki.preflight(cfg.deck)
            if (!report.ok) {
                if (cfg.offlineBatch && store.hasBatch()) {
                    Log.i(TAG, "preflight failed; drilling saved batch")
                    announceRecognition()
                    speak("Anki is offline. Drilling saved cards.")
                    runOffline(cfg, anki, store, store.readBatch(), ankiUp = false)
                } else {
                    Log.i(TAG, "preflight failed")
                    showFailure(report.message)
                    speak(report.message)
                }
                return
            }
            DrillUi.update { it.copy(showError = false, errorMessage = "") }
            unsuspendSkipped(anki, store)
            val pendingLeft = GradeSync.sync(filesDir, anki, attempts = 2)
            DrillUi.update { it.copy(pendingSync = pendingLeft) }
            if (cfg.offlineBatch) {
                val cards = fetchBatch(anki, cfg, store)
                if (cards.isEmpty()) {
                    announceRecognition()
                    showStatus("No cards due"); speak("No cards were due. Drill complete.")
                    return
                }
                runOffline(cfg, anki, store, cards, ankiUp = true)
            } else {
                runOnline(cfg, anki, store)
            }
        } catch (e: InterruptedException) {
            Log.i(TAG, "session interrupted")
            Thread.currentThread().interrupt()
        } catch (e: MicDeadException) {
            // Fail-loud message already spoken in listen(); just end the drill.
            Log.e(TAG, "microphone judged dead; drill aborted")
        } catch (e: AnkiConnectException) {
            Log.e(TAG, "AnkiConnect failure", e)
            val message = "Connection problem. ${e.message}. Drill stopped."
            showFailure(message, title = "Drill stopped: can't reach Anki")
            speak(message)
        } catch (e: Exception) {
            Log.e(TAG, "session failure", e)
            val message = "Something went wrong. Drill stopped."
            showFailure(message, title = "Drill stopped")
            speak(message)
        } finally {
            runCatching { explainSession?.stop() }
            explainSession = null
            gradeGate.close()
            pendingGrades.clear()
            publishDrillRunning(false)
            runCatching {
                DrillUi.update {
                    val cleared = it.copy(
                        drillRunning = false,
                        ratingMicOpen = false,
                        progressLine = "",
                        ratingVisible = false,
                        selectedEase = null,
                        awaitingShowAnswer = false
                    )
                    when {
                        pendingRestart.get() -> cleared
                        stopRequested.get() -> cleared.copy(status = "Stopped")
                        it.status in TRANSIENT_STATUS -> cleared.copy(status = "Drill complete")
                        else -> cleared
                    }
                }
            }
            if (!pendingRestart.get()) stopSelf()
        }
    }

    private fun setGrading(value: Boolean) {
        synchronized(injectLock) { grading = value }
    }

    private fun showStatus(status: String) {
        runCatching { DrillUi.update { it.copy(status = status) } }
    }

    private fun showEngine(label: String) {
        runCatching { DrillUi.update { it.copy(engineLabel = label) } }
    }

    private fun showQuestionUi(questionHtml: String) = synchronized(gradeGate) {
        synchronized(injectLock) {
            pendingGrades.clear()
            gradeGate.newCard()
        }
        tapGrade = false
        tapTranscript = false
        runCatching {
            DrillUi.update {
                it.copy(
                    status = "Reading the question",
                    frontHtml = AnkiText.displayHtml(questionHtml),
                    backHtml = "",
                    showBack = false,
                    ratingVisible = false,
                    heard = "",
                    ratingMicOpen = false,
                    selectedEase = null,
                    awaitingShowAnswer = false
                )
            }
        }
    }

    private fun showAnswerUi(questionHtml: String, answerHtml: String) = synchronized(gradeGate) {
        synchronized(injectLock) {
            // Drop anything tapped while only the question was showing, then open grading.
            pendingGrades.clear()
            gradeGate.reveal()
        }
        runCatching {
            DrillUi.update {
                it.copy(
                    status = "Answer",
                    frontHtml = if (questionHtml.isBlank()) it.frontHtml else AnkiText.displayHtml(questionHtml),
                    backHtml = AnkiText.displayHtml(answerHtml),
                    showBack = true,
                    ratingVisible = true,
                    ratingMicOpen = false,
                    awaitingShowAnswer = false
                )
            }
        }
    }

    // ---------- VoiceIo (blocking; called from the session thread) ----------

    /**
     * Cut the utterance in progress. [TextToSpeech.stop] counts [speakLatch] down
     * via onStop. Neural playback sees [speechInterrupted] on its cancel poll.
     */
    fun interruptSpeech() {
        speechInterrupted = true
        tapGrade = true
        try { tts?.stop() } catch (_: Exception) {}
    }

    override fun speak(text: String) {
        speechInterrupted = false
        if (text.isBlank()) return
        when (text) {
            RatingGuides.LONG, RatingGuides.SHORT -> Log.i(TAG, "rating prompt spoken")
            RatingGuides.REPROMPT -> Log.i(TAG, "rating reprompt")
        }
        val cfg = try {
            Prefs.load(this)
        } catch (e: Exception) {
            Log.w(TAG, "prefs load failed during speak", e)
            if (speakOnDevice(text)) {
                Log.i(TAG, "TTS engine=on-device ok")
                showEngine(EngineLabel.of(EngineChoice.ON_DEVICE, false))
            }
            return
        }
        if (useNeuralNow(cfg)) {
            val ok = try {
                EdgeSpeaker(this).speakBlocking(
                    cfg.neuralUrl,
                    cfg.neuralToken,
                    cfg.neuralVoice,
                    cfg.ttsRate,
                    text
                ) { stopRequested.get() || speechInterrupted }
            } catch (e: Exception) {
                Log.w(TAG, "neural speak error")
                false
            }
            if (ok) {
                Log.i(TAG, "TTS engine=neural ok")
                showEngine(EngineLabel.of(EngineChoice.NEURAL, true))
                return
            }
            // A tap or Stop already ended this utterance. Do not replay it on-device.
            if (stopRequested.get() || speechInterrupted) return
            if (speakOnDevice(text)) {
                Log.i(TAG, "TTS engine=neural fail; fallback on-device ok")
                showEngine(EngineLabel.of(EngineChoice.NEURAL, false))
            } else {
                Log.w(TAG, "TTS engine=neural fail; fallback on-device failed")
            }
            return
        }
        if (speakOnDevice(text)) {
            Log.i(TAG, "TTS engine=on-device ok")
            showEngine(EngineLabel.of(EngineChoice.ON_DEVICE, false))
        } else {
            Log.w(TAG, "TTS engine=on-device failed")
        }
    }

    /** On-device utterance. Blocks until the engine finishes, same as before. */
    private fun speakOnDevice(text: String): Boolean {
        val t = tts ?: return false
        try {
            t.setSpeechRate(TtsRate.clamp(Prefs.load(this).ttsRate))
        } catch (_: Exception) {
        }
        val latch = CountDownLatch(1)
        speakLatch = latch
        try {
            if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
                != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS speak rejected")
                return false
            }
            // Close the gap where the tap landed after the flag reset and before playback.
            if (speechInterrupted || stopRequested.get()) {
                try { t.stop() } catch (_: Exception) {}
            }
            latch.await(SPEAK_CAP_MS, TimeUnit.MILLISECONDS)
            return true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        } finally {
            speakLatch = null
        }
    }

    override fun stopped(): Boolean = stopRequested.get() || destroyed

    override fun listen(timeoutMs: Long): String? {
        if (sessionSilent) return null
        tapTranscript = false
        val result = captureListen(timeoutMs)
        // A tap word is not a mic sample: no "I heard" and no dead-mic streak change.
        if (tapTranscript) {
            tapTranscript = false
            return result
        }
        return afterListen(result)
    }

    /**
     * Answer phase. A null window can prompt once and listen again. The pair is
     * one dead-mic sample, so a quiet think plus the retry does not abort early.
     */
    private fun listenForAnswer(timeoutMs: Long, relistenOnce: Boolean, speedMode: Boolean): String? {
        val first = captureListen(timeoutMs)
        val didRelisten = ListenWindow.shouldRelistenAnswer(first, relistenOnce, stopped(), speedMode)
        val second = if (!didRelisten) {
            null
        } else {
            Log.i(TAG, "relisten answer")
            speak(ListenWindow.ANSWER_RELISTEN_CUE)
            if (stopped()) {
                null
            } else {
                beep()
                captureListen(timeoutMs)
            }
        }
        return afterListen(ListenWindow.healthSample(first, second, didRelisten))
    }

    private fun captureListen(timeoutMs: Long): String? {
        val scripted = SpeechHooks.scripted
        val result = if (scripted != null && scripted.enabled()) {
            // Poll in short slices so a chip tap (queued grade) is honoured during a scripted listen,
            // the same way the real recognizer path accepts it.
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            var text: String? = null
            while (text == null) {
                consumeQueuedGrade()?.let { text = it }
                if (text != null || stopRequested.get() || destroyed) break
                val left = deadline - SystemClock.elapsedRealtime()
                if (left <= 0) break
                text = scripted.poll(minOf(left, 250L))
                if (text != null) Log.i(TAG, "listen ok: SCRIPT")
            }
            if (text == null) Log.i(TAG, "listen ok: SCRIPT_EMPTY")
            text
        } else {
            consumeQueuedGrade() ?: listenWithRecognizer(timeoutMs)
        }
        if (!result.isNullOrBlank()) {
            val action = GradeMapper.parse(result)
            logAction(action)
            if (action is VoiceAction.Grade && grading) {
                // Voice grade: claim the card and highlight its chip. A tap that came first wins.
                val used = gradeGate.resolveVoice(action.ease)
                if (used == null) {
                    Log.i(TAG, "voice grade ignored: answer not revealed")
                    return result
                }
                runCatching { DrillUi.update { it.copy(selectedEase = used) } }
                Log.i(TAG, "grade chip highlighted ease=$used")
                if (used != action.ease) {
                    Log.i(TAG, "voice grade ease=${action.ease} ignored; tapped ease=$used wins")
                    pendingGrades.clear()
                    return GradeGate.wordOf(used)
                }
            }
        }
        return result
    }

    private fun logAction(action: VoiceAction) {
        val name = when (action) {
            is VoiceAction.Grade -> "grade ease=${action.ease}"
            VoiceAction.Repeat -> "repeat"
            VoiceAction.Skip -> "skip"
            VoiceAction.Pause -> "pause"
            VoiceAction.None -> "none"
        }
        Log.i(TAG, "voice action: $name")
    }

    private fun announceRecognition() {
        if (sessionSilent) return
        val scripted = SpeechHooks.scripted?.enabled() == true
        if (scripted) Log.i(TAG, "scripted recognizer enabled")
        if (recognitionAvailable || scripted) {
            speak("Voice recognition ready.")
        } else {
            speak("Warning: speech recognition is not available on this phone. " +
                    "Cards will be read aloud, but I cannot hear your grades.")
        }
    }

    private fun showFailure(message: String, title: String = "") {
        DrillUi.update {
            it.copy(
                showError = true,
                errorMessage = message,
                errorTitle = title,
                status = "Anki offline",
                ratingMicOpen = false
            )
        }
    }

    private fun publish(progress: SessionProgress, store: LocalQueueStore) {
        DrillUi.update {
            it.copy(
                progressLine = progress.line(),
                pendingSync = store.readGrades().size,
                showError = false
            )
        }
    }

    private fun finishGoal(progress: SessionProgress, startedElapsed: Long) {
        val summary = progress.spokenSummary(SystemClock.elapsedRealtime() - startedElapsed)
        Log.i(TAG, "goal reached")
        showStatus("Goal reached")
        speak(summary)
    }

    private fun stopFromSession() {
        stopRequested.set(true)
        try { EdgeSpeaker.stopActive() } catch (_: Exception) {}
    }

    private fun fetchBatch(anki: AnkiConnect, cfg: AnkiConfig, store: LocalQueueStore): List<OfflineCard> {
        val ids = anki.dueCardIds(cfg.deck, cfg.offlineBatchSize)
        if (ids.isEmpty()) {
            store.writeBatch(emptyList())
            return emptyList()
        }
        val cards = anki.cardsInfo(ids)
        store.writeBatch(cards)
        Log.i(TAG, "offline batch ${cards.size}")
        return cards
    }

    private fun unsuspendSkipped(anki: AnkiConnect, store: LocalQueueStore) {
        val ids = store.readSkipped()
        if (ids.isEmpty()) return
        try {
            anki.unsuspendCards(ids)
            store.writeSkipped(emptyList())
            Log.i(TAG, "unsuspended skipped cards: ${ids.size}")
        } catch (e: Exception) {
            Log.w(TAG, "unsuspend failed: ${e.javaClass.simpleName}")
        }
    }

    /** Reveal with guiShowAnswer, then leave the card ungraded and move on. */
    private fun skipOnline(anki: AnkiConnect, deck: String, card: AnkiCard, store: LocalQueueStore) {
        Log.i(TAG, "skip card id=${card.cardId}")
        speak("Skipped.")
        runCatching { anki.guiShowAnswer() }
        if (card.cardId <= 0L) return
        val skipped = store.readSkipped().toMutableList()
        if (card.cardId !in skipped) skipped += card.cardId
        store.writeSkipped(skipped)
        runCatching { anki.suspendCards(listOf(card.cardId)) }
        runCatching { anki.guiDeckReview(deck) }
    }

    private fun runOnline(cfg: AnkiConfig, anki: AnkiConnect, store: LocalQueueStore) {
        announceRecognition()
        speak("Connecting to Anki.")
        anki.sync()
        anki.guiDeckReview(cfg.deck)
        val due = try {
            anki.dueCount(cfg.deck)
        } catch (_: Exception) {
            0
        }
        val progress = SessionProgress(due)
        val started = SystemClock.elapsedRealtime()
        publish(progress, store)
        var lastSkipId = 0L
        var goalSpoken = false
        var resyncs = 0
        var resyncHalt = false
        while (!stopRequested.get()) {
            val elapsed = SystemClock.elapsedRealtime() - started
            if (!goalSpoken && progress.goalReached(cfg.goalMode, cfg.goalCards, cfg.goalMinutes, elapsed)) {
                finishGoal(progress, started)
                goalSpoken = true
                break
            }
            val card = anki.guiCurrentCard() ?: break
            if (card.cardId != 0L && card.cardId == lastSkipId) {
                speak("Couldn't skip that card. Drill stopped.")
                break
            }
            anki.guiShowQuestion()
            val step = drillOne(
                card.questionHtml,
                progress.done,
                cfg.speedMode,
                card.buttons,
                ListenWindow.waitMs(cfg.listenWaitSec),
                cfg.relistenOnce
            ) {
                anki.guiShowAnswer()
                val shown = anki.guiCurrentCard()
                val same = CardIdentity.sameCard(card.cardId, shown)
                if (same == null) {
                    Log.w(TAG, CardIdentity.mismatchLog(card.cardId, shown?.cardId))
                    Revealed.Changed
                } else {
                    Revealed.Answer(same.questionHtml, same.answerHtml, same.buttons)
                }
            }
            when (step) {
                CardStep.Paused -> {
                    unsuspendSkipped(anki, store)
                    return
                }
                CardStep.Resync -> {
                    resyncs += 1
                    if (CardIdentity.shouldStop(resyncs)) {
                        val message = "The card on screen changed. Drill stopped."
                        Log.w(TAG, message)
                        speak(message)
                        resyncHalt = true
                        break
                    }
                }
                CardStep.Blank -> {
                    Log.w(TAG, "empty question; marking good")
                    if (!stillCurrent(anki, card)) {
                        resyncs += 1
                        if (CardIdentity.shouldStop(resyncs)) {
                            val message = "The card on screen changed. Drill stopped."
                            Log.w(TAG, message)
                            speak(message)
                            resyncHalt = true
                            break
                        }
                    } else {
                        anki.guiAnswerCard(GradeMapper.toAvailableEase(3, card.buttons))
                        resyncs = 0
                        runCatching {
                            explainSession?.noteSeen(card.cardId)
                            explainSession?.prefetchOnline(anki)
                        }
                    }
                }
                CardStep.Skipped -> {
                    lastSkipId = card.cardId
                    skipOnline(anki, cfg.deck, card, store)
                    resyncs = 0
                    runCatching {
                        explainSession?.noteSeen(card.cardId)
                        explainSession?.prefetchOnline(anki)
                    }
                }
                is CardStep.Graded -> {
                    lastSkipId = 0L
                    if (!stillCurrent(anki, card)) {
                        resyncs += 1
                        if (CardIdentity.shouldStop(resyncs)) {
                            val message = "The card on screen changed. Drill stopped."
                            Log.w(TAG, message)
                            speak(message)
                            resyncHalt = true
                            break
                        }
                    } else {
                        anki.guiAnswerCard(step.ease)
                        resyncs = 0
                        // Ease is applied first. recordOnline calls cardsInfo and must not run before the grade.
                        runCatching {
                            explainSession?.recordOnline(
                                anki,
                                card.cardId,
                                step.questionHtml.ifBlank { card.questionHtml },
                                step.answerHtml,
                                step.transcript,
                                step.ease
                            )
                        }
                        progress.recordGrade(step.ease, step.elapsedMs)
                        publish(progress, store)
                        Log.i(TAG, "graded ease=${step.ease}")
                        runCatching { explainSession?.prefetchOnline(anki) }
                    }
                }
            }
            val after = SystemClock.elapsedRealtime() - started
            if (!goalSpoken && progress.goalReached(cfg.goalMode, cfg.goalCards, cfg.goalMinutes, after)) {
                finishGoal(progress, started)
                goalSpoken = true
                break
            }
        }
        unsuspendSkipped(anki, store)
        if (!stopRequested.get() && !goalSpoken && !resyncHalt) {
            val summary = if (progress.done == 0) {
                showStatus("No cards due")
                "No cards were due. Drill complete."
            } else {
                progress.spokenSummary(SystemClock.elapsedRealtime() - started)
            }
            speak(summary)
            runCatching { anki.sync() }
        }
    }

    /** True when the desktop reviewer still has [card]. Logs and returns false on a change. */
    private fun stillCurrent(anki: AnkiConnect, card: AnkiCard): Boolean {
        val current = anki.guiCurrentCard()
        if (CardIdentity.beforeGrade(card.cardId, current?.cardId) == CardIdentity.Verdict.PROCEED) {
            return true
        }
        Log.w(TAG, CardIdentity.mismatchLog(card.cardId, current?.cardId))
        return false
    }

    private fun runOffline(
        cfg: AnkiConfig,
        anki: AnkiConnect,
        store: LocalQueueStore,
        cards: List<OfflineCard>,
        ankiUp: Boolean
    ) {
        announceRecognition()
        val progress = SessionProgress(cards.size)
        val started = SystemClock.elapsedRealtime()
        publish(progress, store)
        var goalSpoken = false
        for ((index, card) in cards.withIndex()) {
            if (stopRequested.get()) break
            val elapsed = SystemClock.elapsedRealtime() - started
            if (progress.goalReached(cfg.goalMode, cfg.goalCards, cfg.goalMinutes, elapsed)) {
                finishGoal(progress, started)
                goalSpoken = true
                break
            }
            val step = drillOne(
                card.front,
                progress.done,
                cfg.speedMode,
                card.buttons,
                ListenWindow.waitMs(cfg.listenWaitSec),
                cfg.relistenOnce
            ) {
                Revealed.Answer(card.front, card.back, card.buttons)
            }
            when (step) {
                CardStep.Paused -> return
                CardStep.Resync -> Log.w(TAG, "offline resync ignored")
                CardStep.Blank -> {
                    Log.w(TAG, "offline card had an empty question")
                    runCatching {
                        explainSession?.noteSeen(card.cardId)
                        explainSession?.prefetchOffline(cards, index)
                    }
                }
                CardStep.Skipped -> {
                    Log.i(TAG, "offline skip id=${card.cardId}")
                    speak("Skipped.")
                    runCatching {
                        explainSession?.noteSeen(card.cardId)
                        explainSession?.prefetchOffline(cards, index)
                    }
                }
                is CardStep.Graded -> {
                    runCatching { explainSession?.recordOffline(card, step.transcript, step.ease) }
                    store.enqueueGrade(PendingGrade(card.cardId, step.ease, System.currentTimeMillis()))
                    progress.recordGrade(step.ease, step.elapsedMs)
                    publish(progress, store)
                    Log.i(TAG, "queued ease=${step.ease}")
                    runCatching { explainSession?.prefetchOffline(cards, index) }
                }
            }
        }
        if (!stopRequested.get() && !goalSpoken) {
            val summary = if (progress.done == 0) {
                showStatus("No cards due")
                "No cards were due. Drill complete."
            } else {
                progress.spokenSummary(SystemClock.elapsedRealtime() - started)
            }
            speak(summary)
        }
        val left = if (ankiUp) {
            GradeSync.sync(filesDir, anki, attempts = 4)
        } else {
            store.readGrades().size
        }
        DrillUi.update { it.copy(pendingSync = left) }
    }

    /**
     * Question, optional commands, answer, then a grade.
     * [reveal] runs once the spoken answer is about to be shown. Online mode
     * uses it to call guiShowAnswer and keep the question and answer on one card.
     * [Revealed.Changed] does not speak or show the other card's answer.
     */
    private fun drillOne(
        questionHtml: String,
        cardIndex: Int,
        speedMode: Boolean,
        buttons: List<Int>,
        listenWaitMs: Long,
        relistenOnce: Boolean,
        reveal: () -> Revealed
    ): CardStep {
        val question = AnkiText.spokenText(questionHtml)
        if (question.isBlank()) return CardStep.Blank
        val started = SystemClock.elapsedRealtime()
        showQuestionUi(questionHtml)
        var repeats = 0
        var transcript = ""
        if (!sessionSilent) {
            while (!stopRequested.get()) {
                speak(question)
                if (stopRequested.get()) return CardStep.Paused
                showStatus("Listening for your answer")
                beep()
                val heard = listenForAnswer(listenWaitMs, relistenOnce, speedMode)
                when (val action = GradeMapper.parse(heard)) {
                    VoiceAction.Repeat -> {
                        repeats++
                        if (repeats >= 8) break
                        continue
                    }
                    VoiceAction.Skip -> return CardStep.Skipped
                    VoiceAction.Pause -> {
                        stopFromSession()
                        return CardStep.Paused
                    }
                    else -> {
                        transcript = heard?.trim().orEmpty()
                        break
                    }
                }
            }
        } else {
            speak(question)
            if (stopRequested.get()) return CardStep.Paused
            if (!awaitShowAnswer()) return CardStep.Paused
        }
        if (stopRequested.get()) return CardStep.Paused
        val revealed = reveal()
        if (revealed is Revealed.Changed) return CardStep.Resync
        val answer = revealed as Revealed.Answer
        val answerHtml = answer.answerHtml
        val answerButtons = answer.buttons
        val answerBody = AnkiText.answerOnly(answerHtml)
        val answerText = AnkiText.spokenText(answerBody)
        Log.d(TAG, "tts answer: $answerText")
        showAnswerUi(answer.questionHtml, answerBody)
        if (answerText.isNotBlank()) speak(answerText)
        if (stopRequested.get()) return CardStep.Paused
        val gradeButtons = answerButtons.ifEmpty { buttons }
        val tapped = synchronized(gradeGate) {
            if (gradeGate.isClaimed()) gradeGate.ease() else null
        }
        if (tapped != null) {
            return gradedByTap(tapped, gradeButtons, started, transcript, answerHtml, answer.questionHtml)
        }
        if (sessionSilent) {
            showStatus("Rate this card")
            val ease = awaitSilentGrade()
            if (ease == null || stopRequested.get()) return CardStep.Paused
            return gradedByTap(ease, gradeButtons, started, transcript, answerHtml, answer.questionHtml)
        }
        showStatus("Rate this card")
        setGrading(true)
        return try {
            val ease = grader.grade(
                GradeContext(
                    AnkiCard(answer.questionHtml.ifBlank { questionHtml }, answerBody, "", gradeButtons),
                    answerText,
                    cardIndex = cardIndex,
                    speedMode = speedMode,
                    listenTimeoutMs = listenWaitMs,
                    claimedEase = {
                        synchronized(gradeGate) {
                            if (gradeGate.isClaimed()) gradeGate.ease() else null
                        }
                    }
                ),
                this
            )
            if (tapGrade) {
                val raw = synchronized(gradeGate) { gradeGate.ease() } ?: ease
                return gradedByTap(raw, gradeButtons, started, transcript, answerHtml, answer.questionHtml)
            }
            CardStep.Graded(
                ease,
                SystemClock.elapsedRealtime() - started,
                transcript,
                answerHtml,
                answer.questionHtml
            )
        } catch (_: SkipRequested) {
            CardStep.Skipped
        } catch (_: PauseRequested) {
            stopFromSession()
            CardStep.Paused
        } catch (e: InterruptedException) {
            throw e
        } catch (e: MicDeadException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "grader failed; defaulting to Good", e)
            CardStep.Graded(
                GradeMapper.toAvailableEase(3, answerButtons.ifEmpty { buttons }),
                SystemClock.elapsedRealtime() - started,
                transcript,
                answerHtml,
                answer.questionHtml
            )
        } finally {
            setGrading(false)
        }
    }

    /** Block until Show answer, or Stop. The button and TEST_SHOW_ANSWER share this latch. */
    private fun awaitShowAnswer(): Boolean {
        val latch = CountDownLatch(1)
        showAnswerLatch.set(latch)
        return try {
            Log.i(TAG, "waiting for show answer")
            runCatching {
                DrillUi.update { it.copy(awaitingShowAnswer = true, status = "Tap to show the answer") }
            }
            while (!stopRequested.get() && !destroyed) {
                try {
                    if (latch.await(250, TimeUnit.MILLISECONDS)) {
                        return !stopRequested.get() && !destroyed
                    }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
            false
        } finally {
            showAnswerLatch.compareAndSet(latch, null)
            runCatching { DrillUi.update { it.copy(awaitingShowAnswer = false) } }
        }
    }

    /**
     * Block until a grade chip claims the card, or Stop.
     * No spoken prompt, no listen, and no Good default.
     */
    private fun awaitSilentGrade(): Int? {
        val latch = CountDownLatch(1)
        silentGradeLatch.set(latch)
        return try {
            val already = synchronized(gradeGate) {
                if (gradeGate.isClaimed()) gradeGate.ease() else null
            }
            if (already != null) return already
            Log.i(TAG, "waiting for grade")
            while (!stopRequested.get() && !destroyed) {
                try {
                    if (latch.await(250, TimeUnit.MILLISECONDS)) break
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
            if (stopRequested.get() || destroyed) return null
            synchronized(gradeGate) {
                if (gradeGate.isClaimed()) gradeGate.ease() else null
            }
        } finally {
            silentGradeLatch.compareAndSet(latch, null)
        }
    }

    private fun releaseShowAnswer() {
        val latch = showAnswerLatch.get()
        if (latch == null) {
            Log.i(TAG, "show answer ignored")
            return
        }
        Log.i(TAG, "show answer")
        latch.countDown()
    }

    private fun releaseSilentGradeWait() {
        silentGradeLatch.get()?.countDown()
    }

    /**
     * One [CardStep.Graded] for a tap. Logs `graded by tap` here; the caller still
     * logs `graded ease=` once. Drops the queued word so it cannot leak forward.
     */
    private fun gradedByTap(
        rawEase: Int,
        buttons: List<Int>,
        startedElapsed: Long,
        transcript: String,
        answerHtml: String,
        questionHtml: String = ""
    ): CardStep.Graded {
        val ease = GradeMapper.toAvailableEase(rawEase, buttons)
        Log.i(TAG, "graded by tap ease=$ease")
        tapGrade = false
        synchronized(gradeGate) {
            synchronized(injectLock) { pendingGrades.clear() }
        }
        return CardStep.Graded(
            ease,
            SystemClock.elapsedRealtime() - startedElapsed,
            transcript,
            answerHtml,
            questionHtml
        )
    }

    /** Hear-back + dead-mic accounting for microphone transcripts. Tap words skip this. */
    private fun afterListen(result: String?): String? {
        if (sessionSilent) return result
        if (stopRequested.get()) return result
        val speed = try { Prefs.load(this).speedMode } catch (_: Exception) { false }
        if (verboseHearBack && !speed) {
            val line = if (result.isNullOrBlank()) "I heard nothing." else "I heard: $result"
            runCatching { DrillUi.update { it.copy(heard = line) } }
            speak(line)
        } else if (speed) {
            runCatching { DrillUi.update { it.copy(heard = "") } }
        }
        if (listenHealth.recordHeard(result)) {
            speak("The microphone does not seem to be working. " +
                    "Please check the microphone permission and your connection, then restart the drill.")
            throw MicDeadException()
        }
        return result
    }

    /**
     * Pull a debug grade if we are inside the grader. Logs [listen ok: TEST_GRADE]
     * so the harness can count it as a completed listen on the grading path.
     */
    private fun consumeQueuedGrade(): String? {
        val word = synchronized(injectLock) {
            if (!grading) null else pendingGrades.poll()
        } ?: return null
        return noteTapTranscript(word)
    }

    /** Mark [word] as a tap so [listen] does not hear it back or touch the dead-mic streak. */
    private fun noteTapTranscript(word: String): String {
        tapTranscript = true
        Log.i(TAG, "listen ok: TEST_GRADE")
        return word
    }

    private fun listenWithRecognizer(timeoutMs: Long): String? {
        if (sessionSilent) return null
        if (!awaitBackoff()) return null
        if (stopRequested.get()) return null
        // Cue finishes (and is stopped) before the microphone opens.
        toneListenStart()
        if (!sleepQuiet(LISTEN_CUE_WAIT_MS)) return null
        try { toneGen?.stopTone() } catch (_: Exception) {}
        if (stopRequested.get() || destroyed) return null
        consumeQueuedGrade()?.let { return it }
        if (!recognitionAvailable) {
            Log.w(TAG, "no recognizer; skipping listen")
            return null
        }

        val latch = CountDownLatch(1)
        val openedAt = SystemClock.elapsedRealtime()
        val token = synchronized(injectLock) {
            val queued = if (grading) pendingGrades.poll() else null
            if (queued != null) {
                return noteTapTranscript(queued)
            }
            listenLatch = latch
            listenResult.set(null)
            listenActive = true
            inFlightClaimed = false
            clientRetryUsed = false
            errorGate.set(false)
            speechBegan = false
            silenceRestarts = 0
            listenExtrasLogged = false
            listenWaitMs = timeoutMs
            listenSilenceMs = configuredSilenceMs
            listenDeadlineMs = openedAt + timeoutMs
            ++listenToken
        }
        val delay = (listenDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        handler.postDelayed({
            if (token == listenToken) finishListen(null)
        }, delay)
        mainHandler.post { beginListenOnMain(token) }
        try {
            if (!latch.await(delay + LISTEN_LATCH_SLACK_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "listen latch timed out")
                finishListen(null)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            finishListen(null)
        }
        return listenResult.get()
    }

    /** Main thread. The listen token is still the one [beginListenOnMain] was given. */
    private fun sessionOpen(session: Int): Boolean {
        if (destroyed || stopRequested.get()) return false
        synchronized(injectLock) {
            return listenActive && session == listenToken && !inFlightClaimed
        }
    }

    /**
     * A tap that landed in the queue during a restart gap, before the mic opens again.
     * Null when grading is closed or another delivery already claimed this listen.
     */
    private fun pollQueuedGrade(): String? {
        synchronized(injectLock) {
            if (!grading || !listenActive || inFlightClaimed) return null
            return pendingGrades.poll()
        }
    }

    /** @return false if the drill was stopped while waiting. */
    private fun awaitBackoff(): Boolean {
        val wait = backoffUntilElapsed - SystemClock.elapsedRealtime()
        if (wait <= 0L) return true
        Log.i(TAG, "backing off ${wait}ms before listen")
        return sleepQuiet(wait)
    }

    private fun sleepQuiet(ms: Long): Boolean {
        return try {
            Thread.sleep(ms)
            true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    override fun beep() {
        try { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 180) } catch (_: Exception) {}
    }

    /** Short HIGH tone. Played only while the recognizer is NOT listening. */
    private fun toneListenStart() {
        try { toneGen?.startTone(ToneGenerator.TONE_PROP_PROMPT, LISTEN_CUE_TONE_MS) } catch (_: Exception) {}
    }

    /** Short LOW tone: the listen just ended (result, error, or timeout). */
    private fun toneListenEnd() {
        try { toneGen?.startTone(ToneGenerator.TONE_PROP_NACK, 150) } catch (_: Exception) {}
    }

    /**
     * Completes the in-flight listen. Hops to the main thread, then destroys
     * the recognizer. Idempotent: a second call (re-entrant error, timeout
     * after result) does nothing and does not touch the recognizer again.
     */
    private fun finishListen(text: String?) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // Block until main applies the result. Otherwise the session thread
            // can observe a stale transcript and leave the recognizer alive.
            val done = CountDownLatch(1)
            mainHandler.post {
                finishListen(text)
                done.countDown()
            }
            try {
                done.await(2, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return
        }
        val latch: CountDownLatch? = synchronized(injectLock) {
            if (!listenActive) return
            // A debug grade already claimed this listen; let that delivery finish it.
            if (text == null && inFlightClaimed) return
            listenActive = false
            listenToken++
            if (text != null || !inFlightClaimed) listenResult.set(text)
            val pending = listenLatch
            listenLatch = null
            pending
        }
        errorGate.set(false)
        destroyRecognizerOnMain()
        latch?.countDown()
        toneListenEnd()
        runCatching { DrillUi.update { it.copy(ratingMicOpen = false) } }
    }

    /**
     * If a grade listen is blocked in the recognizer, complete it with [word]
     * instead of queueing. Returns false when the caller should queue [word].
     */
    /** A tap is already in [pendingGrades]; move it into the open listen if one is waiting. */
    private fun deliverQueuedTap(word: String): Boolean {
        synchronized(injectLock) {
            if (!(grading && listenActive) || inFlightClaimed) return false
            if (!pendingGrades.remove(word)) return false
        }
        if (deliverTestGrade(word)) return true
        pendingGrades.add(word)
        return false
    }

    private fun deliverTestGrade(word: String): Boolean {
        synchronized(injectLock) {
            if (!(grading && listenActive) || inFlightClaimed) return false
            inFlightClaimed = true
            listenResult.set(word)
        }
        noteTapTranscript(word)
        mainHandler.post { finishListen(word) }
        return true
    }

    /** Main thread. Drops any previous recognizer and starts a new session. */
    private fun beginListenOnMain(session: Int) {
        if (sessionSilent) return
        if (!sessionOpen(session)) return
        val queued = pollQueuedGrade()
        if (queued != null) {
            if (!deliverTestGrade(queued)) pendingGrades.add(queued)
            return
        }
        if (!sessionOpen(session)) return
        if (grading) {
            runCatching {
                DrillUi.update { it.copy(ratingMicOpen = true, status = "Listening for your grade") }
            }
        }
        errorGate.set(false)
        if (!createRecognizerOnMain(session)) {
            finishListen(null)
            return
        }
        val waitMs = listenWaitMs
        val silenceMs = listenSilenceMs.toLong()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
        }
        if (!listenExtrasLogged) {
            listenExtrasLogged = true
            Log.i(TAG, "listen extras wait=$waitMs silence=$silenceMs")
        }
        try {
            recognizer?.startListening(intent)
            Log.i(TAG, "startListening")
        } catch (e: Exception) {
            Log.w(TAG, "startListening failed", e)
            finishListen(null)
        }
    }

    /** Posted from onError. Same token, no cue. A queued tap in the gap wins over the new mic. */
    private fun restartSilenceListen(session: Int) {
        mainHandler.post {
            if (!sessionOpen(session)) return@post
            val left = listenDeadlineMs - SystemClock.elapsedRealtime()
            if (left < ListenWindow.MIN_REMAINING_MS) {
                finishListen(null)
                return@post
            }
            destroyRecognizerOnMain()
            if (!sessionOpen(session)) return@post
            beginListenOnMain(session)
        }
    }

    /**
     * Availability only, on the main thread. Voice sessions call this once at start.
     * The recognizer itself is created later, per listen.
     */
    private fun initRecognizerOnMain() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            probeRecognizer()
            return
        }
        val done = CountDownLatch(1)
        mainHandler.post {
            try {
                probeRecognizer()
            } finally {
                done.countDown()
            }
        }
        try {
            done.await(5, TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun probeRecognizer() {
        recognitionAvailable = try {
            SpeechRecognizer.isRecognitionAvailable(this)
        } catch (e: Exception) {
            Log.w(TAG, "recognizer availability check failed", e)
            false
        }
        if (recognitionAvailable) {
            Log.i(TAG, "SpeechRecognizer available")
        } else {
            Log.w(TAG, "SpeechRecognizer not available on this device")
        }
    }

    /** Main thread. Fresh instance; the previous one is destroyed first. */
    private fun createRecognizerOnMain(session: Int): Boolean {
        if (sessionSilent || !SilentPolicy.of(sessionSilent).createRecognizer) return false
        destroyRecognizerOnMain()
        if (destroyed || !listenActive || session != listenToken) return false
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer not available on this device")
            recognitionAvailable = false
            return false
        }
        return try {
            val gen = recognizerGen
            val created = SpeechRecognizer.createSpeechRecognizer(this)
            created.setRecognitionListener(SpeechListener(session, gen))
            recognizer = created
            Log.i(TAG, "recognizer created")
            Log.i(TAG, "SpeechRecognizer created for listen session")
            true
        } catch (e: Exception) {
            Log.w(TAG, "recognizer creation failed", e)
            recognizer = null
            false
        }
    }

    /**
     * Main thread. Detach the listener before cancel/destroy so the framework
     * cannot deliver another onError into this session. Never calls
     * stopListening(): that re-enters onError(ERROR_CLIENT) in a tight loop.
     */
    private fun destroyRecognizerOnMain() {
        recognizerGen++
        val dying = recognizer ?: return
        recognizer = null
        try { dying.setRecognitionListener(null) } catch (_: Exception) {}
        try { dying.cancel() } catch (_: Exception) {}
        try { dying.destroy() } catch (_: Exception) {}
        Log.i(TAG, "SpeechRecognizer destroyed")
    }

    // ---------- SpeechRecognizer callbacks (main thread) ----------

    private inner class SpeechListener(
        private val session: Int,
        private val gen: Int
    ) : RecognitionListener {
        private fun current(): Boolean {
            return !destroyed && gen == recognizerGen && session == listenToken && listenActive
        }

        override fun onReadyForSpeech(params: Bundle) {
            // The cue already finished before startListening. Do not play it here.
            if (!current()) return
            Log.i(TAG, "listen ready")
        }
        override fun onBeginningOfSpeech() {
            if (!current()) return
            speechBegan = true
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle) {}
        override fun onEvent(eventType: Int, params: Bundle) {}

        override fun onResults(results: Bundle) {
            if (!current()) return
            if (!errorGate.compareAndSet(false, true)) return
            val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            Log.i(TAG, "listen ok: RESULT")
            Log.d(TAG, "heard: $text")
            try { recognizer?.setRecognitionListener(null) } catch (_: Exception) {}
            finishListen(text)
        }

        override fun onError(error: Int) {
            if (gen != recognizerGen || session != listenToken) return
            if (!listenActive) return
            if (!errorGate.compareAndSet(false, true)) {
                // A second error from the same instance must not restart or stopListening.
                Log.d(TAG, "recognizer error suppressed: $error")
                return
            }
            val retryable = error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            val normalEnd = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            // Detach before any cancel/destroy so this callback cannot chain.
            try { recognizer?.setRecognitionListener(null) } catch (_: Exception) {}
            // A tap already owns this listen. finishListen(null) would drop it.
            if (synchronized(injectLock) { inFlightClaimed }) return
            if (retryable && !clientRetryUsed && sessionOpen(session)) {
                clientRetryUsed = true
                Log.i(TAG, "recognizer backoff: $error")
                mainHandler.post {
                    if (!sessionOpen(session)) return@post
                    destroyRecognizerOnMain()
                    mainHandler.postDelayed({
                        if (!sessionOpen(session)) return@postDelayed
                        Log.i(TAG, "recreate recognizer after backoff")
                        beginListenOnMain(session)
                    }, CLIENT_BACKOFF_MS)
                }
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (ListenWindow.shouldRestart(speechBegan, error, now, listenDeadlineMs, silenceRestarts)
                && sessionOpen(session)) {
                silenceRestarts += 1
                val remaining = listenDeadlineMs - now
                Log.i(TAG, "listen restart: silence n=$silenceRestarts remainingMs=$remaining")
                restartSilenceListen(session)
                return
            }
            if (retryable) {
                backoffUntilElapsed = SystemClock.elapsedRealtime() + NEXT_LISTEN_BACKOFF_MS
                Log.w(TAG, "recognizer error: $error")
            } else if (normalEnd) {
                val name = if (error == SpeechRecognizer.ERROR_NO_MATCH) "NO_MATCH" else "SPEECH_TIMEOUT"
                Log.i(TAG, "listen ok: $name")
            } else {
                Log.w(TAG, "recognizer error: $error")
            }
            finishListen(null)
        }
    }
}
