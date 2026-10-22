package com.evan.voicesrs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * One-shot dictation for a chat question.
 * This is a separate [SpeechRecognizer] from the drill. The drill listener is
 * cancelled and destroyed before [start], and pause must not cancel this one.
 * Partials fill the field. An error keeps a partial when there is one, and
 * otherwise reports a miss so the UI can say it didn't catch that.
 */
class ChatDictation(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun start(onPartial: (String) -> Unit, onFinal: (String) -> Unit, onMiss: () -> Unit) {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onMiss()
            return
        }
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w(TAG, "dictation unavailable")
            onMiss()
            return
        }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            private var delivered = false
            private var latest = ""

            private fun deliver(result: DictationGate.Result) {
                if (delivered) return
                delivered = true
                stop()
                when (result) {
                    is DictationGate.Result.Text -> onFinal(result.text)
                    DictationGate.Result.Miss -> onMiss()
                    DictationGate.Result.Empty -> onMiss()
                }
            }

            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                deliver(DictationGate.result(text, latest, error = false))
            }

            override fun onError(error: Int) {
                Log.i(TAG, "dictation error $error")
                deliver(DictationGate.result(null, latest, error = true))
            }

            override fun onPartialResults(partialResults: Bundle) {
                val text = partialResults
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isEmpty()) return
                latest = text
                onPartial(text)
            }

            override fun onReadyForSpeech(params: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle) {}
        })
        val silence = ListenWindow.DEFAULT_SILENCE_MS.toLong()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silence)
        }
        try {
            created.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "dictation start failed")
            stop()
            onMiss()
        }
    }

    fun stop() {
        val dying = recognizer ?: return
        recognizer = null
        try { dying.setRecognitionListener(null) } catch (_: Exception) {}
        try { dying.cancel() } catch (_: Exception) {}
        try { dying.destroy() } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "ChatDictation"
    }
}
