package com.evan.voicesrs
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
/** h89: zip diagnostic log files for share/export. Pure JVM; never throws to the caller. */
object LogExport {
    private val TS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    val JST: ZoneId = ZoneId.of("Asia/Tokyo")
    /** Build the header.txt body. */
    fun header(version: String, device: String, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = JST): String {
        val when_ = TS.format(Instant.ofEpochMilli(nowMs).atZone(zone))
        return "voice-srs-diag\nversion=$version\ndevice=$device\nexported=$when_\n"
    }
    /**
     * Zip every existing `diag-*.log` under [dir] (sorted by name) into [out], with `header.txt` as the first entry.
     * Each log line is re-masked with LogMask.mask before writing. Missing dir or empty dir still produces a zip with only header.txt.
     * Returns true on success, false on any failure (and deletes a partial [out] when possible). Never throws.
     */
    fun zipTo(dir: File, out: File, version: String, device: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        try {
            out.parentFile?.mkdirs()
            ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zos ->
                zos.putNextEntry(ZipEntry("header.txt"))
                zos.write(header(version, device, nowMs).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
                if (dir.isDirectory) {
                    val logs = dir.listFiles { f -> f.isFile && f.name.matches(Regex("diag-\\d+\\.log")) }?.sortedBy { it.name }.orEmpty()
                    for (f in logs) {
                        zos.putNextEntry(ZipEntry(f.name))
                        f.forEachLine { line ->
                            zos.write((LogMask.mask(line) + "\n").toByteArray(Charsets.UTF_8))
                        }
                        zos.closeEntry()
                    }
                }
            }
            return true
        } catch (t: Throwable) {
            try { out.delete() } catch (_: Throwable) {}
            return false
        }
    }
}
