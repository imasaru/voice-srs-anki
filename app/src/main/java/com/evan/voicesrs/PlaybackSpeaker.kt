package com.evan.voicesrs

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Plays one explanation section through Edge, with on-device fallback.
 * Pause stops the clip. Resume starts that section again.
 */
object PlaybackSpeaker {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var generation = 0
    @Volatile private var lastText = ""
    @Volatile private var lastTurn = 0L
    @Volatile private var lastSection = -1

    fun play(context: Context, turnId: Long, section: Int, text: String, onEnded: () -> Unit = {}) {
        val spoken = text.trim()
        if (spoken.isEmpty()) return
        val gen = ++generation
        lastText = spoken
        lastTurn = turnId
        lastSection = section
        val app = context.applicationContext
        PlaybackUi.update { PlaybackControl.play(it, turnId, section) }
        Thread({
            runCatching { EdgeSpeaker.stopActive() }
            main.post {
                if (generation == gen) PlaybackUi.update { PlaybackControl.started(it) }
            }
            val cfg = try {
                Prefs.load(app)
            } catch (_: Exception) {
                main.post { if (generation == gen) PlaybackUi.update { PlaybackControl.stop(it) } }
                return@Thread
            }
            val cancelled = { generation != gen }
            val ok = try {
                EdgeSpeaker(app).speakBlocking(
                    cfg.neuralUrl,
                    cfg.neuralToken,
                    cfg.neuralVoice,
                    cfg.ttsRate,
                    spoken,
                    cancelled = cancelled,
                    onPlaybackStart = {
                        DrillUi.update { it.copy(engineLabel = EngineLabel.forPlayback(EngineChoice.NEURAL)) }
                    }
                )
            } catch (_: Exception) {
                false
            }
            if (!ok && !cancelled()) {
                runCatching { EdgeSpeaker.stopActive() }
            }
            if (generation == gen) {
                main.post {
                    PlaybackUi.update { PlaybackControl.stop(it) }
                    onEnded()
                }
            }
        }, "section-playback").start()
    }

    fun togglePause(context: Context) {
        val snap = PlaybackUi.state
        when (snap.state) {
            PlaybackControl.State.PAUSED -> {
                play(context, lastTurn, lastSection, lastText)
            }
            PlaybackControl.State.READING, PlaybackControl.State.LOADING -> {
                generation++
                runCatching { EdgeSpeaker.stopActive() }
                PlaybackUi.update { PlaybackControl.pause(it) }
            }
            PlaybackControl.State.IDLE -> Unit
        }
    }

    fun stop() {
        generation++
        lastText = ""
        lastTurn = 0L
        lastSection = -1
        runCatching { EdgeSpeaker.stopActive() }
        PlaybackUi.update { PlaybackControl.stop(it) }
    }
}
