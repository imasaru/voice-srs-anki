package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** h110: exact field HTML of 8 real Databricks DEA cards (AnkiConnect notesInfo 11-02) -> spoken text. */
class SpeakableTextTest {
    private fun check(html: String, expected: String) = assertEquals(expected, SpeakableText.speakable(html))

    @Test fun pythonControlFlow_q008_Stem() = check(
        "<p>A data engineer intends to run the last block of a Python program only if <code>day_of_week</code> is set to 1 and <code>review_period</code> is <code>True</code>. The incomplete code block is shown below:</p>\n<pre><code class=\"language-python\">day_of_week = get_day_of_week()\nreview_period = check_review_period()\n\n_____\n    run_final_block()\n</code></pre>\n<p>What control flow statement should be used to complete the blank?</p>",
        "A data engineer intends to run the last block of a Python program only if day of week is set to 1 and review period is true. The incomplete code block is shown below:\nday of week equals get day of week, review period equals check review period, blank, run final block\nWhat control flow statement should be used to complete the blank?"
    )

    @Test fun pythonControlFlow_q008_CorrectC() = check(
        "<code>if day_of_week == 1 and review_period:</code>",
        "if day of week double equals 1 and review period"
    )

    @Test fun pythonControlFlow_q008_OptionB() = check(
        "<code>if day_of_week = 1 and review_period == \"True\":</code>",
        "if day of week equals 1 and review period double equals true"
    )

    @Test fun cdfColumns_mock1q028_Stem() = check(
        "<p>A data engineer enables Change Data Feed (CDF) on a Delta table. What additional metadata columns does CDF expose when reading changes?</p>",
        "A data engineer enables Change Data Feed (CDF) on a Delta table. What additional metadata columns does CDF expose when reading changes?"
    )

    @Test fun cdfColumns_mock1q028_CorrectB() = check(
        "Columns <code>_change_type</code>, <code>_commit_version</code>, and <code>_commit_timestamp</code> showing what changed and when",
        "Columns change type, commit version, and commit timestamp showing what changed and when"
    )

    @Test fun cdfColumns_mock1q028_Explanation() = check(
        "<p>Change Data Feed adds three metadata columns: <code>_change_type</code> (insert, update_preimage, update_postimage, delete), <code>_commit_version</code> (Delta version number), and <code>_commit_timestamp</code> (when the change occurred). These are read using <code>.option(\"readChangeFeed\", \"true\")</code>. CDF data is stored within the Delta table, not a separate table or JSON file.</p>",
        "Change Data Feed adds three metadata columns: change type (insert, update preimage, update postimage, delete), commit version (Delta version number), and commit timestamp (when the change occurred). These are read using dot option of read change feed, true. CDF data is stored within the Delta table, not a separate table or JSON file."
    )

    @Test fun unionTables_q007_Stem() = check(
        "<p>A data engineering team maintains <code>march_transactions</code> (all March retail transactions) and <code>april_transactions</code> (all April retail transactions) with no overlapping records. What command creates a new table <code>all_transactions</code> containing all unique records from both?</p>",
        "A data engineering team maintains march transactions (all March retail transactions) and april transactions (all April retail transactions) with no overlapping records. What command creates a new table all transactions containing all unique records from both?"
    )

    @Test fun unionTables_q007_CorrectD() = check(
        "<code>CREATE TABLE all_transactions AS SELECT * FROM march_transactions UNION SELECT * FROM april_transactions;</code>",
        "create table all transactions as select star from march transactions union select star from april transactions"
    )

    @Test fun countIf_q017_Stem() = check(
        "<p>Which command shows the count of null values in the <code>member_id</code> column?</p>",
        "Which command shows the count of null values in the member id column?"
    )

    @Test fun countIf_q017_CorrectA() = check(
        "<code>SELECT count_if(member_id IS NULL) FROM my_table;</code>",
        "select count if of member id is null, from my table"
    )

    @Test fun mergeInsert_mock1q026_Stem() = check(
        "<p>A data engineer needs to upsert records into a Delta table: update existing customer records when <code>customer_id</code> matches, and insert new records when there is no match. Which clause correctly completes the MERGE statement?</p>\n<pre><code class=\"language-sql\">MERGE INTO customers AS target\nUSING updates AS source\nON target.customer_id = source.customer_id\nWHEN MATCHED THEN\n  UPDATE SET target.name = source.name, target.email = source.email\nWHEN NOT MATCHED THEN\n  ___________\n</code></pre>",
        "A data engineer needs to upsert records into a Delta table: update existing customer records when customer id matches, and insert new records when there is no match. Which clause correctly completes the MERGE statement?\ncode example shown on screen, first line: merge into customers as target"
    )

    @Test fun mergeInsert_mock1q026_CorrectC() = check(
        "<code>INSERT (customer_id, name, email) VALUES (source.customer_id, source.name, source.email)</code>",
        "insert, customer id, name, email, values, source dot customer id, source dot name, source dot email"
    )

    @Test fun filterLambda_q018_Stem() = check(
        "<p>A data engineer needs to implement custom logic for the <code>employees</code> array column in the <code>stores</code> table to find employees with more than 5 years of experience. The result should be a new column <code>exp_employees</code> containing only matching employees for each row. The incomplete SQL query is shown below:</p>\n<pre><code class=\"language-sql\">SELECT store_id,\n       _____ AS exp_employees\nFROM stores\n</code></pre>\n<p>Which code correctly completes the blank using the FILTER higher-order function?</p>",
        "A data engineer needs to implement custom logic for the employees array column in the stores table to find employees with more than 5 years of experience. The result should be a new column exp employees containing only matching employees for each row. The incomplete SQL query is shown below:\nselect store id, blank as exp employees, from stores\nWhich code correctly completes the blank using the FILTER higher-order function?"
    )

    @Test fun filterLambda_q018_CorrectA() = check(
        "<code>FILTER(employees, i -&gt; i.years_exp &gt; 5)</code>",
        "filter, employees, i arrow i dot years exp greater than 5"
    )

    @Test fun filterLambda_q018_OptionB() = check(
        "<code>SELECT_FROM_ARRAY(employees, years_exp &gt; 5)</code>",
        "select from array, employees, years exp greater than 5"
    )

    @Test fun groupByOrdinal_q025_Stem() = check(
        "<p>What is wrong with the following SQL query?</p>\n<pre><code class=\"language-sql\">SELECT name, count(*) FROM people GROUP BY 2\n</code></pre>",
        "What is wrong with the following SQL query?\nselect name, count of star, from people group by 2"
    )

    @Test fun groupByOrdinal_q025_CorrectD() = check(
        "<code>GROUP BY</code> using a numeric ordinal position is not valid in Spark SQL",
        "group by using a numeric ordinal position is not valid in Spark SQL"
    )

    @Test fun insertValues_q011_Stem() = check(
        "<p>A data engineer needs to add a new record <code>('a1', 6, 9.4)</code> to the existing Delta table <code>my_table</code>. Which SQL command accomplishes this?</p>",
        "A data engineer needs to add a new record a1, 6, 9.4 to the existing Delta table my table. Which SQL command accomplishes this?"
    )

    @Test fun insertValues_q011_CorrectD() = check(
        "<code>INSERT INTO my_table VALUES ('a1', 6, 9.4);</code>",
        "insert into my table values, a1, 6, 9.4"
    )
    // --- rules ---
    @Test fun snakeCaseUnderscoresSilent() = assertEquals("spark sql shuffle partitions", SpeakableText.inline("spark_sql_shuffle_partitions"))
    @Test fun screamingCase() = assertEquals("max file size bytes", SpeakableText.words("MAX_FILE_SIZE_BYTES"))
    @Test fun camelCase() = assertEquals("read change feed", SpeakableText.words("readChangeFeed"))
    @Test fun dotOnlyBetweenIdentifiers() = assertEquals("spark dot conf dot get of spark sql shuffle partitions", SpeakableText.inline("spark.conf.get(spark_sql_shuffle_partitions)"))
    @Test fun emptyCallSilent() = assertEquals("dbutils dot fs dot ls", SpeakableText.inline("dbutils.fs.ls()"))
    @Test fun operators() = assertEquals("a double equals b, c arrow d, e greater than 1, f less than 2, g equals 3", SpeakableText.inline("a == b, c -> d, e > 1, f < 2, g = 3"))
    @Test fun decimalKept() = assertEquals("9.4", SpeakableText.inline("9.4"))
    @Test fun blankRun() = assertEquals("blank", SpeakableText.inline("_____"))
    @Test fun plainProseUnchanged() = check("<p>Which Spark method persists data? Use e.g. caching.</p>", "Which Spark method persists data? Use e.g. caching.")
    @Test fun longBlockNoticePlusFirstLine() {
        val out = SpeakableText.speakable("<pre><code>" + (1..30).joinToString("\n") { "x_$it = y_$it" } + "</code></pre>")
        assertEquals(SpeakableText.LONG_BLOCK + " x 1 equals y 1", out)
    }
    @Test fun neverAddsSentenceEnds() {
        for (src in listOf("a.b.c()", "df.write.mode(\"overwrite\").saveAsTable(\"t\")", "i -> i.years_exp > 5", "x...y")) {
            val out = SpeakableText.inline(src)
            assertFalse(out, Regex("[.!?](\\s|$)").containsMatchIn(out))
        }
    }
    @Test fun sentenceCountKeptForProse() {
        val html = "<p>First one. Second <code>a_b</code> here? Third.</p>"
        assertEquals(SentenceSplit.ranges(AnkiText.stripHtml(html)).size, SentenceSplit.ranges(SpeakableText.speakable(html)).size)
    }
    @Test fun noTagsOrUnderscoresLeft() {
        val out = SpeakableText.speakable("<p>Use <code>&lt;b&gt;</code> and <code>_delta_log</code></p>")
        assertFalse(out, out.contains('<') || out.contains('>') || out.contains('_'))
        assertTrue(out, out.contains("delta log"))
    }
}
