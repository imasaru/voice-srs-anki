package com.evan.voicesrs

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * What Send does with the composer.
 * No card becomes a general question about the selected deck.
 * Empty text, no deck, a dead connection, and an AI failure each name themselves.
 * Sending does not pause the drill.
 */
object ChatSend {
    enum class Fail { EMPTY, NO_DECK, NO_NETWORK, DROPPED, TIMEOUT_WAITING, TIMEOUT_GENERATING, AI }

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
        Fail.DROPPED -> "Connection dropped while the model was working. Retry?"
        Fail.TIMEOUT_WAITING -> "The model is busy and hasn't started answering yet. Your request was sent. Retry?"
        Fail.TIMEOUT_GENERATING -> "The model stopped mid-answer. Retry?"
        Fail.AI -> "The AI could not answer. Try again."
    }

    /** p88a: general-chat error bubbles retry on tap, so the text says so. */
    fun tapRetry(fail: Fail): String =
        visible(fail).removeSuffix("Retry?").removeSuffix("Try again.").trimEnd() + " Tap to retry."

    /** p88b: paths with no tap retry ask the user to send the question again instead. */
    fun askAgain(fail: Fail): String {
        val base = visible(fail).removeSuffix("Retry?").trimEnd()
        return if (base.endsWith("Try again.")) base else "$base Ask again."
    }

    fun kindOf(error: Exception): Fail = when (error) {
        is AiTimeout -> if (error.phase == AiTimeout.Phase.GENERATING) Fail.TIMEOUT_GENERATING else Fail.TIMEOUT_WAITING
        is SocketTimeoutException -> Fail.TIMEOUT_WAITING
        is ConnectException, is UnknownHostException, is NoRouteToHostException -> Fail.NO_NETWORK
        is SocketException -> Fail.DROPPED
        is AiHttpException -> Fail.AI
        is IOException -> Fail.NO_NETWORK
        else -> Fail.AI
    }
}