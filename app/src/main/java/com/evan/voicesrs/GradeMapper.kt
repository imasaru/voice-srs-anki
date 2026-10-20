package com.evan.voicesrs

import java.text.Normalizer
import java.util.Locale

/** What a spoken utterance means during a drill. */
sealed class VoiceAction {
    data class Grade(val ease: Int) : VoiceAction()
    data object Repeat : VoiceAction()
    data object Skip : VoiceAction()
    data object Pause : VoiceAction()
    data object None : VoiceAction()
}

/**
 * Spoken grades and drill commands.
 *
 * Ambiguity rules, applied in order:
 * 1. Normalize: Unicode NFKC, lower-case, punctuation to spaces (apostrophes kept).
 * 2. The whole utterance, if it is exactly a pause, skip, or repeat phrase, is that command.
 * 3. Otherwise drop filler words (that, was, pretty, please, …) and read the remaining tokens.
 * 4. A token is a grade, a command, a negator (not / don't), or unknown.
 * 5. Any unknown token makes the utterance [VoiceAction.None] (so "goodbye" and "sandwich" are not grades).
 * 6. A negator plus anything else is [VoiceAction.None] ("not good" is not Good).
 * 7. Two different eases are [VoiceAction.None] ("good or easy", "1 2").
 * 8. One ease wins over command words in the same utterance ("repeat it wrong" is Again, "gud" is Good).
 * 9. With no ease, one command type wins. Mixed commands ("skip pause") are [VoiceAction.None].
 *
 * Ease map (Anki buttons): 1 again, 2 hard, 3 good, 4 easy.
 * Numbers and Japanese counters use that same map: one/ichi/いち, two/ni/に, three/san/さん/3, four/yon/shi/よん/し/4.
 * A shorter token that is a prefix of again/hard/good/easy (at least 3 letters) is that grade: "goo", "har", "eas".
 * Listed mis-hearings are exact: gud, ez, nope, wrong, no.
 *
 * "again" is the Again grade, including "say that again". The repeat command is the word "repeat"
 * (or an exact phrase such as "one more time"), not the word "again".
 */
object GradeMapper {
    private val EXACT_GRADE_WORDS = setOf("again", "hard", "good", "easy")

    private val PAUSE_PHRASES = setOf(
        "pause", "stop", "quit", "hold on", "stop drill", "pause drill", "end drill", "end session"
    )
    private val SKIP_PHRASES = setOf(
        "skip", "skip it", "skip this", "skip card", "skip this card", "pass",
        "next", "next card", "next one"
    )
    private val REPEAT_PHRASES = setOf(
        "repeat", "repeat that", "repeat it", "repeat please",
        "repeat the question", "repeat the answer", "repeat question", "repeat answer",
        "read it back", "one more time"
    )

    private val FILLERS = setOf(
        "a", "an", "the", "that", "this", "was", "is", "it", "its", "it's",
        "please", "pretty", "very", "so", "really", "just", "i", "id", "i'd",
        "think", "mark", "rate", "give", "me", "my", "to", "for", "of", "and",
        "too", "kinda", "kind", "um", "uh", "card", "say", "like", "well", "got",
        "ive", "i've", "yeah"
    )

    private val EASE = mapOf(
        "again" to 1, "agen" to 1, "agin" to 1,
        "wrong" to 1, "no" to 1, "nope" to 1, "incorrect" to 1, "fail" to 1, "failed" to 1,
        "hard" to 2, "hrd" to 2, "difficult" to 2, "tough" to 2,
        "good" to 3, "goo" to 3, "gud" to 3, "ok" to 3, "okay" to 3, "fine" to 3,
        "easy" to 4, "ez" to 4, "eazy" to 4, "ezy" to 4, "simple" to 4,
        "1" to 1, "one" to 1, "ichi" to 1, "いち" to 1,
        "2" to 2, "two" to 2, "ni" to 2, "に" to 2,
        "3" to 3, "three" to 3, "san" to 3, "さん" to 3,
        "4" to 4, "four" to 4, "yon" to 4, "shi" to 4, "よん" to 4, "し" to 4
    )

    private val CANONICAL = listOf("again" to 1, "hard" to 2, "good" to 3, "easy" to 4)

    /**
     * Exact grade word for the debug TEST_GRADE hook.
     * Returns again/hard/good/easy, or null if [raw] is anything else.
     */
    fun canonicalGradeWord(raw: String?): String? {
        val word = raw?.trim()?.lowercase(Locale.US) ?: return null
        return if (word in EXACT_GRADE_WORDS) word else null
    }

    fun parse(raw: String?): VoiceAction {
        if (raw.isNullOrBlank()) return VoiceAction.None
        val text = normalize(raw)
        if (text.isEmpty()) return VoiceAction.None
        if (text in PAUSE_PHRASES) return VoiceAction.Pause
        if (text in SKIP_PHRASES) return VoiceAction.Skip
        if (text in REPEAT_PHRASES) return VoiceAction.Repeat

        val tokens = text.split(' ').filter { it.isNotEmpty() && it !in FILLERS }
        if (tokens.isEmpty()) return VoiceAction.None

        var ease: Int? = null
        var command: VoiceAction? = null
        var negated = false
        for (token in tokens) {
            if (token == "not" || token == "dont" || token == "don't") {
                negated = true
                continue
            }
            val graded = easeOf(token)
            if (graded != null) {
                if (ease != null && ease != graded) return VoiceAction.None
                ease = graded
                continue
            }
            val cmd = commandOf(token)
            if (cmd != null) {
                if (command != null && command != cmd) return VoiceAction.None
                command = cmd
                continue
            }
            return VoiceAction.None
        }
        if (negated) return VoiceAction.None
        if (ease != null) return VoiceAction.Grade(ease)
        return command ?: VoiceAction.None
    }

    /** Returns 1..4 for a recognized grade, or null for commands, ambiguity, and non-grades. */
    fun wordToEase(text: String): Int? = (parse(text) as? VoiceAction.Grade)?.ease

    fun isRepeat(text: String): Boolean = parse(text) is VoiceAction.Repeat

    fun isSkip(text: String): Boolean = parse(text) is VoiceAction.Skip

    fun isPause(text: String): Boolean = parse(text) is VoiceAction.Pause

    /**
     * New/learning cards don't always offer all four eases. If the mapped ease
     * isn't offered, use the closest available button (e.g. easy->3 when
     * buttons=[1,2,3]; again->2 when buttons=[2,3,4]).
     */
    fun toAvailableEase(ease: Int, buttons: List<Int>): Int {
        if (buttons.isEmpty()) return ease.coerceIn(1, 4)
        if (ease in buttons) return ease
        return buttons.minByOrNull { kotlin.math.abs(it - ease) } ?: ease
    }

    private fun normalize(raw: String): String {
        val nfkc = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val lower = nfkc.lowercase(Locale.US)
        val cleaned = buildString(lower.length) {
            for (ch in lower) {
                if (ch.isLetterOrDigit() || ch == '\'' || ch == ' ') append(ch) else append(' ')
            }
        }
        return cleaned.trim().replace(Regex("\\s+"), " ")
    }

    private fun easeOf(token: String): Int? {
        EASE[token]?.let { return it }
        if (token.length < 3) return null
        for ((word, ease) in CANONICAL) {
            if (token.length < word.length && word.startsWith(token)) return ease
        }
        return null
    }

    private fun commandOf(token: String): VoiceAction? = when (token) {
        "repeat" -> VoiceAction.Repeat
        "skip", "pass", "next" -> VoiceAction.Skip
        "pause", "stop", "quit" -> VoiceAction.Pause
        else -> null
    }
}
