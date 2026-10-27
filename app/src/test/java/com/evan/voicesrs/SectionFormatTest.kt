package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionFormatTest {

    private val sample = """
        Core concept
        `filter()` returns a new array with only the elements where the lambda is true.

        Why B
        It keeps elements passing the predicate, so `x > 100` drops everything at or below 100.

        Common traps
        - `transform()` maps every element, never removes.
        - `aggregate()` folds to one value.
        - `array_contains()` returns a boolean.

        Example
        ```
        filter(array(1,2,3,4), x -> x > 2)
        ```
        Note: Checked against Databricks SQL / PySpark docs
        Source: https://docs.databricks.com/sql
    """.trimIndent()

    @Test
    fun `headed sections render code chips a fenced block and a footer`() {
        val doc = SectionFormat.parse(sample)
        val headings = doc.blocks.filter { it.kind == SectionFormat.Kind.HEADING }.map { it.text }
        assertEquals(listOf("Core concept", "Why B", "Common traps", "Example"), headings)
        assertTrue(doc.blocks.none { it.kind == SectionFormat.Kind.HEADING && it.text.contains("#") })
        assertTrue(doc.blocks.none { it.text.contains("`") })

        val concept = doc.blocks.first { it.kind == SectionFormat.Kind.BODY }
        assertTrue(concept.runs.any { it.code && it.text == "filter()" })
        assertFalse(concept.text.contains("`"))

        val traps = doc.blocks.filter { it.kind == SectionFormat.Kind.BULLET }
        assertEquals(3, traps.size)
        assertTrue(traps[0].runs.any { it.code && it.text == "transform()" })
        assertTrue(traps[0].text.contains("maps every element"))
        assertFalse(traps[0].text.startsWith("-"))

        val code = doc.blocks.single { it.kind == SectionFormat.Kind.CODE }
        assertTrue(code.text.contains("filter(array(1,2,3,4), x -> x > 2)"))
        assertTrue(code.runs.single().code)

        assertTrue(doc.footer.contains("Note: Checked against Databricks SQL / PySpark docs"))
        assertTrue(doc.footer.contains("Source: https://docs.databricks.com/sql"))
        val footer = doc.blocks.single { it.kind == SectionFormat.Kind.FOOTER }
        assertEquals(doc.footer, footer.text)

        val hashed = SectionFormat.parse("## Core concept\nDelta is ACID.\n**Why C**\nC is right.")
        assertEquals(
            listOf("Core concept", "Why C"),
            hashed.blocks.filter { it.kind == SectionFormat.Kind.HEADING }.map { it.text }
        )
    }

    @Test
    fun `spoken section text keeps the words and skips the footer`() {
        val doc = SectionFormat.parse(sample)
        assertTrue(doc.spoken.contains("Core concept"))
        assertTrue(doc.spoken.contains("Why B"))
        assertTrue(doc.spoken.contains("filter() returns a new array"))
        assertTrue(doc.spoken.contains("Common traps"))
        assertTrue(doc.spoken.contains("transform() maps every element"))
        assertTrue(doc.spoken.contains("filter(array(1,2,3,4), x -> x > 2)"))
        assertFalse(doc.spoken.contains("Note:"))
        assertFalse(doc.spoken.contains("Source:"))
        assertFalse(doc.spoken.contains("https://"))
        assertFalse(doc.spoken.contains("`"))
        assertEquals(doc.spoken, SectionFormat.spoken(sample))

        val license = SectionFormat.parse("Core concept\nKeep this.\nLicense: Apache-2.0\nhttps://example.com/doc")
        assertTrue(license.spoken.contains("Keep this."))
        assertFalse(license.spoken.contains("License"))
        assertFalse(license.spoken.contains("http"))
        assertTrue(license.footer.contains("License: Apache-2.0"))
    }

    @Test
    fun `gapBefore marks paragraph breaks and code fences`() {
        val src = """
            Use INSERT OVERWRITE to replace data.

            It rewrites the whole table.
            ```sql
            INSERT OVERWRITE TABLE sales
            SELECT * FROM staging_sales;
            ```
            After the fence.
            Next line.
        """.trimIndent()

        val blocks = SectionFormat.parse(src).blocks

        // the first block of any doc has gapBefore = false
        assertFalse(blocks[0].gapBefore)
        assertEquals(SectionFormat.Kind.BODY, blocks[0].kind)

        // a blank line came before this paragraph
        assertEquals(SectionFormat.Kind.BODY, blocks[1].kind)
        assertTrue(blocks[1].gapBefore)

        // the fenced code block carries its text and gets a gap
        val code = blocks.single { it.kind == SectionFormat.Kind.CODE }
        assertEquals("INSERT OVERWRITE TABLE sales\nSELECT * FROM staging_sales;", code.text)
        assertTrue(code.gapBefore)

        // a body block immediately after a code block gets a gap
        assertEquals(SectionFormat.Kind.BODY, blocks[3].kind)
        assertTrue(blocks[3].gapBefore)

        // consecutive lines with no blank between them get no gap
        assertEquals(SectionFormat.Kind.BODY, blocks[4].kind)
        assertFalse(blocks[4].gapBefore)
    }
}
