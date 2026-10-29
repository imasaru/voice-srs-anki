package com.evan.voicesrs

/** What the Settings model Test button checks: the chat model, and the decision model when it is on. */
object ModelTestTargets {
    enum class Kind { CHAT, DECISION }

    data class Target(val kind: Kind, val baseUrl: String, val model: String, val apiKey: String)

    fun build(
        chatUrl: String,
        chatModel: String,
        apiKey: String,
        decisionEnabled: Boolean,
        decisionUrl: String,
        decisionModel: String
    ): List<Target> {
        val chat = Target(Kind.CHAT, chatUrl, chatModel, apiKey)
        if (!decisionEnabled) return listOf(chat)
        val decision = Target(
            Kind.DECISION,
            OwnWordsJudge.effectiveBase(decisionUrl, chatUrl),
            decisionModel.trim(),
            apiKey
        )
        return listOf(chat, decision)
    }

    /** p74a: plain word for the decision test reply (rating word, e.g. "good"), never the data class dump. */
    fun decisionReply(verdict: OwnWordsJudge.Verdict): String = when (verdict) {
        is OwnWordsJudge.Verdict.Graded -> verdict.rating.word
        OwnWordsJudge.Verdict.Fallback -> "not understood"
    }

    fun line(kind: Kind, body: String): String = when (kind) {
        Kind.CHAT -> "Chat: $body"
        Kind.DECISION -> "Decision: $body"
    }
}
