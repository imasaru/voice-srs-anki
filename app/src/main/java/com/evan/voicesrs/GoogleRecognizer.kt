package com.evan.voicesrs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * The on-device Google recognizer. Intent extras match the previous
 * DrillService path: complete-silence is an Int (a Long was ignored),
 * possibly-complete stays a Long, and nothing calls stopListening().
 */
class GoogleRecognizer(private val context: Context, private val language: String? = SpokenLetter.LANGUAGE) : Recognizer {
    override val kind: EngineKind = EngineKind.GOOGLE

    private var speech: SpeechRecognizer? = null
    private var callbacks: RecognizerCallbacks? = null

    /** True when the bundle just delivered had no RESULTS_RECOGNITION list. */
    var resultsMissing: Boolean = false
        private set

    fun open(): Boolean {
        val created = SpeechRecognizer.createSpeechRecognizer(context)
        speech = created
        return true
    }

    override fun start(cb: RecognizerCallbacks, maxMs: Long, silenceMs: Long) {
        callbacks = cb
        val created = speech ?: return
        created.setRecognitionListener(Adapter())
        val complete = SpokenLetter.silenceExtra(silenceMs)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (language != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, SpokenLetter.PREFER_OFFLINE)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, SpokenLetter.MAX_RESULTS)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, complete)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
        }
        created.startListening(intent)
    }

    override fun stop() {
        // stopListening() re-enters onError(ERROR_CLIENT). The service finishes
        // from the partials it already holds.
    }

    override fun cancel() {
        callbacks = null
        val dying = speech ?: return
        try { dying.setRecognitionListener(null) } catch (_: Exception) {}
        try { dying.cancel() } catch (_: Exception) {}
    }

    override fun destroy() {
        val dying = speech ?: return
        speech = null
        callbacks = null
        try { dying.setRecognitionListener(null) } catch (_: Exception) {}
        try { dying.cancel() } catch (_: Exception) {}
        try { dying.destroy() } catch (_: Exception) {}
    }

    override fun detach() {
        try { speech?.setRecognitionListener(null) } catch (_: Exception) {}
    }

    private inner class Adapter : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            callbacks?.onReady()
        }

        override fun onBeginningOfSpeech() {
            callbacks?.onSpeechBegin()
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            callbacks?.onSpeechEnd()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            callbacks?.onPartial(take(partialResults))
        }

        override fun onResults(results: Bundle?) {
            callbacks?.onFinal(take(results))
        }

        override fun onError(error: Int) {
            callbacks?.onError(error)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}

        private fun take(bundle: Bundle?): List<String> {
            val raw = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            resultsMissing = raw == null
            return raw?.toList().orEmpty()
        }
    }
}
