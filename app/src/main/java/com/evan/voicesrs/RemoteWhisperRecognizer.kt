package com.evan.voicesrs

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.SpeechRecognizer
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Records one whole answer at 16 kHz mono PCM16 and posts a WAV to
 * `<asrBase>/asr/transcribe`. No 10 s cap: the listen ends on [stop], on
 * [maxMs], or after [silenceMs] of quiet once speech has started.
 * Partials are never emitted. HTTP or IO failure is [SpeechRecognizer.ERROR_NETWORK].
 */
class RemoteWhisperRecognizer(
    private val context: Context,
    private val baseUrl: String,
    private val token: String,
    private val language: String? = null
) : Recognizer {
    override val kind: EngineKind = EngineKind.WHISPER

    /**
     * h143: whether DrillService reported the comm route actually active (latch
     * hit, not a timeout). Only when true do we pin the AudioRecord to the
     * SCO/BLE device; otherwise leave default routing so a cleared/not-yet-active
     * SCO link doesn't record silence.
     */
    @Volatile var commActive = false

    private val main = Handler(Looper.getMainLooper())
    private val state = AtomicInteger(IDLE)
    private var callbacks: RecognizerCallbacks? = null
    private var recorder: AudioRecord? = null
    private var endedAt = 0L

    override val audioEndedAtMs: Long get() = endedAt

    override fun start(cb: RecognizerCallbacks, maxMs: Long, silenceMs: Long) {
        callbacks = cb
        endedAt = 0L
        if (baseUrl.isBlank()) {
            main.post { if (state.get() != CANCELLED) cb.onError(SpeechRecognizer.ERROR_NETWORK) }
            return
        }
        state.set(RUNNING)
        Thread({
            record(cb, maxMs, silenceMs)
        }, "whisper-rec").start()
    }

    override fun stop() {
        state.compareAndSet(RUNNING, STOPPING)
        releaseRecorder()
    }

    override fun cancel() {
        state.set(CANCELLED)
        callbacks = null
        releaseRecorder()
    }

    override fun destroy() {
        cancel()
    }

    @SuppressLint("MissingPermission")
    private fun record(cb: RecognizerCallbacks, maxMs: Long, silenceMs: Long) {
        val rec = openRecorder()
        if (rec == null) {
            postError(cb, SpeechRecognizer.ERROR_CLIENT)
            return
        }
        recorder = rec
        val vad = SilenceVad(SilenceVad.DEFAULT_THRESHOLD_RMS, silenceMs.coerceAtLeast(0L))
        val pcm = ByteArrayOutputStream()
        val frame = ShortArray(FRAME_SAMPLES)
        val started = SystemClock.elapsedRealtime()
        var began = false
        try {
            ListenMic.preferWhen(commActive, ListenMic.preferred(context))?.let { rec.setPreferredDevice(it) }
            rec.startRecording()
            while (state.get() == RUNNING) {
                if (maxMs > 0L && SystemClock.elapsedRealtime() - started >= maxMs) break
                val n = rec.read(frame, 0, frame.size)
                if (n <= 0) continue
                val slice = if (n == frame.size) frame else frame.copyOf(n)
                appendPcm(pcm, slice, n)
                if (!began && SilenceVad.rms(slice) >= SilenceVad.DEFAULT_THRESHOLD_RMS) {
                    began = true
                    main.post { if (state.get() != CANCELLED) cb.onSpeechBegin() }
                }
                if (vad.feed(slice)) break
            }
        } catch (_: Exception) {
            postError(cb, SpeechRecognizer.ERROR_CLIENT)
            return
        } finally {
            if (endedAt == 0L) endedAt = SystemClock.elapsedRealtime()
            releaseRecorder()
        }
        if (state.get() == CANCELLED) return
        state.compareAndSet(RUNNING, STOPPING)
        if (state.get() == CANCELLED) return
        val bytes = pcm.toByteArray()
        if (bytes.isEmpty()) {
            postFinal(cb, "")
            return
        }
        val text = AsrHttp.transcribe(baseUrl, token, WavBytes.wrap(bytes), language)
        if (state.get() == CANCELLED) return
        if (text == null) postError(cb, SpeechRecognizer.ERROR_NETWORK)
        else postFinal(cb, text)
    }

    private fun postFinal(cb: RecognizerCallbacks, text: String) {
        val nbest = if (text.isBlank()) emptyList() else listOf(text)
        main.post { if (state.get() != CANCELLED) cb.onFinal(nbest) }
    }

    private fun postError(cb: RecognizerCallbacks, code: Int) {
        main.post { if (state.get() != CANCELLED) cb.onError(code) }
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(
            SilenceVad.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val buf = maxOf(min, FRAME_SAMPLES * 2 * 8)
        val voice = build(MediaRecorder.AudioSource.VOICE_RECOGNITION, buf)
        if (voice != null && voice.state == AudioRecord.STATE_INITIALIZED) return voice
        voice?.release()
        val mic = build(MediaRecorder.AudioSource.MIC, buf)
        if (mic != null && mic.state == AudioRecord.STATE_INITIALIZED) return mic
        mic?.release()
        return null
    }

    @SuppressLint("MissingPermission")
    private fun build(source: Int, buf: Int): AudioRecord? {
        return try {
            AudioRecord(
                source,
                SilenceVad.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                buf
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseRecorder() {
        val rec = recorder ?: return
        recorder = null
        try { rec.stop() } catch (_: Exception) {}
        try { rec.release() } catch (_: Exception) {}
    }

    private fun appendPcm(out: ByteArrayOutputStream, frame: ShortArray, n: Int) {
        for (i in 0 until n) {
            val sample = frame[i].toInt()
            out.write(sample and 0xff)
            out.write((sample shr 8) and 0xff)
        }
    }

    companion object {
        private const val IDLE = 0
        private const val RUNNING = 1
        private const val STOPPING = 2
        private const val CANCELLED = 3
        private const val FRAME_SAMPLES = 320
    }
}
