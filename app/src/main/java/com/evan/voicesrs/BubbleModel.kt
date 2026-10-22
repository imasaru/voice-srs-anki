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
    const val NOT_ANSWERED = "Not answered"

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
        val retry: Boolean,
        val footer: String = ""
    )

    /** Learner's answer bubble. Replaces the old "answer · rating" line. */
    data class UserBubble(
        val answer: String,
        val answered: Boolean,
        val rating: String,
        val ease: Int?
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

    /** Past question bubbles keep two lines until the learner taps to expand. */
    const val COLLAPSED_LINES = 2

    fun cardText(front: String, expanded: Boolean): String {
        val full = fullQuestion(front).ifBlank { AnkiText.stripHtml(front) }
        if (expanded) return full
        return previewLines(full, COLLAPSED_LINES).ifBlank { full }
    }

    fun previewLines(full: String, maxLines: Int): String {
        if (maxLines <= 0) return ""
        return full.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(maxLines)
            .joinToString("\n")
    }

    /**
     * ANSWER text (or [NOT_ANSWERED]) and a separate rating word.
     * A blank transcript is not an em dash.
     */
    fun userBubble(transcript: String, ease: Int?): UserBubble {
        val spoken = spokenAnswer(transcript)
        val answered = spoken != BLANK_ANSWER
        val safeEase = ease?.takeIf { it in 1..4 }
        return UserBubble(
            answer = if (answered) spoken else NOT_ANSWERED,
            answered = answered,
            rating = if (safeEase == null) "" else ratingLabel(safeEase),
            ease = safeEase
        )
    }

    /** Plain form of [userBubble] for surfaces that are one text view. */
    fun userPlain(transcript: String, ease: Int?): String {
        val bubble = userBubble(transcript, ease)
        return "ANSWER\n${bubble.answer}\nRATING\n${bubble.rating}"
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
                val parts = shownParts(state.partial, front, back)
                if (state.thinking && parts.body.isBlank() && parts.footer.isBlank()) {
                    AiDisplay(true, THINKING, "", unread = false, retry = false)
                } else {
                    AiDisplay(true, GENERATING, parts.body, unread = false, retry = false, footer = parts.footer)
                }
            }
            is ExplState.Ready -> {
                val parts = shownParts(state.text, front, back)
                if (parts.body.isBlank() && parts.footer.isBlank()) {
                    AiDisplay(true, AiMessages.NO_TEXT, "", unread = false, retry = true)
                } else {
                    AiDisplay(
                        true,
                        "",
                        parts.body,
                        unread = unread && !state.cached,
                        retry = false,
                        footer = parts.footer
                    )
                }
            }
            is ExplState.Error -> AiDisplay(true, state.message, "", unread = false, retry = state.retryable)
        }
    }

    /** Body without Source / License / URL lines. Those stay on [Citation.Parts.footer]. */
    private fun shownParts(text: String, front: String, back: String): Citation.Parts {
        return Citation.splitPlain(visibleExplanation(text, front, back))
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
            user = userPlain(transcript, ease),
            aiBody = ai.body,
            aiStatus = ai.status,
            unread = ai.unread,
            retry = ai.retry
        )
    }
}
