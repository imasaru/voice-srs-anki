package com.evan.voicesrs

import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Debug stand-in for SpeechRecognizer. Utterances queued here are returned from
 * [DrillService.listen] and then parsed by the same grade and command path.
 */
object ScriptedSpeech : ScriptedListener {
    private const val TAG = "ScriptedSpeech"
    private val queue = LinkedBlockingQueue<String>()

    @Volatile
    private var on = false

    override fun enabled(): Boolean = on

    fun setEnabled(value: Boolean) {
        on = value
        if (!value) queue.clear()
        Log.i(TAG, "enabled=$value")
    }

    fun enqueue(lines: List<String>) {
        var added = 0
        for (line in lines) {
            val text = line.trim()
            if (text.isEmpty()) continue
            queue.offer(text)
            added++
        }
        Log.i(TAG, "queued=$added waiting=${queue.size}")
    }

    override fun poll(timeoutMs: Long): String? {
        return try {
            queue.poll(timeoutMs.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            null
        }
    }
}
