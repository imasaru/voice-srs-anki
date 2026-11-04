package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class H145bTest {

    private fun card(cid: Long, deck: String, domain: String, stem: String, correctText: String, ease: Int) =
        DumpCard(cid, deck, domain, stem, correctText, listOf(DumpRev("10:00", ease, 0, 0, 0L)))

    private fun section(out: String, from: String, to: String): String {
        val i = out.indexOf(from)
        if (i < 0) return ""
        val j = if (to.isEmpty()) out.length else out.indexOf(to, i + from.length)
        return out.substring(i + from.length, if (j < 0) out.length else j)
    }

    @Test
    fun `all good yields no weak domains no misses and omits the Hard section`() {
        val r = DailyReportBuilder.build("2026-10-26", listOf(
            card(1L, "D::DeckA", "Alpha", "Q alpha?", "A", 3),
            card(2L, "D::DeckB", "Beta", "Q beta?", "B", 3),
        ))
        val out = DailyReportBuilder.render(r)
        assertTrue("Weakest none", out.contains("## Weakest domains\n- None — no misses\n"))
        assertTrue("Missed none", out.contains("## Missed cards by concept\n- None\n"))
        assertTrue("Focus none", section(out, "## Focus next\n", "").contains("- None — no misses\n"))
        assertFalse("no Hard section", out.contains("## Hard — worth a review"))
        assertFalse("Alpha absent from Weakest", section(out, "## Weakest domains\n", "## Missed cards by concept\n").contains("Alpha"))
        assertFalse("Beta absent from Focus", section(out, "## Focus next\n", "").contains("Beta"))
    }

    @Test
    fun `one Hard card shows the domain as weak lists it under Hard and focuses it`() {
        val r = DailyReportBuilder.build("2026-10-26", listOf(
            card(1L, "D::DeckA", "Alpha", "Which hard thing?", "the answer", 2),
        ))
        val out = DailyReportBuilder.render(r)
        val weakest = section(out, "## Weakest domains\n", "## Missed cards by concept\n")
        assertTrue("Alpha weak", weakest.contains("Alpha"))
        assertTrue("1 Hard count", weakest.contains("1 Hard"))
        val missed = section(out, "## Missed cards by concept\n", "## Hard — worth a review\n")
        assertTrue("Missed none", missed.contains("- None\n"))
        val hard = section(out, "## Hard — worth a review\n", "## Focus next\n")
        assertTrue("Hard lists stem", hard.contains("- Which hard thing? → the answer\n"))
        val focus = section(out, "## Focus next\n", "")
        assertTrue("Focus lists the domain", focus.contains("Alpha"))
    }

    @Test
    fun `one Again shows the domain and missed card and drops perfect domains`() {
        val r = DailyReportBuilder.build("2026-10-26", listOf(
            card(1L, "D::DeckA", "Alpha", "Which again thing?", "the answer", 1),
            card(2L, "D::DeckB", "Beta", "Which good thing?", "the answer", 3),
        ))
        val out = DailyReportBuilder.render(r)
        val weakest = section(out, "## Weakest domains\n", "## Missed cards by concept\n")
        assertTrue("Alpha weak", weakest.contains("Alpha"))
        assertFalse("Beta perfect absent from Weakest", weakest.contains("Beta"))
        assertTrue("Missed lists the card", out.contains("- Which again thing? → the answer\n"))
        assertFalse("no Hard section", out.contains("## Hard — worth a review"))
        val focus = section(out, "## Focus next\n", "")
        assertTrue("Focus lists the domain", focus.contains("Alpha"))
        assertFalse("Beta perfect absent from Focus", focus.contains("Beta"))
    }
}
