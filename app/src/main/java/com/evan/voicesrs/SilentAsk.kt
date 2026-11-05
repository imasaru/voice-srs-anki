package com.evan.voicesrs

/** Item 36 + item 62: before auto-silent on the rating listen, ask aloud and listen once. */
object SilentAsk {
    const val QUESTION = "No rating heard. Go silent, or say rate again?"

    const val KEEP_VOICE = "Okay, staying in voice. Your rating?"

    const val MARKED_AGAIN = "No rating, marked Again."

    enum class Reply { YES, NO, NONE, RATE_AGAIN }

    enum class Action { GO_SILENT, KEEP_VOICE, RATE_AGAIN }

    private val yes = setOf("yes", "yeah", "yep", "sure", "ok", "okay", "silent", "go silent", "silent mode", "yes please")
    private val no = setOf("no", "nope", "nah", "no thanks", "not now", "keep voice", "voice", "stay", "not really", "stay in voice")
    private val rateAgain = setOf("rate again", "mark again", "again", "rated again")

    fun parse(heard: String?): Reply {
        val t = (heard ?: return Reply.NONE).lowercase()
            .replace(Regex("[^a-z' ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return Reply.NONE
        if (t in no || t.startsWith("no ")) return Reply.NO
        if (t in rateAgain || t.startsWith("rate again") || t.startsWith("mark again")) return Reply.RATE_AGAIN
        if (t in yes || t.startsWith("yes ")) return Reply.YES
        return Reply.NONE
    }

    /** Only a clear "no" keeps voice. Rate Again is not silent either. */
    fun goSilent(reply: Reply): Boolean = reply == Reply.YES || reply == Reply.NONE

    fun action(reply: Reply): Action = when (reply) {
        Reply.NO -> Action.KEEP_VOICE
        Reply.RATE_AGAIN -> Action.RATE_AGAIN
        Reply.YES, Reply.NONE -> Action.GO_SILENT
    }
}
