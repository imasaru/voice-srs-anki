package com.evan.voicesrs

/** Data blobs the one-tap prompt chip row hands to [GeneralChat.runChip]. Pure Kotlin, no Android. */
object PromptChipRow {
    /** Max characters of saved card chats sent with a chip (newest kept). */
    const val MAX_CHATS = 20000

    /** The card on screen as plain text: "Front: ...\nBack: ...". A blank face is skipped; both blank -> "". */
    fun cardBlob(frontHtml: String, backHtml: String): String {
        val front = plain(frontHtml)
        val back = plain(backHtml)
        val lines = mutableListOf<String>()
        if (front.isNotEmpty()) lines += "Front: $front"
        if (back.isNotEmpty()) lines += "Back: $back"
        return lines.joinToString("\n")
    }

    /** Saved card chats, trimmed. If longer than [max], keep only the last [max] characters (the newest). */
    fun chatsBlob(threadsText: String, max: Int = MAX_CHATS): String {
        val t = threadsText.trim()
        return if (t.length > max) t.takeLast(max) else t
    }

    /** HTML to plain text: <br> becomes a newline, other tags are removed, a few entities decoded, trimmed. */
    internal fun plain(html: String): String =
        html.replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .trim()
}
