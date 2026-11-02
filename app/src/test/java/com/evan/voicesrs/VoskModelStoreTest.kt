package com.evan.voicesrs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoskModelStoreTest {

    @Test
    fun `ready only when the acoustic model and the config are both present`() {
        val dir = createTempDir(prefix = "vosk-model")
        try {
            assertFalse(VoskModelStore.isReady(dir))
            File(dir, "am").mkdirs()
            File(dir, "am/final.mdl").writeText("mdl")
            assertFalse(VoskModelStore.isReady(dir))
            File(dir, "conf").mkdirs()
            File(dir, "conf/model.conf").writeText("conf")
            assertTrue(VoskModelStore.isReady(dir))
            File(dir, "am/final.mdl").delete()
            assertFalse(VoskModelStore.isReady(dir))
        } finally {
            dir.deleteRecursively()
        }
    }
}
