package com.evan.voicesrs

import java.util.Locale

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

    /**
     * Forced-tap image-wait voice fallback. Built from a [Policy] so the same
     * decision is unit-testable:
     *
     *  - [speakPrompt] speaks a prompt telling the learner to say "next".
     *  - [autoAdvance] resumes automatically after [timeoutMs] of silence.
     *    A spoken "next" or "continue" always resumes.
     *
     * [autoAdvance] / [timeoutMs] are the forced-tap resume settings, distinct
     * from [Policy.autoAdvance] (which means the card needs no forced tap).
     */
    data class WaitFallback(
        val speakPrompt: Boolean,
        val autoAdvance: Boolean,
        val timeoutMs: Long
    ) {
        /** Prompt to speak, or null when [speakPrompt] is false. */
        val prompt: String?
            get() = if (speakPrompt) "Say next or continue to move on." else null

        /** Auto-advance timeout in ms; 0 when auto-advance is off. */
        val autoAdvanceTimeoutMs: Long
            get() = if (autoAdvance && timeoutMs > 0L) timeoutMs else 0L

        /** True when [heard] should resume the forced-tap wait. */
        fun resumes(heard: String?): Boolean {
            val text = heard?.trim()?.lowercase(Locale.US).orEmpty()
            return text == "next" || text == "continue"
        }
    }

    /**
     * The forced-tap image-wait fallback for [policy]. [autoAdvance] and
     * [timeoutMs] are the resume settings; a timeout of 0 disables auto-advance.
     */
    fun waitFallback(policy: Policy, autoAdvance: Boolean, timeoutMs: Long = 0L): WaitFallback =
        WaitFallback(policy.speakPrompt, autoAdvance, timeoutMs)

    /**
     * Whether a forced-tap wait should resume given what was [heard] and how long
     * the learner has been waiting ([elapsedMs]). A spoken "next"/"continue"
     * resumes immediately; otherwise auto-advance once
     * [WaitFallback.autoAdvanceTimeoutMs] has elapsed. A timeout of 0 never
     * auto-advances.
     *
     * [pausedMs] is the time the drill or chat was paused during [elapsedMs]; it
     * is excluded from the auto-advance timer so a paused wait never counts toward
     * the timeout. Defaults to 0 for the tap-only and silent paths. Pure so the
     * same call path is unit-tested.
     */
    fun shouldResume(
        fallback: WaitFallback,
        heard: String?,
        elapsedMs: Long,
        pausedMs: Long = 0
    ): Boolean =
        fallback.resumes(heard) ||
            (fallback.autoAdvanceTimeoutMs > 0L &&
                (elapsedMs - pausedMs).coerceAtLeast(0L) >= fallback.autoAdvanceTimeoutMs)

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
