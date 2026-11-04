package com.evan.voicesrs

import org.junit.Assert.*
import org.junit.Test

/** h150b (item 63 part 2): the study block chip drawn above the timer row. */
class H150bTest {
    private fun readSrc(rel: String): String =
        listOf(rel, "app/$rel").firstOrNull { java.io.File(it).exists() }?.let { java.io.File(it).readText() }
            ?: throw IllegalStateException("$rel not found")

    private fun noHigh(label: String, s: String) {
        for (ch in s) {
            val cp = ch.code
            assertFalse("high code point in $label: U+" + Integer.toHexString(cp), cp > 0x2000)
        }
    }

    @Test fun studyBlockChipInTimerRow() {
        val src = readSrc("src/main/res/layout/activity_main.xml")
        assertTrue("missing studyBlockChip id", src.contains("android:id=\"@+id/studyBlockChip\""))
        assertTrue("missing Material3.Chip.Assist style", src.contains("Widget.Material3.Chip.Assist"))
        val chipId = src.indexOf("android:id=\"@+id/studyBlockChip\"")
        assertTrue("visibility gone follows studyBlockChip id", src.indexOf("android:visibility=\"gone\"", chipId) > chipId)
        val timerRow = src.indexOf("android:id=\"@+id/timerRowInclude\"")
        val headerMeta = src.indexOf("android:id=\"@+id/headerMeta\"")
        assertTrue("studyBlockChip before timerRowInclude", chipId < timerRow)
        assertTrue("studyBlockChip after headerMeta", chipId > headerMeta)
    }

    @Test fun tickElapsedCallsRenderStudyBlockChip() {
        val src = readSrc("src/main/java/com/evan/voicesrs/MainActivity.kt")
        val a = src.indexOf("private fun tickElapsed()")
        val b = src.indexOf("private fun startElapsedTicker()")
        assertTrue("startElapsedTicker not found", b > a)
        assertTrue("tickElapsed calls renderStudyBlockChip", src.substring(a, b).contains("renderStudyBlockChip()"))
    }

    @Test fun renderStudyBlockChipBody() {
        val src = readSrc("src/main/java/com/evan/voicesrs/MainActivity.kt")
        val a = src.indexOf("private fun renderStudyBlockChip(")
        assertTrue("renderStudyBlockChip func not found", a >= 0)
        val b = src.indexOf("\n    }\n", a)
        assertTrue("closing brace not found", b > a)
        val body = src.substring(a, b)
        assertTrue("kind call", body.contains("StudyBlockChip.kind(s, now, ZoneId.systemDefault())"))
        assertTrue("label call", body.contains("StudyBlockChip.label(s, now, kind)"))
        assertTrue("ic_play_arrow", body.contains("R.drawable.ic_play_arrow"))
        assertTrue("ic_drill_pause", body.contains("R.drawable.ic_drill_pause"))
        assertTrue("ic_check_circle", body.contains("R.drawable.ic_check_circle"))
        assertTrue("dialogBody call", body.contains("StudyBlockChip.dialogBody(s, now)"))
        assertTrue("View.GONE", body.contains("View.GONE"))
        noHigh("renderStudyBlockChip", body)
    }

    @Test fun chipBlockHasNoHighCodePoints() {
        val layout = readSrc("src/main/res/layout/activity_main.xml")
        val chipId = layout.indexOf("android:id=\"@+id/studyBlockChip\"")
        assertTrue("studyBlockChip id not found", chipId >= 0)
        val end = layout.indexOf("/>", chipId)
        assertTrue("chip close not found", end > chipId)
        noHigh("studyBlockChip layout block", layout.substring(chipId, end))

        val kt = readSrc("src/main/java/com/evan/voicesrs/MainActivity.kt")
        val a = kt.indexOf("private fun renderStudyBlockChip(")
        val b = kt.indexOf("\n    }\n", a)
        noHigh("renderStudyBlockChip body", kt.substring(a, b))
    }
}
