package com.evan.voicesrs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Debug-only harness hook. Not in the release source set.
 *
 * Routes a grade word into the running drill's grade listen, which then goes
 * through VoiceSelfGrader like speech. Explicit broadcast (required on API 26+):
 *
 *   adb shell am broadcast -a com.evan.voicesrs.TEST_GRADE \
 *     -n com.evan.voicesrs/.TestGradeReceiver --es grade good
 *
 * Show answer (silent mode), no extras:
 *
 *   adb shell am broadcast -a com.evan.voicesrs.TEST_SHOW_ANSWER \
 *     -n com.evan.voicesrs/.TestGradeReceiver
 *
 * Accepted extra keys: grade, grade-word, grade_word, gradeWord.
 * Accepted values: again, hard, good, easy.
 */
class TestGradeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            DrillService.ACTION_TEST_GRADE -> {
                val raw = EXTRA_KEYS.firstNotNullOfOrNull { key -> intent?.getStringExtra(key) }
                Log.i(TAG, "TEST_GRADE received")
                DrillService.submitTestGrade(raw)
            }
            DrillService.ACTION_TEST_SHOW_ANSWER -> {
                Log.i(TAG, "TEST_SHOW_ANSWER received")
                DrillService.submitShowAnswer()
            }
        }
    }

    companion object {
        private const val TAG = "TestGradeReceiver"
        private val EXTRA_KEYS = listOf("grade", "grade-word", "grade_word", "gradeWord")
    }
}
