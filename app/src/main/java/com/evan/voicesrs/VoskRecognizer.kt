package com.evan.voicesrs

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import android.util.Log
import org.vosk.Model
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/** Offline Vosk. Live partials keep letter and command paths working. */
class VoskRecognizer(private val context: Context) : Recognizer {
    override val kind: EngineKind = EngineKind.VOSK

    private val main = Handler(Looper.getMainLooper())
    private var service: SpeechService? = null
    private var callbacks: RecognizerCallbacks? = null
    private var cancelled = false
    private var endedAt = 0L

    override val audioEndedAtMs: Long get() = endedAt

    override fun start(cb: RecognizerCallbacks, maxMs: Long, silenceMs: Long) {
        callbacks = cb
        cancelled = false
        endedAt = 0L
        val dir = VoskModelStore.modelDir(context.filesDir)
        if (!VoskModelStore.isReady(dir)) {
            main.post { cb.onError(SpeechRecognizer.ERROR_CLIENT) }
            return
        }
        try {
            val model = VoskModels.open(dir)
            val recognizer = org.vosk.Recognizer(model, 16000f)
            val speech = SpeechService(recognizer, 16000f)
            service = speech
            val ok = speech.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    val text = hypothesis?.let { VoskJson.partial(it) } ?: return
                    main.post { if (!cancelled) cb.onPartial(listOf(text)) }
                }

                override fun onResult(hypothesis: String?) {
                    deliver(cb, hypothesis)
                }

                override fun onFinalResult(hypothesis: String?) {
                    deliver(cb, hypothesis)
                }

                override fun onError(e: Exception?) {
                    Log.w(TAG, "vosk error")
                    main.post { if (!cancelled) cb.onError(SpeechRecognizer.ERROR_CLIENT) }
                }

                override fun onTimeout() {
                    main.post { if (!cancelled) cb.onError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT) }
                }
            })
            if (!ok) {
                Log.w(TAG, "vosk start failed")
                main.post { if (!cancelled) cb.onError(SpeechRecognizer.ERROR_CLIENT) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "vosk start failed", e)
            main.post { if (!cancelled) cb.onError(SpeechRecognizer.ERROR_CLIENT) }
        }
    }

    private fun deliver(cb: RecognizerCallbacks, hypothesis: String?) {
        if (endedAt == 0L) endedAt = SystemClock.elapsedRealtime()
        val text = hypothesis?.let { VoskJson.text(it) }
        val nbest = if (text.isNullOrBlank()) emptyList() else listOf(text)
        main.post { if (!cancelled) cb.onFinal(nbest) }
    }

    override fun stop() {
        if (endedAt == 0L) endedAt = SystemClock.elapsedRealtime()
        try { service?.stop() } catch (_: Exception) {}
    }

    override fun cancel() {
        cancelled = true
        callbacks = null
        try { service?.cancel() } catch (_: Exception) {}
    }

    override fun destroy() {
        cancelled = true
        callbacks = null
        val dying = service
        service = null
        try { dying?.cancel() } catch (_: Exception) {}
        try { dying?.shutdown() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "VoskRecognizer"
    }
}

/** One shared model. Recognizers are per listen; the model stays open. */
internal object VoskModels {
    private var model: Model? = null
    private var path: String? = null

    @Synchronized
    fun open(dir: File): Model {
        val existing = model
        if (existing != null && path == dir.absolutePath) return existing
        try { model?.close() } catch (_: Exception) {}
        val opened = Model(dir.absolutePath)
        model = opened
        path = dir.absolutePath
        return opened
    }

    @Synchronized
    fun close() {
        try { model?.close() } catch (_: Exception) {}
        model = null
        path = null
    }
}
