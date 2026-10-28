package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class RouteRebuildTest {

    @Test
    fun changedRebuilds() {
        assertEquals(RouteRebuild.Action.REBUILD, RouteRebuild.onListen(true))
    }

    @Test
    fun unchangedDoesNothing() {
        assertEquals(RouteRebuild.Action.NONE, RouteRebuild.onListen(false))
    }
}
