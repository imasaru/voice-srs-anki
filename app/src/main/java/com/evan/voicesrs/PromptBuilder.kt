package com.evan.voicesrs

/**
 * Base explanation prompt. The transcript is not included, so a lookahead
 * prefetch and the answered card share one cache row. PROMPT_VERSION is part
 * of [ContentHash]; bumping it misses every cached row.
 */
object PromptBuilder {
    const val PROMPT_VERSION = 2

    /**
     * System prompt for headed thread sections. PROMPT_VERSION is in the cache key.
     */
    const val SYSTEM: String =
        "You are a concise tutor for the Databricks Certified Data Engineer Associate exam. " +
            "Explain flashcards in ≤120 words. Always reply with these headed sections, in order: " +
            "Core concept, Why <letter>, Common traps, Example. " +
            "Core concept is one sentence. Why <letter> names the correct option letter and says why it is right. " +
            "Common traps is a bullet list. Example is a fenced code block. Put code in backticks. " +
            "If the card's answer looks outdated or wrong relative to current Databricks docs, " +
            "end with one line starting \"Note:\". No preamble."

    fun userMessage(front: String, back: String): String {
        return "Card front: ${AnkiText.stripHtml(front)}\nCard back: ${AnkiText.stripHtml(back)}"
    }

    const val FOLLOW_UP_EXTRA =
        " Answer the follow-up in ≤100 words, staying on exam-relevant detail."

    fun followUpSystem(): String = SYSTEM + FOLLOW_UP_EXTRA

    data class ChatMsg(val role: String, val content: String)

    /**
     * Multi-turn thread. Context is the card, the spoken answer, and the
     * explanation. Earlier exchanges are the last four finished follow-ups.
     */
    fun followUpMessages(
        front: String,
        back: String,
        transcript: String,
        explanation: String,
        prior: List<FollowUp>,
        question: String
    ): List<ChatMsg> {
        val context = buildString {
            append(userMessage(front, back))
            append("\nLearner said: ")
            append(transcript.trim())
            if (explanation.isNotBlank()) {
                append("\nExplanation: ")
                append(explanation.trim())
            }
        }
        val messages = ArrayList<ChatMsg>()
        messages += ChatMsg("user", context)
        val done = prior.mapNotNull { follow ->
            val ready = follow.state as? ExplState.Ready ?: return@mapNotNull null
            if (follow.q.isBlank() || ready.text.isBlank()) null
            else follow.q to ready.text
        }.takeLast(4)
        for ((q, a) in done) {
            messages += ChatMsg("user", q)
            messages += ChatMsg("assistant", a)
        }
        messages += ChatMsg("user", question.trim())
        return messages
    }
}
