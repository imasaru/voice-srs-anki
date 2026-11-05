package com.evan.voicesrs

/**
 * Pure plan for a question/card read-aloud while a drill is running (tracker 14B).
 * Replays never count as missed listens; when the replay ends, listening resumes
 * with the miss count reset. Wiring (DrillService / ChatPane) is a later round.
 */
object ReplayPause {
    data class Start(
        val pauseListening: Boolean,
        val stopMic: Boolean,
        val countsAsMiss: Boolean,
    )

    data class Finish(
        val resumeListening: Boolean,
        val resetMissCount: Boolean,
    )

    /** When a question/card corner read-aloud starts. Only acts while the drill is active. */
    fun starting(drilling: Boolean): Start =
        if (drilling) Start(pauseListening = true, stopMic = true, countsAsMiss = false)
        else Start(pauseListening = false, stopMic = false, countsAsMiss = false)

    /** When that replay ends. Only resumes if the drill was active when it started. */
    fun finished(wasDrilling: Boolean): Finish =
        if (wasDrilling) Finish(resumeListening = true, resetMissCount = true)
        else Finish(resumeListening = false, resetMissCount = false)
}
