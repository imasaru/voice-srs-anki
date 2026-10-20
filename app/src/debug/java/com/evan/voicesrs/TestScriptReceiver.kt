package com.evan.voicesrs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug-only scripted recognizer. Not in the release source set.
 *
 * Arm it, then queue a whole drill (answer, then grade, per card):
 *
 *   adb -s emulator-5554 shell am broadcast -a com.evan.voicesrs.TEST_RECOGNIZER \
 *     -n com.evan.voicesrs/.TestScriptReceiver --es enabled true
 *   adb -s emulator-5554 shell am broadcast -a com.evan.voicesrs.TEST_SCRIPT \
 *     -n com.evan.voicesrs/.TestScriptReceiver \
 *     --es utterances "tokyo|gud|paris|repeat|that was hard|skip|rome|san"
 *
 * TEST_UTTER adds one line. The real SpeechRecognizer path stays in use until
 * TEST_RECOGNIZER enables this one. TEST_GRADE still feeds exact grade words
 * when the scripted recognizer is off.
 */
class TestScriptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        SpeechHooks.scripted = ScriptedSpeech
        when (intent?.action) {
            ACTION_RECOGNIZER -> {
                val enabled = intent.getBooleanExtra("enabled", false) ||
                    intent.getStringExtra("enabled").equals("true", ignoreCase = true)
                ScriptedSpeech.setEnabled(enabled)
                Log.i(TAG, "TEST_RECOGNIZER enabled=$enabled")
            }
            ACTION_UTTER -> {
                val text = intent.getStringExtra("text") ?: intent.getStringExtra("utter")
                if (text.isNullOrBlank()) {
                    Log.w(TAG, "TEST_UTTER ignored")
                    return
                }
                ScriptedSpeech.setEnabled(true)
                ScriptedSpeech.enqueue(listOf(text))
                Log.i(TAG, "TEST_UTTER queued")
            }
            ACTION_SCRIPT -> {
                val lines = intent.getStringArrayListExtra("lines")
                    ?: intent.getStringExtra("utterances")
                        ?.split('|', '\n')
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                if (lines.isNullOrEmpty()) {
                    Log.w(TAG, "TEST_SCRIPT ignored")
                    return
                }
                ScriptedSpeech.setEnabled(true)
                ScriptedSpeech.enqueue(lines)
                Log.i(TAG, "TEST_SCRIPT count=${lines.size}")
            }
        }
    }

    companion object {
        private const val TAG = "TestScriptReceiver"
        const val ACTION_SCRIPT = "com.evan.voicesrs.TEST_SCRIPT"
        const val ACTION_UTTER = "com.evan.voicesrs.TEST_UTTER"
        const val ACTION_RECOGNIZER = "com.evan.voicesrs.TEST_RECOGNIZER"
    }
}
