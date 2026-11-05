package com.evan.voicesrs

import android.util.Log

/** h89: log facade. Each call goes to logcat (unchanged text) and to DiagLog as "<tag> I|W|E msg=... [err=...]". Never throws. */
object DLog {
    /** Logcat writer (level i/w/e, tag, msg, throwable). Unit tests replace it; production uses android.util.Log. */
    @Volatile var logcat: (Char, String, String, Throwable?) -> Unit = { level, tag, msg, tr ->
        when (level) {
            'e' -> Log.e(tag, msg, tr)
            'w' -> Log.w(tag, msg, tr)
            else -> Log.i(tag, msg, tr)
        }
    }

    fun i(tag: String, msg: String, tr: Throwable? = null) = write('i', tag, msg, tr)
    fun w(tag: String, msg: String, tr: Throwable? = null) = write('w', tag, msg, tr)
    fun e(tag: String, msg: String, tr: Throwable? = null) = write('e', tag, msg, tr)

    private fun write(level: Char, tag: String, msg: String, tr: Throwable?) {
        try { logcat(level, tag, msg, tr) } catch (_: Throwable) { }
        val kv = if (tr == null) listOf("msg" to msg) else listOf("msg" to msg, "err" to tr.javaClass.simpleName)
        DiagLog.log(tag, level.uppercaseChar().toString(), kv)
    }
}
