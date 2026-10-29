package com.evan.voicesrs

/** Item 36: before auto-silent on the rating listen, ask aloud and listen once. */
object SilentAsk {
    const val QUESTION = "Do you want to go into silent mode?"

    const val KEEP_VOICE = "Okay, staying in voice. Your rating?"

    enum class Reply { YES, NO, NONE }

    private val yes = setOf("yes", "yeah", "yep", "sure", "ok", "okay", "silent", "go silent", "silent mode", "yes please")
    private val no = setOf("no", "nope", "nah", "no thanks", "not now", "keep voice", "voice", "stay", "not really", "stay in voice")

    fun parse(heard: String?): Reply {
        val t = (heard ?: return Reply.NONE).lowercase()
            .replace(Regex("[^a-z' ]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return Reply.NONE
        if (t in no || t.startsWith("no ")) return Reply.NO
        if (t in yes || t.startsWith("yes ")) return Reply.YES
        return Reply.NONE
    }

    /** Only a clear "no" keeps voice. No reply or an unclear one goes silent, as before. */
    fun goSilent(reply: Reply): Boolean = reply != Reply.NO
}
