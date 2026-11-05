package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class LogExportTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun entries(zip: File): Map<String, String> {
        val out = linkedMapOf<String, String>()
        ZipFile(zip).use { z ->
            for (e in z.entries()) {
                out[e.name] = z.getInputStream(e).bufferedReader().readText()
            }
        }
        return out
    }

    @Test fun a_zipsAllRotatedFiles() {
        val dir = tmp.newFolder("a")
        File(dir, "diag-0.log").writeText("new\n")
        File(dir, "diag-1.log").writeText("old\n")
        File(dir, "other.txt").writeText("skip\n")
        val out = File(tmp.root, "a.zip")
        assertTrue(LogExport.zipTo(dir, out, "0.1.24", "test-device", nowMs = 0L))
        val m = entries(out)
        assertEquals(setOf("header.txt", "diag-0.log", "diag-1.log"), m.keys)
        assertEquals("new\n", m["diag-0.log"])
        assertEquals("old\n", m["diag-1.log"])
    }

    @Test fun b_includesHeader() {
        val dir = tmp.newFolder("b")
        val out = File(tmp.root, "b.zip")
        assertTrue(LogExport.zipTo(dir, out, "0.1.24", "Pixel Fold", nowMs = 0L))
        val hdr = entries(out)["header.txt"]!!
        assertTrue(hdr.startsWith("voice-srs-diag\n"))
        assertTrue(hdr.contains("version=0.1.24\n"))
        assertTrue(hdr.contains("device=Pixel Fold\n"))
        assertTrue(hdr.contains("exported=1970-01-01T09:00:00.000+09:00\n"))
    }

    @Test fun c_remasksContent() {
        val dir = tmp.newFolder("c")
        File(dir, "diag-0.log").writeText("ai ok Bearer sk-abcdefghijklmnopqrstuvwxyz1234 host=api.example\n")
        val out = File(tmp.root, "c.zip")
        assertTrue(LogExport.zipTo(dir, out, "1", "d", nowMs = 0L))
        val body = entries(out)["diag-0.log"]!!
        assertFalse(body.contains("sk-abcdefghijklmnopqrstuvwxyz1234"))
        assertTrue(body.contains("Bearer ***"))
        assertTrue(body.contains("api.example"))
    }

    @Test fun d_emptyDirProducesHeaderOnlyZip() {
        val dir = tmp.newFolder("d")
        val out = File(tmp.root, "d.zip")
        assertTrue(LogExport.zipTo(dir, out, "0.1.24", "dev", nowMs = 0L))
        assertEquals(setOf("header.txt"), entries(out).keys)
        val missing = File(tmp.root, "missing-dir")
        val out2 = File(tmp.root, "d2.zip")
        assertTrue(LogExport.zipTo(missing, out2, "0.1.24", "dev", nowMs = 0L))
        assertEquals(setOf("header.txt"), entries(out2).keys)
    }
}
