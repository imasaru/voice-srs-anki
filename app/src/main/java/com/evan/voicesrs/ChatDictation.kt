package com.evan.voicesrs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/** One-shot dictation for a chat question. The drill microphone is already stopped. */
class ChatDictation(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun start(onResult: (String) -> Unit) {
        stop()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            Log.w(TAG, "dictation unavailable")
            return
        }
        recognizer = created
        created.setRecognitionListener(object : RecognitionListener {
            private var delivered = false

            private fun finish(text: String?) {
                if (delivered) return
                delivered = true
                val spoken = text?.trim().orEmpty()
                stop()
                if (spoken.isNotEmpty()) onResult(spoken)
            }

            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                finish(text)
            }

            override fun onError(error: Int) {
                finish(null)
            }

            override fun onReadyForSpeech(params: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle) {}
            override fun onEvent(eventType: Int, params: Bundle) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
        }
        try {
            created.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "dictation start failed")
            stop()
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
