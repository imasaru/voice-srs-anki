package com.evan.voicesrs

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.File
import java.util.zip.ZipInputStream

/** Downloads and unpacks the small English model. Not bundled in the APK. */
object VoskModelFetcher {
    private const val TAG = "VoskModel"

    fun unmeteredOrWifi(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: SecurityException) {
            false
        }
    }

    /** True when the model is on disk after this call. Does not log the token. */
    fun download(context: Context, base: String, token: String): Boolean {
        val dest = VoskModelStore.modelDir(context.filesDir)
        if (VoskModelStore.isReady(dest)) return true
        if (base.isBlank()) return false
        val zip = File(context.cacheDir, "${VoskModelStore.MODEL_NAME}.zip")
        val url = base.trimEnd('/') + "/asr/${VoskModelStore.MODEL_NAME}.zip"
        Log.i(TAG, "download start")
        if (!AsrHttp.download(url, token, zip)) {
            Log.i(TAG, "download failed")
            return false
        }
        return try {
            unzip(zip, File(context.filesDir, "vosk"))
            val ready = VoskModelStore.isReady(dest)
            Log.i(TAG, if (ready) "download ok" else "download unpacked but model files missing")
            ready
        } catch (_: Exception) {
            Log.i(TAG, "download unpack failed")
            false
        } finally {
            zip.delete()
        }
    }

    private fun unzip(zip: File, destDir: File) {
        destDir.mkdirs()
        val root = destDir.canonicalFile
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = File(destDir, entry.name).canonicalFile
                    if (!out.path.startsWith(root.path + File.separator)) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
