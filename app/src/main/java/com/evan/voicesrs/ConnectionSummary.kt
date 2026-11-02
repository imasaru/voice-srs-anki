package com.evan.voicesrs

import java.net.SocketTimeoutException
import java.util.Locale

/**
 * One line for Settings "Test Anki" and the AI Test button.
 * Never includes a deck list or a stack trace.
 */
object ConnectionSummary {
    fun ankiOk(version: Int, deckCount: Int, deck: String, label: String): String {
        val leaf = DeckName.split(deck).second.ifBlank { deck.trim() }
        return "✓ Connected · AnkiConnect v$version · $deckCount decks · '$leaf': $label"
    }

    fun aiOk(model: String, elapsedMs: Long, reply: String): String {
        val seconds = String.format(Locale.US, "%.1f", elapsedMs / 1000.0)
        val shown = reply.trim().replace(Regex("\\s+"), " ")
        return "✓ $model replied in $seconds s: \"$shown\""
    }

    fun failure(error: Throwable): String = "✗ ${shortReason(error)}"

    fun shortReason(error: Throwable): String {
        if (error is AiHttpException) return "HTTP ${error.code}"
        if (error is SocketTimeoutException) return "Timed out"
        val raw = error.message?.trim().orEmpty()
        val line = raw.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("at ") }
            .orEmpty()
            .substringBefore("\tat")
            .trim()
        return line.take(160).ifBlank { "Connection failed" }
    }
}
