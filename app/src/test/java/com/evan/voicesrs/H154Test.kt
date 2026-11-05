package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** h154: closing the Bookmarks sheet makes the main chat list repaint bookmark icons. */
class H154Test {
    private fun src(name: String) = File("src/main/java/com/evan/voicesrs/$name").readText()

    @Test fun a_sheetNotifiesActivityOnDismiss() {
        val s = src("BookmarksSheet.kt")
        val at = s.indexOf("override fun onDismiss(dialog: DialogInterface)")
        assertTrue("BookmarksSheet.onDismiss missing", at >= 0)
        val body = s.substring(at, minOf(s.length, at + 300))
        assertTrue(body.contains("super.onDismiss(dialog)"))
        assertTrue(body.contains("onBookmarksChanged()"))
    }

    @Test fun b_activityForwardsToChatPane() {
        val s = src("MainActivity.kt")
        val at = s.indexOf("fun onBookmarksChanged()")
        assertTrue("MainActivity.onBookmarksChanged missing", at >= 0)
        assertTrue(s.substring(at, minOf(s.length, at + 200)).contains("chatPane?.refreshBookmarks()"))
    }

    @Test fun c_chatPaneRebindsRows() {
        val s = src("ChatPane.kt")
        val at = s.indexOf("fun refreshBookmarks()")
        assertTrue("ChatPane.refreshBookmarks missing", at >= 0)
        val body = s.substring(at, minOf(s.length, at + 200))
        assertTrue(body.contains("if (!attached) return"))
        assertTrue(body.contains("adapter.notifyDataSetChanged()"))
    }
}
