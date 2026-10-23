package com.evan.voicesrs

import java.util.concurrent.CopyOnWriteArrayList

/** Snapshot the chat rebinds when a section is read aloud. */
object PlaybackUi {
    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(PlaybackControl.Snapshot) -> Unit>()

    @Volatile
    var state: PlaybackControl.Snapshot = PlaybackControl.Snapshot()
        private set

    fun update(block: (PlaybackControl.Snapshot) -> PlaybackControl.Snapshot) {
        val next = synchronized(lock) {
            val updated = block(state)
            state = updated
            updated
        }
        for (listener in listeners) {
            try {
                listener(next)
            } catch (_: Exception) {
            }
        }
    }

    fun addListener(listener: (PlaybackControl.Snapshot) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (PlaybackControl.Snapshot) -> Unit) {
        listeners.remove(listener)
    }
}
