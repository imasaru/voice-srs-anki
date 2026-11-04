package com.evan.voicesrs

/**
 * h142a: a filler-only answer transcript ("huh", "um", configured phrases) is noise, not an answer.
 * Pure Kotlin — no Android imports — so it is unit-tested directly.
 *
 * Matching is whole-transcript only: "uh the index" is NOT filler (the normalized text must equal a
 * configured phrase exactly). [normalize] is shared by [isFiller], [encode] and [decode].
 */
object FillerFilter {

    val HESITATIONS: Set<String> = setOf("um", "uh", "hmm", "er", "huh", "mm", "ah")

    val DEFAULT_PHRASES: List<String> = HESITATIONS.toList() + listOf("thank you", "thanks for watching", "you")

    fun normalize(s: String): String = buildString {
        for (c in s.lowercase()) {
            when {
                c.isLetter() || c.isDigit() || c == ' ' -> append(c)
                else -> append(' ')
            }
        }
    }
        .replace(Regex("\\s+"), " ")
        .trim()

    fun isFiller(text: String, enabled: Boolean, phrases: List<String>): Boolean =
        enabled && normalize(text).isNotEmpty() &&
            normalize(text) in phrases.map(::normalize).toSet()

    fun encode(phrases: List<String>): String = phrases.joinToString("\n")

    fun decode(s: String?): List<String> {
        if (s.isNullOrEmpty()) return DEFAULT_PHRASES
        val seen = LinkedHashSet<String>()
        for (line in s.split("\n")) {
            val n = normalize(line)
            if (n.isNotEmpty()) seen.add(n)
        }
        return if (seen.isEmpty()) DEFAULT_PHRASES else seen.toList()
    }
}
