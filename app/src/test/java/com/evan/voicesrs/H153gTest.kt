package com.evan.voicesrs

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class H153gTest {
    private fun src() = File("src/main/java/com/evan/voicesrs/DrillService.kt").readText()

    @Test fun a_ownsCardCid() { assertTrue(src().contains("private val cardCid = CardCid()")) }

    @Test fun b_newSessionAtSessionStart() {
        val s = src()
        val i = s.indexOf("private fun runSessionOnce()")
        assertTrue(i >= 0)
        val j = s.indexOf("cardCid.newSession()", i)
        assertTrue(j > i && j - i < 1500)
    }

    @Test fun c_nextCardInDrillOne() {
        val s = src()
        val i = s.indexOf("currentListenTag = DrillLog.tag(cardIndex + 1, cardId)")
        assertTrue(i >= 0)
        val j = s.indexOf("cardCid.nextCard()", i)
        assertTrue(j > i && j - i < 300)
    }

    @Test fun d_nextListenInCaptureListen() {
        val s = src()
        val i = s.indexOf("private fun captureListen(")
        assertTrue(i >= 0)
        val j = s.indexOf("cardCid.nextListen()", i)
        assertTrue(j > i && j - i < 300)
    }
}
