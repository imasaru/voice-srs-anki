package com.evan.voicesrs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Security baseline (item 42): no android.util.Log call may print a secret.
 *
 * Scans every .kt file under src/main/java/com/evan/voicesrs, finds each line
 * with a Log call, strips quoted string literals (so the TAG arg and any literal
 * text are not treated as code), and fails if the remaining code references a
 * secret-bearing identifier.
 */
class LogSafetyTest {

    private val logMethods = listOf("Log.v(", "Log.d(", "Log.i(", "Log.w(", "Log.e(", "Log.wtf(")

    private val secretWords = listOf(
        "apikey", "aiapikey", "neuraltoken", "token",
        "secret", "password", "authorization", "bearer"
    )

    private val sourceRoot = File("src/main/java/com/evan/voicesrs")

    private val stringLiteral = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")

    private val template = Regex("\\$\\{[^}]*\\}|\\$[A-Za-z_][A-Za-z0-9_]*")

    private data class LogCall(val path: String, val lineNo: Int, val raw: String)

    private fun findLogCalls(): List<LogCall> {
        val found = mutableListOf<LogCall>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    if (logMethods.any { line.contains(it) }) {
                        found.add(LogCall(file.name, idx + 1, line))
                    }
                }
            }
        return found
    }

    private fun codePart(line: String): String {
        // Strip quoted string literals so the TAG arg and literal text are ignored.
        // But keep string-template expressions ($x / ${x}) inside literals,
        // since that is how a secret would get interpolated into log text.
        val templates = stringLiteral.findAll(line).flatMap { lit ->
            template.findAll(lit.value).map { it.value }
        }.joinToString(" ")
        return (stringLiteral.replace(line, "") + " " + templates).lowercase()
    }

    @Test
    fun noLogCallPrintsASecret() {
        val violations = mutableListOf<String>()
        for (call in findLogCalls()) {
            val code = codePart(call.raw)
            val hit = secretWords.firstOrNull { code.contains(it) }
            if (hit != null) {
                violations.add("${call.path}:${call.lineNo} -> ${call.raw.trim()}")
            }
        }
        assertTrue(
            "These Log call site(s) reference a secret:\n" + violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    @Test
    fun templateInsideLiteralIsStillChecked() {
        val code = codePart("Log.d(TAG, \"key=\${prefs.apiKey} t=\$neuralToken\")")
        assertTrue(code, code.contains("apikey") && code.contains("neuraltoken"))
    }

    @Test
    fun scanReadsRealFiles() {
        val calls = findLogCalls()
        assertTrue(
            "Expected the scan to find at least 3 Log calls, found ${calls.size}",
            calls.size >= 3
        )
    }
}
