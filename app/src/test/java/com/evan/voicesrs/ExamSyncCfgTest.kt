package com.evan.voicesrs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the two cfgassign fixes in [ExamSync.execute]:
 *  - a rejected `setDeckConfigId` is treated as a failure and never writes, and
 *  - the retention preset is saved only by the clone's own id, so the shared
 *    original preset can never be modified.
 */
class ExamSyncCfgTest {

    private val retention = 0.92

    private fun preset(id: Long, name: String = "Default"): JSONObject =
        JSONObject().put("id", id).put("name", name).put("new", JSONObject().put("perDay", 20))

    @Test
    fun `a failed setDeckConfigId is never saved`() {
        // Even when the deck looks like it is on the clone, a rejected
        // assignment must not write anything.
        assertNull(
            ExamSync.retentionPresetToSave(
                assignedId = 44L, setOk = false, cfg = preset(44L), retention = retention
            )
        )
    }

    @Test
    fun `the shared original preset is never saved`() {
        // The deck is still on the shared preset (id 1655) but the clone is 44.
        assertNull(
            ExamSync.retentionPresetToSave(
                assignedId = 44L, setOk = true, cfg = preset(1655L), retention = retention
            )
        )
    }

    @Test
    fun `the clone config is saved by its own id`() {
        val saved = ExamSync.retentionPresetToSave(
            assignedId = 44L,
            setOk = true,
            cfg = preset(44L, "VoiceSRS exam Licenses::Driving"),
            retention = retention
        )!!
        assertEquals(44L, saved.getLong("id"))
        assertEquals(retention, saved.optDouble("desiredRetention"), 1e-9)
    }

    @Test
    fun `no assigned clone id is never saved`() {
        assertNull(
            ExamSync.retentionPresetToSave(
                assignedId = null, setOk = true, cfg = preset(44L), retention = retention
            )
        )
    }

}
