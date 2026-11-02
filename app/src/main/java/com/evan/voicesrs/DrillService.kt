package com.evan.voicesrs

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import java.time.ZoneId
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
        /** Boolean extra on the start Intent: true starts a new study block, false/absent continues today's block. */
        const val EXTRA_NEW_BLOCK = "newBlock"
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
        /** Last successful GET /asr/health stays fresh this long. */
        private const val WHISPER_HEALTH_TTL_MS = 5L * 60L * 1000L
        /** A failed Whisper listen stays down this long before Auto tries it again. */
        private const val WHISPER_DOWN_MS = 60_000L
        /** Covers the 3 s connect plus 15 s read on /asr/transcribe. */
        private const val WHISPER_FINAL_GRACE_MS = 25_000L
        private const val VOSK_FINAL_GRACE_MS = 2_000L
        /** Forced-tap image wait auto-advances after this much silence. */
        private const val FORCED_TAP_ADVANCE_MS = 30_000L
        /** Per-tick slice for the forced-tap voice wait latch (tap/stop detection). */
        private const val FALLBACK_TICK_MS = 250L
        /** One listen per forced-tap cycle: hear "next"/"continue" for this long. */
        private const val FALLBACK_LISTEN_MS = 3_500L

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

        /** Card tap: cut TTS without grading. */
        fun skipSpeech() { active?.skipSpeechNow() }

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
            // The next question is held above the fold. Do not reveal it until it is on screen.
            if (svc.screenAttached && svc.shownOrdinal > 0 && svc.screenReadyOrdinal != svc.shownOrdinal) {
                Log.i(TAG, "show answer ignored: next question held")
                return
            }
            svc.releaseShowAnswer()
        }

        /**
         * Dictate. Release the drill recognizer without the no-match tone, then
         * run [onReady] so a separate dictation recognizer can take the mic.
         * Pause after this must not cancel that recognizer.
         */
        fun prepareDictation(onReady: () -> Unit) {
            val svc = active
            if (svc == null) {
                onReady()
                return
            }
            svc.handoffMicToDictation(onReady)
        }

        /** Dictation ended or failed. The drill stays paused until Resume or a rating. */
        fun dictationFinished() {
            active?.dictating = false
        }

        /** Chat column measurement. [ready] means this question may leave the hold. */
        fun reportChatScreen(attached: Boolean, ordinal: Int, ready: Boolean) {
            active?.onChatScreen(attached, ordinal, ready)
        }

        /** Tapped MCQ row. The letter is the original one. A paused card resumes first unless the user is typing. */
        fun submitLetter(letter: String, typing: Boolean = false) {
            val svc = active ?: return
            val plan = ChatPause.tapPlan(svc.chatPaused.get(), typing) ?: return
            svc.acceptLetter(letter)
            if (plan == ChatPause.Action.RESUME) svc.leaveChatPause(ChatPause.Action.RESUME)
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
            svc.releaseFocus()
            svc.enterChatPause(manual = trigger == ChatPause.Trigger.MANUAL)
            if (ChatPause.triggerReengages(trigger)) svc.cancelAutoResume(showButton = true)
        }

        /** Resume button. A rating tap resumes inside [submitTestGrade]. */
        fun resumeFromChat() {
            active?.leaveChatPause(ChatPause.Action.RESUME)
        }

        /** Focus, typing, or dictation again. Drops a pending auto-resume. */
        fun noteChatReengage() {
            active?.cancelAutoResume(showButton = true)
        }

        /**
         * The composer engagement ended. No question schedules a one-second resume.
         * A sent question stays paused and pulses Resume.
         */
        fun noteChatDisengage(end: ChatPause.Disengage) {
            active?.onChatDisengage(end)
        }

        /**
         * The composer was cleared. Stays paused and shows Resume; never arms an
         * auto-resume. See [ChatPause.afterTextCleared].
         */
        fun noteChatCleared() {
            val svc = active ?: return
            if (!svc.chatPaused.get()) return
            svc.applyChatPlan(ChatPause.afterTextCleared())
        }

        /** A question left the composer. The card stays paused. */
        fun noteQuestionSent() {
            active?.onQuestionSent()
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
    private var engine: Recognizer? = null
    /** Bumped on every destroy so late callbacks from the old instance are ignored. */
    private var recognizerGen = 0
    private var activeKind: EngineKind = EngineKind.GOOGLE
    private var fellBackFrom: EngineKind? = null
    @Volatile private var engineFallback = false
    @Volatile private var audioEndedAtMs = 0L
    private var engineLogged = false
    @Volatile private var whisperOkUntilMs = 0L
    @Volatile private var whisperDownUntilMs = 0L
    @Volatile private var voskReady = false
    /** Card tag for the speech-engine log, "n= id=". */
    @Volatile private var currentListenTag = "n=0 id=0"
    private var mediaSession: MediaSessionCompat? = null
    private var lastMediaToggleMs = 0L
    private var voskFetch: Thread? = null
    /** Re-routes the mic when audio devices change (item 29b). Main thread only. */
    private var routeWatcher: AudioRouteWatcher? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var toneGen: ToneGenerator? = null
    private lateinit var mainHandler: Handler
    @Volatile private var recognitionAvailable = false
    @Volatile private var destroyed = false
    /** Captured once in [onStartCommand], before the session thread starts. */
    @Volatile private var sessionSilent = false
    /** Set from [EXTRA_NEW_BLOCK] in [onStartCommand]; consumed (reset to false) by [blockStart]. */
    @Volatile private var newBlockRequested = false
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
    /** Focus state kept between callbacks; fed to [FocusPolicy.onChange]. */
    private val focusState = FocusPolicy.State()
    /** Focus phase fed to [FocusPolicy.onChange]; drives pause/abort/resume on focus loss. */
    @Volatile private var focusPhase = FocusPolicy.Phase.IDLE
    /** True from a focus-loss action until a RESUME clears it. Blocks re-speak/listen. */
    @Volatile private var focusPaused = false
    /** h100: sentence index the read-aloud is on (what highlighting shows); -1 = unknown. */
    @Volatile private var readingIndex = -1
    /** Latch the RESUME callback counts down. */
    @Volatile private var focusWake: CountDownLatch? = null
    /** The built audio-focus request, created lazily once. */
    private var focusReq: AudioFocusRequest? = null
    /** Set by a card tap ([skipSpeechNow]); stops the cut readout from being re-read. Cleared by [speak]. */
    @Volatile private var speechSkipped = false
    /** This card was claimed by a tap. Cleared when that grade is taken or the next question starts. */
    @Volatile private var tapGrade = false
    /** The listen result in hand came from a tap, not the microphone. */
    @Volatile private var tapTranscript = false
    /** Skip count per card this session (cardId -> how many times skipped). */
    private val skipCounts = HashMap<Long, Int>()
    /** CardId -> graded count at which it was skipped; a return is due once progress.done passes the gap. */
    private val skipReturns = LinkedHashMap<Long, Int>()
    /** Dictation owns the microphone. Pause and the drill listener must not cancel it. */
    @Volatile private var dictating = false
    /** False when no chat column is attached, so a headless drill does not wait forever. */
    @Volatile private var screenAttached = false
    /** True after the chat column has reported once this service instance. */
    @Volatile private var screenKnown = false
    /** Ordinal whose question bubble is on screen and not held. */
    @Volatile private var screenReadyOrdinal = 0
    /** Ordinal passed to [showQuestionUi] for the card in the loop. */
    @Volatile private var shownOrdinal = 0
    /** Original letter from a tap or a spoken "B" / "bee". Null until the learner picks. */
    @Volatile private var chosenLetter: String? = null
    /** Ease from the decision model for a free-speech MCQ answer. */
    @Volatile private var judgedEase: Int? = null
    /** h84: first answer window of this card (elapsedRealtime, 0 = not opened), its length, and first speech in it. */
    @Volatile private var answerWindowStartMs = 0L
    @Volatile private var answerWindowMs = 0L
    @Volatile private var answerSpeechStartMs = 0L
    /** Wall clock at session start. Option order is seeded with this and the card id. */
    @Volatile private var sessionStartedMs = 0L
    /** Question HTML of the card in [drillOne]. Letter parsing reads it. */
    @Volatile private var activeQuestionHtml = ""
    private val screenLatch = AtomicReference<CountDownLatch?>(null)

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
    /** The error that set [listenFault]. The mic sentence uses only a few of these. */
    @Volatile private var listenErrorCode: Int? = null
    /** SpeechRecognizer missing, or createSpeechRecognizer failed. */
    @Volatile private var recognizerUnavailable = false
    /** A question was sent during this pause. Unfocus must not auto-resume. */
    private var questionSentWhilePaused = false
    private var autoResumeArmed = false
    private val autoResumeRunnable = Runnable {
        autoResumeArmed = false
        if (chatPaused.get() && !questionSentWhilePaused) {
            leaveChatPause(ChatPause.Action.RESUME)
        }
    }
    @Volatile private var listenLatch: CountDownLatch? = null
    private val showAnswerLatch = AtomicReference<CountDownLatch?>(null)
    /** A Show answer tap that landed before the wait latch existed. */
    private val showAnswerEarly = AtomicBoolean(false)
    /** Chat pause blocks the session thread on this card. It does not end the drill. */
    private val chatPaused = AtomicBoolean(false)
    /** Goal-clock base for the elapsed chip; mirrors the [started] used by finishGoal. */
    @Volatile private var elapsedStartMs = 0L
    /** Cumulative paused time already subtracted from the elapsed chip. */
    @Volatile private var elapsedPausedTotalMs = 0L
    /** SystemClock.elapsedRealtime the drill last paused; 0 while running. */
    @Volatile private var elapsedPausedSinceMs = 0L
    /** Study block (item 20B), persisted in Prefs so it survives app restarts. null when no drill is running. */
    @Volatile private var block: StudyBlockStore.State? = null
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
    /**
     * The recognizer closed after speech, or a hypothesis arrived during an answer listen.
     * That window is not silence: do not restart it, and count it once a letter misses.
     */
    @Volatile private var listenSpeechDetected = false
    private var silenceRestarts = 0
    @Volatile private var ownListen: OwnWordsListen? = null
    private var restartRecognizer = false
    private var restartBusyRetries = 0
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
        runCatching { AnswerTimeLog.init(filesDir) }
        pendingGrades.clear()
        active = this
        mainHandler = Handler(Looper.getMainLooper())
        worker = HandlerThread("drill-worker").apply { start() }
        handler = Handler(worker.looper)
        routeWatcher = try {
            AudioRouteWatcher(this).also { it.start() }
        } catch (e: Exception) {
            Log.w(TAG, "audio route watcher failed; continuing without it", e)
            null
        }
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
                        override fun onStart(utteranceId: String) {
                            onUtteranceStarted(utteranceId)
                        }
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
            newBlockRequested = intent?.getBooleanExtra(EXTRA_NEW_BLOCK, false) == true
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
        releaseMediaSession()
        voskFetch?.interrupt()
        runCatching { VoskModels.close() }
        releaseFocus()
        val wasActive = active === this
        if (wasActive) active = null
        mainHandler.removeCallbacksAndMessages(null)
        requestStop()
        destroyRecognizerOnMain()
        try { routeWatcher?.stop() } catch (_: Exception) {}
        routeWatcher = null
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
        releaseMediaSession()
        releaseChatPause()
        releaseFocus()
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
        startMediaSession()
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
        val notifBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.notif_stop), stopIntent)
        mediaSession?.sessionToken?.let { token ->
            notifBuilder.setStyle(
                androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(token)
            )
        }
        val notif = notifBuilder.build()
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

    /** Headset and car buttons pause or resume. Does not request audio focus. */
    private fun startMediaSession() {
        releaseMediaSession()
        val session = try {
            MediaSessionCompat(this, "VoiceSrsDrill")
        } catch (e: Exception) {
            Log.w(TAG, "media session unavailable", e)
            return
        }
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                return handleMediaButton(mediaButtonEvent)
            }
        }, Handler(Looper.getMainLooper()))
        session.setPlaybackState(mediaPlaybackState(playing = true))
        session.isActive = true
        mediaSession = session
    }

    private fun releaseMediaSession() {
        val session = mediaSession ?: return
        mediaSession = null
        try { session.isActive = false } catch (_: Exception) {}
        try { session.release() } catch (_: Exception) {}
    }

    private fun publishMediaPlayback(playing: Boolean) {
        val session = mediaSession ?: return
        try { session.setPlaybackState(mediaPlaybackState(playing)) } catch (_: Exception) {}
    }

    private fun mediaPlaybackState(playing: Boolean): PlaybackStateCompat {
        val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        return PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE
            )
            .setState(state, 0L, if (playing) 1f else 0f)
            .build()
    }

    private fun handleMediaButton(intent: Intent?): Boolean {
        val ev = keyEventOf(intent) ?: return false
        val now = SystemClock.elapsedRealtime()
        val a = MediaKeys.actionFor(
            ev.keyCode,
            ev.action == KeyEvent.ACTION_DOWN,
            now,
            lastMediaToggleMs
        )
        val consumed = ev.keyCode == MediaKeys.KEYCODE_HEADSETHOOK ||
            ev.keyCode == MediaKeys.KEYCODE_MEDIA_PLAY_PAUSE ||
            ev.keyCode == MediaKeys.KEYCODE_MEDIA_PLAY ||
            ev.keyCode == MediaKeys.KEYCODE_MEDIA_PAUSE
        if (a == MediaKeys.Action.TOGGLE) {
            lastMediaToggleMs = now
            when (MediaBtnPlan.next(chatPaused.get(), a)) {
                MediaBtnPlan.Plan.PAUSE -> pauseForChat(ChatPause.Trigger.MANUAL)
                MediaBtnPlan.Plan.RESUME -> resumeFromChat()
                MediaBtnPlan.Plan.NONE -> Unit
            }
        }
        val pausedAfter = chatPaused.get()
        Log.i("MediaBtn", "key=${ev.keyCode} action=$a paused=$pausedAfter")
        return consumed
    }

    private fun keyEventOf(intent: Intent?): KeyEvent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
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
            return
        }
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
        } else {
            Log.i(TAG, "neural health fail")
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
        listenErrorCode = null
        recognizerUnavailable = false
        dictating = false
        chosenLetter = null
        shownOrdinal = 0
        screenReadyOrdinal = 0
        sessionStartedMs = System.currentTimeMillis()
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
            blockStart(cfg)
            explainSession = ExplainSession.start(this, cfg)
            configuredSilenceMs = ListenWindow.normalizeSilenceMs(cfg.endSilenceMs)
            refreshNeuralHealth(cfg, force = true)
            refreshAsr(cfg)
            DrillUi.update {
                it.copy(
                    showError = false,
                    errorMessage = "",
                    status = "Checking Anki…",
                    progressLine = "",
                    pendingSync = store.readGrades().size,
                    sessionStartedMs = sessionStartedMs,
                    elapsedStartMs = elapsedStartMs,
                    elapsedPausedTotalMs = elapsedPausedTotalMs,
                    elapsedPausedSinceMs = elapsedPausedSinceMs,
                    cardId = 0L,
                    chosenLetter = null,
                    suggestedEase = null
                )
            }
            if (cfg.apiKey.isBlank()) {
                val message = "No AnkiConnect API key is saved. Open Settings and enter it."
                showFailure(message)
                speak(message)
                return
            }
            val anki = AnkiConnect(cfg.url, cfg.apiKey)
            CardMedia.bind(anki)
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
            val exam = try {
                ExamSync.run(this, anki, cfg.deck)
            } catch (e: Exception) {
                ExamSync.Result(spoken = "", error = ExamError.format(e))
            }
            if (exam.error != null) {
                showStatus(exam.error)
                speak(exam.error)
            } else if (exam.spoken.isNotBlank()) {
                speak(exam.spoken)
            }
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
            skipCounts.clear()
            skipReturns.clear()
            releaseChatPause()
            blockPause()
            block = null
            publishDrillRunning(false)
            runCatching {
                DrillUi.update {
                    val cleared = it.copy(
                        drillRunning = false,
                        elapsedStartMs = 0L,
                        elapsedPausedTotalMs = 0L,
                        elapsedPausedSinceMs = 0L,
                        ratingMicOpen = false,
                        progressLine = "",
                        ratingVisible = false,
                        selectedEase = null,
                        awaitingShowAnswer = false,
                        thinkSecondsLeft = null,
                        chatPaused = false,
                        questionOrdinal = 0,
                        cardId = 0L,
                        chosenLetter = null,
                        suggestedEase = null,
                        questionLabel = "",
                        correctField = "",
                        noteId = null,
                        memoFront = "",
                        memoBack = "",
                        memoAvailable = false,
                        memoHint = false,
                        chatShowResume = false,
                        chatResumePulse = false,
                        manualPause = false,
                        heard = ""
                    )
                    when {
                        pendingRestart.get() -> cleared
                        stopRequested.get() -> cleared.copy(status = "Stopped")
                        it.status in TRANSIENT_STATUS ||
                            it.status.startsWith(SpokenLetter.HEARD_PREFIX) ->
                            cleared.copy(status = "Drill complete")
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

    /** Home-card label follows the engine that actually began this utterance. */
    private fun reportPlaybackStart(engine: EngineChoice) {
        showEngine(EngineLabel.forPlayback(engine))
    }

    private fun showQuestionUi(
        questionHtml: String,
        cardIndex: Int,
        cardId: Long,
        noteId: Long?,
        correct: String = "",
        memoFront: String = "",
        memoBack: String = "",
        memoAvailable: Boolean = false,
        memoHint: Boolean = false
    ) {
        // Prefetch media before taking the gate so the AnkiConnect fetch never blocks on it.
        val resolved = CardMedia.prefetch(questionHtml)
        synchronized(gradeGate) {
            synchronized(injectLock) {
                pendingGrades.clear()
                gradeGate.newCard()
            }
            tapGrade = false
            tapTranscript = false
            chosenLetter = null
            val ordinal = cardIndex + 1
            shownOrdinal = ordinal
            screenReadyOrdinal = 0
            val gate = synchronized(gateLock) { listenGate }
            val silent = gate.silent
            val waitForTap = CardImages.waitForTap(questionHtml)
            val front = CardMedia.display(questionHtml, resolved)
            runCatching {
                DrillUi.update {
                    it.copy(
                        status = if (silent || waitForTap) "Tap to show the answer" else "Reading the question",
                        frontHtml = front,
                        backHtml = "",
                        showBack = false,
                        ratingVisible = false,
                        heard = "",
                        ratingMicOpen = false,
                        selectedEase = null,
                        awaitingShowAnswer = true,
                        silentSession = silent,
                        silentGeneration = gate.epoch,
                        questionOrdinal = ordinal,
                        cardId = cardId,
                        sessionStartedMs = sessionStartedMs,
                        elapsedStartMs = elapsedStartMs,
                        elapsedPausedTotalMs = elapsedPausedTotalMs,
                        elapsedPausedSinceMs = elapsedPausedSinceMs,
                        chosenLetter = null,
                        suggestedEase = null,
                        thinkSecondsLeft = null,
                        questionLabel = QuestionLabel.of(questionHtml, ordinal).label,
                        correctField = correct,
                        noteId = noteId,
                        memoFront = memoFront,
                        memoBack = memoBack,
                        memoAvailable = memoAvailable,
                        memoHint = memoHint
                    )
                }
            }
            LiveThread.open(
                ChatTarget.Screen(cardId, ordinal, revealed = false),
                noteId,
                BubbleModel.fullQuestion(questionHtml).ifBlank { AnkiText.stripHtml(questionHtml) }
            )
        }
    }

    private fun showAnswerUi(questionHtml: String, answerHtml: String) {
        // Prefetch media before taking the gate so the AnkiConnect fetch never blocks on it.
        val front = if (questionHtml.isBlank()) null else CardMedia.display(questionHtml)
        val back = CardMedia.display(answerHtml)
        synchronized(gradeGate) {
            synchronized(injectLock) {
                // Drop anything tapped while only the question was showing, then open grading.
                pendingGrades.clear()
                gradeGate.reveal()
            }
            runCatching {
                DrillUi.update {
                    it.copy(
                        status = "Answer",
                        frontHtml = if (front == null) it.frontHtml else front,
                        backHtml = back,
                        showBack = true,
                        ratingVisible = true,
                        ratingMicOpen = false,
                        awaitingShowAnswer = false,
                        chosenLetter = chosenLetter,
                        suggestedEase = null
                    )
                }
            }
            publishSuggestion()
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

    /** Card tap: cut the readout and move on (no re-read, no grade). */
    fun skipSpeechNow() {
        speechSkipped = true
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

    // ---------- Audio focus (h90) ----------

    /** Builds once and reuses; the focus-change listener posts back on the main thread. */
    @Suppress("DEPRECATION")
    private fun focusRequest(): AudioFocusRequest {
        focusReq?.let { return it }
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(object : AudioManager.OnAudioFocusChangeListener {
                override fun onAudioFocusChange(change: Int) { onFocusChange(change) }
            }, mainHandler)
            .build()
        focusReq = req
        return req
    }

    /** Blocks until focus is granted (retrying DELAYED/FAILED refusals) or the drill stops. */
    private fun acquireFocus(): Boolean {
        while (!stopped()) {
            val granted = try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.requestAudioFocus(focusRequest()) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } catch (e: Exception) {
                false
            }
            if (granted) {
                Log.i(TAG, "audiofocus granted")
                // h90 review: we hold focus again, so an old loss must not poison later listens.
                if (!focusPaused) { focusState.transientLoss = false; focusState.lostAt = 0L }
                return true
            }
            Log.i(TAG, "audiofocus refused; retry")
            if (!sleepQuiet(FocusPolicy.RESUME_DELAY_MS)) return false
        }
        return false
    }

    /** Drop focus and reset all focus state. Called on every manual pause and stop. */
    private fun releaseFocus() {
        runCatching {
            val req = focusReq
            focusReq = null
            if (req != null) {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.abandonAudioFocusRequest(req)
            }
        }
        focusPhase = FocusPolicy.Phase.IDLE
        focusPaused = false
        focusState.transientLoss = false
        focusState.lostForGood = false
        focusState.lostAt = 0L
        focusWake?.countDown()
        focusWake = null
    }

    /** Runs on the main thread via the request's handler. */
    private fun onFocusChange(change: Int) {
        val action = FocusPolicy.onChange(change, focusPhase, focusState, SystemClock.elapsedRealtime())
        when (action) {
            FocusPolicy.Action.PAUSE_SPEAK -> {
                focusPaused = true
                stopPlaybackNow()
            }
            FocusPolicy.Action.ABORT_LISTEN -> {
                focusPaused = true
                destroyRecognizerOnMain()
                finishListen(null, tone = false)
            }
            FocusPolicy.Action.RESUME -> {
                mainHandler.postDelayed({
                    focusPaused = false
                    focusWake?.countDown()
                }, FocusPolicy.RESUME_DELAY_MS)
            }
            FocusPolicy.Action.NONE -> {
                // h90 review: a transient loss between speak and listen still waits for GAIN,
                // so the next question/mic doesn't grab focus back from a nav prompt.
                if (focusState.transientLoss) focusPaused = true
            }
        }
        if (focusState.lostForGood) {
            // Permanent loss: hand control to the user with the same manual pause the Pause button uses.
            runCatching { DrillService.pauseForChat(ChatPause.Trigger.MANUAL) }
        }
    }

    /** Wait in 500 ms slices while focus stays lost. False when the drill stops. */
    private fun awaitFocusResume(): Boolean {
        while (focusPaused && !stopRequested.get() && !destroyed) {
            val latch = CountDownLatch(1)
            focusWake = latch
            if (!focusPaused || stopRequested.get() || destroyed) {
                focusWake = null
                break
            }
            try {
                latch.await(500, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return !stopRequested.get() && !destroyed
    }

    override fun speak(text: String) {
        if (text.isBlank()) return
        val spoken = AnkiText.sanitizeForSpeech(text)
        if (spoken.isBlank()) return
        if (!AudioGate.outputAllowed(sessionSilent)) return
        var reSpeaks = 0
        speechInterrupted = false
        speechSkipped = false
        readingIndex = -1
        if (focusPaused && !awaitFocusResume()) return
        if (!acquireFocus()) return
        focusPhase = FocusPolicy.Phase.SPEAKING
        when (spoken) {
            RatingGuides.LONG, RatingGuides.SHORT -> Log.i(TAG, "rating prompt spoken")
            RatingGuides.REPROMPT -> Log.i(TAG, "rating reprompt")
        }
        val parts = TtsFallback.onDeviceParts(spoken)
        try {
            val cfg = try { Prefs.load(this) } catch (_: Exception) { null }
            val neuralCard = cfg != null && useNeuralNow(cfg) && spoken.length <= NeuralTts.MAX_TEXT
            if (neuralCard) {
                if (speakNeuralCard(cfg!!, spoken)) return
                val stoppedNow = stopRequested.get() || speechInterrupted || speechSkipped
                val was = neuralBlocked
                neuralBlocked = TtsFallback.blockedAfter(neuralBlocked, ok = false, stopped = stoppedNow)
                if (neuralBlocked && !was) {
                    lastNeuralHealthAt = SystemClock.elapsedRealtime()
                    Log.i(TAG, "neural blocked after card fail; on-device until health ok")
                }
            }
            if (stopRequested.get() || speechInterrupted || speechSkipped || sessionSilent) return
            for ((i, part) in parts.withIndex()) {
                if (stopRequested.get() || speechInterrupted || speechSkipped || sessionSilent) break
                readingIndex = i
                DrillUi.update(SentenceReading.at(i, part))
                if (neuralCard) {
                    val spoke = speakOnDevice(part)
                    if (spoke == TtsFallback.Spoke.DONE) Log.i(TAG, "TTS engine=neural card fail; fallback on-device ok")
                    if (spoke == TtsFallback.Spoke.STOPPED) break
                } else {
                    speakOne(part)
                }
            }
        } finally {
            DrillUi.update(SentenceReading.cleared())
            focusPhase = FocusPolicy.Phase.IDLE
        }
        // Cut off by a focus-loss pause: after focus returns, h100 resumes from the start of the
        // sentence that was playing (not the whole card); at most 3 re-speaks so a stuck focus can't loop.
        while (focusPaused && reSpeaks++ < 3 && awaitFocusResume()) {
            val rest = SentenceReading.resumeText(spoken, readingIndex)
            Log.i(TAG, "tts resume from sentence ${readingIndex.coerceAtLeast(0)}")
            speak(rest)
        }
    }

    /** 58E: the whole card in one Edge request; readingIndex follows playback via ReadingTimeline. true = played to the end or the user stopped/skipped it. */
    private fun speakNeuralCard(cfg: AnkiConfig, spoken: String): Boolean {
        val ranges = SentenceSplit.ranges(spoken)
        var timed: List<ReadingTimeline.Timed> = emptyList()
        var shown = -1
        val ok = try {
            EdgeSpeaker(this).speakWithMarks(
                cfg.neuralUrl,
                cfg.neuralToken,
                cfg.neuralVoice,
                cfg.ttsRate,
                spoken,
                cancelled = { stopRequested.get() || speechInterrupted || speechSkipped || sessionSilent },
                onMarks = { marks -> timed = ReadingTimeline.align(spoken, marks) },
                onPlaybackStart = { reportPlaybackStart(EngineChoice.NEURAL) },
                onPosition = { ms, total ->
                    val i = ReadingTimeline.sentenceAt(spoken, timed, total, ms)
                    if (i != shown && i in ranges.indices) {
                        shown = i
                        readingIndex = i
                        DrillUi.update(SentenceReading.at(i, spoken.substring(ranges[i])))
                    }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "neural card speak error")
            false
        }
        if (ok) {
            Log.i(TAG, "TTS engine=neural card ok marks=${timed.size}")
            return true
        }
        return stopRequested.get() || speechInterrupted || speechSkipped
    }

    /** One sentence through the chosen engine (neural with on-device fallback, or on-device). Blocks until it ends. */
    private fun speakOne(spoken: String) {
        val cfg = try {
            Prefs.load(this)
        } catch (e: Exception) {
            Log.w(TAG, "prefs load failed during speak", e)
            if (speakOnDevice(spoken) == TtsFallback.Spoke.DONE) {
                Log.i(TAG, "TTS engine=on-device ok")
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
                    spoken,
                    cancelled = { stopRequested.get() || speechInterrupted || sessionSilent },
                    onPlaybackStart = { reportPlaybackStart(EngineChoice.NEURAL) }
                )
            } catch (e: Exception) {
                Log.w(TAG, "neural speak error")
                false
            }
            if (ok) {
                Log.i(TAG, "TTS engine=neural ok")
                return
            }
            // A tap or Stop already ended this utterance. Do not replay it on-device.
            if (stopRequested.get() || speechInterrupted) return
            if (speakOnDevice(spoken) == TtsFallback.Spoke.DONE) {
                Log.i(TAG, "TTS engine=neural fail; fallback on-device ok")
            } else {
                Log.w(TAG, "TTS engine=neural fail; fallback on-device failed")
            }
            return
        }
        if (speakOnDevice(spoken) == TtsFallback.Spoke.DONE) {
            Log.i(TAG, "TTS engine=on-device ok")
        } else {
            Log.w(TAG, "TTS engine=on-device failed")
        }
    }

    /**
     * On-device utterance. Blocks until THIS utterance finishes, the same way
     * neural playback blocks, so both engines enter the listen that follows.
     */
    private fun speakOnDevice(text: String): TtsFallback.Spoke {
        val t = tts ?: return TtsFallback.Spoke.FAILED
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
                return TtsFallback.Spoke.FAILED
            }
            // Close the gap where the tap landed after the flag reset and before playback.
            if (speechInterrupted || stopRequested.get()) {
                try { t.stop() } catch (_: Exception) {}
            }
            if (!latch.await(SPEAK_CAP_MS, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "on-device speak still running at cap")
            }
            val r = TtsFallback.spokeResult(true, speechInterrupted || stopRequested.get())
            if (r == TtsFallback.Spoke.STOPPED && showAnswerEarly.get()) Log.i(TAG, "show answer during question read")
            return r
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return TtsFallback.Spoke.FAILED
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

    /** On-device audio actually began. A rejected speak never reaches here. */
    private fun onUtteranceStarted(utteranceId: String?) {
        if (SpeakLatch.isCurrent(speakUtterance, utteranceId)) {
            reportPlaybackStart(EngineChoice.ON_DEVICE)
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
            consumeMicFault()
            if (stopped()) return null
            if (showAnswerEarly.get() || sessionSilent) return null
            val letters = spokenLetters()
            if (letters != null && !isAnswerCommand(heard)) {
                val decision = SpokenLetter.decide(
                    if (heard.isNullOrBlank()) emptyList() else listOf(heard),
                    emptyList(),
                    letters,
                    speechDetected = speechBegan || listenSpeechDetected
                )
                val step = SpokenLetter.advance(decision, completed, max)
                when (decision) {
                    is SpokenLetter.Decision.Match -> {
                        if (noteChoice(decision.letter)) return chosenLetter
                    }
                    is SpokenLetter.Decision.Unmatched -> {
                        if (decision.heard.isNotBlank() && ownWordsOpen()) {
                            runCatching { DrillUi.update { it.copy(heard = decision.heard) } }
                            return decision.heard
                        }
                        runCatching { DrillUi.update { it.copy(heard = decision.heard) } }
                        completed = step.attempts
                        Log.i(TAG, "answer unmatched $completed/$max")
                        if (step.giveUp ||
                            !ListenWindow.openAnother(completed, max, lastMissed = true, stopped = false)
                        ) {
                            giveUpToSilent()
                            return null
                        }
                        if (!relistenAnswer(completed, max, speedMode)) return null
                        continue
                    }
                    SpokenLetter.Decision.Empty -> {
                        completed += 1
                        if (!ListenWindow.openAnother(completed, max, lastMissed = true, stopped = false)) {
                            giveUpToSilent()
                            return null
                        }
                        if (!relistenAnswer(completed, max, speedMode)) return null
                        continue
                    }
                }
            }
            completed += 1
            val missed = heard.isNullOrBlank()
            if (!missed) {
                if (noteChoice(heard)) return chosenLetter
                return afterListen(heard)
            }
            if (!ListenWindow.openAnother(completed, max, lastMissed = true, stopped = false)) {
                giveUpToSilent()
                return null
            }
            if (!relistenAnswer(completed, max, speedMode)) return null
        }
        giveUpToSilent()
        return null
    }

    /** Cue and beep before another answer listen. False when the card is leaving this phase. */
    private fun relistenAnswer(completed: Int, max: Int, speedMode: Boolean): Boolean {
        Log.i(TAG, "relisten answer $completed/$max")
        if (!speedMode) speak(ListenWindow.ANSWER_RELISTEN_CUE)
        if (stopped() || sessionSilent || showAnswerEarly.get()) return false
        if (chatPaused.get()) return true
        showStatus("Listening for your answer")
        beep()
        return true
    }

    private fun captureListen(timeoutMs: Long): String? {
        var reopens = 0
        while (true) {
            if (focusPaused && !awaitFocusResume()) return null
            if (!acquireFocus()) return null
            focusPhase = FocusPolicy.Phase.LISTENING
            val listenStartedAt = SystemClock.elapsedRealtime()
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
            focusPhase = FocusPolicy.Phase.IDLE
            val lostInWindow = focusState.lostAt > listenStartedAt &&
                !FocusPolicy.shouldGrade(focusState.lostAt, SystemClock.elapsedRealtime())
            if (!focusPaused && !lostInWindow) {
                return maybeGrade(result)
            }
            // A focus loss hit the answer window: drop the result, do NOT count a miss, and reopen.
            if (reopens++ >= 3 || stopped() || chatPaused.get()) {
                if (focusState.lostAt > listenStartedAt) Log.i(TAG, "listen dropped: focus lost during answer window")
                return null
            }
            awaitFocusResume()
        }
    }

    /** Grade parsing moved out of [captureListen] so a focus-loss drop can skip it. */
    private fun maybeGrade(result: String?): String? {
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
        val haveEngine = scripted || recognitionAvailable ||
            engineOrder().any { it != EngineKind.GOOGLE }
        if (haveEngine) {
            speak("Voice recognition ready.")
        } else {
            recognizerUnavailable = true
            postMicWarning(null, unavailable = true, speakIt = false)
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

    private fun afterGradePause(cfg: AnkiConfig) {
        val wait = HandsFree.waitAfterGrade(cfg.handsFree, cfg.handsFreeReady)
        val ms = HandsFree.waitMs(wait)
        if (ms <= 0L || stopRequested.get()) return
        HandsFree.spokenFor(wait)?.let { speak(it) }
        val until = SystemClock.elapsedRealtime() + ms
        while (!stopRequested.get() && SystemClock.elapsedRealtime() < until) {
            if (chatPaused.get()) {
                if (!awaitChatResume()) return
                continue
            }
            SystemClock.sleep(200)
        }
    }

    private fun blockGoalMin(cfg: AnkiConfig): Int =
        if (GoalModes.normalize(cfg.goalMode) == GoalModes.MINUTES) cfg.goalMinutes else 0

    /** Resumes today's unfinished block, or starts a new one when the start Intent asked for it (EXTRA_NEW_BLOCK). The flag is used once. */
    private fun blockStart(cfg: AnkiConfig) {
        val wantNew = newBlockRequested
        newBlockRequested = false
        runCatching {
            val s = StudyBlockPrompt.choose(
                Prefs.loadStudyBlock(this), System.currentTimeMillis(), ZoneId.systemDefault(),
                blockGoalMin(cfg), continueChosen = !wantNew
            )
            block = s
            Prefs.saveStudyBlock(this, s)
        }.onFailure { Log.w(TAG, "study block start failed", it) }
    }

    /** Banks the running stretch (drill paused or stopped). */
    private fun blockPause() {
        val s = block ?: return
        runCatching {
            val p = StudyBlockStore.pause(s, System.currentTimeMillis())
            block = p
            Prefs.saveStudyBlock(this, p)
        }.onFailure { Log.w(TAG, "study block pause failed", it) }
    }

    /** Restarts the running stretch after a pause. A finished block is left alone. */
    private fun blockResume() {
        val s = block ?: return
        if (s.done) return
        runCatching {
            val r = StudyBlockStore.start(s, System.currentTimeMillis(), ZoneId.systemDefault(), s.goalMin, newBlock = false)
            block = r
            Prefs.saveStudyBlock(this, r)
        }.onFailure { Log.w(TAG, "study block resume failed", it) }
    }

    /** Marks the block done once its goal minutes are reached. Saves only when something changed. */
    private fun blockTick() {
        val s = block ?: return
        runCatching {
            val t = StudyBlockStore.tick(s, System.currentTimeMillis())
            if (t != s) {
                block = t
                Prefs.saveStudyBlock(this, t)
            }
        }.onFailure { Log.w(TAG, "study block tick failed", it) }
    }

    /** Goal-check elapsed time: the study block's time when one is running, else the session's. */
    private fun goalElapsed(sessionElapsedMs: Long): Long =
        StudyBlockStore.goalElapsedMs(block, System.currentTimeMillis(), sessionElapsedMs)

    private fun finishGoal(progress: SessionProgress, startedElapsed: Long) {
        val summary = StudyBlockPrompt.withBlock(progress.spokenSummary(SystemClock.elapsedRealtime() - startedElapsed), block, System.currentTimeMillis())
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

    /** Unsuspend skipped cards whose return is due (done = Int.MAX_VALUE returns them all). */
    private fun returnDueSkips(anki: AnkiConnect, store: LocalQueueStore, done: Int) {
        val ids = skipReturns.filterValues { SkipQueue.dueForReturn(it, done) }.keys
        if (ids.isEmpty()) return
        try { anki.unsuspendCards(ids.toList()) } catch (e: Exception) { return }
        store.writeSkipped(store.readSkipped().filterNot { it in ids })
        for (id in ids) skipReturns.remove(id)
    }

    /** Reveal with guiShowAnswer, then leave the card ungraded and move on. */
    private fun skipOnline(anki: AnkiConnect, deck: String, card: AnkiCard, store: LocalQueueStore, done: Int, speedMode: Boolean) {
        Log.i(TAG, "skip card id=${card.cardId}")
        val n = (skipCounts[card.cardId] ?: 0) + 1
        skipCounts[card.cardId] = n
        val say = SkipQueue.say(n, speedMode)
        speak(say)
        postStatusNotice(say)
        runCatching { anki.guiShowAnswer() }
        if (card.cardId <= 0L) return
        val skipped = store.readSkipped().toMutableList()
        if (card.cardId !in skipped) skipped += card.cardId
        store.writeSkipped(skipped)
        runCatching { anki.suspendCards(listOf(card.cardId)) }
        SkipQueue.returnMark(n, done)?.let { skipReturns[card.cardId] = it }
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
        elapsedStartMs = StudyBlockStore.chipStartMs(block, System.currentTimeMillis(), started)
        elapsedPausedTotalMs = 0L
        elapsedPausedSinceMs = 0L
        publish(progress, store)
        var lastSkipId = 0L
        var goalSpoken = false
        var resyncs = 0
        var resyncHalt = false
        while (!stopRequested.get()) {
            blockTick()
            val elapsed = SystemClock.elapsedRealtime() - started
            if (!goalSpoken && progress.goalReached(cfg.goalMode, cfg.goalCards, cfg.goalMinutes, goalElapsed(elapsed))) {
                finishGoal(progress, started)
                goalSpoken = true
                break
            }
            val card = anki.guiCurrentCard()
            if (card == null) {
                if (skipReturns.isEmpty()) break
                returnDueSkips(anki, store, Int.MAX_VALUE)
                if (skipReturns.isNotEmpty()) break // unsuspend failed: end instead of spinning
                lastSkipId = 0L // a returned card may be the one just skipped
                runCatching { anki.guiDeckReview(cfg.deck) }
                continue
            }
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
                ThinkTime.normalize(cfg.thinkTimeSec),
                card.cardId,
                card.noteId,
                card.correct,
                card.memoFront,
                card.memoBack,
                card.memoAvailable,
                card.memoHint
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
                    skipOnline(anki, cfg.deck, card, store, progress.done, cfg.speedMode)
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
                                step.ease,
                                step.elapsedMs
                            )
                        }
                        progress.recordGrade(step.ease, step.elapsedMs)
                        returnDueSkips(anki, store, progress.done)
                        publish(progress, store)
                        Log.i(TAG, "graded ease=${step.ease}")
                        runCatching { explainSession?.prefetchOnline(anki) }
                        afterGradePause(cfg)
                    }
                }
            }
            val after = SystemClock.elapsedRealtime() - started
            if (!goalSpoken && progress.goalReached(cfg.goalMode, cfg.goalCards, cfg.goalMinutes, goalElapsed(after))) {
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
        elapsedStartMs = StudyBlockStore.chipStartMs(block, System.currentTimeMillis(), started)
        elapsedPausedTotalMs = 0L
        elapsedPausedSinceMs = 0L
        publish(progress, store)
        var goalSpoken = false
        val queue = cards.toMutableList()
        var index = 0
        while (index < queue.size) {
            if (stopRequested.get()) break
            blockTick()
            val card = queue[index]
            val elapsed = SystemClock.elapsedRealtime() - started
            if (progress.goalReached(cfg.goalMode, cfg.goalCards, cfg.goalMinutes, goalElapsed(elapsed))) {
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
                ThinkTime.normalize(cfg.thinkTimeSec),
                card.cardId,
                card.noteId,
                card.correct,
                card.memoFront,
                card.memoBack,
                card.memoAvailable,
                card.memoHint
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
                        explainSession?.prefetchOffline(queue, index)
                    }
                }
                CardStep.Skipped -> {
                    Log.i(TAG, "offline skip id=${card.cardId}")
                    val n = (skipCounts[card.cardId] ?: 0) + 1
                    skipCounts[card.cardId] = n
                    val say = SkipQueue.say(n, cfg.speedMode)
                    speak(say)
                    postStatusNotice(say)
                    val drop = n > SkipQueue.MAX_SKIPS_PER_CARD + 1
                    if (!drop) queue.add(if (n <= SkipQueue.MAX_SKIPS_PER_CARD) SkipQueue.insertAt(index + 1, queue.size) else queue.size, card)
                    queue.removeAt(index)
                    index-- // the next card slid into this slot; index++ below lands on it
                    runCatching {
                        explainSession?.noteSeen(card.cardId)
                        explainSession?.prefetchOffline(queue, index)
                    }
                }
                is CardStep.Graded -> {
                    runCatching {
                        explainSession?.recordOffline(
                            card,
                            step.transcript,
                            step.ease,
                            step.elapsedMs
                        )
                    }
                    store.enqueueGrade(
                        PendingGrade(
                            card.cardId,
                            step.ease,
                            System.currentTimeMillis(),
                            step.elapsedMs
                        )
                    )
                    progress.recordGrade(step.ease, step.elapsedMs)
                    publish(progress, store)
                    Log.i(TAG, "queued ease=${step.ease}")
                    showStatus(GradeConfirm.queuedStatus(store.readGrades().size))
                    runCatching { explainSession?.prefetchOffline(queue, index) }
                    afterGradePause(cfg)
                }
            }
            index++
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
        cardId: Long,
        noteId: Long?,
        correct: String = "",
        memoFront: String = "",
        memoBack: String = "",
        memoAvailable: Boolean = false,
        memoHint: Boolean = false,
        reveal: () -> Revealed
    ): CardStep {
        activeQuestionHtml = questionHtml
        currentListenTag = "n=${cardIndex + 1} id=$cardId"
        val screenCard = CardImages.waitForTap(questionHtml)
        val question = spokenQuestion(questionHtml, cardId)
        if (question.isBlank() && !screenCard) return CardStep.Blank
        var presentedAt = 0L
        var questionRead = false
        showAnswerEarly.set(false)
        silentReveal.set(false)
        chosenLetter = null
        judgedEase = null
        answerWindowStartMs = 0L
        answerWindowMs = 0L
        answerSpeechStartMs = 0L
        if (!awaitChatResume()) return CardStep.Paused
        showQuestionUi(
            questionHtml,
            cardIndex,
            cardId,
            noteId,
            correct,
            memoFront,
            memoBack,
            memoAvailable,
            memoHint
        )
        if (!awaitOnScreen(cardIndex + 1)) return CardStep.Paused
        presentedAt = SystemClock.elapsedRealtime()
        var repeats = 0
        var transcript = ""
        if (screenCard) {
            speak(getString(R.string.image_look_screen))
            if (question.isNotBlank()) speak(question)
            if (!questionRead) {
                presentedAt = SystemClock.elapsedRealtime()
                questionRead = true
            }
            if (stopRequested.get()) return CardStep.Paused
            val fallback = CardImages.waitFallback(
                CardImages.policy(questionHtml),
                autoAdvance = true,
                timeoutMs = FORCED_TAP_ADVANCE_MS
            )
            fallback.prompt?.let { speak(it) }
            when (awaitShowAnswer(forceTap = true, fallback = fallback)) {
                ShowWait.STOPPED -> return CardStep.Paused
                ShowWait.SHOWN, ShowWait.RESUME_VOICE -> { }
            }
            if (transcript.isBlank()) chosenLetter?.let { transcript = it }
        }
        var questionCut = false
        while (!screenCard && !stopRequested.get()) {
            if (!awaitChatResume()) return CardStep.Paused
            if (showAnswerEarly.getAndSet(false)) {
                if (transcript.isBlank()) chosenLetter?.let { transcript = it }
                if (questionCut && !sessionSilent) { Log.i(TAG, "show answer: announce"); speak(TtsFallback.SHOWING_ANSWER) }
                break
            }
            when (AudioGate.resume(sessionSilent, cardUp = true, answerShown = false)) {
                CardResume.HOLD -> when (awaitShowAnswer()) {
                    ShowWait.STOPPED -> return CardStep.Paused
                    ShowWait.SHOWN -> break
                    ShowWait.RESUME_VOICE -> continue
                }
                CardResume.READ_ANSWER -> break
                CardResume.READ_QUESTION -> {
                    speak(question)
                    if (!questionRead) {
                        presentedAt = SystemClock.elapsedRealtime()
                        questionRead = true
                    }
                    val after = TtsFallback.afterQuestion(showAnswerEarly.get(), speechInterrupted, sessionSilent, ThinkTime.armed(thinkTimeSec, sessionSilent))
                    if (after == TtsFallback.AfterQuestion.ANNOUNCE_THEN_ANSWER) questionCut = true
                    if (stopRequested.get()) return CardStep.Paused
                    if (chatPaused.get()) continue
                    if (showAnswerEarly.get() || sessionSilent) continue
                    // Silent on cut the readout, then Silent off landed before this
                    // loop noticed. Read the question again instead of listening.
                    if (TapAnswer.rereadAfterCut(speechInterrupted, speechSkipped)) continue
                    showStatus("Listening for your answer")
                    if (ThinkTime.armed(thinkTimeSec, sessionSilent)) {
                        when (val end = thinkForAnswer(thinkTimeSec, listenWaitMs, listenAttempts)) {
                            ThinkResult.Reveal -> {
                                thinkTranscript?.let { transcript = it }
                                if (transcript.isBlank()) chosenLetter?.let { transcript = it }
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
                    if (chatPaused.get() || showAnswerEarly.get() || sessionSilent) {
                        if (showAnswerEarly.get() && !chosenLetter.isNullOrBlank()) {
                            transcript = chosenLetter.orEmpty()
                            break
                        }
                        continue
                    }
                    if (!chosenLetter.isNullOrBlank() && heard == chosenLetter) {
                        transcript = chosenLetter.orEmpty()
                        break
                    }
                    when (val action = OwnWordsMode.answerAction(heard, lettersHeard())) {
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
        if (transcript.isBlank()) chosenLetter?.let { transcript = it }
        judgeOwnWordsIfNeeded(questionHtml, correct, transcript, cardId, buttons)
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
        LiveThread.reveal(AnkiText.stripHtml(answerBody), transcript)
        val gradeButtons = answerButtons.ifEmpty { buttons }
        val questionForGrade = answer.questionHtml.ifBlank { questionHtml }
        var speakAnswer = true
        while (!stopRequested.get()) {
            if (!awaitChatResume()) return CardStep.Paused
            val tappedEarly = claimedEase()
            if (tappedEarly != null) {
                return gradedByTap(tappedEarly, gradeButtons, presentedAt, transcript, answerHtml, questionForGrade)
            }
            if (screenCard) {
                if (speakAnswer && answerText.isNotBlank()) {
                    speak(answerText)
                    speakAnswer = false
                    if (stopRequested.get()) return CardStep.Paused
                    if (chatPaused.get()) {
                        speakAnswer = true
                        continue
                    }
                }
                when (val wait = awaitSilentGrade(forceTap = true)) {
                    GradeWait.Stopped -> return CardStep.Paused
                    GradeWait.ResumeVoice -> continue
                    is GradeWait.Ease -> return gradedByTap(
                        wait.ease, gradeButtons, presentedAt, transcript, answerHtml, questionForGrade
                    )
                }
                continue
            }
            when (AudioGate.resume(sessionSilent, cardUp = true, answerShown = true)) {
                CardResume.HOLD, CardResume.READ_QUESTION -> when (val wait = awaitSilentGrade()) {
                    GradeWait.Stopped -> return CardStep.Paused
                    GradeWait.ResumeVoice -> {
                        speakAnswer = true
                        continue
                    }
                    is GradeWait.Ease -> return gradedByTap(
                        wait.ease, gradeButtons, presentedAt, transcript, answerHtml, questionForGrade
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
                        speakAnswer = sessionSilent || TapAnswer.rereadAfterCut(speechInterrupted, speechSkipped)
                        if (stopRequested.get()) return CardStep.Paused
                        continue
                    }
                    val auto = judgedEase
                    if (auto != null && !screenCard) {
                        val mapped = GradeMapper.toAvailableEase(auto, gradeButtons)
                        runCatching { DrillUi.update { it.copy(selectedEase = mapped) } }
                        Log.i(TAG, "grade chip highlighted ease=$mapped")
                        showStatus(GradeConfirm.ratedStatus(mapped))
                        val holdUntil = SystemClock.elapsedRealtime() + GradeConfirm.CHIP_HOLD_MS
                        while (!stopRequested.get() && SystemClock.elapsedRealtime() < holdUntil) SystemClock.sleep(100)
                        return CardStep.Graded(
                            mapped,
                            ThinkTime.answerDurationMs(presentedAt, SystemClock.elapsedRealtime()),
                            transcript,
                            answerHtml,
                            questionForGrade
                        )
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
                                raw, gradeButtons, presentedAt, transcript, answerHtml, questionForGrade
                            )
                        }
                        return CardStep.Graded(
                            ease,
                            ThinkTime.answerDurationMs(presentedAt, SystemClock.elapsedRealtime()),
                            transcript,
                            answerHtml,
                            questionForGrade
                        )
                    } catch (_: SilentHandoff) {
                        speakAnswer = true
                        continue
                    } catch (_: ListenGaveUp) {
                        if (!askSilentFirst(listenWaitMs)) {
                            // Item 36: "no" keeps voice; listen for the rating again.
                            if (!stopRequested.get() && !destroyed) speak(SilentAsk.KEEP_VOICE)
                            speakAnswer = false
                            continue
                        }
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
     * Speech that is not a letter on the card counts as one listen attempt.
     * A spoken letter, "show answer", or the Show answer tap reveals early.
     */
    private fun thinkForAnswer(thinkTimeSec: Int, listenWaitMs: Long, listenAttempts: Int): ThinkResult {
        val opened = SystemClock.elapsedRealtime()
        thinkDeadlineMs = if (frozenThinkMs >= 0L) {
            val restored = ChatPause.resumeDeadline(opened, frozenThinkMs)
            frozenThinkMs = -1L
            restored
        } else {
            opened + ThinkTime.durationMs(thinkTimeSec)
        }
        if (answerWindowStartMs == 0L) {
            answerWindowStartMs = opened
            answerWindowMs = (thinkDeadlineMs - opened).coerceAtLeast(0L)
        }
        quietListen = true
        thinkTranscript = null
        val buffer = ThinkBuffer()
        var settling = false
        var restarts = 0
        val maxAttempts = ListenWindow.clampAttempts(listenAttempts)
        var letterAttempts = 0
        armThinkTicker()
        beep()
        try {
            while (!stopRequested.get() && !destroyed) {
                if (chatPaused.get()) {
                    if (!awaitChatResume()) return ThinkResult.Stop
                    continue
                }
                if (showAnswerEarly.getAndSet(false)) return ThinkResult.Reveal
                if (sessionSilent) {
                    if (!SilentTap.thinkContinues(true, thinkDeadlineMs, SystemClock.elapsedRealtime())) return ThinkResult.Reveal
                    SystemClock.sleep(200L)
                    continue
                }
                val now = SystemClock.elapsedRealtime()
                if (thinkDeadlineMs <= 0L || ThinkTime.expired(now, thinkDeadlineMs)) {
                    if (buffer.isEmpty()) {
                        val blank = noteBlankAnswer(letterAttempts, maxAttempts)
                        letterAttempts = blank.attempts
                        if (blank.giveUp) {
                            giveUpToSilent()
                            return ThinkResult.Again
                        }
                    } else {
                        thinkTranscript = buffer.text()
                    }
                    return ThinkResult.Reveal
                }
                val slice = if (settling) {
                    ThinkTime.settleMs(listenSilenceMs.toLong()).coerceAtMost(thinkDeadlineMs - now)
                } else {
                    if (lettersHeard()) {
                        minOf(ThinkTime.listenSliceMs(thinkDeadlineMs - now, listenWaitMs), ThinkTime.MIC_SLICE_MS)
                    } else {
                        thinkDeadlineMs - now
                    }
                }
                if (slice <= 0L) return ThinkResult.Reveal
                val heard = captureListen(slice)
                if (chatPaused.get()) continue
                if (showAnswerEarly.getAndSet(false)) return ThinkResult.Reveal
                if (sessionSilent) {
                    if (!SilentTap.thinkContinues(true, thinkDeadlineMs, SystemClock.elapsedRealtime())) return ThinkResult.Reveal
                    SystemClock.sleep(200L)
                    continue
                }
                if (stopRequested.get()) return ThinkResult.Stop
                if (listenFault) consumeMicFault()
                // A spoken letter on the card reveals at once, exactly as before.
                val letters = spokenLetters()
                if (letters != null) {
                    val decision = SpokenLetter.decide(
                        listOf(heard),
                        emptyList(),
                        letters,
                        speechDetected = true
                    )
                    if (decision is SpokenLetter.Decision.Match) {
                        thinkTranscript = if (noteChoice(decision.letter)) {
                            chosenLetter
                        } else {
                            decision.letter
                        }
                        return ThinkResult.Reveal
                    }
                }
                val hear = OwnWordsMode.thinkHear(heard, lettersHeard())
                when (ThinkTime.nextStep(hear, buffer.isEmpty(), thinkDeadlineMs - SystemClock.elapsedRealtime(), settling)) {
                    ThinkTime.Step.ACT -> {
                        buffer.clear()
                        return when (hear) {
                            ThinkTime.Hear.SHOW_ANSWER -> ThinkResult.Reveal
                            ThinkTime.Hear.REPEAT -> ThinkResult.Repeat
                            ThinkTime.Hear.SKIP -> ThinkResult.Skip
                            ThinkTime.Hear.STOP_DRILL -> {
                                stopFromSession()
                                ThinkResult.Stop
                            }
                            // Unreachable: nextStep routes ANSWER to ACCEPT and EMPTY elsewhere.
                            ThinkTime.Hear.ANSWER, ThinkTime.Hear.EMPTY -> ThinkResult.Reveal
                        }
                    }
                    ThinkTime.Step.ACCEPT -> {
                        buffer.append(heard)
                        runCatching { DrillUi.update { it.copy(heard = buffer.text()) } }
                        thinkTranscript = buffer.text()
                        return ThinkResult.Reveal
                    }
                    ThinkTime.Step.REVEAL -> {
                        thinkTranscript = if (buffer.isEmpty()) null else buffer.text()
                        return ThinkResult.Reveal
                    }
                    ThinkTime.Step.RESTART_QUIET -> {
                        if (buffer.isEmpty()) settling = false
                        restarts++
                        if (restarts > ThinkTime.MAX_RESTARTS) SystemClock.sleep(300L)
                        if (ThinkTime.capJustReached(restarts)) Log.i(TAG, "think: mic restart cap reached, slowing restarts")
                        quietListen = true
                        continue
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
    /** All final hypotheses (n-best) of the latest listen, for the own-words judge. */
    @Volatile private var lastFinals: List<String> = emptyList()

    /** h102: latest partials across the whole card listen, so a cutoff/destroy keeps them. */
    @Volatile private var sessionPartials: List<String> = emptyList()

    /**
     * Speech for the question. A number printed on the card is not read again.
     * Unchanged cards keep [AnkiText.spokenText]. [ContentHash] does not use this.
     */
    private fun spokenQuestion(questionHtml: String, cardId: Long): String {
        val original = AnkiText.spokenText(questionHtml)
        val plain = BubbleModel.fullQuestion(questionHtml).ifBlank { AnkiText.stripHtml(questionHtml) }
        val face = QuestionLabel.resolve(plain.trim(), 1)
        val spoken = if (!face.fromCard) {
            original
        } else {
            val fromSpoken = QuestionLabel.resolve(original.trim(), 1)
            if (fromSpoken.fromCard) fromSpoken.spoken else face.spoken
        }
        val parsed = QuestionCard.parse(if (face.fromCard) face.stem else plain)
        if (!parsed.mcq) return spoken
        val enabled = try {
            Prefs.load(this).shuffleOptions
        } catch (_: Exception) {
            true
        }
        val ordered = OptionShuffle.order(parsed.options, cardId, sessionStartedMs, enabled)
        val changed = ordered.map { it.letter } != parsed.options.map { it.letter }
        if (!changed) return spoken
        return OptionShuffle.spoken(parsed.stem, ordered)
    }

    /**
     * Block until this question is on screen, or until there is no chat column.
     * The first report may still be in flight, so an unknown screen waits briefly
     * instead of treating "not yet reported" as detached.
     */
    private fun awaitOnScreen(ordinal: Int): Boolean {
        val unknownUntil = SystemClock.elapsedRealtime() + 500
        while (!stopRequested.get() && !destroyed) {
            if (showAnswerEarly.get() || screenReadyOrdinal == ordinal) return true
            if (screenKnown && !screenAttached) return true
            if (!screenKnown && SystemClock.elapsedRealtime() >= unknownUntil) return true
            if (chatPaused.get()) {
                if (!awaitChatResume()) return false
                continue
            }
            val latch = CountDownLatch(1)
            screenLatch.set(latch)
            try {
                if (showAnswerEarly.get() || screenReadyOrdinal == ordinal) return true
                if (screenKnown && !screenAttached) return true
                if (!screenKnown && SystemClock.elapsedRealtime() >= unknownUntil) return true
                latch.await(250, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            } finally {
                screenLatch.compareAndSet(latch, null)
            }
        }
        return false
    }

    private fun onChatScreen(attached: Boolean, ordinal: Int, ready: Boolean) {
        screenKnown = true
        screenAttached = attached
        if (ready && ordinal > 0 && ordinal == shownOrdinal) screenReadyOrdinal = ordinal
        screenLatch.get()?.countDown()
    }

    /** Remember the original letter and reveal. Does not claim a grade. */
    private fun acceptLetter(letter: String) {
        if (gradeGate.isRevealed()) return
        val choice = TapAnswer.onTap(letter, correct = null, revealed = false) ?: return
        chosenLetter = choice.letter
        if (shownOrdinal > 0) screenReadyOrdinal = shownOrdinal
        screenLatch.get()?.countDown()
        runCatching { DrillUi.update { it.copy(chosenLetter = choice.letter) } }
        releaseShowAnswer()
    }

    /** A spoken letter during the answer window. Does not grade. */
    private fun noteChoice(raw: String?): Boolean {
        val choice = letterChoice(raw) ?: return false
        chosenLetter = choice.letter
        runCatching { DrillUi.update { it.copy(chosenLetter = choice.letter) } }
        return true
    }

    /** Original letters on the unrevealed MCQ. Null once the answer is up, or when it is not an MCQ. */
    private fun answerLetters(): Set<String>? {
        if (grading || gradeGate.isRevealed()) return null
        val plain = BubbleModel.fullQuestion(activeQuestionHtml).ifBlank {
            AnkiText.stripHtml(activeQuestionHtml)
        }
        val parsed = QuestionCard.parse(plain)
        if (!parsed.mcq) return null
        val letters = parsed.options.map { it.letter }.toSet()
        return letters.takeIf { it.isNotEmpty() }
    }

    private fun letterChoice(raw: String?): TapAnswer.Choice? {
        val all = answerLetters() ?: return null
        val letters = spokenLetters() ?: run {
            // h83: letters off -> only an explicit FINAL "answer B" / "option B" picks.
            // letterChoice is reached only from final transcripts (noteChoice in
            // listenForAnswer and thinkForAnswer); partials return early via spokenLetters().
            val pick = OwnWordsMode.prefixedLetter(raw, all, partial = false) ?: return null
            return TapAnswer.onSpoken(pick, all, correct = null, revealed = false)
        }
        return TapAnswer.onSpoken(raw, letters, correct = null, revealed = false)
    }

    private fun ownWordsOpen(): Boolean {
        return try {
            Prefs.load(this).decisionEnabled
        } catch (_: Exception) {
            false
        }
    }

    /**
     * h83: letters to match in SPEECH. Null when not an unrevealed MCQ, or own
     * words is on with "Listen for letter answers" off. Taps use acceptLetter.
     */
    private fun spokenLetters(): Set<String>? {
        val letters = answerLetters() ?: return null
        val cfg = try { Prefs.load(this) } catch (_: Exception) { return letters }
        return OwnWordsMode.spokenLetters(letters, cfg.decisionEnabled, cfg.letterListen)
    }

    /** h83: false only when own words is on and letter listening is off. */
    private fun lettersHeard(): Boolean = try {
        val cfg = Prefs.load(this)
        OwnWordsMode.listensForLetters(cfg.decisionEnabled, cfg.letterListen)
    } catch (_: Exception) {
        true
    }

    /**
     * Free-speech MCQ answer. A letter still uses the existing path.
     * Unreachable or malformed judge results fall back to [VoiceSelfGrader].
     */
    private fun judgeOwnWordsIfNeeded(questionHtml: String, correctField: String, transcript: String, cardId: Long, buttons: List<Int>) {
        JudgeNote.drop(cardId)
        judgedEase = null
        val letters = answerLetters()
        if (!OwnWordsJudge.shouldJudge(transcript, letters, lettersHeard(), tapped = chosenLetter)) return
        val cfg = try {
            Prefs.load(this)
        } catch (_: Exception) {
            return
        }
        if (!cfg.decisionEnabled || sessionSilent) return
        val strictness = OwnWordsJudge.Strictness.of(cfg.judgeStrictness)
        val decisionBase = OwnWordsJudge.effectiveBase(cfg.decisionUrl, cfg.aiBaseUrl)
        if (decisionBase.isBlank()) {
            val line = getString(R.string.own_words_fallback)
            showStatus(line)
            speak(line)
            return
        }
        val correctAnswer = OwnWordsJudge.correctText(questionHtml, correctField, "")
        if (correctAnswer.isBlank()) {
            val line = getString(R.string.own_words_fallback)
            showStatus(line)
            speak(line)
            return
        }
        showStatus(getString(R.string.own_words_checking))
        val question = BubbleModel.fullQuestion(questionHtml).ifBlank {
            AnkiText.stripHtml(questionHtml)
        }
        val judgeInput = OwnWordsJudge.judgeInput(transcript, lastFinals)
        val attempt = OwnWordsJudge.judgeWithReminder({ stopRequested.get() || destroyed }) { previous ->
            val body = OwnWordsJudge.requestBody(cfg.decisionModel, question, correctAnswer, judgeInput, strictness, previous)
            AiClient().postDecision(decisionBase, cfg.aiApiKey, body) { stopRequested.get() || destroyed }
        }
        val verdict = attempt.verdict
        Log.i(TAG, "own-words judge tries=${attempt.tries} content=${OwnWordsJudge.contentForLog(attempt.raw)}")
        when (verdict) {
            is OwnWordsJudge.Verdict.Graded -> {
                val startedAfter = if (answerWindowStartMs > 0L && answerSpeechStartMs >= answerWindowStartMs) {
                    answerSpeechStartMs - answerWindowStartMs
                } else null
                val rating = OwnWordsJudge.applyTiming(verdict.rating, startedAfter, answerWindowMs)
                judgedEase = rating.ease
                JudgeNote.put(cardId, GradeMapper.toAvailableEase(rating.ease, buttons), verdict.reason)
                Log.i(TAG, OwnWordsJudge.verdictLog(verdict.rating, rating, verdict.reason, strictness,
                    startedAfter, answerWindowMs, judgeInput))
                val line = getString(OwnWordsJudge.spokenLine(rating))
                showStatus(line)
                speak(line)
            }
            OwnWordsJudge.Verdict.Fallback -> {
                val reason = attempt.reason
                if (reason != null) Log.w(TAG, "own-words judge fallback: $reason")
                Log.i(TAG, "own-words judge rating=fallback strictness=${strictness.key} input=$judgeInput")
                val line = getString(attempt.failLine ?: R.string.own_words_fallback)
                showStatus(line)
                speak(line)
            }
        }
    }

    /** Repeat, skip, pause, and "show answer" still leave the letter loop. */
    private fun isAnswerCommand(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        if (ThinkTime.isShowAnswer(raw)) return true
        return when (GradeMapper.parse(raw)) {
            VoiceAction.Repeat, VoiceAction.Skip, VoiceAction.Pause -> true
            else -> false
        }
    }

    /**
     * Blank capture while thinking. Speech with no letter counts as one attempt.
     * Silence keeps the same count and the mic restarts inside the think deadline.
     */
    private fun noteBlankAnswer(attempts: Int, max: Int): SpokenLetter.Step {
        val letters = answerLetters()
        val decision = if (letters == null) {
            SpokenLetter.Decision.Empty
        } else {
            SpokenLetter.decide(
                emptyList(),
                emptyList(),
                letters,
                speechDetected = speechBegan || listenSpeechDetected
            )
        }
        val step = SpokenLetter.advance(decision, attempts, max)
        if (decision is SpokenLetter.Decision.Unmatched) {
            Log.i(TAG, "think spoke; unmatched ${step.attempts}/$max")
        } else {
            Log.i(TAG, "think silence; mic restart")
        }
        return step
    }

    /** Outline Good or Again. The chip is not selected and the card is not graded. */
    private fun publishSuggestion() {
        val letter = chosenLetter ?: return
        val plain = BubbleModel.fullQuestion(DrillUi.state.frontHtml).ifBlank { DrillUi.state.frontHtml }
        val parsed = QuestionCard.parse(plain)
        val answer = QuestionCard.highlightSource(
            DrillUi.state.correctField,
            AnkiText.stripHtml(DrillUi.state.backHtml)
        )
        val correct = QuestionCard.correctLetter(answer, parsed.options)
        val ease = TapAnswer.suggestedEase(letter, correct)
        runCatching { DrillUi.update { it.copy(chosenLetter = letter, suggestedEase = ease) } }
    }

    /**
     * Main thread. Cancel and destroy the drill recognizer first, with no end tone,
     * then mark dictation as the owner, then start the dictation recognizer.
     */
    private fun handoffMicToDictation(onReady: () -> Unit) {
        val run = Runnable {
            if (!dictating) enterChatPause(endTone = false)
            dictating = true
            mainHandler.post(onReady)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else mainHandler.post(run)
    }

    private fun releaseChatPause() {
        dictating = false
        chatPaused.set(false)
        runCatching { DrillUi.update { it.copy(chatPaused = false, chatShowResume = false, chatResumePulse = false, manualPause = false) } }
        chatPauseLatch.get()?.countDown()
        pendingVoiceReply.set(null)
        thinkDeadlineMs = 0L
        frozenThinkMs = -1L
        quietListen = false
        if (::mainHandler.isInitialized) mainHandler.removeCallbacks(thinkTicker)
        shownPaused = false
        shownThinkSec = null
        clearResumeChrome()
    }

    private fun onQuestionSent() {
        if (!chatPaused.get()) return
        questionSentWhilePaused = true
        onChatDisengage(ChatPause.Disengage.QUESTION_SENT)
    }

    private fun onChatDisengage(end: ChatPause.Disengage) {
        if (!chatPaused.get()) return
        if (questionSentWhilePaused && end != ChatPause.Disengage.QUESTION_SENT) return
        if (!::mainHandler.isInitialized) return
        if (end == ChatPause.Disengage.QUESTION_SENT) questionSentWhilePaused = true
        applyChatPlan(ChatPause.afterDisengage(end))
    }

    /**
     * Apply a chat pause plan: cancel any pending auto-resume, then either arm
     * the resume timer or just show/hide the Resume button.
     */
    private fun applyChatPlan(plan: ChatPause.ResumePlan) {
        mainHandler.removeCallbacks(autoResumeRunnable)
        autoResumeArmed = false
        if (plan.autoResume) {
            autoResumeArmed = true
            mainHandler.postDelayed(autoResumeRunnable, plan.delayMs)
            publishResume(show = false, pulse = false)
        } else {
            publishResume(plan.showResume, plan.pulse)
        }
    }

    private fun cancelAutoResume(showButton: Boolean) {
        val armed = autoResumeArmed
        if (ChatPause.reengageCancels(armed)) {
            if (::mainHandler.isInitialized) mainHandler.removeCallbacks(autoResumeRunnable)
            autoResumeArmed = false
        }
        if (!chatPaused.get()) return
        if (questionSentWhilePaused) publishResume(show = true, pulse = true)
        else if (showButton) publishResume(show = true, pulse = false)
    }

    private fun publishResume(show: Boolean, pulse: Boolean) {
        runCatching {
            DrillUi.update { it.copy(chatShowResume = show, chatResumePulse = pulse) }
        }
    }

    private fun clearResumeChrome() {
        questionSentWhilePaused = false
        autoResumeArmed = false
        if (::mainHandler.isInitialized) mainHandler.removeCallbacks(autoResumeRunnable)
        publishResume(show = false, pulse = false)
    }

    private fun enterChatPause(endTone: Boolean = true, manual: Boolean = false) {
        if (dictating) {
            // The dictation recognizer owns the mic. Freeze the card and leave it alone.
            if (chatPaused.compareAndSet(false, true)) {
                elapsedPausedSinceMs = SystemClock.elapsedRealtime()
                blockPause()
                if (thinkDeadlineMs > 0L) {
                    frozenThinkMs = ChatPause.freeze(SystemClock.elapsedRealtime(), thinkDeadlineMs)
                    thinkDeadlineMs = 0L
                }
                stopPlaybackNow()
                showAnswerLatch.get()?.countDown()
                silentGradeLatch.get()?.countDown()
                chatPauseLatch.get()?.countDown()
                if (::mainHandler.isInitialized) mainHandler.removeCallbacks(thinkTicker)
                publishEnterChrome()
            }
            publishCue(true, null, manual)
            publishMediaPlayback(playing = false)
            return
        }
        if (!chatPaused.compareAndSet(false, true)) {
            publishCue(true, null, manual)
            return
        }
        elapsedPausedSinceMs = SystemClock.elapsedRealtime()
        blockPause()
        if (thinkDeadlineMs > 0L) {
            frozenThinkMs = ChatPause.freeze(SystemClock.elapsedRealtime(), thinkDeadlineMs)
            thinkDeadlineMs = 0L
        }
        stopPlaybackNow()
        finishListen(null, tone = endTone)
        showAnswerLatch.get()?.countDown()
        silentGradeLatch.get()?.countDown()
        chatPauseLatch.get()?.countDown()
        publishCue(true, null, manual)
        mainHandler.removeCallbacks(thinkTicker)
        publishEnterChrome()
        publishMediaPlayback(playing = false)
    }

    /** Resume is on the pause row immediately. The pulse is a later plan. */
    private fun publishEnterChrome() {
        val plan = ChatPause.onEnter()
        publishResume(plan.showResume, plan.pulse)
    }

    private fun leaveChatPause(action: ChatPause.Action) {
        if (!ChatPause.accepts(action)) return
        if (!chatPaused.compareAndSet(true, false)) return
        val now = SystemClock.elapsedRealtime()
        if (elapsedPausedSinceMs > 0L) {
            elapsedPausedTotalMs += now - elapsedPausedSinceMs
            elapsedPausedSinceMs = 0L
        }
        blockResume()
        if (frozenThinkMs >= 0L) {
            thinkDeadlineMs = ChatPause.resumeDeadline(now, frozenThinkMs)
            frozenThinkMs = -1L
            armThinkTicker()
        }
        chatPauseLatch.get()?.countDown()
        val left = if (thinkDeadlineMs > 0L) ThinkTime.remainingSec(now, thinkDeadlineMs) else null
        clearResumeChrome()
        publishCue(false, if (left != null && left > 0) left else null)
        publishMediaPlayback(playing = true)
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
        if (destroyed || chatPaused.get() || thinkDeadlineMs <= 0L) return
        // Silent flipped on mid-think keeps the timer (h86 silentthink), so keep ticking
        // the countdown too; thinkDeadlineMs is only > 0 inside thinkForAnswer.
        val sec = ThinkTime.remainingSec(SystemClock.elapsedRealtime(), thinkDeadlineMs)
        if (sec <= 0) {
            publishCue(false, null)
            return
        }
        publishCue(false, sec)
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

    /** [manual] defaults to the current flag, so a plain re-publish while paused keeps a manual pause. */
    private fun publishCue(paused: Boolean, think: Int?, manual: Boolean = DrillUi.state.manualPause) {
        val shown = if (paused) null else think
        val manualNow = paused && manual
        if (paused == shownPaused && shown == shownThinkSec && DrillUi.state.manualPause == manualNow) return
        shownPaused = paused
        shownThinkSec = shown
        runCatching {
            DrillUi.update {
                it.copy(
                    chatPaused = paused,
                    manualPause = manualNow,
                    thinkSecondsLeft = shown,
                    elapsedStartMs = elapsedStartMs,
                    elapsedPausedTotalMs = elapsedPausedTotalMs,
                    elapsedPausedSinceMs = elapsedPausedSinceMs
                )
            }
        }
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
     *
     * When [fallback] is set (the forced-tap image wait) and the mic is live, also
     * resume on a spoken "next"/"continue" and auto-advance after
     * [CardImages.WaitFallback.autoAdvanceTimeoutMs], so the hands-free drill never
     * hangs. The timeout still fires in silent mode so it never waits forever when
     * auto-advance is on. [fallback] is null on the normal path: tap-only, as before.
     */
    private fun awaitShowAnswer(
        forceTap: Boolean = false,
        fallback: CardImages.WaitFallback? = null
    ): ShowWait {
        fun hold(): Boolean = forceTap || sessionSilent
        val voiceWait = fallback != null && !sessionSilent
        var startedAt = 0L
        // Wall-clock time the drill/chat was paused during this wait; excluded from
        // the auto-advance timer so a paused wait never counts toward the timeout.
        var pausedMs = 0L
        // We only want to catch "next" here, so suppress the listen start cue.
        if (voiceWait) quietListen = true
        try {
            while (!stopRequested.get() && !destroyed) {
                if (chatPaused.get()) {
                    val pausedAt = SystemClock.elapsedRealtime()
                    if (!awaitChatResume()) return ShowWait.STOPPED
                    pausedMs += SystemClock.elapsedRealtime() - pausedAt
                    continue
                }
                if (showAnswerEarly.get()) {
                    Log.i(TAG, "show answer already tapped")
                    return ShowWait.SHOWN
                }
                if (!hold()) return ShowWait.RESUME_VOICE
                val latch = CountDownLatch(1)
                showAnswerLatch.set(latch)
                try {
                    if (chatPaused.get()) continue
                    if (showAnswerEarly.get()) {
                        Log.i(TAG, "show answer already tapped")
                        return ShowWait.SHOWN
                    }
                    if (!hold()) return ShowWait.RESUME_VOICE
                    if (startedAt == 0L) startedAt = SystemClock.elapsedRealtime()
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
                        if (!hold()) return ShowWait.RESUME_VOICE
                        // A spoken "next"/"continue" (non-silent) or the auto-advance
                        // timeout both break the hang; treat them like a shown answer.
                        // One 3.5 s listen per cycle: the recognizer runs the full
                        // window instead of restarting every tick, and is cancelled
                        // immediately by a tap or Stop (finishListen / latch).
                        if (fallback != null) {
                            val heard = if (voiceWait) listen(FALLBACK_LISTEN_MS) else null
                            val elapsed = SystemClock.elapsedRealtime() - startedAt
                            if (CardImages.shouldResume(fallback, heard, elapsed, pausedMs)) {
                                return ShowWait.RESUME_VOICE
                            }
                        }
                        try {
                            if (latch.await(FALLBACK_TICK_MS, TimeUnit.MILLISECONDS)) {
                                if (chatPaused.get()) break
                                if (showAnswerEarly.get()) return ShowWait.SHOWN
                                if (!hold()) return ShowWait.RESUME_VOICE
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
                    if (showAnswerEarly.get() || hold()) {
                        runCatching { DrillUi.update { it.copy(awaitingShowAnswer = !showAnswerEarly.get()) } }
                    }
                }
            }
            return ShowWait.STOPPED
        } finally {
            if (voiceWait) quietListen = false
        }
    }

    /**
     * Block until a grade chip, Silent turning off, or Stop.
     * Voice resuming is not a grade.
     */
    private fun awaitSilentGrade(forceTap: Boolean = false): GradeWait {
        fun hold(): Boolean = forceTap || sessionSilent
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
                if (!hold()) return GradeWait.ResumeVoice
                Log.i(TAG, "waiting for grade")
                var paused = false
                while (!stopRequested.get() && !destroyed) {
                    if (chatPaused.get()) {
                        paused = true
                        break
                    }
                    if (!hold()) return GradeWait.ResumeVoice
                    val claimed = claimedEase()
                    if (claimed != null) return GradeWait.Ease(claimed)
                    try {
                        if (latch.await(250, TimeUnit.MILLISECONDS)) {
                            if (chatPaused.get() && claimedEase() == null) {
                                paused = true
                                break
                            }
                            if (!hold() && claimedEase() == null) return GradeWait.ResumeVoice
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
                if (!hold()) return GradeWait.ResumeVoice
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
        stopThinkClock()
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
            fromUser = fromUser,
            thinkRunning = thinkDeadlineMs > 0L
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

    /** Item 36: ask aloud before auto-silent. True = go silent (yes, no reply, or not understood). */
    private fun askSilentFirst(listenWaitMs: Long): Boolean {
        if (stopRequested.get() || destroyed || sessionSilent) return true
        speak(SilentAsk.QUESTION)
        if (stopRequested.get() || destroyed) return true
        val heard = try {
            captureListen(listenWaitMs)
        } catch (e: InterruptedException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "silent ask listen failed", e)
            null
        }
        val reply = SilentAsk.parse(heard)
        Log.i(TAG, "silent ask reply=$reply")
        return SilentAsk.goSilent(reply)
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
        postStatusNotice(notice)
        runCatching { DrillUi.update { it.copy(silentSession = true, heard = "") } }
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
            ThinkTime.answerDurationMs(startedElapsed, SystemClock.elapsedRealtime()),
            transcript,
            answerHtml,
            questionHtml
        )
    }

    /**
     * A real recognizer error may warn once. The sentence is only for audio,
     * permission, or no recognizer. Silence, no-match, and the listen timeout
     * do not blame the mic. The warning is the status line. It never ends the drill.
     * @return true when this window was a microphone fault.
     */
    private fun consumeMicFault(): Boolean {
        val fault = listenFault
        val error = listenErrorCode
        val unavailable = recognizerUnavailable
        listenFault = false
        listenErrorCode = null
        if (!fault && !unavailable) return false
        if (sessionSilent || stopRequested.get()) return fault
        if (fault) listenHealth.recordFault()
        if (DrillNotice.blamesMic(error, unavailable)) {
            postMicWarning(error, unavailable, speakIt = true)
        }
        return fault
    }

    /** Drill notices draw on the status line, never inside the card. */
    private fun postStatusNotice(text: String) {
        if (text.isBlank()) return
        if (DrillNotice.route(text) != DrillNotice.Sink.STATUS) return
        runCatching { DrillUi.update { it.copy(status = text) } }
    }

    private fun postMicWarning(error: Int?, unavailable: Boolean, speakIt: Boolean) {
        val warning = DrillNotice.text(error, unavailable) ?: return
        if (micWarned) return
        micWarned = true
        postStatusNotice(warning)
        if (speakIt) speak(warning)
    }

    /** Hear-back for microphone transcripts. Tap words skip this. Nulls do not end the drill. */
    private fun afterListen(result: String?): String? {
        val fault = consumeMicFault()
        if (sessionSilent || stopRequested.get() || fault) return result
        listenHealth.recordHeard(result)
        runCatching { DrillUi.update { it.copy(heard = result.orEmpty()) } }
        val speed = try { Prefs.load(this).speedMode } catch (_: Exception) { false }
        if (verboseHearBack && !speed && !result.isNullOrBlank()) {
            speak("I heard: $result")
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

    private data class ListenAttempt(val text: String?, val fallback: Boolean)

    /** Whisper health is cached from drill start. Vosk is ready once its model is on disk. */
    private fun engineOrder(): List<EngineKind> {
        val choice = runCatching { Prefs.load(this).speechEngine }.getOrDefault(SpeechChoice.AUTO)
        val now = SystemClock.elapsedRealtime()
        val whisper = now >= whisperDownUntilMs && now < whisperOkUntilMs
        if (VoskModelStore.isReady(VoskModelStore.modelDir(filesDir))) voskReady = true
        return SpeechEngine.order(choice, whisper, voskReady)
    }

    /** Off the main thread, once at drill start. Does not log the token. */
    private fun refreshAsr(cfg: AnkiConfig) {
        val base = SpeechEngine.asrBase(cfg.asrUrl, cfg.neuralUrl)
        val now = SystemClock.elapsedRealtime()
        if (base != null && AsrHttp.healthOk(base, cfg.neuralToken)) {
            whisperOkUntilMs = now + WHISPER_HEALTH_TTL_MS
            whisperDownUntilMs = 0L
        } else {
            whisperOkUntilMs = 0L
        }
        voskReady = VoskModelStore.isReady(VoskModelStore.modelDir(filesDir))
        val wantVosk = cfg.speechEngine == SpeechChoice.AUTO || cfg.speechEngine == SpeechChoice.VOSK
        if (!voskReady && base != null && wantVosk && VoskModelFetcher.unmeteredOrWifi(this)) {
            val token = cfg.neuralToken
            voskFetch?.interrupt()
            voskFetch = Thread({
                if (VoskModelFetcher.download(this, base, token)) voskReady = true
            }, "vosk-fetch").also { it.start() }
        }
    }

    private fun noteAudioEndFromEngine() {
        val ended = engine?.audioEndedAtMs ?: 0L
        if (ended > 0L && (audioEndedAtMs == 0L || ended < audioEndedAtMs)) audioEndedAtMs = ended
    }

    private fun logEngineResult() {
        if (engineLogged || engineFallback) return
        noteAudioEndFromEngine()
        engineLogged = true
        val end = if (audioEndedAtMs > 0L) audioEndedAtMs else SystemClock.elapsedRealtime()
        val latency = (SystemClock.elapsedRealtime() - end).coerceAtLeast(0L)
        Log.i("SpeechEngine", SpeechEngine.logLine(activeKind, latency, currentListenTag, fellBackFrom))
    }

    private fun listenWithRecognizer(timeoutMs: Long): String? {
        if (dictating || chatPaused.get()) return null
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
        val order = engineOrder()
        if (!recognitionAvailable && order.all { it == EngineKind.GOOGLE }) {
            Log.w(TAG, "no recognizer; skipping listen")
            return null
        }
        var fellBack: EngineKind? = null
        for (kind in order) {
            if (stopRequested.get() || destroyed || chatPaused.get() || sessionSilent) return null
            if (kind == EngineKind.GOOGLE && !recognitionAvailable) continue
            consumeQueuedGrade()?.let { return it }
            activeKind = kind
            fellBackFrom = fellBack
            val attempt = listenOnce(timeoutMs, epoch)
            if (attempt.fallback) {
                fellBack = kind
                continue
            }
            return attempt.text
        }
        return null
    }

    /** One engine attempt. A transport error before any final is [ListenAttempt.fallback]. */
    private fun listenOnce(timeoutMs: Long, epoch: Int): ListenAttempt {
        engineFallback = false
        engineLogged = false
        audioEndedAtMs = 0L
        val latch = CountDownLatch(1)
        val openedAt = SystemClock.elapsedRealtime()
        val token = synchronized(injectLock) {
            val queued = if (grading) pendingGrades.poll() else null
            if (queued != null) {
                return ListenAttempt(noteTapTranscript(queued), fallback = false)
            }
            listenLatch = latch
            listenResult.set(null)
            lastFinals = emptyList()
            sessionPartials = emptyList()
            listenActive = true
            inFlightClaimed = false
            clientRetryUsed = false
            errorGate.set(false)
            speechBegan = false
            listenSpeechDetected = false
            silenceRestarts = 0
            ownListen = if (lettersHeard()) null else answerLetters()?.let { OwnWordsListen(it) }
            restartRecognizer = false
            restartBusyRetries = 0
            listenExtrasLogged = false
            listenWaitMs = timeoutMs
            listenSilenceMs = configuredSilenceMs
            listenDeadlineMs = openedAt + timeoutMs
            if (answerWindowStartMs == 0L) {
                answerWindowStartMs = openedAt
                answerWindowMs = timeoutMs
            }
            ++listenToken
        }
        val delay = (listenDeadlineMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        listenEpoch = epoch
        handler.postDelayed({
            if (token != listenToken) return@postDelayed
            if (activeKind == EngineKind.GOOGLE) {
                if (audioEndedAtMs == 0L) audioEndedAtMs = SystemClock.elapsedRealtime()
                finishListen(cutoffResult(), logEngine = true)
            } else {
                mainHandler.post {
                    if (token != listenToken) return@post
                    try { engine?.stop() } catch (_: Exception) {}
                }
                val grace = if (activeKind == EngineKind.WHISPER) WHISPER_FINAL_GRACE_MS else VOSK_FINAL_GRACE_MS
                handler.postDelayed({
                    if (token == listenToken) {
                        if (audioEndedAtMs == 0L) audioEndedAtMs = SystemClock.elapsedRealtime()
                        finishListen(cutoffResult(), logEngine = true)
                    }
                }, grace)
            }
        }, delay)
        mainHandler.post { beginListenOnMain(token, epoch) }
        val slack = if (activeKind == EngineKind.WHISPER) {
            LISTEN_LATCH_SLACK_MS + WHISPER_FINAL_GRACE_MS
        } else {
            LISTEN_LATCH_SLACK_MS
        }
        try {
            if (!latch.await(delay + slack, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "listen latch timed out")
                finishListen(cutoffResult(), logEngine = true)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            finishListen(cutoffResult(), logEngine = true)
        }
        return ListenAttempt(listenResult.get(), engineFallback)
    }

    /** h102: deadline text wins; else the latest non-blank session partial; else null. */
    private fun cutoffResult(): String? {
        val own = (ownListen?.onDeadline() as? OwnWordsListen.Step.Finish)?.text
        val text = ListenWindow.cutoffText(own, sessionPartials)
        if (own == null && text != null) {
            lastFinals = sessionPartials.map { it.trim() }.filter { it.isNotEmpty() }
            Log.i(TAG, "listen ok: CUTOFF kept partial")
        }
        return text
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
    private fun finishListen(text: String?, tone: Boolean = true, logEngine: Boolean = false) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            // Block until main applies the result. Otherwise the session thread
            // can observe a stale transcript and leave the recognizer alive.
            val done = CountDownLatch(1)
            mainHandler.post {
                finishListen(text, tone, logEngine)
                done.countDown()
            }
            try {
                done.await(2, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            return
        }
        if (logEngine) logEngineResult()
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
        if (tone && !dictating) toneListenEnd()
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
        if (dictating) {
            if (sessionOpen(session)) finishListen(null, tone = false)
            return
        }
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
            finishListen(null, tone = !engineFallback)
            return
        }
        val waitMs = listenWaitMs
        // h99: Google's recogniser reads this as Integer; a Long was ignored (default 0).
        val silenceMs = SpokenLetter.silenceExtra(listenSilenceMs.toLong())
        if (activeKind == EngineKind.GOOGLE && !listenExtrasLogged) {
            listenExtrasLogged = true
            Log.i(
                TAG,
                "listen extras wait=$waitMs silence=$silenceMs lang=${SpokenLetter.LANGUAGE} " +
                    "max=${SpokenLetter.MAX_RESULTS} offline=${SpokenLetter.PREFER_OFFLINE}"
            )
        }
        if (!listenEpochOpen(epoch) || !sessionOpen(session)) {
            if (sessionOpen(session)) {
                destroyRecognizerOnMain()
                finishListen(null)
            }
            return
        }
        if (RouteRebuild.onListen(routeWatcher?.consumeChange() == true) == RouteRebuild.Action.REBUILD) {
            Log.i("MicRoute", "route changed before startListening; rebuilding recognizer")
            if (!createRecognizerOnMain(session, epoch)) {
                finishListen(null, tone = !engineFallback)
                return
            }
        }
        val started = engine
        if (started == null) {
            finishListen(null)
            return
        }
        val callbacks = SpeechListener(session, recognizerGen, epoch, started)
        try {
            started.start(callbacks, listenWaitMs, listenSilenceMs.toLong())
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
            if (activeKind != EngineKind.GOOGLE) engineFallback = true
            finishListen(null, tone = !engineFallback)
        }
    }

    /** Cancel one instance. A newer listen's recognizer is left alone. */
    private fun abandonRecognizer(instance: Recognizer?) {
        if (instance == null) return
        try { instance.cancel() } catch (_: Exception) {}
        try { instance.destroy() } catch (_: Exception) {}
        if (engine === instance) engine = null
    }

    /** Posted from onError. Same token and epoch, no cue. A queued tap in the gap wins. */
    private fun restartSilenceListen(session: Int) {
        val epoch = listenEpoch
        mainHandler.post {
            if (!listenEpochOpen(epoch) || !sessionOpen(session)) return@post
            val left = listenDeadlineMs - SystemClock.elapsedRealtime()
            if (left < ListenWindow.MIN_REMAINING_MS) {
                finishListen(null, logEngine = true)
                return@post
            }
            restartSilenceListenAfter(session, SilenceRestart.RESTART_DELAY_MS)
        }
    }

    /** h83: own words + letters off. Restart the mic without touching silenceRestarts;
     * a short remaining window finalizes with the stitched transcript. */
    private fun restartOwnWordsListen(session: Int) {
        val epoch = listenEpoch
        mainHandler.post {
            if (!listenEpochOpen(epoch) || !sessionOpen(session)) return@post
            val left = listenDeadlineMs - SystemClock.elapsedRealtime()
            if (left < ListenWindow.MIN_REMAINING_MS) {
                finishListen(ownListen?.transcript(), logEngine = true)
                return@post
            }
            restartSilenceListenAfter(session, SilenceRestart.RESTART_DELAY_MS)
        }
    }

    /** Destroy, wait [delayMs], recreate; a restart's busy error retries once. */
    private fun restartSilenceListenAfter(session: Int, delayMs: Long) {
        val epoch = listenEpoch
        mainHandler.post {
            if (!listenEpochOpen(epoch) || !sessionOpen(session)) return@post
            val left = listenDeadlineMs - SystemClock.elapsedRealtime()
            if (left < ListenWindow.MIN_REMAINING_MS) {
                finishListen(null, logEngine = true)
                return@post
            }
            restartRecognizer = true
            SilenceRestart.schedule(
                delayMs,
                destroy = { destroyRecognizerOnMain() },
                postDelayed = { ms, r -> mainHandler.postDelayed(r, ms) },
                stillOpen = { listenEpochOpen(epoch) && sessionOpen(session) },
                begin = { beginListenOnMain(session, epoch) }
            )
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
        if (dictating) return false
        if (!listenEpochOpen(epoch) || !SilentPolicy.of(sessionSilent).createRecognizer) return false
        if (RouteRebuild.onListen(routeWatcher?.consumeChange() == true) == RouteRebuild.Action.REBUILD) {
            Log.i("MicRoute", "route changed; building recognizer on new route")
        }
        destroyRecognizerOnMain()
        if (destroyed || !listenActive || session != listenToken) return false
        if (activeKind == EngineKind.GOOGLE && !SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer not available on this device")
            recognitionAvailable = false
            recognizerUnavailable = true
            listenFault = true
            listenErrorCode = null
            return false
        }
        return try {
            val created = RecognizerFactory.create(activeKind, this, Prefs.load(this))
            if (created is GoogleRecognizer) created.open()
            engine = created
            recognizerUnavailable = false
            if (created.kind == EngineKind.GOOGLE) {
                Log.i(TAG, "recognizer created")
                Log.i(TAG, "SpeechRecognizer created for listen session")
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "recognizer creation failed", e)
            engine = null
            if (activeKind == EngineKind.GOOGLE) {
                recognizerUnavailable = true
                listenFault = true
                listenErrorCode = null
            } else {
                engineFallback = true
            }
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
        val dying = engine ?: return
        engine = null
        try { dying.destroy() } catch (_: Exception) {}
        if (dying.kind == EngineKind.GOOGLE) Log.i(TAG, "SpeechRecognizer destroyed")
    }

    /** Raw n-best, including blanks, at debug. Google only; other engines have no bundle. */
    private fun logGoogleNBest(kind: String, raw: List<String>) {
        if (activeKind != EngineKind.GOOGLE) return
        val missing = (engine as? GoogleRecognizer)?.resultsMissing == true
        if (missing) {
            Log.d(TAG, "$kind n-best: <none>")
        } else {
            Log.d(TAG, "$kind n-best: ${raw.joinToString(" | ")}")
        }
    }

    /** Source guards look for onPartialResults. Engines call [RecognizerCallbacks.onPartial]. */
    private interface PartialResults {
        fun onPartialResults(partialResults: List<String>)
    }

    // ---------- SpeechRecognizer callbacks (main thread) ----------

    private inner class SpeechListener(
        private val session: Int,
        private val gen: Int,
        private val epoch: Int,
        private val instance: Recognizer
    ) : RecognizerCallbacks, PartialResults {
        private var partials: List<String> = emptyList()

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

        /** Detach, then finish. Logs the engine line unless a fallback already claimed it. */
        private fun complete(text: String?) {
            try { instance.detach() } catch (_: Exception) {}
            finishListen(text, logEngine = true)
        }

        override fun onReady() {
            if (closeIfSilent()) return
            // The cue already finished before startListening. Do not play it here.
            if (!current()) return
            Log.i(TAG, "listen ready")
        }
        override fun onSpeechBegin() {
            if (closeIfSilent()) return
            if (!current()) return
            speechBegan = true
            if (answerSpeechStartMs == 0L && answerWindowStartMs != 0L) {
                answerSpeechStartMs = SystemClock.elapsedRealtime()
            }
        }
        override fun onSpeechEnd() {
            if (closeIfSilent()) return
            if (!current()) return
            // The mic closed because speech ended. This is not a silent timeout.
            listenSpeechDetected = true
            if (audioEndedAtMs == 0L) audioEndedAtMs = SystemClock.elapsedRealtime()
        }
        override fun onPartial(nbest: List<String>) {
            onPartialResults(nbest)
        }
        override fun onPartialResults(partialResults: List<String>) {
            if (closeIfSilent()) return
            if (!current()) return
            logGoogleNBest("partial", partialResults)
            val raw = partialResults
            if (raw.none { it.isNotBlank() }) return
            partials = raw
            sessionPartials = raw
            if (spokenLetters() == null) return
            listenSpeechDetected = true
            speechBegan = true
            // A letter in this partial ends the listen now. Other words keep listening.
            absorbAnswer(rawFinals = emptyList(), allowUnmatched = false)
        }

        override fun onFinal(nbest: List<String>) {
            // Late result after a focus-loss abort is not graded.
            if (focusPaused) return
            if (closeIfSilent()) return
            if (!current()) return
            if (!errorGate.compareAndSet(false, true)) return
            logGoogleNBest("final", nbest)
            val raw = nbest
            lastFinals = raw.map { it.trim() }.filter { it.isNotEmpty() }
            ownListen?.let { ow ->
                when (val step = ow.onFinal(raw, listenDeadlineMs - SystemClock.elapsedRealtime())) {
                    OwnWordsListen.Step.Restart -> { restartOwnWordsListen(session); return }
                    is OwnWordsListen.Step.Finish -> {
                        lastFinals = ow.alternatives()
                        complete(step.text); return
                    }
                    OwnWordsListen.Step.Keep -> return
                }
            }
            if (raw.any { it.isNotBlank() }) listenSpeechDetected = true
            if (absorbAnswer(raw, allowUnmatched = true)) return
            val usedPartial = raw.none { it.isNotBlank() } && partials.any { it.isNotBlank() }
            val text = ListenWindow.finalOrPartial(raw, partials)
            if (usedPartial) {
                lastFinals = partials.map { it.trim() }.filter { it.isNotEmpty() }
                Log.i(TAG, "listen ok: EMPTY_FINAL kept partial")
            } else Log.i(TAG, "listen ok: RESULT")
            Log.d(TAG, "heard: $text")
            complete(text)
        }

        /**
         * Letter on this card: finish with that letter so the tap path runs.
         * Other text, or speech that ended with nothing usable: finish without
         * a silence restart. [allowUnmatched] is false while the person may
         * still be talking (a partial "option" is not yet "option b").
         * @return true when this listen was completed here.
         */
        private fun absorbAnswer(rawFinals: List<String>, allowUnmatched: Boolean): Boolean {
            val letters = spokenLetters() ?: return false
            val decision = SpokenLetter.decide(
                rawFinals,
                partials,
                letters,
                speechDetected = listenSpeechDetected || speechBegan
            )
            if (!allowUnmatched && decision !is SpokenLetter.Decision.Match) return false
            when (decision) {
                is SpokenLetter.Decision.Match -> {
                    Log.i(TAG, "spoken letter ${decision.letter}")
                    Log.i(TAG, "listen ok: RESULT")
                    Log.d(TAG, "heard letter: ${decision.letter}")
                    complete(decision.letter)
                }
                is SpokenLetter.Decision.Unmatched -> {
                    Log.i(TAG, "listen ok: UNMATCHED")
                    if (decision.heard.isNotBlank()) {
                        Log.d(TAG, "heard: ${decision.heard}")
                        complete(decision.heard)
                    } else {
                        listenSpeechDetected = true
                        complete(null)
                    }
                }
                SpokenLetter.Decision.Empty -> return false
            }
            return true
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
            // Whisper and Vosk have not delivered a final. Try the next engine.
            // No miss is charged: finishListen does not play the end tone.
            if (activeKind != EngineKind.GOOGLE) {
                engineFallback = true
                if (activeKind == EngineKind.WHISPER) {
                    whisperDownUntilMs = SystemClock.elapsedRealtime() + WHISPER_DOWN_MS
                }
                Log.i(TAG, "engine fallback from=$activeKind error=$error")
                try { instance.detach() } catch (_: Exception) {}
                finishListen(null, tone = false)
                return
            }
            val retryable = error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            val normalEnd = error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            // Detach before any cancel/destroy so this callback cannot chain.
            try { instance.detach() } catch (_: Exception) {}
            // A tap already owns this listen. finishListen(null) would drop it.
            if (synchronized(injectLock) { inFlightClaimed }) return
            ownListen?.let { ow ->
                when (val step = ow.onError(error, listenDeadlineMs - SystemClock.elapsedRealtime())) {
                    OwnWordsListen.Step.Restart -> { restartOwnWordsListen(session); return }
                    is OwnWordsListen.Step.Finish -> {
                        lastFinals = ow.alternatives()
                        complete(step.text); return
                    }
                    else -> Unit
                }
            }
            // A heard letter ends the window. A no-match after speech does too:
            // that used to look like silence and reopen the mic in a loop.
            if (absorbAnswer(emptyList(), allowUnmatched = normalEnd)) return
            ListenWindow.noMatchPartial(error, speechBegan || listenSpeechDetected, partials)?.let {
                Log.i(TAG, "listen ok: NO_MATCH kept partial")
                Log.d(TAG, "heard: $it")
                complete(it)
                return
            }
            when (val p = SilenceRestart.onError(error, restartRecognizer, restartBusyRetries, SystemClock.elapsedRealtime(), listenDeadlineMs)) {
                is SilenceRestart.Plan.Retry -> {
                    restartBusyRetries += 1
                    Log.i(TAG, "restart busy: $error retry in ${p.delayMs}ms")
                    restartSilenceListenAfter(session, p.delayMs)
                    return
                }
                SilenceRestart.Plan.EndQuiet -> {
                    Log.i(TAG, "listen ok: restart busy $error")
                    complete(null)
                    return
                }
                SilenceRestart.Plan.NotMine -> Unit
            }
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
            val spoke = speechBegan || listenSpeechDetected
            if (!spoke &&
                ListenWindow.shouldRestart(speechBegan, error, now, listenDeadlineMs, silenceRestarts) &&
                sessionOpen(session)
            ) {
                silenceRestarts += 1
                val remaining = listenDeadlineMs - now
                Log.i(TAG, "listen restart: silence n=$silenceRestarts remainingMs=$remaining")
                restartSilenceListen(session)
                return
            }
            if (ListenWindow.isMicFault(error)) {
                listenFault = true
                listenErrorCode = error
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
            complete(null)
        }
    }
}

/** h103: confirmation for a judge auto-grade. Pure, no Android. */
internal object GradeConfirm {
    /** How long the lit chip stays visible before the card moves on. */
    const val CHIP_HOLD_MS = 1200L

    fun label(ease: Int): String = when (ease) {
        1 -> "Again"; 2 -> "Hard"; 3 -> "Good"; 4 -> "Easy"; else -> "Good"
    }

    fun ratedStatus(ease: Int): String =
        if (ease >= 3) "Correct, rated ${label(ease)}" else "Rated ${label(ease)}"

    fun queuedStatus(pending: Int): String = "Queued, $pending pending sync"
}
