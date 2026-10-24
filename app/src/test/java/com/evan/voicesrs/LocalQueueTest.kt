package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalQueueTest {
    @Test
    fun `grades round trip and partial sync keeps failures`() {
        val grades = listOf(
            PendingGrade(1_700_000_000_001L, 3, 10),
            PendingGrade(2L, 2, 11)
        )
        val decoded = LocalQueueJson.decodeGrades(LocalQueueJson.encodeGrades(grades))
        assertEquals(grades, decoded)
        val left = PendingSync.stillPending(grades, listOf(true, false))
        assertEquals(listOf(grades[1]), left)
    }

    @Test
    fun `pending grade duration round trips and old rows default to unknown`() {
        val withTime = listOf(PendingGrade(9L, 3, 50L, durationMs = 4_200L))
        assertEquals(withTime, LocalQueueJson.decodeGrades(LocalQueueJson.encodeGrades(withTime)))
        val old = """{"grades":[{"cardId":9,"ease":3,"queuedAtMs":50}]}"""
        val decoded = LocalQueueJson.decodeGrades(old).single()
        assertEquals(9L, decoded.cardId)
        assertEquals(3, decoded.ease)
        assertEquals(50L, decoded.queuedAtMs)
        assertEquals(0L, decoded.durationMs)
        val zero = listOf(PendingGrade(9L, 1, 1L, durationMs = 0L))
        assertEquals(zero, LocalQueueJson.decodeGrades(LocalQueueJson.encodeGrades(zero)))
    }

    @Test
    fun `bad grade json is empty`() {
        assertTrue(LocalQueueJson.decodeGrades("nope").isEmpty())
        assertTrue(LocalQueueJson.decodeBatch("{").isEmpty())
    }

    @Test
    fun `batch and file store`() {
        val dir = File.createTempFile("vqueue", "dir")
        dir.delete()
        dir.mkdirs()
        val store = LocalQueueStore(dir)
        assertFalse(store.hasBatch())
        val cards = listOf(OfflineCard(9L, "<b>Q</b>", "A", listOf(1, 3)))
        store.writeBatch(cards)
        assertEquals(cards, store.readBatch())
        assertTrue(store.hasBatch())
        store.enqueueGrade(PendingGrade(9L, 4, 1))
        store.enqueueGrade(PendingGrade(9L, 1, 2))
        assertEquals(2, store.readGrades().size)
        store.writeSkipped(listOf(9L, 10L))
        assertEquals(listOf(9L, 10L), store.readSkipped())
        store.writeSkipped(emptyList())
        assertTrue(store.readSkipped().isEmpty())
    }

    @Test
    fun `old batches decode a null note id and a note id round trips`() {
        val old = """{"cards":[{"cardId":9,"front":"Q","back":"A","buttons":[1,3]}]}"""
        assertEquals(null, LocalQueueJson.decodeBatch(old).single().noteId)
        val zero = """{"cards":[{"cardId":9,"front":"Q","back":"A","noteId":0}]}"""
        assertEquals(null, LocalQueueJson.decodeBatch(zero).single().noteId)
        val cards = listOf(OfflineCard(9L, "Q", "A", listOf(1, 3), 42L))
        assertEquals(cards, LocalQueueJson.decodeBatch(LocalQueueJson.encodeBatch(cards)))
        val withCorrect = listOf(OfflineCard(9L, "Q", "A", listOf(1, 3), 42L, "B"))
        assertEquals(withCorrect, LocalQueueJson.decodeBatch(LocalQueueJson.encodeBatch(withCorrect)))
        assertEquals("", LocalQueueJson.decodeBatch(old).single().correct)
    }

    @Test
    fun `backoff doubles and caps`() {
        assertEquals(2_000L, SyncBackoff.delayMs(0))
        assertEquals(4_000L, SyncBackoff.delayMs(1))
        assertEquals(8_000L, SyncBackoff.delayMs(2))
        assertEquals(60_000L, SyncBackoff.delayMs(10))
    }
}
