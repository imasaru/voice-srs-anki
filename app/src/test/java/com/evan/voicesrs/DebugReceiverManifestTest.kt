package com.evan.voicesrs

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Ensures the debug-only test receivers (app/src/debug/AndroidManifest.xml) are
 * locked down: every <receiver> is either exported=false or carries an
 * android:permission. The adb-driven harness sends an explicit-component
 * broadcast from the shell uid, which holds android.permission.DUMP — so the
 * receivers keep exported="true" but require DUMP, which third-party apps
 * cannot obtain.
 */
class DebugReceiverManifestTest {

    private fun loadReceivers(): List<Element> {
        // Unit tests run with the working dir set to the :app module, so the
        // manifest is at src/debug/AndroidManifest.xml; try project-relative too.
        val candidates = listOf(
            "src/debug/AndroidManifest.xml",
            "app/src/debug/AndroidManifest.xml",
            "../app/src/debug/AndroidManifest.xml",
        )
        val file = candidates.map { File(it) }.firstOrNull { it.isFile }
            ?: error(
                "AndroidManifest.xml not found. Tried: ${candidates.joinToString(", ")}",
            )
        assertTrue(
            "AndroidManifest.xml not found at ${file.absolutePath}",
            file.isFile,
        )
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Harden against XXE; the file is a local trusted manifest.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(file.inputStream()))
        val nodes = doc.getElementsByTagName("receiver")
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.exported(): Boolean =
        (getAttribute("android:exported") ?: "false").toBoolean()

    private fun Element.hasPermission(): Boolean =
        getAttribute("android:permission").isNotBlank()

    @Test
    fun everyTestReceiverIsLockedDown() {
        for (receiver in loadReceivers()) {
            val name = receiver.getAttribute("android:name")
            val exported = receiver.exported()
            val permission = receiver.hasPermission()
            // A receiver must not be world-exported without a protecting permission.
            assertTrue(
                "receiver $name is exported=true without android:permission",
                !exported || permission,
            )
        }
    }

    @Test
    fun bothHarnessReceiversArePresentAndLockedDown() {
        val byName = loadReceivers().associateBy { it.getAttribute("android:name") }
        for (name in listOf(".TestGradeReceiver", ".TestScriptReceiver")) {
            assertTrue("missing receiver $name in manifest", byName.containsKey(name))
            val receiver = byName.getValue(name)
            assertTrue(
                "$name must be locked down (exported=false or android:permission set)",
                !receiver.exported() || receiver.hasPermission(),
            )
        }
    }
}
