package com.evan.voicesrs

import android.speech.SpeechRecognizer

/**
 * h83: one own-words answer window (letters off). Partials never end it.
 * A silence stop restarts the mic and the segment is appended; it finalizes
 * at the window end, on a command alone, or on a final "answer B" (first segment only).
 */
class OwnWordsListen(private val letters: Set<String>?) {
    sealed class Step {
        data object Keep : Step()
        data object Restart : Step()
        data class Finish(val text: String?) : Step()
    }

    private val segments = mutableListOf<List<String>>()

    fun transcript(): String? =
        segments.map { it.first().trim() }.filter { it.isNotEmpty() }.joinToString(" ").ifBlank { null }

    /** Top stitched transcript first, then one variant per (segment, alternative); max 5. */
    fun alternatives(): List<String> {
        val tops = segments.map { it.first().trim() }
        val out = linkedSetOf(tops.joinToString(" "))
        segments.forEachIndexed { i, seg ->
            seg.drop(1).forEach { alt ->
                out += tops.mapIndexed { j, t -> if (j == i) alt.trim() else t }.joinToString(" ")
            }
        }
        return out.filter { it.isNotBlank() }.take(5)
    }

    fun onPartial(hyps: List<String>): Step = Step.Keep

    fun onFinal(hyps: List<String>, remainingMs: Long): Step {
        val clean = hyps.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return onSilence(remainingMs)
        val top = clean.first()
        if (segments.isEmpty()) {
            OwnWordsMode.prefixedLetter(top, letters, partial = false)?.let { return Step.Finish(it) }
            if (isCommandAlone(top)) return Step.Finish(top)
        }
        segments += clean
        return if (remainingMs >= ListenWindow.MIN_REMAINING_MS) Step.Restart else Step.Finish(transcript())
    }

    /** NO_MATCH / SPEECH_TIMEOUT restart inside the window; other errors return null (caller's normal path). */
    fun onError(error: Int, remainingMs: Long): Step? {
        if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) return null
        return onSilence(remainingMs)
    }

    fun onDeadline(): Step = Step.Finish(transcript())

    private fun onSilence(remainingMs: Long): Step =
        if (remainingMs >= ListenWindow.MIN_REMAINING_MS) Step.Restart else Step.Finish(transcript())

    private fun isCommandAlone(text: String): Boolean =
        OwnWordsMode.answerAction(text, lettersOn = false) !is VoiceAction.None &&
            OwnWordsMode.answerAction(text, lettersOn = false) !is VoiceAction.Grade ||
            ThinkTime.isShowAnswer(text)
}
