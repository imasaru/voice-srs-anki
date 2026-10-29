package com.evan.voicesrs

import android.media.MediaPlayer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/** h81: recreating MainActivity (split screen / fold) must not stop the read-aloud player. */
@RunWith(RobolectricTestRunner::class)
class MainActivityRecreateTest {
    private class FakePlayer : MediaPlayer() {
        var stops = 0
        override fun stop() { stops++ }
    }

    @After
    fun tearDown() {
        EdgeSpeaker.active = null
        PlaybackUi.update { PlaybackControl.Snapshot() }
    }

    @Test
    fun `recreate keeps the active player and READING state`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val fake = FakePlayer()
        EdgeSpeaker.active = fake
        PlaybackUi.update { PlaybackControl.Snapshot(PlaybackControl.State.READING, 0, 1L) }
        val old = controller.get()
        controller.recreate()
        assertTrue(old.isDestroyed)
        assertEquals(0, fake.stops)
        assertSame(fake, EdgeSpeaker.active)
        assertEquals(PlaybackControl.State.READING, PlaybackUi.state.state)
    }
}
