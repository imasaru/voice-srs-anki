package com.evan.voicesrs

import java.io.IOException

/**
 * What Send does with the composer.
 * No card becomes a general question about the selected deck.
 * Empty text, no deck, a dead connection, and an AI failure each name themselves.
 * Sending does not pause the drill.
 */
object ChatSend {
    enum class Fail { EMPTY, NO_DECK, NO_NETWORK, AI }

    sealed class Outcome {
        data class Visible(val message: String) : Outcome()
        data class General(
            val deck: String,
            val question: String,
            val pausesDrill: Boolean = false
        ) : Outcome()
        data class Card(
            val cardId: Long,
            val ordinal: Int,
            val revealed: Boolean,
            val question: String,
            val pausesDrill: Boolean = false
        ) : Outcome()
    }

    fun resolve(raw: String, screen: ChatTarget.Screen?, deck: String): Outcome {
        val question = raw.trim()
        if (question.isEmpty()) return Outcome.Visible(visible(Fail.EMPTY))
        val target = ChatTarget.composer(screen)
        if (target is ChatTarget.Target.Live) {
            return Outcome.Card(
                cardId = target.cardId,
                ordinal = target.ordinal,
                revealed = target.revealed,
                question = question
            )
        }
        val name = deck.trim()
        if (name.isEmpty()) return Outcome.Visible(visible(Fail.NO_DECK))
        return Outcome.General(name, question)
    }

    fun visible(fail: Fail): String = when (fail) {
        Fail.EMPTY -> "Type a question first."
        Fail.NO_DECK -> "Select a deck in Settings, then ask again."
        Fail.NO_NETWORK -> "No connection. The question was not sent."
        Fail.AI -> "The AI could not answer. Try again."
    }

    fun kindOf(error: Exception): Fail = when (error) {
        is IOException -> Fail.NO_NETWORK
        else -> Fail.AI
    }
}