package com.evan.voicesrs

/**
 * Whether the chat follows the live question or holds it off-screen.
 * The pane measures "at bottom" (within [BOTTOM_SLOP_DP]) and applies [Decision].
 * Nothing here starts a grade. A hold never advances the card.
 */
class ChatFollow {
    enum class Scroll { NONE, TO_LIVE }

    data class View(
        val atBottom: Boolean = true,
        val liveOrdinal: Int = 0,
        val liveVisible: Boolean = false,
        val holdingOrdinal: Int = 0,
        val chatPaused: Boolean = false,
        val silent: Boolean = false
    )

    data class Decision(
        val view: View,
        val scroll: Scroll = Scroll.NONE,
        val showPill: Boolean = false,
        /** The card may leave the question gate (silent included). */
        val mayBegin: Boolean = false,
        /** TTS of the question. Silent and a hold both keep this false. */
        val maySpeak: Boolean = false,
        /** Think timer and listening. Only once the bubble is visible. */
        val mayStartTiming: Boolean = false,
        val movedScroll: Boolean = false,
        val stoleFocus: Boolean = false,
        /** A follow decision never grades or advances. */
        val autoAdvance: Boolean = false
    )

    /** A new question bubble. Always pin it to the top, even if the user was scrolled up. */
    fun onQuestion(view: View, ordinal: Int): Decision {
        if (ordinal <= 0) return decide(view.copy(liveOrdinal = 0, holdingOrdinal = 0), Scroll.NONE, false)
        val fresh = view.copy(
            liveOrdinal = ordinal,
            liveVisible = false,
            holdingOrdinal = 0,
            atBottom = true
        )
        return decide(fresh, Scroll.TO_LIVE, moved = true)
    }

    /** Reaching the bottom releases a hold. Leaving the bottom does not grab a card already released. */
    fun onScroll(view: View, atBottom: Boolean): Decision {
        val next = view.copy(atBottom = atBottom)
        if (next.holdingOrdinal != 0 && atBottom) {
            return decide(next.copy(holdingOrdinal = 0), Scroll.TO_LIVE, moved = true)
        }
        return decide(next, Scroll.NONE, moved = false)
    }

    fun onPill(view: View): Decision {
        if (view.holdingOrdinal == 0) return decide(view, Scroll.NONE, moved = false)
        return decide(view.copy(holdingOrdinal = 0, atBottom = true), Scroll.TO_LIVE, moved = true)
    }

    /** Visibility of the live bubble. A hold stays until the pill or the bottom, not a sliver. */
    fun onVisible(view: View, visible: Boolean): Decision {
        return decide(view.copy(liveVisible = visible), Scroll.NONE, moved = false)
    }

    /** A late explanation updates a row in place. Scroll and focus stay where they are. */
    fun onExplanation(view: View): Decision {
        return decide(view, Scroll.NONE, moved = false)
    }

    fun onPause(view: View, paused: Boolean): Decision {
        return decide(view.copy(chatPaused = paused), Scroll.NONE, moved = false)
    }

    fun onSilent(view: View, silent: Boolean): Decision {
        return decide(view.copy(silent = silent), Scroll.NONE, moved = false)
    }

    private fun decide(view: View, scroll: Scroll, moved: Boolean): Decision {
        val holding = view.holdingOrdinal != 0
        val mayBegin = !holding && view.liveVisible && view.liveOrdinal > 0 && !view.chatPaused
        val maySpeak = mayBegin && !view.silent
        return Decision(
            view = view,
            scroll = scroll,
            showPill = holding,
            mayBegin = mayBegin,
            maySpeak = maySpeak,
            mayStartTiming = maySpeak,
            movedScroll = moved,
            stoleFocus = false,
            autoAdvance = false
        )
    }

    companion object {
        const val BOTTOM_SLOP_DP = 48
    }
}
