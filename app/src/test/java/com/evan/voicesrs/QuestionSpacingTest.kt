package com.evan.voicesrs

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionSpacingTest {
    private val root = File("src/main")

    private fun layoutFiles(): List<File> =
        File(root, "res/layout").listFiles { f -> f.isFile && f.extension == "xml" }
            ?.sortedBy { it.name }
            ?: emptyList()

    private fun parse(path: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }
        try {
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        } catch (_: Exception) {
        }
        val doc = factory.newDocumentBuilder().parse(File(root, path))
        doc.documentElement.normalize()
        return doc
    }

    @Test
    fun `dimens defines question_label_gap as 8dp`() {
        val doc = parse("res/values/dimens.xml")
        val dimens = doc.getElementsByTagName("dimen")
        var gap: String? = null
        for (i in 0 until dimens.length) {
            val el = dimens.item(i) as org.w3c.dom.Element
            if (el.getAttribute("name") == "question_label_gap") {
                gap = el.textContent.trim()
            }
        }
        assertEquals("dimens.xml must declare question_label_gap=8dp", "8dp", gap)
    }

    @Test
    fun `every questionLabel view carries the gap margin`() {
        val expected = "@dimen/question_label_gap"
        var found = 0
        for (file in layoutFiles()) {
            val doc = parse("res/layout/${file.name}")
            val views = doc.getElementsByTagName("TextView")
            for (i in 0 until views.length) {
                val el = views.item(i) as org.w3c.dom.Element
                val id = el.getAttribute("android:id")
                if (id.endsWith("questionLabel")) {
                    found++
                    val margin = el.getAttribute("android:layout_marginBottom")
                    assertEquals(
                        "${file.name}: questionLabel must set layout_marginBottom=$expected",
                        expected,
                        margin,
                    )
                }
            }
        }
        assertTrue("expected at least one questionLabel view", found >= 1)
    }
}
