package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ResumeButtonThemeTest {

    private fun projectRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        if (File(cwd, "src/main").isDirectory) return cwd.parentFile ?: cwd
        var d: File? = cwd
        while (d != null) {
            if (File(d, "app/src/main").isDirectory) return d
            d = d.parentFile
        }
        error("project root not found from ${System.getProperty("user.dir")}")
    }

    private fun buttonById(root: Node, id: String): Element? {
        val target = "@+id/$id"
        val stack = ArrayDeque<Node>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (n is Element && n.getAttribute("android:id") == target) return n
            val children = n.childNodes
            for (i in 0 until children.length) stack.add(children.item(i))
        }
        return null
    }

    private val hexColor = Regex("""#[0-9a-fA-F]{3,8}""")

    @Test
    fun `resume button uses theme colorPrimary and colorOnPrimary, no hard-coded colour`() {
        val layout = File(projectRoot(), "app/src/main/res/layout/include_session_cue.xml")
        assertTrue("layout missing: $layout", layout.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val doc = factory.newDocumentBuilder().parse(layout)
        val button = requireNotNull(buttonById(doc.documentElement, "chatResumeButton"))
        val bg = button.getAttribute("android:backgroundTint")
        val tc = button.getAttribute("android:textColor")
        assertTrue(bg.startsWith("?"))
        assertTrue(bg.contains("colorPrimary"))
        assertTrue(tc.contains("colorOnPrimary"))
        assertFalse(hexColor.containsMatchIn(bg))
        assertFalse(hexColor.containsMatchIn(tc))
    }

    @Test
    fun `no hard-coded hex colour remains for the resume pulse`() {
        val root = projectRoot()
        val chatPause = File(root, "app/src/main/java/com/evan/voicesrs/ChatPause.kt").readText()
        val main = File(root, "app/src/main/java/com/evan/voicesrs/MainActivity.kt").readText()
        assertFalse(chatPause.contains("RESUME_PULSE"))
        assertFalse(hexColor.containsMatchIn(chatPause))
        assertFalse(main.contains("Color.parseColor"))
    }
}
