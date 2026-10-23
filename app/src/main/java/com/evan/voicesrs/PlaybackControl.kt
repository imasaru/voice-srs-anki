package com.evan.voicesrs

/**
 * Shared read-aloud for explanation sections. Pause is always on screen.
 * [highlight] is the section currently being read when the setting is on.
 */
object PlaybackControl {
    enum class State { IDLE, LOADING, READING, PAUSED }

    data class Snapshot(
        val state: State = State.IDLE,
        val section: Int = -1,
        val turnId: Long = 0L
    )

    fun speakable(doc: SectionFormat.Doc): List<SectionFormat.Block> {
        return doc.blocks.filter { it.kind != SectionFormat.Kind.FOOTER && it.text.isNotBlank() }
    }

    fun play(current: Snapshot, turnId: Long, section: Int): Snapshot {
        if (section < 0) return current
        return Snapshot(State.LOADING, section, turnId)
    }

    fun started(current: Snapshot): Snapshot {
        if (current.state != State.LOADING && current.state != State.READING) return current
        return current.copy(state = State.READING)
    }

    fun pause(current: Snapshot): Snapshot {
        if (current.state != State.READING && current.state != State.LOADING) return current
        return current.copy(state = State.PAUSED)
    }

    fun resume(current: Snapshot): Snapshot {
        if (current.state != State.PAUSED) return current
        return current.copy(state = State.LOADING)
    }

    fun stop(current: Snapshot): Snapshot = Snapshot()

    fun pauseVisible(): Boolean = true

    fun pauseEnabled(current: Snapshot): Boolean {
        return current.state == State.READING ||
            current.state == State.LOADING ||
            current.state == State.PAUSED
    }

    fun pauseLabel(current: Snapshot): String =
        if (current.state == State.PAUSED) "Resume" else "Pause"

    fun sectionState(current: Snapshot, turnId: Long, section: Int): State {
        if (current.turnId != turnId || current.section != section) return State.IDLE
        return current.state
    }

    fun highlight(current: Snapshot, turnId: Long, section: Int, settingOn: Boolean): Boolean {
        if (!settingOn) return false
        if (current.turnId != turnId || current.section != section) return false
        return current.state == State.READING || current.state == State.LOADING
    }
}
