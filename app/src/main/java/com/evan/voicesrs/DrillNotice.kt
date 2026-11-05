package com.evan.voicesrs

/**
 * Every drill notice is drawn on the status line.
 * The microphone sentence is only for a real mic failure: audio, permission,
 * or no recognizer. Silence, no-match, and speech-timeout do not blame the mic.
 */
object DrillNotice {
    /** SpeechRecognizer.ERROR_AUDIO. */
    const val ERROR_AUDIO = 3
    /** SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS. */
    const val ERROR_INSUFFICIENT_PERMISSIONS = 9
    /** SpeechRecognizer.ERROR_NO_MATCH. */
    const val ERROR_NO_MATCH = 7
    /** SpeechRecognizer.ERROR_SPEECH_TIMEOUT. */
    const val ERROR_SPEECH_TIMEOUT = 6

    const val MIC_WARNING =
        "The microphone does not seem to be working. " +
            "Please check the microphone permission and your connection, then restart the drill."

    /** h148b: status line while another app holds audio focus. */
    const val OTHER_AUDIO = "Paused for other audio"

    /** h148b: after a focus wait, drop our notice and restore [before]; any newer status stays. */
    fun afterFocusWait(current: String, before: String): String =
        if (current == OTHER_AUDIO) before else current

    enum class Sink { STATUS, CARD }

    fun blamesMic(error: Int?, recognizerUnavailable: Boolean): Boolean {
        if (recognizerUnavailable) return true
        return error == ERROR_AUDIO || error == ERROR_INSUFFICIENT_PERMISSIONS
    }

    fun text(error: Int?, recognizerUnavailable: Boolean): String? {
        if (!blamesMic(error, recognizerUnavailable)) return null
        return MIC_WARNING
    }

    fun route(@Suppress("UNUSED_PARAMETER") notice: String): Sink = Sink.STATUS

    fun sink(): Sink = Sink.STATUS

    /**
     * h155a: text for the chat notice row. A general chat notice wins. Otherwise the
     * skip notice shows while the drill runs, on the skipped card and the next one.
     */
    fun chatNotice(general: String?, state: DrillUiState): String? {
        val g = general?.trim().orEmpty()
        if (g.isNotEmpty()) return g
        val skip = state.skipNotice.trim()
        if (!state.drillRunning || skip.isEmpty()) return null
        val ord = state.questionOrdinal
        return if (ord >= state.skipNoticeOrdinal && ord <= state.skipNoticeOrdinal + 1) skip else null
    }
}