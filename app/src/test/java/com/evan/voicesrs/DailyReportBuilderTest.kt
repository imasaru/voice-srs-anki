package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyReportBuilderTest {

    private fun load(): String =
        javaClass.classLoader!!.getResource("daily/fixture-daily-synthetic.txt")!!.readText()

    private fun cards(): List<DumpCard> = DailyReportBuilder.parseDump(load())

    @Test
    fun `parseDump reads one card per brace line and ignores the rest`() {
        val cards = DailyReportBuilder.parseDump(load())
        assertEquals(6, cards.size)
        val first = cards.first()
        assertTrue(first.deck.endsWith("Practice B"))
        assertEquals(1001L, first.cid)
        assertTrue(first.revs.isNotEmpty())
    }

    @Test
    fun `build reports volume, accuracy, first-try and rating breakdown`() {
        val r = DailyReportBuilder.build("2000-01-01", cards())
        assertEquals(12, r.reviews)
        assertEquals(6, r.cards)
        assertEquals("75.0%", DailyReportBuilder.pct(r.accuracyPct))
        assertEquals("66.7%", DailyReportBuilder.pct(r.firstTryPct))
        assertEquals(3, r.again)
        assertEquals(2, r.hard)
        assertEquals(5, r.good)
        assertEquals(2, r.easy)
        assertEquals(3, r.newCards)
        assertEquals(3, r.reviewCards)
    }

    @Test
    fun `byDeck is sorted by total and reports each deck's accuracy`() {
        val rows = DailyReportBuilder.build("2000-01-01", cards()).byDeck
        assertEquals("Practice A", rows[0].name)
        assertEquals(8, rows[0].correct)
        assertEquals(8, rows[0].total)
        assertEquals("100.0%", DailyReportBuilder.pct(rows[0].pct))
        assertEquals("Practice B", rows[1].name)
        assertEquals(1, rows[1].correct)
        assertEquals(4, rows[1].total)
        assertEquals("25.0%", DailyReportBuilder.pct(rows[1].pct))
    }

    @Test
    fun `byDomain is sorted weakest first with correct correct and total`() {
        val rows = DailyReportBuilder.build("2000-01-01", cards()).byDomain
        assertEquals("Synthetic shapes", rows[0].name)
        assertEquals(2, rows[0].correct)
        assertEquals(4, rows[0].total)
        assertEquals("50.0%", DailyReportBuilder.pct(rows[0].pct))
        assertEquals("Synthetic arithmetic", rows[1].name)
        assertEquals(3, rows[1].correct)
        assertEquals(4, rows[1].total)
        assertEquals("75.0%", DailyReportBuilder.pct(rows[1].pct))
        assertEquals("Synthetic colors", rows[2].name)
        assertEquals(4, rows[2].correct)
        assertEquals(4, rows[2].total)
        assertEquals("100.0%", DailyReportBuilder.pct(rows[2].pct))
    }

    @Test
    fun `missedByConcept groups every missed card in exactly one group`() {
        val r = DailyReportBuilder.build("2000-01-01", cards())
        val missed = r.missedByConcept
        assertTrue(missed.values.all { it.isNotEmpty() })
        val totalCards = cards().count { it.revs.any { it.ease == 1 } }
        val grouped = missed.values.sumOf { it.size }
        assertEquals(totalCards, grouped)
        val all = missed.values.flatten()
        assertEquals(all.size, all.distinct().size)
        if (missed.containsKey("Other")) {
            assertEquals("Other", missed.keys.last())
        }
    }

    @Test
    fun `concept maps a card to its group or Other`() {
        val clusters = DumpCard(1, "D::X", "D", "Which feature reduces cluster start time?", "answer", emptyList())
        assertEquals("Clusters & compute", DailyReportBuilder.concept(clusters))
        val notebooks = DumpCard(2, "D::X", "D", "What does dbutils.widgets.get do?", "answer", emptyList())
        assertEquals("Notebook utilities", DailyReportBuilder.concept(notebooks))
        val none = DumpCard(3, "D::X", "D", "Nothing matches here", "answer", emptyList())
        assertEquals("Other", DailyReportBuilder.concept(none))
    }

    @Test
    fun `build on empty input yields zeros and null times`() {
        val r = DailyReportBuilder.build("2000-01-02", emptyList())
        assertEquals(0, r.reviews)
        assertEquals(0, r.cards)
        assertEquals(0.0, r.accuracyPct, 0.0001)
        assertEquals(0.0, r.firstTryPct, 0.0001)
        assertNull(r.firstTime)
        assertNull(r.lastTime)
        assertTrue(r.byDeck.isEmpty())
        assertTrue(r.byDomain.isEmpty())
        assertTrue(r.missedByConcept.isEmpty())
    }

    @Test
    fun `render produces the full report with all sections in order`() {
        val r = DailyReportBuilder.build("2000-01-01", cards())
        val out = DailyReportBuilder.render(r)
        val headings = listOf(
            "# Daily study report — 2000-01-01",
            "## Time span",
            "## Volume & accuracy",
            "## By deck",
            "## Weakest domains",
            "## Missed cards by concept",
            "## Focus next",
        )
        var last = -1
        for (h in headings) {
            val idx = out.indexOf(h)
            assertTrue("missing heading: $h", idx >= 0)
            assertTrue("$h not after previous ($idx <= $last)", idx > last)
            last = idx
        }
        assertTrue(out.contains("12 reviews"))
        assertTrue(out.contains("75.0% accuracy"))
        assertTrue(out.contains("66.7% first-try"))
        assertTrue(out.contains("Again 3 / Hard 2 / Good 5 / Easy 2"))
        assertTrue(out.contains("3 new, 3 review"))
        assertTrue(out.contains("- Redo Practice B"))
        val zeroCount = cards().flatMap { it.revs }.count { it.ms == 0L }
        assertEquals(zeroCount, r.zeroMsReviews)
        assertTrue(r.zeroMsReviews > 0)
        assertTrue(out.contains(
            "Note: ${r.zeroMsReviews} of ${r.reviews} answer times were recorded as 0 ms, so time-per-card is not reliable."
        ))
    }

    @Test
    fun `render on empty input contains every heading and does not throw`() {
        val r = DailyReportBuilder.build("2000-01-02", emptyList())
        val out = DailyReportBuilder.render(r)
        for (h in listOf(
            "# Daily study report — 2000-01-02",
            "## Time span",
            "## Volume & accuracy",
            "## By deck",
            "## Weakest domains",
            "## Missed cards by concept",
            "## Focus next",
        )) {
            assertTrue(out.contains(h))
        }
    }

    @Test
    fun `render keeps a multi-line missed card on one bullet line`() {
        val card = DumpCard(1L, "D::Mock Exam 1", "Data Governance", "Which cluster\nmode is\tcheapest?",
            "Job\ncluster", listOf(DumpRev("10:00", 1, 0, 0, 0L)))
        val out = DailyReportBuilder.render(DailyReportBuilder.build("2000-01-02", listOf(card)))
        assertTrue(out, out.contains("- Which cluster mode is cheapest? → Job cluster\n"))
    }
}
