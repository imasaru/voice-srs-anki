package com.evan.voicesrs

/**
 * Decides whether a [ChatPane.render()] call changed anything the chat shows.
 *
 * While the drill waits for an answer the think-countdown ticks every frame and
 * calls [DrillUi.update] on every tick, which fires [ChatPane.render()]. When
 * nothing the chat displays has actually changed, [unchanged] reports true so
 * render() can return before rebuilding the same list rows or running its
 * follow/scroll logic.
 */
object ChatRenderGate {

    /**
     * True only when the previous snapshot existed and is identical to the new
     * one: the rows are equal and both the paused and silent session flags are
     * unchanged. A null [prevRows] means this is the first render, which always
     * renders.
     */
    fun unchanged(
        prevRows: List<ChatRow>?,
        rows: List<ChatRow>,
        prevPaused: Boolean?,
        paused: Boolean,
        prevSilent: Boolean?,
        silent: Boolean
    ): Boolean =
        prevRows != null &&
            prevRows == rows &&
            prevPaused == paused &&
            prevSilent == silent
}
