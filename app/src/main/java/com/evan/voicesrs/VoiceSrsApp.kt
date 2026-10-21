package com.evan.voicesrs

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.google.android.material.color.DynamicColors
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Installs a crash reporter: any uncaught exception (any thread) is written
 * to the app-private files dir before the process dies, so the next launch
 * can surface it. This is how we diagnose crashes we cannot reproduce on a
 * dev device (e.g. the v0.1.x Start-drill crash on the maintainer's phone).
 */
class VoiceSrsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        try {
            ExplainBatchSchedule.sync(this, Prefs.load(this))
        } catch (_: Exception) {
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashReporter.writeCrash(this, thread, throwable)
            } catch (_: Exception) {
                // The crash reporter must never crash itself.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val DIR_NAME = "crashes"
    private const val MAX_FILES = 5

    fun crashDir(ctx: Context): File = File(ctx.filesDir, DIR_NAME).apply { mkdirs() }

    /** Writes the report; returns the file, or null on failure. Never throws. */
    fun writeCrash(ctx: Context, thread: Thread, t: Throwable): File? {
        return try {
            val dir = crashDir(ctx)
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val f = File(dir, "crash-$ts.txt")
            f.writeText(formatReport(collectHeader(ctx), thread.name, t))
            dir.listFiles()
                ?.sortedBy { it.name }
                ?.dropLast(MAX_FILES)
                ?.forEach { runCatching { it.delete() } }
            f
        } catch (e: Exception) {
            Log.e(TAG, "failed to write crash report", e)
            null
        }
    }

    fun latestCrash(ctx: Context): File? = try {
        crashDir(ctx).listFiles()?.maxByOrNull { it.name }
    } catch (_: Exception) {
        null
    }

    fun clearCrashes(ctx: Context) {
        try {
            crashDir(ctx).listFiles()?.forEach { runCatching { it.delete() } }
        } catch (_: Exception) {
        }
    }

    private fun collectHeader(ctx: Context): String {
        val version = try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${pi.versionName} (${pi.versionCode})"
        } catch (_: Exception) {
            "unknown"
        }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return "Voice SRS crash report\n" +
            "time: $time\n" +
            "device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} " +
            "(SDK ${Build.VERSION.SDK_INT})\n" +
            "app version: $version"
    }

    /**
     * Pure formatting (StringWriter, no android.util.Log) so it stays
     * unit-testable on the JVM.
     */
    fun formatReport(header: String, threadName: String, t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return buildString {
            appendLine(header)
            appendLine("thread: $threadName")
            appendLine("--- stack trace ---")
            append(sw.toString())
        }
    }
}
