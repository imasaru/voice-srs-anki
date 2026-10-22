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
 * Silent mode is live. Flipping it mid-drill takes effect immediately:
 * on stops the microphone, TTS, and earcons (including a readout in progress);
 * off lets the next prompt use voice. Silent answers are taps, not timers.
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
                val claimed = gradeGate.tryClaim(ease)
                if (!claimed) {
                    Log.i(TAG, "TEST_GRADE ignored: no open card or already graded")
                    return
                }
                runCatching { DrillUi.update { it.copy(selectedEase = ease) } }
                pendingGrades.clear()
                pendingGrades.add(word)
            }
            // A rating on the revealed card resumes a chat pause and then grades.
            active?.leaveChatPause(ChatPause.Action.RATING)
            // Voice and silent: a rating tap cuts TTS and the open microphone, then grades.
            val svc = active
            svc?.interruptSpeech()
            Log.i(TAG, "tap interrupt speech")
            svc?.releaseSilentGradeWait()
            if (svc != null && svc.deliverQueuedTap(word)) {
                Log.i(TAG, "TEST_GRADE delivered: $word")
                return
            }
            svc?.finishListen(null)
            Log.i(TAG, "TEST_GRADE queued: $word")
        }

        /**
         * Show answer button and [ACTION_TEST_SHOW_ANSWER].
         * Stops TTS and listening, then reveals. Ignored once the answer is up.
         */
        fun submitShowAnswer() {
            val svc = active
            if (svc == null || gradeGate.isRevealed()) {
                Log.i(TAG, "show answer ignored")
                return
            }
            if (svc.chatPaused.get()) {
                Log.i(TAG, "show answer ignored: paused for chat")
                return
            }
            svc.releaseShowAnswer()
        }

        /** Live silent switch. Does not restart the drill. */
        fun setSilentMode(on: Boolean) {
            val svc = active
            if (svc == null) {
                DrillUi.update {
                    val next = ListenGate.toggle(
                        ListenGate.State(it.silentSession, it.silentGeneration),
                        on
                    )
                    it.copy(silentSession = next.silent, silentGeneration = next.epoch)
                }
                return
            }
            svc.applySilent(on, fromUser = true)
        }

        /** Freeze TTS, listening, and the think timer. The card stays where it is. */
        fun pauseForChat(trigger: ChatPause.Trigger) {
            val svc = active ?: return
            if (!ChatPause.pauses(running, trigger)) return
            svc.enterChatPause()
        }

        /** Resume button. A rating tap resumes inside [submitTestGrade]. */
        fun resumeFromChat() {
            active?.leaveChatPause(ChatPause.Action.RESUME)
        }

        /**
         * Speak a reply only while paused, with Silent off, after a dictated question.
         * Typed questions stay on screen. The footer is not spoken.
         */
        fun offerChatReply(text: String, askedByVoice: Boolean) {
            val svc = active ?: return
            if (!ChatPause.speakReply(svc.chatPaused.get(), svc.sessionSilent, askedByVoice)) return
            val spoken = SectionFormat.spoken(text)
            if (spoken.isBlank()) return
            svc.pendingVoiceReply.set(spoken)
            svc.chatPauseLatch.get()?.countDown()
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
    private val gateLock = Any()
    /** Epoch advances only when Silent flips. A posted start keeps its epoch. */
    private var listenGate = ListenGate.State(silent = false, epoch = 0)
    /** Epoch of the listen that was posted. Restarts must use this same epoch. */
    @Volatile private var listenEpoch = 0
    /** User Silent-on asked to reveal. Turning Silent off before that lands cancels it. */
    private val silentReveal = AtomicBoolean(false)

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
    /** Real recognizer faults only. Silence never trips it, and it never ends the drill. */
    private val listenHealth by lazy { ListenHealthTracker() }
    /** One spoken mic warning per session. Timeouts and no-match do not set this. */
    private var micWarned = false

    // Latches for the blocking VoiceIo calls (touched from worker/binder threads).
    @Volatile private var speakLatch: CountDownLatch? = null
    /** On-device utterance the latch is waiting for. A stale callback must not release it. */
    @Volatile private var speakUtterance: String? = null
    /** Set only when the recognizer returns a real error, not silence or no-match. */
    @Volatile private var listenFault = false
    @Volatile private var listenLatch: CountDownLatch? = null
    private val showAnswerLatch = AtomicReference<CountDownLatch?>(null)
    /** A Show answer tap that landed before the wait latch existed. */
    private val showAnswerEarly = AtomicBoolean(false)
    /** Chat pause blocks the session thread on this card. It does not end the drill. */
    private val chatPaused = AtomicBoolean(false)
    private val chatPauseLatch = AtomicReference<CountDownLatch?>(null)
    @Volatile private var thinkDeadlineMs = 0L
    /** Remaining think time frozen by a chat pause. Negative means the timer is not frozen. */
    @Volatile private var frozenThinkMs = -1L
    /** Think-time mic restarts skip the listen cue so only the first beep is heard. */
    @Volatile private var quietListen = false
    private val pendingVoiceReply = AtomicReference<String?>(null)
    @Volatile private var shownThinkSec: Int? = null
    @Volatile private var shownPaused = false
    private val thinkTicker = Runnable { onThinkTick() }
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
                        override fun onDone(utteranceId: String) { onUtteranceFinished(utteranceId) }
                        override fun onError(utteranceId: String) { onUtteranceFinished(utteranceId) }
                        override fun onStop(utteranceId: String, interrupted: Boolean) {
                            onUtteranceFinished(utteranceId)
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
                    releaseChatPause()
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
            val prefsSilent = try {
                Prefs.load(this).silentMode
            } catch (e: Exception) {
                Log.w(TAG, "prefs load failed; voice session", e)
                false
            }
            val ui = DrillUi.state
            val epoch = if (ui.silentSession == prefsSilent) {
                ui.silentGeneration
            } else {
                ui.silentGeneration + 1
            }
            synchronized(gateLock) {
                listenGate = ListenGate.State(silent = prefsSilent, epoch = epoch)
            }
            sessionSilent = prefsSilent
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
        releaseChatPause()
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
                if (active) {
                    it.copy(
                        drillRunning = true,
                        silentSession = sessionSilent,
                        silentGeneration = synchronized(gateLock) { listenGate.epoch }
                    )
                } else {
                    it.copy(drillRunning = false, progressLine = "", awaitingShowAnswer = false)
                }
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
        micWarned = false
        listenHealth.reset()
        listenFault = false
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
            // Silence and no-match do not throw this. If it still surfaces, do not
            // pretend the mic check ended the drill: keep going only via Stop.
            Log.e(TAG, "microphone fault; drill aborted", e)
        } catch (e: AnkiConnectException) {
            Log.e(TAG, "AnkiConnect failure", e)
            showFailure(DrillErrors.ANKI, title = DrillErrors.STOPPED)
            speak(DrillErrors.ANKI)
        } catch (e: Exception) {
            Log.e(TAG, "session failure", e)
            showFailure(DrillErrors.GENERIC, title = DrillErrors.STOPPED)
            speak(DrillErrors.GENERIC)
        } finally {
            runCatching { explainSession?.stop() }
            explainSession = null
            gradeGate.close()
            pendingGrades.clear()
            releaseChatPause()
            publishDrillRunning(false)
            runCatching {
                DrillUi.update {
                    val cleared = it.copy(
                        drillRunning = false,
                        ratingMicOpen = false,
                        progressLine = "",
                        ratingVisible = false,
                        selectedEase = null,
                        awaitingShowAnswer = false,
                        thinkSecondsLeft = null,
                        chatPaused = false,
                        questionOrdinal = 0
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

    private fun showQuestionUi(questionHtml: String, cardIndex: Int) = synchronized(gradeGate) {
        synchronized(injectLock) {
            pendingGrades.clear()
            gradeGate.newCard()
        }
        tapGrade = false
        tapTranscript = false
        val gate = synchronized(gateLock) { listenGate }
        val silent = gate.silent
        val pausedNow = chatPaused.get()
        runCatching {
            DrillUi.update {
                it.copy(
                    status = if (silent) "Tap to show the answer" else "Reading the question",
                    frontHtml = AnkiText.displayHtml(questionHtml),
                    backHtml = "",
                    showBack = false,
                    ratingVisible = false,
                    heard = "",
                    ratingMicOpen = false,
                    selectedEase = null,
                    awaitingShowAnswer = true,
                    silentSession = silent,
                    silentGeneration = gate.epoch,
                    questionOrdinal = cardIndex + 1,
                    thinkSecondsLeft = null,
                    chatPaused = pausedNow
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
        tapGrade = true
        stopPlaybackNow()
    }

    /** Stops TTS and earcons without claiming a grade. Used when silent turns on. */
    fun stopPlaybackNow() {
        speechInterrupted = true
        try { tts?.stop() } catch (_: Exception) {}
        try { EdgeSpeaker.stopActive() } catch (_: Exception) {}
        try { toneGen?.stopTone() } catch (_: Exception) {}
        speakLatch?.countDown()
    }

    override fun audioSuppressed(): Boolean = sessionSilent

    override fun speak(text: String) {
        if (text.isBlank()) return
        if (!AudioGate.outputAllowed(sessionSilent)) return
        speechInterrupted = false
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
                ) { stopRequested.get() || speechInterrupted || sessionSilent }
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

    /**
     * On-device utterance. Blocks until THIS utterance finishes, the same way
     * neural playback blocks, so both engines enter the listen that follows.
     */
    private fun speakOnDevice(text: String): Boolean {
        val t = tts ?: return false
        try {
            t.setSpeechRate(TtsRate.clamp(Prefs.load(this).ttsRate))
        } catch (_: Exception) {
        }
        val latch = CountDownLatch(1)
        val utteranceId = UUID.randomUUID().toString()
        speakUtterance = utteranceId
        speakLatch = latch
        try {
            if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS speak rejected")
                return false
            }
            // Close the gap where the tap landed after the flag reset and before playback.
            if (speechInterrupted || stopRequested.get()) {
                try { t.stop() } catch (_: Exception) {}
            }
            if (!latch.await(SPEAK_CAP_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "on-device speak still running at cap")
            }
            return true
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        } finally {
            if (speakLatch === latch) speakLatch = null
            if (speakUtterance == utteranceId) speakUtterance = null
        }
    }

    /** Ignore a completion from an older on-device utterance. */
    private fun onUtteranceFinished(utteranceId: String?) {
        if (SpeakLatch.isCurrent(speakUtterance, utteranceId)) {
            speakLatch?.countDown()
        }
    }

    override fun stopped(): Boolean = stopRequested.get() || destroyed

    override fun listen(timeoutMs: Long): String? {
        while (!stopped()) {
            if (!awaitChatResume()) return null
            if (!AudioGate.micAllowed(sessionSilent)) return null
            if (showAnswerEarly.get()) return null
            tapTranscript = false
            val result = captureListen(timeoutMs)
            // Pause killed this window. Retry it without charging a listen attempt.
            if (chatPaused.get()) continue
            // A tap word is not a mic sample: no "I heard" and no dead-mic streak change.
            if (tapTranscript) {
                tapTranscript = false
                return result
            }
            return afterListen(result)
        }
        return null
    }

    /**
     * Answer phase. Listens up to [listenAttempts] times, the same count the
     * grader uses. A miss is not a grade and does not reveal the answer.
     * The last miss switches to silent. Speed mode skips the spoken cue only.
     */
    private fun listenForAnswer(timeoutMs: Long, listenAttempts: Int, speedMode: Boolean): String? {
        if (!AudioGate.micAllowed(sessionSilent) || showAnswerEarly.get()) return null
        val max = ListenWindow.clampAttempts(listenAttempts)
        var completed = 0
        while (completed < max) {
            if (!awaitChatResume()) return null
            if (sessionSilent || showAnswerEarly.get() || stopped()) return null
            val heard = captureListen(timeoutMs)
            if (chatPaused.get()) continue
            completed += 1
            consumeMicFault()
            if (stopped()) return null
            if (showAnswerEarly.get() || sessionSilent) return null
            val missed = heard.isNullOrBlank()
            if (!missed) return afterListen(heard)
            if (!ListenWindow.openAnother(completed, max, lastMissed = true, stopped = false)) {
                giveUpToSilent()
                return null
            }
            Log.i(TAG, "relisten answer $completed/$max")
            if (!speedMode) speak(ListenWindow.ANSWER_RELISTEN_CUE)
            if (stopped() || sessionSilent || showAnswerEarly.get()) return null
            if (chatPaused.get()) continue
            beep()
        }
        giveUpToSilent()
        return null
    }

    private fun captureListen(timeoutMs: Long): String? {
        val scripted = SpeechHooks.scripted
        val result = if (scripted != null && scripted.enabled()) {
            // Poll in short slices so a chip tap (queued grade) is honoured during a scripted listen,
            // the same way the real recognizer path accepts it.
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            var text: String? = null
            while (text == null) {
                if (showAnswerEarly.get() || sessionSilent || chatPaused.get()) break
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

    private fun showFailure(message: String, title: String = DrillErrors.STOPPED) {
        DrillUi.update {
            it.copy(
                showError = true,
                errorMessage = message,
                errorTitle = title.ifBlank { DrillErrors.STOPPED },
                // Status is always on screen; QA dumps truncate other nodes at 80 chars.
                status = DrillErrors.STOPPED,
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
                cfg.listenAttempts,
                ThinkTime.normalize(cfg.thinkTimeSec)
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
                cfg.listenAttempts,
                ThinkTime.normalize(cfg.thinkTimeSec)
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
        listenAttempts: Int,
        thinkTimeSec: Int,
        reveal: () -> Revealed
    ): CardStep {
        val question = AnkiText.spokenText(questionHtml)
        if (question.isBlank()) return CardStep.Blank
        val started = SystemClock.elapsedRealtime()
        showAnswerEarly.set(false)
        silentReveal.set(false)
        if (!awaitChatResume()) return CardStep.Paused
        showQuestionUi(questionHtml, cardIndex)
        var repeats = 0
        var transcript = ""
        while (!stopRequested.get()) {
            if (!awaitChatResume()) return CardStep.Paused
            if (showAnswerEarly.getAndSet(false)) break
            when (AudioGate.resume(sessionSilent, cardUp = true, answerShown = false)) {
                CardResume.HOLD -> when (awaitShowAnswer()) {
                    ShowWait.STOPPED -> return CardStep.Paused
                    ShowWait.SHOWN -> break
                    ShowWait.RESUME_VOICE -> continue
                }
                CardResume.READ_ANSWER -> break
                CardResume.READ_QUESTION -> {
                    speak(question)
                    if (stopRequested.get()) return CardStep.Paused
                    if (chatPaused.get()) continue
                    if (showAnswerEarly.get() || sessionSilent) continue
                    // Silent on cut the readout, then Silent off landed before this
                    // loop noticed. Read the question again instead of listening.
                    if (speechInterrupted) continue
                    showStatus("Listening for your answer")
                    if (ThinkTime.armed(thinkTimeSec, sessionSilent)) {
                        when (val end = thinkForAnswer(thinkTimeSec, listenWaitMs)) {
                            ThinkResult.Reveal -> {
                                thinkTranscript?.let { transcript = it }
                                thinkTranscript = null
                                break
                            }
                            ThinkResult.Again -> continue
                            ThinkResult.Repeat -> {
                                repeats++
                                if (repeats >= 8) break
                                continue
                            }
                            ThinkResult.Skip -> return CardStep.Skipped
                            ThinkResult.Stop -> return CardStep.Paused
                        }
                    }
                    beep()
                    if (showAnswerEarly.get() || sessionSilent || chatPaused.get()) continue
                    val heard = listenForAnswer(listenWaitMs, listenAttempts, speedMode)
                    if (stopRequested.get()) return CardStep.Paused
                    if (chatPaused.get() || showAnswerEarly.get() || sessionSilent) continue
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
            }
        }
        showAnswerEarly.set(false)
        if (!awaitChatResume()) return CardStep.Paused
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
        val gradeButtons = answerButtons.ifEmpty { buttons }
        val questionForGrade = answer.questionHtml.ifBlank { questionHtml }
        var speakAnswer = true
        while (!stopRequested.get()) {
            if (!awaitChatResume()) return CardStep.Paused
            val tappedEarly = claimedEase()
            if (tappedEarly != null) {
                return gradedByTap(tappedEarly, gradeButtons, started, transcript, answerHtml, questionForGrade)
            }
            when (AudioGate.resume(sessionSilent, cardUp = true, answerShown = true)) {
                CardResume.HOLD, CardResume.READ_QUESTION -> when (val wait = awaitSilentGrade()) {
                    GradeWait.Stopped -> return CardStep.Paused
                    GradeWait.ResumeVoice -> {
                        speakAnswer = true
                        continue
                    }
                    is GradeWait.Ease -> return gradedByTap(
                        wait.ease, gradeButtons, started, transcript, answerHtml, questionForGrade
                    )
                }
                CardResume.READ_ANSWER -> {
                    if (speakAnswer && answerText.isNotBlank()) {
                        speak(answerText)
                        if (stopRequested.get()) return CardStep.Paused
                        if (chatPaused.get()) {
                            speakAnswer = true
                            continue
                        }
                        speakAnswer = sessionSilent || speechInterrupted
                        if (stopRequested.get()) return CardStep.Paused
                        continue
                    }
                    showStatus("Rate this card")
                    setGrading(true)
                    try {
                        val ease = grader.grade(
                            GradeContext(
                                AnkiCard(questionForGrade, answerBody, "", gradeButtons),
                                answerText,
                                cardIndex = cardIndex,
                                speedMode = speedMode,
                                listenTimeoutMs = listenWaitMs,
                                listenAttempts = listenAttempts,
                                claimedEase = { claimedEase() }
                            ),
                            this
                        )
                        if (tapGrade) {
                            val raw = claimedEase() ?: ease
                            return gradedByTap(
                                raw, gradeButtons, started, transcript, answerHtml, questionForGrade
                            )
                        }
                        return CardStep.Graded(
                            ease,
                            SystemClock.elapsedRealtime() - started,
                            transcript,
                            answerHtml,
                            questionForGrade
                        )
                    } catch (_: SilentHandoff) {
                        speakAnswer = true
                        continue
                    } catch (_: ListenGaveUp) {
                        // No ease. Silent stays on this card with the rating buttons.
                        giveUpToSilent()
                        speakAnswer = true
                        continue
                    } catch (_: SkipRequested) {
                        return CardStep.Skipped
                    } catch (_: PauseRequested) {
                        stopFromSession()
                        return CardStep.Paused
                    } catch (e: InterruptedException) {
                        throw e
                    } catch (e: MicDeadException) {
                        Log.w(TAG, "mic fault during grade; staying on the card", e)
                        giveUpToSilent()
                        speakAnswer = true
                        continue
                    } catch (e: Exception) {
                        Log.w(TAG, "grader failed; staying on the card", e)
                        giveUpToSilent()
                        speakAnswer = true
                        continue
                    } finally {
                        setGrading(false)
                    }
                }
            }
        }
        return CardStep.Paused
    }

    private enum class ThinkResult { Reveal, Again, Repeat, Skip, Stop }

    /**
     * App-side think timer. Silence restarts the mic inside the deadline and
     * does not charge Listen attempts or say "I heard nothing".
     * A spoken answer, "show answer", or the Show answer tap reveals early.
     */
    private fun thinkForAnswer(thinkTimeSec: Int, listenWaitMs: Long): ThinkResult {
        val opened = SystemClock.elapsedRealtime()
        thinkDeadlineMs = if (frozenThinkMs >= 0L) {
            val restored = ChatPause.resumeDeadline(opened, frozenThinkMs)
            frozenThinkMs = -1L
            restored
        } else {
            opened + ThinkTime.durationMs(thinkTimeSec)
        }
        quietListen = true
        thinkTranscript = null
        armThinkTicker()
        beep()
        try {
            while (!stopRequested.get() && !destroyed) {
                if (chatPaused.get()) {
                    if (!awaitChatResume()) return ThinkResult.Stop
                    continue
                }
                if (showAnswerEarly.getAndSet(false)) return ThinkResult.Reveal
                if (sessionSilent) return ThinkResult.Again
                val now = SystemClock.elapsedRealtime()
                if (thinkDeadlineMs <= 0L || ThinkTime.expired(now, thinkDeadlineMs)) {
                    return ThinkResult.Reveal
                }
                val slice = ThinkTime.listenSliceMs(thinkDeadlineMs - now, listenWaitMs)
                if (slice <= 0L) return ThinkResult.Reveal
                val heard = captureListen(slice)
                if (chatPaused.get()) continue
                if (showAnswerEarly.getAndSet(false)) return ThinkResult.Reveal
                if (sessionSilent) return ThinkResult.Again
                if (stopRequested.get()) return ThinkResult.Stop
                if (listenFault) consumeMicFault()
                when (ThinkTime.classify(heard)) {
                    ThinkTime.Hear.EMPTY -> {
                        Log.i(TAG, "think silence; mic restart")
                        continue
                    }
                    ThinkTime.Hear.SHOW_ANSWER -> return ThinkResult.Reveal
                    ThinkTime.Hear.ANSWER -> {
                        thinkTranscript = heard?.trim().orEmpty()
                        return ThinkResult.Reveal
                    }
                    ThinkTime.Hear.REPEAT -> return ThinkResult.Repeat
                    ThinkTime.Hear.SKIP -> return ThinkResult.Skip
                    ThinkTime.Hear.STOP_DRILL -> {
                        stopFromSession()
                        return ThinkResult.Stop
                    }
                }
            }
            return ThinkResult.Stop
        } finally {
            thinkDeadlineMs = 0L
            frozenThinkMs = -1L
            quietListen = false
            mainHandler.removeCallbacks(thinkTicker)
            if (chatPaused.get()) publishCue(true, null) else publishCue(false, null)
        }
    }

    /** Spoken answer captured during think time. Null for show-answer, silence, and expiry. */
    @Volatile private var thinkTranscript: String? = null

    private fun releaseChatPause() {
        chatPaused.set(false)
        chatPauseLatch.get()?.countDown()
        pendingVoiceReply.set(null)
        thinkDeadlineMs = 0L
        frozenThinkMs = -1L
        quietListen = false
        if (::mainHandler.isInitialized) mainHandler.removeCallbacks(thinkTicker)
        shownPaused = false
        shownThinkSec = null
    }

    private fun enterChatPause() {
        if (!chatPaused.compareAndSet(false, true)) {
            publishCue(true, null)
            return
        }
        if (thinkDeadlineMs > 0L) {
            frozenThinkMs = ChatPause.freeze(SystemClock.elapsedRealtime(), thinkDeadlineMs)
            thinkDeadlineMs = 0L
        }
        stopPlaybackNow()
        finishListen(null)
        showAnswerLatch.get()?.countDown()
        silentGradeLatch.get()?.countDown()
        chatPauseLatch.get()?.countDown()
        publishCue(true, null)
        mainHandler.removeCallbacks(thinkTicker)
    }

    private fun leaveChatPause(action: ChatPause.Action) {
        if (!ChatPause.accepts(action)) return
        if (!chatPaused.compareAndSet(true, false)) return
        val now = SystemClock.elapsedRealtime()
        if (frozenThinkMs >= 0L) {
            thinkDeadlineMs = ChatPause.resumeDeadline(now, frozenThinkMs)
            frozenThinkMs = -1L
            armThinkTicker()
        }
        chatPauseLatch.get()?.countDown()
        val left = if (thinkDeadlineMs > 0L) ThinkTime.remainingSec(now, thinkDeadlineMs) else null
        publishCue(false, if (left != null && left > 0) left else null)
    }

    /**
     * Block until Resume, a rating tap, or Stop.
     * A dictated reply is spoken here, while the card is still paused.
     * @return false when the drill is stopping.
     */
    private fun awaitChatResume(): Boolean {
        while (chatPaused.get() && !stopRequested.get() && !destroyed) {
            val reply = pendingVoiceReply.getAndSet(null)
            if (!reply.isNullOrBlank()) {
                speechInterrupted = false
                speak(reply)
            }
            if (!chatPaused.get() || stopRequested.get() || destroyed) break
            val latch = CountDownLatch(1)
            chatPauseLatch.set(latch)
            if (!chatPaused.get() || stopRequested.get() || destroyed) {
                chatPauseLatch.compareAndSet(latch, null)
                break
            }
            try {
                latch.await(250, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return !stopRequested.get() && !destroyed
    }

    private fun armThinkTicker() {
        mainHandler.removeCallbacks(thinkTicker)
        mainHandler.post(thinkTicker)
    }

    private fun onThinkTick() {
        if (sessionSilent || destroyed || chatPaused.get() || thinkDeadlineMs <= 0L) return
        val sec = ThinkTime.remainingSec(SystemClock.elapsedRealtime(), thinkDeadlineMs)
        if (sessionSilent) return
        if (sec <= 0) {
            publishCue(false, null)
            return
        }
        publishCue(false, sec)
        if (sessionSilent) return
        mainHandler.postDelayed(thinkTicker, 250L)
    }

    /**
     * Drop the countdown immediately. Does not zero [thinkDeadlineMs]: the think
     * loop treats a deadline of zero as "time is up" and would reveal that way.
     */
    private fun stopThinkClock() {
        if (::mainHandler.isInitialized) mainHandler.removeCallbacks(thinkTicker)
        if (!chatPaused.get()) publishCue(false, null)
    }

    private fun publishCue(paused: Boolean, think: Int?) {
        val shown = if (paused) null else think
        if (paused == shownPaused && shown == shownThinkSec) return
        shownPaused = paused
        shownThinkSec = shown
        runCatching { DrillUi.update { it.copy(chatPaused = paused, thinkSecondsLeft = shown) } }
    }

    private fun claimedEase(): Int? = synchronized(gradeGate) {
        if (gradeGate.isClaimed()) gradeGate.ease() else null
    }

    private enum class ShowWait { SHOWN, RESUME_VOICE, STOPPED }

    private sealed class GradeWait {
        class Ease(val ease: Int) : GradeWait()
        object ResumeVoice : GradeWait()
        object Stopped : GradeWait()
    }

    /**
     * Block until Show answer, Silent turning off, or Stop.
     * Silent off does not count as a reveal.
     */
    private fun awaitShowAnswer(): ShowWait {
        while (!stopRequested.get() && !destroyed) {
            if (chatPaused.get()) {
                if (!awaitChatResume()) return ShowWait.STOPPED
                continue
            }
            if (showAnswerEarly.get()) {
                Log.i(TAG, "show answer already tapped")
                return ShowWait.SHOWN
            }
            if (!sessionSilent) return ShowWait.RESUME_VOICE
            val latch = CountDownLatch(1)
            showAnswerLatch.set(latch)
            try {
                if (chatPaused.get()) continue
                if (showAnswerEarly.get()) {
                    Log.i(TAG, "show answer already tapped")
                    return ShowWait.SHOWN
                }
                if (!sessionSilent) return ShowWait.RESUME_VOICE
                Log.i(TAG, "waiting for show answer")
                runCatching {
                    DrillUi.update {
                        val notice = it.status == ListenWindow.SILENT_NOTICE ||
                            it.heard == ListenWindow.SILENT_NOTICE
                        it.copy(
                            awaitingShowAnswer = true,
                            status = if (notice) ListenWindow.SILENT_NOTICE else "Tap to show the answer"
                        )
                    }
                }
                while (!stopRequested.get() && !destroyed) {
                    if (chatPaused.get()) break
                    if (showAnswerEarly.get()) return ShowWait.SHOWN
                    if (!sessionSilent) return ShowWait.RESUME_VOICE
                    try {
                        if (latch.await(250, TimeUnit.MILLISECONDS)) {
                            if (chatPaused.get()) break
                            if (showAnswerEarly.get()) return ShowWait.SHOWN
                            if (!sessionSilent) return ShowWait.RESUME_VOICE
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return ShowWait.STOPPED
                    }
                }
                if (chatPaused.get()) continue
                return ShowWait.STOPPED
            } finally {
                showAnswerLatch.compareAndSet(latch, null)
                if (showAnswerEarly.get() || sessionSilent) {
                    runCatching { DrillUi.update { it.copy(awaitingShowAnswer = !showAnswerEarly.get()) } }
                }
            }
        }
        return ShowWait.STOPPED
    }

    /**
     * Block until a grade chip, Silent turning off, or Stop.
     * Voice resuming is not a grade.
     */
    private fun awaitSilentGrade(): GradeWait {
        while (!stopRequested.get() && !destroyed) {
            if (chatPaused.get()) {
                if (!awaitChatResume()) return GradeWait.Stopped
                continue
            }
            val latch = CountDownLatch(1)
            silentGradeLatch.set(latch)
            try {
                val already = claimedEase()
                if (already != null) return GradeWait.Ease(already)
                if (chatPaused.get()) continue
                if (!sessionSilent) return GradeWait.ResumeVoice
                Log.i(TAG, "waiting for grade")
                var paused = false
                while (!stopRequested.get() && !destroyed) {
                    if (chatPaused.get()) {
                        paused = true
                        break
                    }
                    if (!sessionSilent) return GradeWait.ResumeVoice
                    val claimed = claimedEase()
                    if (claimed != null) return GradeWait.Ease(claimed)
                    try {
                        if (latch.await(250, TimeUnit.MILLISECONDS)) {
                            if (chatPaused.get() && claimedEase() == null) {
                                paused = true
                                break
                            }
                            if (!sessionSilent && claimedEase() == null) return GradeWait.ResumeVoice
                            break
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return GradeWait.Stopped
                    }
                }
                if (paused || chatPaused.get()) continue
                if (stopRequested.get() || destroyed) return GradeWait.Stopped
                val ease = claimedEase()
                if (ease != null) return GradeWait.Ease(ease)
                if (!sessionSilent) return GradeWait.ResumeVoice
                return GradeWait.Stopped
            } finally {
                silentGradeLatch.compareAndSet(latch, null)
            }
        }
        return GradeWait.Stopped
    }

    private fun releaseShowAnswer() {
        showAnswerEarly.set(true)
        val latch = showAnswerLatch.get()
        Log.i(TAG, if (latch == null) "show answer armed" else "show answer")
        latch?.countDown()
        stopPlaybackNow()
        finishListen(null)
    }

    /**
     * Mid-drill silent switch. On aborts TTS, earcons, the microphone, and the
     * think timer immediately, including a recognizer that is still starting.
     * A user tap while the question is up reveals the answer so the rating
     * buttons show and the answer is not read. Giving up after missed listens
     * does not reveal. Off wakes the card that is up: the question, or the answer.
     */
    private fun applySilent(on: Boolean, fromUser: Boolean = false) {
        if (!on && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            val stayed = synchronized(gateLock) {
                listenGate = ListenGate.toggle(listenGate, true)
                listenGate
            }
            sessionSilent = true
            Log.w(TAG, "silent stays on: microphone permission missing")
            publishSilentUi(stayed)
            return
        }
        val was = sessionSilent
        val plan = SilentTap.plan(
            wantOn = on,
            wasOn = was,
            hasQuestion = DrillUi.state.frontHtml.isNotBlank(),
            answerShown = DrillUi.state.showBack,
            fromUser = fromUser
        )
        val next = synchronized(gateLock) {
            listenGate = ListenGate.toggle(listenGate, on)
            listenGate
        }
        sessionSilent = next.silent
        val face = AudioGate.resume(
            nowSilent = on,
            cardUp = DrillUi.state.frontHtml.isNotBlank(),
            answerShown = DrillUi.state.showBack
        )
        Log.i(TAG, "silent live=$on resume=$face reveal=${plan.revealAnswer}")
        // Publish before cancel so a snapshot taken inside finishListen is already Silent.
        publishSilentUi(next)
        if (plan.cancelMic) {
            stopPlaybackNow()
            finishListen(null)
        }
        if (plan.stopThinkTimer) stopThinkClock()
        if (plan.revealAnswer) {
            silentReveal.set(true)
            showAnswerEarly.set(true)
            showAnswerLatch.get()?.countDown()
        }
        if (!on && was) {
            if (silentReveal.compareAndSet(true, false)) showAnswerEarly.set(false)
            if (Looper.myLooper() == Looper.getMainLooper()) probeRecognizer() else initRecognizerOnMain()
            showAnswerLatch.get()?.countDown()
            releaseSilentGradeWait()
        }
        try {
            startForegroundWithNotification()
        } catch (e: Exception) {
            Log.w(TAG, "foreground type update failed", e)
        }
    }

    /**
     * Last missed listen. Flip Silent on, show the notice, and leave the card up.
     * Question face keeps Show answer. Answer face keeps the rating buttons.
     * This does not grade and does not end the session.
     */
    private fun giveUpToSilent() {
        if (stopRequested.get() || destroyed) return
        Log.i(TAG, "missed listens; switching to silent")
        applySilent(true)
        try {
            val cfg = Prefs.load(this)
            if (!cfg.silentMode) Prefs.save(this, cfg.copy(silentMode = true))
        } catch (e: Exception) {
            Log.w(TAG, "silent persist failed", e)
        }
        val notice = ListenWindow.SILENT_NOTICE
        runCatching {
            DrillUi.update {
                it.copy(status = notice, heard = notice, silentSession = true)
            }
        }
    }

    /** Silent flag plus the generation a stale snapshot cannot beat. */
    private fun publishSilentUi(gate: ListenGate.State) {
        runCatching {
            DrillUi.update {
                it.copy(
                    silentSession = gate.silent,
                    silentGeneration = gate.epoch,
                    thinkSecondsLeft = if (gate.silent) null else it.thinkSecondsLeft,
                    ratingMicOpen = if (gate.silent) false else it.ratingMicOpen
                )
            }
        }
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

    /**
     * A real recognizer error may warn once. Silence, no-match, and the listen
     * timeout do not. The warning never ends the drill.
     * @return true when this window was a microphone fault.
     */
    private fun consumeMicFault(): Boolean {
        val fault = listenFault
        listenFault = false
        if (!fault) return false
        if (sessionSilent || stopRequested.get()) return true
        if (listenHealth.recordFault() && !micWarned) {
            micWarned = true
            val warning = "The microphone does not seem to be working. " +
                "Please check the microphone permission and your connection, then restart the drill."
            runCatching { DrillUi.update { it.copy(heard = warning) } }
            speak(warning)
        }
        return true
    }

    /** Hear-back for microphone transcripts. Tap words skip this. Nulls do not end the drill. */
    private fun afterListen(result: String?): String? {
        val fault = consumeMicFault()
        if (sessionSilent || stopRequested.get() || fault) return result
        listenHealth.recordHeard(result)
        val speed = try { Prefs.load(this).speedMode } catch (_: Exception) { false }
        if (verboseHearBack && !speed) {
            val line = if (result.isNullOrBlank()) "I heard nothing." else "I heard: $result"
            runCatching { DrillUi.update { it.copy(heard = line) } }
            speak(line)
        } else if (speed) {
            runCatching { DrillUi.update { it.copy(heard = "") } }
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
        if (chatPaused.get()) return null
        if (!AudioGate.micAllowed(sessionSilent)) return null
        if (!awaitBackoff()) return null
        if (stopRequested.get() || chatPaused.get()) return null
        // Cue finishes (and is stopped) before the microphone opens.
        // Think-time restarts skip it so only the first beep is heard.
        if (!quietListen) {
            toneListenStart()
            if (!sleepUnlessCardAdvances(LISTEN_CUE_WAIT_MS)) return null
            try { toneGen?.stopTone() } catch (_: Exception) {}
        }
        if (stopRequested.get() || destroyed || chatPaused.get() || sessionSilent) return null
        val epoch = synchronized(gateLock) { ListenGate.postedEpoch(listenGate) } ?: return null
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
        listenEpoch = epoch
        handler.postDelayed({
            if (token == listenToken) finishListen(null)
        }, delay)
        mainHandler.post { beginListenOnMain(token, epoch) }
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

    /** Cue wait that a Show answer tap or Silent can cut short. */
    private fun sleepUnlessCardAdvances(ms: Long): Boolean {
        var left = ms
        while (left > 0) {
            if (stopRequested.get() || showAnswerEarly.get() || sessionSilent || chatPaused.get()) return false
            val slice = minOf(50L, left)
            if (!sleepQuiet(slice)) return false
            left -= slice
        }
        return !stopRequested.get() && !showAnswerEarly.get() && !sessionSilent && !chatPaused.get()
    }

    override fun beep() {
        if (!AudioGate.earconAllowed(sessionSilent)) return
        try { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 180) } catch (_: Exception) {}
    }

    /** Short HIGH tone. Played only while the recognizer is NOT listening. */
    private fun toneListenStart() {
        if (quietListen) return
        if (!AudioGate.earconAllowed(sessionSilent)) return
        try { toneGen?.startTone(ToneGenerator.TONE_PROP_PROMPT, LISTEN_CUE_TONE_MS) } catch (_: Exception) {}
    }

    /** Short LOW tone: the listen just ended (result, error, or timeout). */
    private fun toneListenEnd() {
        if (quietListen) return
        if (!AudioGate.earconAllowed(sessionSilent)) return
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

    /** True only for the epoch that posted this start, and only while Silent is off. */
    private fun listenEpochOpen(epoch: Int): Boolean {
        val gate = synchronized(gateLock) { listenGate }
        return ListenGate.mayOpenMic(gate, epoch) && !sessionSilent
    }

    /** Main thread. Drops any previous recognizer and starts a new session. */
    private fun beginListenOnMain(session: Int, epoch: Int) {
        if (!listenEpochOpen(epoch) || chatPaused.get()) {
            // A stale start must not finish a newer listen that already replaced it.
            if (sessionOpen(session)) finishListen(null)
            return
        }
        if (!sessionOpen(session)) return
        val queued = pollQueuedGrade()
        if (queued != null) {
            if (!deliverTestGrade(queued)) pendingGrades.add(queued)
            return
        }
        if (!sessionOpen(session) || !listenEpochOpen(epoch)) {
            finishListen(null)
            return
        }
        if (grading) {
            runCatching {
                DrillUi.update { it.copy(ratingMicOpen = true, status = "Listening for your grade") }
            }
        }
        errorGate.set(false)
        if (!createRecognizerOnMain(session, epoch)) {
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
        if (!listenEpochOpen(epoch) || !sessionOpen(session)) {
            if (sessionOpen(session)) {
                destroyRecognizerOnMain()
                finishListen(null)
            }
            return
        }
        val started = recognizer
        try {
            started?.startListening(intent)
            Log.i(TAG, "startListening")
            // startListening can pump the looper, so the Silent tap may already have run.
            if (!listenEpochOpen(epoch)) {
                abandonRecognizer(started)
                if (synchronized(injectLock) { listenActive && session == listenToken }) {
                    finishListen(null)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "startListening failed", e)
            finishListen(null)
        }
    }

    /** Cancel one instance. A newer listen's recognizer is left alone. */
    private fun abandonRecognizer(instance: SpeechRecognizer?) {
        if (instance == null) return
        try { instance.setRecognitionListener(null) } catch (_: Exception) {}
        try { instance.cancel() } catch (_: Exception) {}
        try { instance.destroy() } catch (_: Exception) {}
        if (recognizer === instance) recognizer = null
    }

    /** Posted from onError. Same token and epoch, no cue. A queued tap in the gap wins. */
    private fun restartSilenceListen(session: Int) {
        val epoch = listenEpoch
        mainHandler.post {
            if (!listenEpochOpen(epoch) || !sessionOpen(session)) return@post
            val left = listenDeadlineMs - SystemClock.elapsedRealtime()
            if (left < ListenWindow.MIN_REMAINING_MS) {
                finishListen(null)
                return@post
            }
            destroyRecognizerOnMain()
            if (!listenEpochOpen(epoch) || !sessionOpen(session)) return@post
            beginListenOnMain(session, epoch)
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
    private fun createRecognizerOnMain(session: Int, epoch: Int): Boolean {
        if (!listenEpochOpen(epoch) || !SilentPolicy.of(sessionSilent).createRecognizer) return false
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
            created.setRecognitionListener(SpeechListener(session, gen, epoch, created))
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
        private val gen: Int,
        private val epoch: Int,
        private val instance: SpeechRecognizer
    ) : RecognitionListener {
        private fun current(): Boolean {
            return !destroyed && gen == recognizerGen && session == listenToken && listenActive &&
                listenEpochOpen(epoch)
        }

        /** Silent won. Cancel this instance even if a later listen owns the field. */
        private fun closeIfSilent(): Boolean {
            if (listenEpochOpen(epoch)) return false
            abandonRecognizer(instance)
            if (synchronized(injectLock) { listenActive && session == listenToken }) finishListen(null)
            return true
        }

        override fun onReadyForSpeech(params: Bundle) {
            if (closeIfSilent()) return
            // The cue already finished before startListening. Do not play it here.
            if (!current()) return
            Log.i(TAG, "listen ready")
        }
        override fun onBeginningOfSpeech() {
            if (closeIfSilent()) return
            if (!current()) return
            speechBegan = true
        }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle) {}
        override fun onEvent(eventType: Int, params: Bundle) {}

        override fun onResults(results: Bundle) {
            if (closeIfSilent()) return
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
            if (closeIfSilent()) return
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
            if (retryable && !clientRetryUsed && sessionOpen(session) && listenEpochOpen(epoch)) {
                clientRetryUsed = true
                Log.i(TAG, "recognizer backoff: $error")
                val retryEpoch = epoch
                mainHandler.post {
                    if (!listenEpochOpen(retryEpoch) || !sessionOpen(session)) return@post
                    destroyRecognizerOnMain()
                    mainHandler.postDelayed({
                        if (!listenEpochOpen(retryEpoch) || !sessionOpen(session)) return@postDelayed
                        Log.i(TAG, "recreate recognizer after backoff")
                        beginListenOnMain(session, retryEpoch)
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
            if (ListenWindow.isMicFault(error)) {
                listenFault = true
                if (retryable) {
                    backoffUntilElapsed = SystemClock.elapsedRealtime() + NEXT_LISTEN_BACKOFF_MS
                }
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
