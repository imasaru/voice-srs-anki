package com.evan.voicesrs

/**
 * Pure-Kotlin HTML -> spoken-text for Anki card fields (unit-testable, no Android deps).
 * Strips tags, decodes common entities, collapses whitespace.
 */
object AnkiText {
    private val scriptStyle = Regex("<(script|style)[^>]*>.*?</\\1>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val lineBreak = Regex("<br\\s*/?>|</p>|</div>|</li>|</h[1-6]>", RegexOption.IGNORE_CASE)
    private val tag = Regex("<[^>]+>")
    private val whitespace = Regex("\\s+")
    private val soundMarker = Regex("\\[sound:[^\\]]*]", RegexOption.IGNORE_CASE)
    private val mediaTag = Regex(
        "<(?:img|audio|video|source|object|embed|svg|iframe|link|meta)\\b[^>]*>",
        RegexOption.IGNORE_CASE
    )
    private val numericEntity = Regex("&#(\\d+);")
    private val hexEntity = Regex("&#x([0-9a-fA-F]+);")

    private val namedEntities = mapOf(
        "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">",
        "quot" to "\"", "apos" to "'", "rsquo" to "'", "lsquo" to "'",
        "rdquo" to "\"", "ldquo" to "\"", "ndash" to "-", "mdash" to "-",
        "hellip" to "...", "copy" to "(c)", "reg" to "(r)"
    )

    fun stripHtml(html: String): String {
        var s = scriptStyle.replace(html, " ")
        s = lineBreak.replace(s, "\n")
        s = tag.replace(s, " ")
        s = decodeEntities(s)
        s = whitespace.replace(s, " ")
        return s.trim()
    }

    /**
     * Sanitized HTML for on-screen cards. Keeps line breaks and basic emphasis.
     * Drops images, [sound:…] tags, media, scripts, and styles.
     * Safe to pass to HtmlCompat. Spoken TTS still uses [stripHtml].
     */
    fun toDisplayHtml(html: String): String {
        var s = html.replace("\u0000", "").replace("\u0001", "")
        s = scriptStyle.replace(s, "")
        s = soundMarker.replace(s, "")
        s = mediaTag.replace(s, "")
        s = Regex("(?i)<br\\s*/?>").replace(s, "\u0001BR\u0001")
        s = Regex("(?i)</(?:div|p|li|tr|h[1-6])>").replace(s, "\u0001BR\u0001")
        s = Regex("(?i)<(?:div|p|li|tr|h[1-6])(?:\\s[^>]*)?>").replace(s, "\u0001BR\u0001")
        s = Regex("(?i)<\\s*(b|strong|i|em)\\b[^>]*>").replace(s) {
            "\u0001${it.groupValues[1].lowercase()}\u0001"
        }
        s = Regex("(?i)</\\s*(b|strong|i|em)\\s*>").replace(s) {
            "\u0001/${it.groupValues[1].lowercase()}\u0001"
        }
        s = tag.replace(s, "")
        s = decodeEntities(s)
        s = s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        s = s.replace("\u0001b\u0001", "<b>")
            .replace("\u0001/b\u0001", "</b>")
            .replace("\u0001strong\u0001", "<strong>")
            .replace("\u0001/strong\u0001", "</strong>")
            .replace("\u0001i\u0001", "<i>")
            .replace("\u0001/i\u0001", "</i>")
            .replace("\u0001em\u0001", "<em>")
            .replace("\u0001/em\u0001", "</em>")
            .replace("\u0001BR\u0001", "<br>")
        s = whitespace.replace(s, " ")
        s = Regex("(?:\\s*<br>\\s*){2,}").replace(s, "<br>")
        s = s.trim()
        s = Regex("^(?:<br>\\s*)+").replace(s, "")
        s = Regex("(?:\\s*<br>)+$").replace(s, "")
        return s.trim()
    }

    private fun decodeEntities(s: String): String {
        var out = s
        out = numericEntity.replace(out) { m ->
            m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
        }
        out = hexEntity.replace(out) { m ->
            m.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: m.value
        }
        for ((name, ch) in namedEntities) out = out.replace("&$name;", ch)
        return out
    }
}
