package com.evan.voicesrs

/**
 * Image-card display HTML and the speak / no-auto-advance policy.
 * Fetching bytes is [CardMedia]; this object stays pure.
 */
object CardImages {
    const val PLACEHOLDER = "[image]"

    private val imgTag = Regex("(?is)<img\\b[^>]*>")
    private const val TOKEN_OPEN = '\uE100'
    private const val TOKEN_CLOSE = '\uE101'

    data class Policy(
        val speakPrompt: Boolean,
        val autoAdvance: Boolean,
        val voiceGrade: Boolean
    )

    fun waitForTap(html: String): Boolean =
        AnkiText.needsScreen(html) || AnkiText.isImageOcclusion(html)

    fun policy(html: String): Policy {
        val wait = waitForTap(html)
        return Policy(
            speakPrompt = wait,
            autoAdvance = !wait,
            voiceGrade = !wait
        )
    }

    /**
     * Same sanitizer as [AnkiText.displayHtml], with IMG tags kept when
     * [resolved] has a src for that filename. A miss becomes [PLACEHOLDER].
     */
    fun displayHtml(html: String, resolved: Map<String, String> = emptyMap()): String {
        if (!html.contains("<img", ignoreCase = true)) return AnkiText.displayHtml(html)
        val tokens = ArrayList<String>()
        val shielded = imgTag.replace(html) { match ->
            val name = AnkiText.imageRefs(match.value).singleOrNull()
            val src = if (name != null) resolved[name] else null
            val replacement = if (src != null) {
                """<img src="${escapeAttr(src)}">"""
            } else {
                PLACEHOLDER
            }
            val id = tokens.size
            tokens += replacement
            "$TOKEN_OPEN$id$TOKEN_CLOSE"
        }
        var out = AnkiText.displayHtml(shielded)
        for (i in tokens.indices) {
            out = out.replace("$TOKEN_OPEN$i$TOKEN_CLOSE", tokens[i])
        }
        return out
    }

    private fun escapeAttr(value: String): String {
        return value.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}
