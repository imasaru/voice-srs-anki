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

    fun userMessage(front: String, back: String, label: String = ""): String {
        val body = "Card front: ${AnkiText.stripHtml(front)}\nCard back: ${AnkiText.stripHtml(back)}"
        val name = label.trim()
        if (name.isEmpty()) return body
        return "Card: $name\n$body"
    }

    /**
     * A question with no card on screen. [SYSTEM] and [PROMPT_VERSION] stay as they are.
     */
    fun generalSystem(): String =
        "You are a concise tutor for the Databricks Certified Data Engineer Associate exam. " +
            "The learner asked a general question about the selected deck. There is no specific card. " +
            "Stay under 120 words. No preamble."

    fun generalMessages(deck: String, question: String): List<ChatMsg> {
        return listOf(
            ChatMsg(
                "user",
                "Deck: ${deck.trim()}\nThere is no specific card.\nQuestion: ${question.trim()}"
            )
        )
    }

    const val FOLLOW_UP_EXTRA =
        " Answer the follow-up in ≤100 words, staying on exam-relevant detail."

    fun followUpSystem(): String = SYSTEM + FOLLOW_UP_EXTRA

    data class ChatMsg(val role: String, val content: String)

    /**
     * Multi-turn thread. The card is the first user message. The spoken
     * answer and explanation are their own user/assistant turns. Earlier
     * exchanges are the last four finished follow-ups. History is never
     * labelled "Learner said:".
     */
    fun followUpMessages(
        front: String,
        back: String,
        transcript: String,
        explanation: String,
        prior: List<FollowUp>,
        question: String,
        label: String = ""
    ): List<ChatMsg> {
        val messages = ArrayList<ChatMsg>()
        messages += ChatMsg("user", userMessage(front, back, label))
        val said = transcript.trim()
        if (said.isNotEmpty()) messages += ChatMsg("user", said)
        val expl = explanation.trim()
        if (expl.isNotEmpty()) messages += ChatMsg("assistant", expl)
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

    /**
     * The card is still unanswered. Concepts and reasoning only.
     * The back of the card is not included. [SYSTEM] and [PROMPT_VERSION] stay as they are.
     */
    fun hintSystem(): String =
        "You are a concise tutor for the Databricks Certified Data Engineer Associate exam. " +
            "The learner has not answered this card yet. Explain the relevant concepts and help him reason toward the answer. " +
            "Do not reveal or confirm the correct option or the correct answer. " +
            "Say that you are withholding the answer until he answers. " +
            "Stay under 120 words. No preamble."

    fun hintMessages(front: String, prior: List<FollowUp>, question: String, label: String = ""): List<ChatMsg> {
        val messages = ArrayList<ChatMsg>()
        val heading = label.trim().let { name ->
            if (name.isEmpty()) "" else "Card: $name\n"
        }
        messages += ChatMsg(
            "user",
            "${heading}Card front: ${AnkiText.stripHtml(front)}\nThe learner has not answered yet. The answer is hidden."
        )
        val done = prior.mapNotNull { follow ->
            val ready = follow.state as? ExplState.Ready ?: return@mapNotNull null
            if (follow.q.isBlank() || ready.text.isBlank()) null else follow.q to ready.text
        }.takeLast(4)
        for ((q, a) in done) {
            messages += ChatMsg("user", q)
            messages += ChatMsg("assistant", a)
        }
        messages += ChatMsg("user", question.trim())
        return messages
    }
}
