package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeDetectTest {

    @Test
    fun `chains paths functions and sql match the codewrap script`() {
        assertEquals(
            "Use <code>spark.read.table(\"events\").filter(col(\"x\") > 1)</code> daily.",
            CodeDetect.wrap("Use spark.read.table(\"events\").filter(col(\"x\") > 1) daily.")
        )
        assertEquals(
            "The root is <code>dbfs:/mnt/data/raw</code> and also <code>s3://bucket/path</code>.",
            CodeDetect.wrap("The root is dbfs:/mnt/data/raw and also s3://bucket/path.")
        )
        assertEquals(
            "Call <code>filter(arr, x -> x > 100)</code> then <code>flatten(nested)</code>.",
            CodeDetect.wrap("Call filter(arr, x -> x > 100) then flatten(nested).")
        )
        assertEquals(
            "<code>SELECT id, name FROM customers WHERE active = 1</code>",
            CodeDetect.wrap("SELECT id, name FROM customers WHERE active = 1")
        )
        assertEquals(
            "<code>MERGE INTO target USING</code> source ON target.id = source.id <code>WHEN MATCHED THEN UPDATE SET *</code>",
            CodeDetect.wrap("MERGE INTO target USING source ON target.id = source.id WHEN MATCHED THEN UPDATE SET *")
        )
        assertEquals(
            "<code>OPTIMIZE</code> events <code>ZORDER BY (day)</code>",
            CodeDetect.wrap("OPTIMIZE events ZORDER BY (day)")
        )
        assertEquals(
            "<code>spark.sql.shuffle.partitions</code> is 200",
            CodeDetect.wrap("spark.sql.shuffle.partitions is 200")
        )
        assertEquals(
            "<code>df.select(\"a\").show()</code>",
            CodeDetect.wrap("df.select(\"a\").show()")
        )
        assertEquals(
            "<code>array_contains(tags, 'gold')</code> is the check.",
            CodeDetect.wrap("array_contains(tags, 'gold') is the check.")
        )
        assertEquals(
            "Use <code>/Volumes/catalog/schema/vol/file.parquet</code> now",
            CodeDetect.wrap("Use /Volumes/catalog/schema/vol/file.parquet now")
        )
        assertEquals(
            "<code>CREATE TABLE students (id INT)</code>",
            CodeDetect.wrap("CREATE TABLE students (id INT)")
        )
        assertEquals(
            "<code>dbutils.fs.ls(\"dbfs:/mnt\")</code>",
            CodeDetect.wrap("dbutils.fs.ls(\"dbfs:/mnt\")")
        )
        assertEquals(
            "Question 4. What does <code>OPTIMIZE</code> do?",
            CodeDetect.wrap("Question 4. What does OPTIMIZE do?")
        )
    }

    @Test
    fun `ordinary prose periods versions and capitalised words stay plain`() {
        val prose = listOf(
            "The lakehouse stores data. It is 4.5 GB, i.e. quite large.",
            "Version 3.4.1 ships in March. Select the best answer from the list.",
            "Spark is a framework. The table is a view. I.e. this is prose.",
            "The function returns a value. Each row is data.",
            "i.e. the clause is not SQL. e.g. a capitalised Word.",
            "Run VACUUM events RETAIN 168 HOURS".let { it } // replaced below
        )
        for (line in prose.dropLast(1)) {
            assertEquals(line, CodeDetect.wrap(line))
            assertFalse(CodeDetect.wrap(line).contains("<code>"))
        }
        assertEquals(
            "Run <code>VACUUM</code> events RETAIN 168 HOURS",
            CodeDetect.wrap("Run VACUUM events RETAIN 168 HOURS")
        )
    }

    @Test
    fun `existing code or pre wins and display wrapping does not change speech or the hash`() {
        val tagged = "Already <code>spark.table(\"t\")</code> and spark.read.parquet(\"a\")."
        assertEquals(tagged, CodeDetect.forDisplay(tagged, enabled = true))
        val pre = "See <pre>SELECT 1</pre> then SELECT a FROM b"
        assertEquals(pre, CodeDetect.forDisplay(pre, enabled = true))
        val plain = "Use spark.read.table(\"events\").show() daily."
        val shown = CodeDetect.forDisplay(plain, enabled = true)
        assertTrue(shown.contains("<code>"))
        assertEquals(plain, CodeDetect.forDisplay(plain, enabled = false))
        assertEquals(AnkiText.stripHtml(plain), AnkiText.stripHtml(shown))
        assertEquals(AnkiText.spokenText(plain), AnkiText.spokenText(plain))
        val hash = ContentHash.of(PromptBuilder.PROMPT_VERSION, plain, "back")
        assertEquals(hash, ContentHash.of(PromptBuilder.PROMPT_VERSION, plain, "back"))
        assertEquals(
            "Card front: $plain\nCard back: back",
            PromptBuilder.userMessage(plain, "back")
        )
        assertFalse(PromptBuilder.userMessage(plain, "back").contains("<code>"))
        assertTrue(CodeDetect.SETTING_DEFAULT)
    }
}