package com.evan.voicesrs

/**
 * Cut a model reply that invents a Learner/User turn, and the stop sequences
 * sent with chat completions so generation ends at that line.
 */
object SelfTalk {
    val STOP = listOf(
        "\nLearner:",
        "\nLearner said:",
        "\nUser:",
        "\nHuman:"
    )

    private val invented = Regex(
        """(?im)^(?:Learner(?: said)?|User|Human)\s*:"""
    )

    fun clip(text: String): String {
        val match = invented.find(text) ?: return text
        return text.substring(0, match.range.first).trimEnd()
    }
}
