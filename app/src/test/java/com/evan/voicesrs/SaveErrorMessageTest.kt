package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SaveErrorMessageTest {

    private val card = SaveErrors.Op.MAKE_CARD
    private val memo = SaveErrors.Op.MEMO

    @Test
    fun `deck not found extracts the deck name`() {
        val e = AnkiConnectException("Error: deck 'Japanese' was not found")
        assertEquals("Deck not found: Japanese", SaveErrors.message(card, e))
    }

    @Test
    fun `deck not found without a quoted name`() {
        val e = AnkiConnectException("deck was not found")
        assertEquals("Deck not found", SaveErrors.message(card, e))
    }

    @Test
    fun `model not found maps to note type`() {
        val e = AnkiConnectException("Error: model 'Flashcard' was not found")
        assertEquals("Note type not found", SaveErrors.message(card, e))
    }

    @Test
    fun `empty note maps to missing field`() {
        val e = AnkiConnectException("cannot create note because it is empty")
        assertEquals("Missing field", SaveErrors.message(card, e))
    }

    @Test
    fun `missing field keyword maps to missing field`() {
        val e = AnkiConnectException("missing field: Front")
        assertEquals("Missing field", SaveErrors.message(card, e))
    }

    @Test
    fun `duplicate maps to duplicate card`() {
        val e = AnkiConnectException("Error: a note with these fields already exists (duplicate)")
        assertEquals("Duplicate card", SaveErrors.message(card, e))
    }

    @Test
    fun `connection wrapper reports unreachable`() {
        val e = AnkiConnectException(
            "Could not reach AnkiConnect at http://127.0.0.1:8910 (Connection refused)",
            ConnectException("Connection refused"),
        )
        assertEquals("AnkiConnect unreachable", SaveErrors.message(memo, e))
    }

    @Test
    fun `unknown host reports unreachable`() {
        val e = UnknownHostException("no route to host")
        assertEquals("AnkiConnect unreachable", SaveErrors.message(memo, e))
    }

    @Test
    fun `socket timeout reports unreachable`() {
        val e = SocketTimeoutException("read timed out")
        assertEquals("AnkiConnect unreachable", SaveErrors.message(memo, e))
    }

    @Test
    fun `memo io exception includes the reason`() {
        val e = IOException("write failed")
        assertEquals("Couldn't save memo: write failed", SaveErrors.message(memo, e))
    }

    @Test
    fun `non-memo io exception falls back with the reason`() {
        val e = IOException("write failed")
        assertEquals("Couldn't save: write failed", SaveErrors.message(card, e))
    }

    @Test
    fun `fallback includes the exception message`() {
        val e = RuntimeException("weird thing broke")
        assertEquals("Couldn't save: weird thing broke", SaveErrors.message(card, e))
    }

    @Test
    fun `fallback without a message is still usable`() {
        val e = RuntimeException()
        assertEquals("Couldn't save", SaveErrors.message(memo, e))
    }
}
