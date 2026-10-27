package com.evan.voicesrs

/**
 * Shared read-aloud for explanation sections. Pause is always on screen.
 * [highlight] is the section currently being read when the setting is on.
 */
object PlaybackControl {
    enum class State { IDLE, LOADING, READING, PAUSED }

    /** Where the on-screen Pause button should act: read-aloud, the drill, or nothing. */
    enum class PauseTarget { PLAYBACK, DRILL, DRILL_RESUME, NONE }

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

    /**
     * Read-aloud wins when it is active. Otherwise the button pauses a running
     * drill, resumes a paused one, and does nothing while IDLE and not drilling.
     */
    fun pauseTarget(snap: Snapshot, drilling: Boolean, drillPaused: Boolean = false): PauseTarget =
        if (snap.state == State.READING || snap.state == State.LOADING || snap.state == State.PAUSED) {
            PauseTarget.PLAYBACK
        } else if (drilling && drillPaused) {
            PauseTarget.DRILL_RESUME
        } else if (drilling) {
            PauseTarget.DRILL
        } else {
            PauseTarget.NONE
        }

    fun pauseEnabled(snap: Snapshot, drilling: Boolean, drillPaused: Boolean = false): Boolean =
        pauseTarget(snap, drilling, drillPaused) != PauseTarget.NONE

    /** The button reads Resume for paused read-aloud or a paused drill. */
    fun pauseShowsResume(snap: Snapshot, drilling: Boolean, drillPaused: Boolean): Boolean =
        snap.state == State.PAUSED || pauseTarget(snap, drilling, drillPaused) == PauseTarget.DRILL_RESUME

    fun pauseLabel(current: Snapshot): String =
        if (current.state == State.PAUSED) "Resume" else "Pause"

    /**
     * The merged drill button reads Stop whenever a drill runs, paused or not.
     */
    fun drillButtonStops(drillRunning: Boolean, serviceRunning: Boolean): Boolean =
        drillRunning || serviceRunning

    /**
     * The pause button reads ⏸ Pause when the drill is running, not paused, and
     * read-aloud is idle.
     */
    fun pauseShowsDrillPause(snap: Snapshot, drilling: Boolean, drillPaused: Boolean): Boolean =
        drilling && !drillPaused && snap.state == State.IDLE

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
