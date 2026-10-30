package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class H100Test {
    // --- 1) resume read-aloud from the interrupted sentence ---
    @Test fun resume_starts_at_interrupted_sentence() {
        val spoken = "Answer C: PIVOT. PIVOT rotates rows into columns. You specify the pivot column."
        assertEquals("PIVOT rotates rows into columns. You specify the pivot column.", SentenceReading.resumeText(spoken, 1))
        assertEquals("You specify the pivot column.", SentenceReading.resumeText(spoken, 2))
    }

    @Test fun resume_unknown_or_bad_index_reads_whole_text() {
        val spoken = "One. Two."
        assertEquals(spoken, SentenceReading.resumeText(spoken, -1))
        assertEquals(spoken, SentenceReading.resumeText(spoken, 0))
        assertEquals(spoken, SentenceReading.resumeText(spoken, 9))
    }

    // --- 2) no new listen once an answer is accepted ---
    @Test fun spoken_answer_is_accepted_without_another_listen() {
        // Logcat 13:01:17.593 JST: "heard: used broadcast join hints" then a 2 s settle listen opened.
        assertEquals(ThinkTime.Step.ACCEPT, ThinkTime.nextStep(ThinkTime.Hear.ANSWER, bufferEmpty = true, remainingMs = 3_000L, settling = false))
        assertEquals(ThinkTime.Step.ACCEPT, ThinkTime.nextStep(ThinkTime.Hear.ANSWER, bufferEmpty = false, remainingMs = 0L, settling = true))
        assertTrue(!ThinkTime.listensAgain(ThinkTime.Step.ACCEPT))
        assertTrue(ThinkTime.listensAgain(ThinkTime.Step.RESTART_QUIET))
    }

    // --- 3) code sentences map onto the shown code (Q26 and synthetic) ---
    private val q26Stem = "A data engineer needs to upsert records into a Delta table: update existing customer records when customer_id matches, and insert new records when there is no match. Which clause correctly completes the MERGE statement?"
    private val q26Code = "MERGE INTO customers AS target\nUSING updates AS source\nON target.customer_id = source.customer_id\nWHEN MATCHED THEN\n  UPDATE SET target.name = source.name, target.email = source.email\nWHEN NOT MATCHED THEN\n  ___________"
    private val q26Opts = "A. INSERT VALUES (source.customer_id, source.name, source.email)\nB. INSERT INTO customers SELECT * FROM source\nC. INSERT (customer_id, name, email) VALUES (source.customer_id, source.name, source.email)\nD. INSERT * FROM source"
    private val q26 = listOf(q26Stem, q26Code, q26Opts)

    private fun shownFor(sentence: String): String? =
        ReadingSpan.pick(q26, sentence)?.let { q26[it.block].substring(it.range) }

    @Test fun q26_stem_with_identifier() {
        val s = shownFor("A data engineer needs to upsert records into a Delta table: update existing customer records when customer id matches, and insert new records when there is no match.")
        assertNotNull(s)
        assertTrue(s!!.startsWith("A data engineer"))
        assertTrue(s.contains("customer_id matches"))
    }

    @Test fun q26_code_statement() {
        val s = shownFor("The statement is: merge into customers as target, using updates as source, on target customer id equals source customer id.")
        assertNotNull(s)
        assertTrue(s!!, s.startsWith("MERGE INTO customers"))
        assertTrue(s, s.endsWith("source.customer_id"))
    }

    @Test fun q26_paraphrased_code_lines() {
        val upd = shownFor("When matched, update set name and email from source.")!!
        assertTrue(upd, upd.startsWith("WHEN MATCHED THEN") && upd.contains("UPDATE SET target.name"))
        assertTrue(shownFor("When not matched, then a blank.")!!.startsWith("WHEN NOT MATCHED"))
    }

    @Test fun q26_options_pick_the_right_line() {
        assertEquals("D. INSERT * FROM source", shownFor("Option D: Insert star, from source."))
        assertEquals("B. INSERT INTO customers SELECT * FROM source", shownFor("Option B: Insert into customers, select star from source."))
    }

    @Test fun synthetic_pyspark_camel_case() {
        val shown = "Run df.groupBy(\"region\").agg(F.sum(\"sales\")) to total sales per region."
        val r = SentenceReading.spanIn(shown, "Run df dot group by region, agg F sum sales, to total sales per region.")
        assertEquals(shown.dropLast(1), shown.substring(r!!))
    }

    @Test fun synthetic_sql_operators() {
        val shown = "Query: SELECT COUNT(*) FROM t WHERE x >= 10;"
        val r = SentenceReading.spanIn(shown, "select count star from t where x greater than or equal to 10.")
        assertEquals("SELECT COUNT(*) FROM t WHERE x >= 10", shown.substring(r!!))
    }

    @Test fun unrelated_sentence_still_misses() {
        assertNull(ReadingSpan.pick(q26, "Photon accelerates SQL workloads on Delta Lake."))
        assertNull(SentenceReading.spanIn("SELECT * FROM t", "insert"))
    }
}
