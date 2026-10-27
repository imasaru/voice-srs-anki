package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class StudyBlockStoreTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    private fun at(h: Int, m: Int): Long =
        java.time.ZonedDateTime.of(2026, 10, 27, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `dayKey shifts day boundary to 0400`() {
        assertEquals("2026-10-26", StudyBlockStore.dayKey(at(3, 59), zone))
        assertEquals("2026-10-27", StudyBlockStore.dayKey(at(4, 0), zone))
        assertEquals("2026-10-27", StudyBlockStore.dayKey(at(23, 59), zone))
    }

    @Test
    fun `read empty map is null`() {
        assertNull(StudyBlockStore.read(emptyMap<String, Any>()))
    }

    @Test
    fun `round trip running survives restart`() {
        val start10 = at(10, 0)
        val s = StudyBlockStore.start(null, start10, zone, goalMin = 90, newBlock = false)
        val map = HashMap<String, Any?>()
        StudyBlockStore.write(map, s)
        val back = StudyBlockStore.read(map)
        assertEquals(s, back)
        assertEquals(start10, back?.runStartMs)
        assertEquals(34 * 60_000L, StudyBlockStore.elapsedAt(back!!, at(10, 34)))
    }

    @Test
    fun `round trip paused drops run start and tolerates Int numbers`() {
        val s1 = StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false)
        val s2 = StudyBlockStore.pause(s1, at(10, 20))
        assertNull(s2.runStartMs)
        val map = HashMap<String, Any?>()
        StudyBlockStore.write(map, s2)
        assertFalse(map.containsKey(StudyBlockStore.KEY_RUN_START))
        val back = StudyBlockStore.read(map)
        assertNull(back?.runStartMs)
        // Numbers may come back as Int or Long.
        map[StudyBlockStore.KEY_ELAPSED] = 5000 as Int
        val back2 = StudyBlockStore.read(map)
        assertEquals(5000L, back2?.elapsedMs)
    }

    @Test
    fun `pause then resume accumulates, paused elapsed does not grow`() {
        val s1 = StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false)
        val s2 = StudyBlockStore.pause(s1, at(10, 20))
        val s3 = StudyBlockStore.start(s2, at(11, 0), zone, goalMin = 90, newBlock = false)
        assertEquals(1, s3.index)
        val s4 = StudyBlockStore.pause(s3, at(11, 14))
        assertEquals(34 * 60_000L, s4.elapsedMs)
        assertNull(s4.runStartMs)
        // Paused: elapsed does not grow.
        assertEquals(s4.elapsedMs, StudyBlockStore.elapsedAt(s4, at(12, 0)))
    }

    @Test
    fun `new block same day increments index`() {
        val s1 = StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false)
        val paused = StudyBlockStore.pause(s1, at(10, 20))
        val next = StudyBlockStore.start(paused, at(13, 0), zone, goalMin = 60, newBlock = true)
        assertEquals(2, next.index)
        assertEquals(0L, next.elapsedMs)
        assertFalse(next.done)
        assertEquals(60, next.goalMin)
        assertEquals("2026-10-27", next.dayKey)
    }

    @Test
    fun `done block does not resume, starts new block`() {
        val s1 = StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false)
        val done = StudyBlockStore.finish(s1, at(10, 30))
        assertTrue(done.done)
        val next = StudyBlockStore.start(done, at(11, 0), zone, goalMin = 90, newBlock = false)
        assertEquals(2, next.index)
        assertEquals(0L, next.elapsedMs)
        assertFalse(next.done)
    }

    @Test
    fun `day rollover starts a fresh block`() {
        val old = StudyBlockStore.State(
            index = 3,
            elapsedMs = 30 * 60_000L,
            goalMin = 90,
            dayKey = "2026-10-26",
            runStartMs = null,
            done = false
        )
        val next = StudyBlockStore.start(old, at(4, 0), zone, goalMin = 90, newBlock = false)
        assertEquals(1, next.index)
        assertEquals(0L, next.elapsedMs)
        assertEquals("2026-10-27", next.dayKey)
        // Old block is unfinished before 04:00, not after.
        assertTrue(StudyBlockStore.hasUnfinished(old, at(3, 59), zone))
        assertFalse(StudyBlockStore.hasUnfinished(old, at(4, 0), zone))
    }

    @Test
    fun `tick finishes at goal and never finishes without goal`() {
        val s1 = StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false)
        val before = StudyBlockStore.tick(s1, at(11, 29))
        assertFalse(before.done)
        val atGoal = StudyBlockStore.tick(s1, at(11, 30))
        assertTrue(atGoal.done)
        assertNull(atGoal.runStartMs)
        assertEquals(90 * 60_000L, atGoal.elapsedMs)

        val noGoal = StudyBlockStore.start(null, at(10, 0), zone, goalMin = 0, newBlock = false)
        val unchanged = StudyBlockStore.tick(noGoal, at(23, 0))
        assertFalse(unchanged.done)
        assertEquals(0, unchanged.goalMin)
    }

    @Test
    fun `hasUnfinished reflects running paused and done states`() {
        assertFalse(StudyBlockStore.hasUnfinished(null, at(10, 0), zone))
        val fresh = StudyBlockStore.pause(
            StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false),
            at(10, 0)
        )
        assertFalse(StudyBlockStore.hasUnfinished(fresh, at(10, 5), zone))
        val running = StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false)
        assertTrue(StudyBlockStore.hasUnfinished(running, at(10, 1), zone))
        val done = StudyBlockStore.finish(running, at(10, 30))
        assertFalse(StudyBlockStore.hasUnfinished(done, at(10, 40), zone))
    }

    @Test
    fun `resume on already-running block is unchanged`() {
        val s1 = StudyBlockStore.start(null, at(10, 0), zone, goalMin = 90, newBlock = false)
        val again = StudyBlockStore.start(s1, at(11, 0), zone, goalMin = 90, newBlock = false)
        assertEquals(s1, again)
        assertEquals(at(10, 0), again.runStartMs)
    }

    @Test
    fun `goalElapsedMs without block uses session time`() {
        assertEquals(123_000L, StudyBlockStore.goalElapsedMs(null, at(10, 0), 123_000L))
    }

    @Test
    fun `goalElapsedMs running block adds banked and running stretch`() {
        val s = StudyBlockStore.State(1, 34 * 60_000L, 90, "2026-10-27", at(10, 0), false)
        assertEquals(44 * 60_000L, StudyBlockStore.goalElapsedMs(s, at(10, 10), 5_000L))
    }

    @Test
    fun `goalElapsedMs paused block ignores session time`() {
        val s = StudyBlockStore.State(1, 34 * 60_000L, 90, "2026-10-27", null, false)
        assertEquals(34 * 60_000L, StudyBlockStore.goalElapsedMs(s, at(10, 10), 999_000L))
    }

    @Test
    fun `chipStartMs without block is the session start`() {
        assertEquals(5_000_000L, StudyBlockStore.chipStartMs(null, at(10, 0), 5_000_000L))
    }

    @Test
    fun `chipStartMs continued block moves the base back by banked and running time`() {
        val s = StudyBlockStore.State(1, 34 * 60_000L, 90, "2026-10-27", at(10, 0), false)
        assertEquals(5_000_000L - 35 * 60_000L, StudyBlockStore.chipStartMs(s, at(10, 1), 5_000_000L))
    }

    @Test
    fun `chipStartMs never goes below 1`() {
        val s = StudyBlockStore.State(1, 34 * 60_000L, 90, "2026-10-27", null, false)
        assertEquals(1L, StudyBlockStore.chipStartMs(s, at(10, 0), 1_000L))
    }
}
