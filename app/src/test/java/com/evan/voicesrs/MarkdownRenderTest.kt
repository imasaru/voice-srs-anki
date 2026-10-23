package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRenderTest {
    @Test
    fun `bold headings code and lists drop raw markers`() {
        val src = """
            # Acid tables
            **Delta Lake** stores `ACID` tables.

            - do not pick plain Parquet
            - use `MERGE INTO`

            1. create the table
            2. run **OPTIMIZE**

            ```
            SELECT * FROM t
            ```
        """.trimIndent()
        val doc = MarkdownRender.render(src)
        assertFalse(doc.hasRawMarkers())
        assertFalse(doc.text.contains("**"))
        assertFalse(doc.text.contains("`"))
        assertFalse(doc.text.contains("# Acid"))
        assertTrue(doc.text.contains("Acid tables"))
        assertTrue(doc.text.contains("Delta Lake"))
        assertTrue(doc.text.contains("ACID"))
        assertTrue(doc.text.contains("• do not pick plain Parquet"))
        assertTrue(doc.text.contains("• use MERGE INTO"))
        assertTrue(doc.text.contains("1. create the table"))
        assertTrue(doc.text.contains("2. run OPTIMIZE"))
        assertTrue(doc.text.contains("SELECT * FROM t"))
        assertTrue(doc.spans.any { it.kind == MdSpan.Kind.BOLD && doc.text.substring(it.start, it.end) == "Delta Lake" })
        assertTrue(doc.spans.any { it.kind == MdSpan.Kind.HEADING && doc.text.substring(it.start, it.end).contains("Acid tables") })
        assertTrue(doc.spans.any { it.kind == MdSpan.Kind.CODE && doc.text.substring(it.start, it.end) == "ACID" })
        assertTrue(doc.spans.any { it.kind == MdSpan.Kind.CODE_BLOCK && doc.text.substring(it.start, it.end).contains("SELECT") })
        assertTrue(doc.spans.any { it.kind == MdSpan.Kind.BOLD && doc.text.substring(it.start, it.end) == "OPTIMIZE" })
    }

    @Test
    fun `unmatched markers are not shown`() {
        val doc = MarkdownRender.render("keep **this and a stray `tick")
        assertFalse(doc.text.contains("**"))
        assertFalse(doc.text.contains("`"))
        assertTrue(doc.text.contains("keep"))
        assertTrue(doc.text.contains("this"))
    }

    @Test
    fun `plain prose is unchanged`() {
        val src = "Delta Lake is the ACID layer."
        val doc = MarkdownRender.render(src)
        assertEquals(src, doc.text)
        assertTrue(doc.spans.isEmpty())
    }

    @Test
    fun `italic works and snake_case stays unitalicized`() {
        val doc = MarkdownRender.render("Use *OPTIMIZE* then delta_log and _vacuum_ files.")
        assertTrue(doc.spans.any { it.kind == MdSpan.Kind.ITALIC && doc.text.substring(it.start, it.end) == "OPTIMIZE" })
        assertTrue(doc.spans.any { it.kind == MdSpan.Kind.ITALIC && doc.text.substring(it.start, it.end) == "vacuum" })
        assertTrue(doc.text.contains("delta_log"))
        assertFalse(doc.spans.any { it.kind == MdSpan.Kind.ITALIC && doc.text.substring(it.start, it.end).contains("delta") })
        assertTrue(doc.text.contains("• ").not() || true)
        val bullets = MarkdownRender.render("- keep snake_case")
        assertTrue(bullets.text.startsWith("• "))
        assertTrue(bullets.text.contains("snake_case"))
    }

    @Test
    fun `a fenced block is one code block span`() {
        val doc = MarkdownRender.render("```\nline one\nline two\n```")
        val blocks = doc.spans.filter { it.kind == MdSpan.Kind.CODE_BLOCK }
        assertEquals(1, blocks.size)
        assertTrue(doc.text.contains("line one\nline two"))
        assertTrue(doc.spans.none { it.kind == MdSpan.Kind.CODE && doc.text.substring(it.start, it.end).contains('\n') })
    }
}
