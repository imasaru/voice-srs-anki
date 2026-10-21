package com.evan.voicesrs

/**
 * Pure display helpers for one graded exchange. The chat pane binds these
 * strings; it never binds [PromptBuilder.userMessage] or the system prompt.
 */
object BubbleModel {
    const val QUEUED = "Queued"
    const val GENERATING = "Generating…"
    const val THINKING = "Thinking…"
    const val BLANK_ANSWER = "—"

    data class Exchange(
        val card: String,
        val user: String,
        val aiBody: String,
        val aiStatus: String,
        val unread: Boolean,
        val retry: Boolean
    )

    data class AiDisplay(
        val visible: Boolean,
        val status: String,
        val body: String,
        val unread: Boolean,
        val retry: Boolean
    )

    /**
     * First non-empty line after HTML is stripped. Block tags and `<br>`
     * become line breaks. A single-paragraph card returns the whole stem.
     */
    fun firstLine(html: String): String {
        return fullQuestion(html).lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
    }

    /** Full question, tags removed, one visual line per block. */
    fun fullQuestion(html: String): String {
        if (html.isBlank()) return ""
        if (!html.contains('<')) {
            return html.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        }
        val display = AnkiText.displayHtml(html)
        return display.split(Regex("(?i)<br\\s*/?>"))
            .map { AnkiText.stripHtml(it) }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun cardText(front: String, expanded: Boolean): String {
        val full = fullQuestion(front).ifBlank { AnkiText.stripHtml(front) }
        if (expanded) return full
        return firstLine(front).ifBlank { full }
    }

    /**
     * Recognised spoken answer (or letter) plus the grade.
     * Blank transcript is an em dash: `— · Good`.
     */
    fun userLabel(transcript: String, ease: Int?): String {
        return "${spokenAnswer(transcript)} · ${ratingLabel(ease)}"
    }

    fun spokenAnswer(raw: String): String {
        val t = raw.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return BLANK_ANSWER
        val letter = Regex("^(?i)(?:answer\\s+|option\\s+)?([A-D])$").matchEntire(t)
        if (letter != null) return letter.groupValues[1].uppercase()
        return t
    }

    fun ratingLabel(ease: Int?): String = when (ease) {
        1 -> "Again"
        2 -> "Hard"
        3 -> "Good"
        4 -> "Easy"
        else -> BLANK_ANSWER
    }

    /**
     * Visible explanation text. The raw prompt, the system prompt, and a
     * leading prompt echo are dropped. Think blocks are already stripped by
     * the client; this is a second pass so a cached row cannot leak them.
     */
    fun visibleExplanation(text: String, front: String, back: String): String {
        var t = ThinkStrip.strip(text)
        if (t.isBlank()) return ""
        val prompt = PromptBuilder.userMessage(front, back)
        if (t.startsWith(prompt)) t = t.removePrefix(prompt).trim()
        if (t.startsWith(PromptBuilder.SYSTEM)) t = t.removePrefix(PromptBuilder.SYSTEM).trim()
        t = ThinkStrip.strip(t)
        if (isPromptEcho(t, front, back)) return ""
        return t
    }

    fun isPromptEcho(text: String, front: String, back: String): Boolean {
        val t = ThinkStrip.strip(text)
        if (t.isBlank()) return false
        if (t == PromptBuilder.SYSTEM) return true
        val prompt = PromptBuilder.userMessage(front, back)
        if (t == prompt) return true
        if (t.startsWith("Card front:") && t.contains("Card back:")) {
            val rest = if (t.startsWith(prompt)) t.removePrefix(prompt).trim() else ""
            if (t.startsWith(prompt) && rest.isEmpty()) return true
            if (t == prompt) return true
        }
        return false
    }

    fun displayAi(state: ExplState, front: String, back: String, unread: Boolean): AiDisplay {
        return when (state) {
            ExplState.None -> AiDisplay(false, "", "", unread = false, retry = false)
            ExplState.Queued -> AiDisplay(true, QUEUED, "", unread = false, retry = false)
            ExplState.Pending -> AiDisplay(true, GENERATING, "", unread = false, retry = false)
            is ExplState.Streaming -> {
                val body = visibleExplanation(state.partial, front, back)
                if (state.thinking && body.isBlank()) {
                    AiDisplay(true, THINKING, "", unread = false, retry = false)
                } else {
                    AiDisplay(true, GENERATING, body, unread = false, retry = false)
                }
            }
            is ExplState.Ready -> {
                val body = visibleExplanation(state.text, front, back)
                if (body.isBlank()) {
                    AiDisplay(true, AiMessages.NO_TEXT, "", unread = false, retry = true)
                } else {
                    AiDisplay(true, "", body, unread = unread && !state.cached, retry = false)
                }
            }
            is ExplState.Error -> AiDisplay(true, state.message, "", unread = false, retry = state.retryable)
        }
    }

    /** The three strings one exchange shows. Used by tests to prove no prompt echo. */
    fun exchange(
        front: String,
        back: String,
        transcript: String,
        ease: Int?,
        state: ExplState,
        unread: Boolean = false,
        cardExpanded: Boolean = false
    ): Exchange {
        val ai = displayAi(state, front, back, unread)
        return Exchange(
            card = cardText(front, cardExpanded),
            user = userLabel(transcript, ease),
            aiBody = ai.body,
            aiStatus = ai.status,
            unread = ai.unread,
            retry = ai.retry
        )
    }
}
