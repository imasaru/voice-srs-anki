package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** h153o: FileProvider + Settings Export/View logs buttons. */
class H153oTest {
    @Test fun a_fileProviderAndManifest() {
        val paths = File("src/main/res/xml/file_paths.xml").readText()
        assertTrue(paths.contains("diag-export/"))
        assertTrue(paths.contains("cache-path"))
        val man = File("src/main/AndroidManifest.xml").readText()
        assertTrue(man.contains("androidx.core.content.FileProvider"))
        assertTrue(man.contains(".fileprovider"))
        assertTrue(man.contains(".DiagLogActivity"))
    }

    @Test fun b_settingsBackupButtons() {
        val layout = File("src/main/res/layout/activity_settings.xml").readText()
        assertTrue(layout.contains("@+id/exportLogsButton"))
        assertTrue(layout.contains("@+id/viewLogsButton"))
        assertTrue(layout.contains("@string/settings_export_logs"))
        val strings = File("src/main/res/values/strings.xml").readText()
        assertTrue(strings.contains(">Export logs<"))
        assertTrue(strings.contains(">View logs<"))
        val act = File("src/main/java/com/evan/voicesrs/SettingsActivity.kt").readText()
        assertTrue(act.contains("LogExport.zipTo"))
        assertTrue(act.contains("FileProvider.getUriForFile"))
        assertTrue(act.contains("DiagLogActivity::class.java"))
    }

    @Test fun c_diagLogActivityStub() {
        val src = File("src/main/java/com/evan/voicesrs/DiagLogActivity.kt").readText()
        assertTrue(src.contains("class DiagLogActivity"))
        assertTrue(src.contains("R.layout.activity_diag_log"))
        assertTrue(src.contains("diagLogToolbar"))
        val lay = File("src/main/res/layout/activity_diag_log.xml").readText()
        assertTrue(lay.contains("@+id/diagLogToolbar"))
        assertTrue(lay.contains("@string/settings_diag_log_title"))
    }
}
