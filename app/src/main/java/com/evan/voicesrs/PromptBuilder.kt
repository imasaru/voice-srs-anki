package com.evan.voicesrs

/**
 * Base explanation prompt. The transcript is not included, so a lookahead
 * prefetch and the answered card share one cache row. PROMPT_VERSION is part
 * of [ContentHash]; bumping it misses every cached row.
 */
object PromptBuilder {
    const val PROMPT_VERSION = 1

    /**
     * Verbatim system prompt (design §7). Do not reflow.
     */
    const val SYSTEM: String =
        "You are a concise tutor for the Databricks Certified Data Engineer Associate exam. " +
            "Explain flashcards in ≤120 words: (1) the core concept in one sentence, " +
            "(2) why the answer is correct, (3) one common exam trap or distractor, " +
            "(4) a tiny example (SQL/PySpark/CLI) only if it clarifies. " +
            "Use plain text, no headings or preamble. If the card's answer looks outdated " +
            "or wrong relative to current Databricks docs, say so in one line starting \"Note:\"."

    fun userMessage(front: String, back: String): String {
        return "Card front: ${AnkiText.stripHtml(front)}\nCard back: ${AnkiText.stripHtml(back)}"
    }
}
