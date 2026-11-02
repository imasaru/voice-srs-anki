package com.evan.voicesrs

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Security rule: no server address, host name, port or token is built into the app.
 * Every server URL comes from Settings (blank default) or gitignored local.properties.
 */
class NoHostLiteralsTest {
    private val rules = listOf(
        "IPv4 literal" to Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b"""),
        "URL with host" to Regex("""https?://(?!schemas\.android\.com/)[A-Za-z0-9\[]"""),
        "tailnet/host name" to Regex("""(?i)\.ts\.net\b|\blocalhost\b|\bexample[.]local\b|\btailscale\b"""),
        "port constant" to Regex("""(?i)\b(\w+_)?PORT\s*=\s*\d{2,5}\b"""),
        "host:port literal" to Regex(""""[^"]*:\d{4,5}\b""")
    )

    private fun files(): List<File> {
        val main = srcFile("main")
        val gradle = listOf(File("build.gradle.kts"), File("app/build.gradle.kts")).filter { it.isFile }.take(1)
        return main.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java", "xml") }.toList() + gradle
    }

    @Test
    fun `no ip host port or url literal in app sources`() {
        val hits = ArrayList<String>()
        val found = files()
        assertTrue("no source files found from ${File(".").absolutePath}", found.size > 20)
        for (f in found) {
            f.readLines().forEachIndexed { i, line ->
                for ((name, re) in rules) if (re.containsMatchIn(line)) hits.add("${f.path}:${i + 1} $name: ${line.trim().take(80)}")
            }
        }
        assertTrue("built-in addresses found:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test
    fun `rules catch the old asr default`() {
        val bad = listOf("    const val ASR_PORT = 8791", "val u = \"http://192.0.2.10:8766\"", "x.tail1234.ts.net", "\"http://h:8791\"")
        for (b in bad) assertTrue(b, rules.any { it.second.containsMatchIn(b) })
        assertTrue(rules.none { it.second.containsMatchIn("xmlns:android=\"http://schemas.android.com/apk/res/android\"") })
        assertTrue(rules.none { it.second.containsMatchIn("if (!url.startsWith(\"http://\")) return") })
    }
}
