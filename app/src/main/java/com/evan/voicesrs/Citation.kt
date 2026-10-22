package com.evan.voicesrs

/**
 * Source, license, and bare URL lines.
 * TTS drops them. The answer and explanation bubbles show them as a footer.
 */
object Citation {
    data class Parts(val body: String, val footer: String)

    private val labeledStart = Regex("""(?i)^(source|license|url)\s*:""")
    private val labeledTail = Regex("""(?i)\s+\b(source|license|url)\s*:.*$""")
    private val urlLine = Regex("""(?i)^https?://\S+(?:\s+\S+){0,3}$""")

    /** A line that is only attribution: Source:, License:, URL:, or a bare URL. */
    fun isCitationLine(plain: String): Boolean {
        val t = plain.trim()
        if (t.isEmpty()) return false
        if (labeledStart.containsMatchIn(t)) return true
        return urlLine.matches(t)
    }

    /**
     * One spoken line. A line that is only a citation becomes blank.
     * A trailing "Source:" / "License:" / "URL:" clause is cut off the end.
     */
    fun withoutCitation(line: String): String {
        val t = line.trim()
        if (t.isEmpty() || isCitationLine(t)) return ""
        return labeledTail.replace(t, "").trim()
    }

    fun splitPlain(text: String): Parts {
        if (text.isBlank()) return Parts("", "")
        val body = ArrayList<String>()
        val foot = ArrayList<String>()
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (isCitationLine(line)) {
                foot += line
                continue
            }
            val kept = withoutCitation(line)
            if (kept.isNotEmpty()) body += kept
            if (kept != line) {
                val tail = line.removePrefix(kept).trim()
                if (tail.isNotEmpty()) foot += tail
            }
        }
        return Parts(body.joinToString("\n"), foot.joinToString("\n"))
    }

    /** Split display HTML on `<br>`. Citation pieces leave the body. */
    fun splitHtml(html: String): Parts {
        if (html.isBlank()) return Parts("", "")
        val pieces = Regex("(?i)<br\\s*/?>").split(html)
        val body = ArrayList<String>()
        val foot = ArrayList<String>()
        for (piece in pieces) {
            val trimmed = piece.trim()
            if (trimmed.isEmpty()) continue
            val plain = AnkiText.stripHtml(trimmed).trim()
            if (plain.isEmpty()) continue
            if (isCitationLine(plain)) {
                foot += plain
                continue
            }
            val kept = withoutCitation(plain)
            if (kept == plain) {
                body += trimmed
            } else {
                if (kept.isNotEmpty()) body += kept
                val tail = plain.removePrefix(kept).trim()
                if (tail.isNotEmpty()) foot += tail
            }
        }
        return Parts(body.joinToString("<br>"), foot.joinToString("\n"))
    }
}
