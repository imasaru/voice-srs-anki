package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure correlation-id helper for h89 diag lines. */
class CardCidTest {

    @Test
    fun a_formatsSessionCardListen() {
        assertEquals("s0a1b2c3d-c4-l2", CardCid.format("0a1b2c3d", 4, 2))
    }

    @Test
    fun b_listenIncrementsWithinCard() {
        val c = CardCid { 0x0a1b2c3d }
        c.nextCard()
        assertEquals("s0a1b2c3d-c1-l1", c.nextListen())
        assertEquals("s0a1b2c3d-c1-l2", c.nextListen())
    }

    @Test
    fun c_resetsOnNextCard() {
        val c = CardCid { 0x0a1b2c3d }
        c.nextCard()
        c.nextListen()
        c.nextListen()
        c.nextCard()
        assertEquals("s0a1b2c3d-c2-l0", c.current())
        assertEquals("s0a1b2c3d-c2-l1", c.nextListen())
    }

    @Test
    fun d_newSessionNewId() {
        var n = 0
        val c = CardCid { ++n }
        assertEquals("s00000001-c0-l0", c.current())
        c.nextCard()
        c.nextListen()
        c.newSession()
        assertEquals("s00000002-c0-l0", c.current())
    }
}
