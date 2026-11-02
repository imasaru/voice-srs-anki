package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReadinessStatsTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val day = 86_400_000L
    private val t0 = AnkiDay.startMs(LocalDate.of(2026, 11, 2), tokyo)

    @Test fun `a anki day rolls over at 4am not midnight`() {
        val d = LocalDate.of(2026, 11, 2)
        val at0330 = d.atTime(3, 30).atZone(tokyo).toInstant().toEpochMilli()
        val at0430 = d.atTime(4, 30).atZone(tokyo).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 11, 1), AnkiDay.dayOf(at0330, tokyo))
        assertEquals(d, AnkiDay.dayOf(at0430, tokyo))
        assertEquals(d, AnkiDay.dayOf(at0330, tokyo, rolloverHour = 0))
    }

    @Test fun `b startMs is the rollover instant`() {
        val d = LocalDate.of(2026, 11, 2)
        assertEquals(d.atTime(4, 0).atZone(tokyo).toInstant().toEpochMilli(), AnkiDay.startMs(d, tokyo))
        assertEquals(d, AnkiDay.dayOf(AnkiDay.startMs(d, tokyo), tokyo))
        assertEquals(d.minusDays(1), AnkiDay.dayOf(AnkiDay.startMs(d, tokyo) - 1, tokyo))
    }

    @Test fun `c states bucket new learning young mature and count suspended`() {
        val cards = listOf(
            CardSnap(1, "D", type = 0), CardSnap(2, "D", type = 0, queue = -1),
            CardSnap(3, "D", type = 1), CardSnap(4, "D", type = 3, ivl = 30),
            CardSnap(5, "D", type = 2, ivl = 20), CardSnap(6, "D", type = 2, ivl = 21),
            CardSnap(7, "D", type = 2, ivl = 100, queue = -1)
        )
        assertEquals(StateCounts(2, 2, 1, 2, 2), ReadinessStats.states(cards))
        assertEquals(7, ReadinessStats.states(cards).total)
    }

    @Test fun `d states by deck sorted by name`() {
        val by = ReadinessStats.statesByDeck(listOf(CardSnap(1, "P::b", type = 0), CardSnap(2, "P::a", type = 2, ivl = 5)))
        assertEquals(listOf("P::a", "P::b"), by.keys.toList())
        assertEquals(1, by.getValue("P::a").young)
    }

    @Test fun `e true retention uses review rows only and splits young mature`() {
        val rows = listOf(
            RevRow(t0 + 1, 1, 3, 1, lastIvl = 5), RevRow(t0 + 2, 2, 1, 1, lastIvl = 5),
            RevRow(t0 + 3, 3, 3, 1, lastIvl = 30), RevRow(t0 + 4, 4, 4, 1, lastIvl = 40),
            RevRow(t0 + 5, 5, 1, 0), RevRow(t0 + 6, 6, 1, 2), // learn + relearn ignored
            RevRow(t0 - 1, 7, 1, 1, lastIvl = 5) // before window
        )
        val r = ReadinessStats.retention(rows, t0, t0 + day)
        assertEquals(3, r.passed); assertEquals(4, r.total); assertEquals(75.0, r.pct!!, 0.0)
        assertEquals(50.0, r.youngPct!!, 0.0); assertEquals(100.0, r.maturePct!!, 0.0)
        assertNull(ReadinessStats.retention(emptyList(), t0, t0 + day).pct)
    }

    @Test fun `f pace counts first-seen new cards and all reviews per day`() {
        val rows = listOf(
            RevRow(t0 - day, 1, 3, 0), // seen before window
            RevRow(t0 + 1, 1, 3, 0), RevRow(t0 + 2, 2, 3, 0), RevRow(t0 + 3, 2, 3, 0),
            RevRow(t0 + day + 1, 3, 3, 0), RevRow(t0 + day + 2, 4, 3, 1)
        )
        val p = ReadinessStats.pace(rows, t0, t0 + 7 * day, 7)
        assertEquals(2, p.newCards) // cards 2 and 3
        assertEquals(5, p.reviews)
        assertEquals(0.3, p.newPerDay, 0.0)
        assertEquals(0.7, p.reviewsPerDay, 0.0)
    }

    @Test fun `g lapse rate by deck and by tag, worst first`() {
        val rows = listOf(
            RevRow(1, 1, 1, 1, deck = "D::a"), RevRow(2, 1, 3, 1, deck = "D::a"),
            RevRow(3, 2, 3, 1, deck = "D::b"), RevRow(4, 2, 1, 2, deck = "D::b") // relearn ignored
        )
        val byDeck = ReadinessStats.lapsesByDeck(rows)
        assertEquals(listOf("D::a", "D::b"), byDeck.map { it.name })
        assertEquals(50.0, byDeck[0].pct, 0.0); assertEquals(0.0, byDeck[1].pct, 0.0)
        val byTag = ReadinessStats.lapsesByTag(rows, mapOf(1L to listOf("delta"), 2L to emptyList()))
        assertEquals(listOf("delta", "Untagged"), byTag.map { it.name })
    }

    @Test fun `h projection uses 0_9 N over pace plus 32 days`() {
        val today = LocalDate.of(2026, 11, 2)
        val p = ReadinessStats.project(today, today.plusDays(60), newRemaining = 100, newPerDay = 10.0)
        assertEquals(9, p.daysToIntroduce)
        assertEquals(today.plusDays(41), p.readyDate)
        assertEquals(19, p.slackDays); assertEquals(true, p.onTrack)
        assertEquals(3.2, p.requiredNewPerDay!!, 0.0) // 90 / 28
    }

    @Test fun `i projection edge cases`() {
        val today = LocalDate.of(2026, 11, 2)
        val stalled = ReadinessStats.project(today, today.plusDays(10), 50, 0.0)
        assertNull(stalled.daysToIntroduce); assertNull(stalled.readyDate); assertNull(stalled.slackDays)
        assertNull(stalled.requiredNewPerDay) // less than 32 days left
        val done = ReadinessStats.project(today, null, 0, 0.0)
        assertEquals(0, done.daysToIntroduce); assertNull(done.daysLeft); assertNull(done.onTrack)
        assertEquals(false, ReadinessStats.project(today, today.plusDays(20), 10, 10.0).onTrack)
    }
}
