package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h150c (item 63 part 3): the redesigned continue study-block dialog. */
class H150cTest {
    private fun readSrc(rel: String): String =
        listOf(rel, "app/$rel").firstOrNull { java.io.File(it).exists() }?.let { java.io.File(it).readText() }
            ?: throw IllegalStateException("$rel not found")

    private fun noHigh(label: String, s: String) {
        for (ch in s) {
            val cp = ch.code
            assertFalse("high code point in $label: U+" + Integer.toHexString(cp), cp > 0x2000)
        }
    }

    private fun startDrillServiceBody(src: String): String {
        val a = src.indexOf("private fun startDrillService")
        assertTrue("startDrillService not found", a >= 0)
        val b = src.indexOf("\n    private fun ", a + 1)
        assertTrue("next method not found", b >= 0)
        return src.substring(a, b)
    }

    @Test fun dialogStudyBlockLayout() {
        val xml = readSrc("src/main/res/layout/dialog_study_block.xml")
        assertTrue("missing studyBlockDialogBody", xml.contains("studyBlockDialogBody"))
        assertTrue("missing studyBlockDialogProgress", xml.contains("studyBlockDialogProgress"))
        assertTrue("missing studyBlockDialogContinue", xml.contains("studyBlockDialogContinue"))
        assertTrue("missing studyBlockDialogNew", xml.contains("studyBlockDialogNew"))
        assertTrue("missing studyBlockDialogCancel", xml.contains("studyBlockDialogCancel"))
        assertTrue("missing LinearProgressIndicator", xml.contains("LinearProgressIndicator"))
        assertTrue("missing TonalButton style", xml.contains("Widget.Material3.Button.TonalButton"))
        assertTrue("missing TextButton style", xml.contains("Widget.Material3.Button.TextButton"))
        assertTrue("missing Cancel label", xml.contains("android:text=\"Cancel\""))
    }

    @Test fun startDrillServiceUsesRedesignedDialog() {
        val src = readSrc("src/main/java/com/evan/voicesrs/MainActivity.kt")
        val body = startDrillServiceBody(src)
        assertTrue("DIALOG_TITLE", body.contains("StudyBlockChip.DIALOG_TITLE"))
        assertTrue("dialogBody", body.contains("StudyBlockChip.dialogBody"))
        assertTrue("continueLabel", body.contains("StudyBlockChip.continueLabel"))
        assertTrue("NEW_LABEL", body.contains("StudyBlockChip.NEW_LABEL"))
        assertTrue("progress", body.contains("StudyBlockChip.progress"))
        assertTrue("R.layout.dialog_study_block", body.contains("R.layout.dialog_study_block"))
        assertFalse("no setPositiveButton", body.contains("setPositiveButton"))
        assertFalse("no setNegativeButton", body.contains("setNegativeButton"))
        assertFalse("no literal title", body.contains("\"Study block\""))
    }

    @Test fun noHighCodePoints() {
        val src = readSrc("src/main/java/com/evan/voicesrs/MainActivity.kt")
        noHigh("startDrillService", startDrillServiceBody(src))
        noHigh("dialog_study_block.xml", readSrc("src/main/res/layout/dialog_study_block.xml"))
    }
}
