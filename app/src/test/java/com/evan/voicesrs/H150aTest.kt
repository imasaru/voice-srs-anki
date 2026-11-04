package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class H150aTest {
    val zone = ZoneId.of("Asia/Tokyo")
    val now = 1_793_800_000_000L
    val today = StudyBlockStore.dayKey(now, zone)

    fun st(index: Int = 2, elapsedMs: Long = 34 * 60_000L, goal: Int = 90, day: String = today, run: Long? = null, done: Boolean = false) =
        StudyBlockStore.State(index, elapsedMs, goal, day, run, done)

    @Test fun kind() {
        assertNull(StudyBlockChip.kind(null, now, zone))
        assertNull(StudyBlockChip.kind(st(day = "2000-01-01"), now, zone))
        assertEquals(StudyBlockChip.Kind.PAUSED, StudyBlockChip.kind(st(), now, zone))
        assertEquals(StudyBlockChip.Kind.RUNNING, StudyBlockChip.kind(st(run = now), now, zone))
        assertEquals(StudyBlockChip.Kind.DONE, StudyBlockChip.kind(st(done = true), now, zone))
        assertEquals(StudyBlockChip.Kind.DONE, StudyBlockChip.kind(st(run = now, done = true), now, zone))
    }

    @Test fun label() {
        assertEquals("Block 2 \u00B7 34/90 min", StudyBlockChip.label(st(), now, StudyBlockChip.Kind.PAUSED))
        assertEquals("Block 2 \u00B7 35/90 min", StudyBlockChip.label(st(run = now - 60_000L), now, StudyBlockChip.Kind.RUNNING))
        assertEquals("Block 2 \u00B7 34 min", StudyBlockChip.label(st(goal = 0), now, StudyBlockChip.Kind.PAUSED))
        assertEquals("Block 2 done \u00B7 90 min", StudyBlockChip.label(st(elapsedMs = 90 * 60_000L, done = true), now, StudyBlockChip.Kind.DONE))
    }

    @Test fun progress() {
        assertEquals(37, StudyBlockChip.progress(st(), now))
        assertEquals(0, StudyBlockChip.progress(st(goal = 0), now))
        assertEquals(0, StudyBlockChip.progress(st(elapsedMs = 0L), now))
        assertEquals(100, StudyBlockChip.progress(st(elapsedMs = 120 * 60_000L), now))
        assertEquals(50, StudyBlockChip.progress(st(elapsedMs = 45 * 60_000L), now))
    }

    @Test fun dialog() {
        assertEquals("Block 2 today \u00B7 34 of 90 min", StudyBlockChip.dialogBody(st(), now))
        assertEquals("Block 2 today \u00B7 34 min", StudyBlockChip.dialogBody(st(goal = 0), now))
        assertEquals("Continue (34/90)", StudyBlockChip.continueLabel(st(), now))
        assertEquals("Continue (34 min)", StudyBlockChip.continueLabel(st(goal = 0), now))
        assertEquals("Continue study block?", StudyBlockChip.DIALOG_TITLE)
        assertEquals("Start new block", StudyBlockChip.NEW_LABEL)
    }

    @Test fun noEmoji() {
        val strs = listOf(StudyBlockChip.SEP, StudyBlockChip.DIALOG_TITLE, StudyBlockChip.NEW_LABEL,
            StudyBlockChip.label(st(), now, StudyBlockChip.Kind.PAUSED),
            StudyBlockChip.label(st(run = now - 60_000L), now, StudyBlockChip.Kind.RUNNING),
            StudyBlockChip.label(st(goal = 0), now, StudyBlockChip.Kind.PAUSED),
            StudyBlockChip.label(st(elapsedMs = 90 * 60_000L, done = true), now, StudyBlockChip.Kind.DONE))
        for (s in strs) for (ch in s) assertTrue("char ${ch.code} in $s", ch.code <= 0x2000)
    }
}
