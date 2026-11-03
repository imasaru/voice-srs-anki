package com.evan.voicesrs

import kotlin.random.Random

/**
 * Display and speech order for multiple-choice rows.
 * Each option keeps the letter it was written with. The seed is the card id
 * mixed with the session start, so reveal, re-read, and rotation stay put.
 * Anki is not written. [ContentHash] does not see this order.
 */
object OptionShuffle {
    private val letterRef = Regex("""\b[a-f] and [a-f]\b""")

    fun seed(cardId: Long, sessionStartMs: Long): Long {
        var x = cardId xor sessionStartMs
        x = x xor (x ushr 30)
        x *= -4658895280553007687L // 0xBF58476D1CE4E5B9
        x = x xor (x ushr 27)
        x *= -7723592293110705685L // 0x94D049BB133111EB
        return x xor (x ushr 31)
    }

    /** True when the row is a meta choice or names other letters ("A and B"). */
    fun skips(text: String): Boolean {
        val n = normalize(text)
        if (n.contains("all of the above")) return true
        if (n.contains("none of the above")) return true
        if (n.contains("both of the above")) return true
        return letterRef.containsMatchIn(n)
    }

    fun order(
        options: List<QuestionCard.Option>,
        cardId: Long,
        sessionStartMs: Long,
        enabled: Boolean
    ): List<QuestionCard.Option> {
        if (!enabled || options.size < 2) return options
        if (options.any { skips(it.text) }) return options
        return options.shuffled(Random(seed(cardId, sessionStartMs)))
    }

    /** Stem, then each row as "B. text" in [options] order. Letters are the originals. */
    fun spoken(stem: String, options: List<QuestionCard.Option>): String {
        val rows = options.joinToString(" ") { opt ->
            val text = SpeakableText.speakable(opt.text).trim()
            "${opt.letter}. $text".trim()
        }
        return listOf(SpeakableText.speakable(stem), rows).filter { it.isNotEmpty() }.joinToString(" ")
    }

    private fun normalize(text: String): String {
        val stripped = AnkiText.stripHtml(text).lowercase().replace("&", " and ")
        return stripped.replace(Regex("[^a-z\\s]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
