package com.evan.voicesrs

/**
 * One listen session. DrillService keeps the window, deadline, and restart
 * policy and only talks to this.
 */
interface RecognizerCallbacks {
    fun onReady()
    fun onSpeechBegin()
    fun onPartial(nbest: List<String>)
    fun onFinal(nbest: List<String>)
    fun onError(code: Int)

    /** Google [android.speech.RecognitionListener.onEndOfSpeech]. Other engines no-op. */
    fun onSpeechEnd() {}
}

interface Recognizer {
    val kind: EngineKind

    /** elapsedRealtime when capture stopped, or 0 when the engine does not know. */
    val audioEndedAtMs: Long get() = 0L

    fun start(cb: RecognizerCallbacks, maxMs: Long, silenceMs: Long)

    /** End now and deliver what was heard as a final. */
    fun stop()

    /** Drop everything. No further callbacks. */
    fun cancel()

    fun destroy()

    /** Drop the platform listener without cancelling. Google uses this so a late error cannot chain. */
    fun detach() {}
}
