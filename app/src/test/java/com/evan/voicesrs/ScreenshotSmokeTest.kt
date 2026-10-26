package com.evan.voicesrs

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * JVM-only Robolectric screenshot smoke tests.
 *
 * Renders three real layouts (drill screen, chat list with one user + one AI bubble,
 * settings screen) at 1080x2400, asserts each measures to a non-zero size, and writes a
 * PNG to app/build/screenshots/. No emulator required.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotSmokeTest {

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun inflate(layout: Int): View {
        // The Application context does not get the manifest theme in Robolectric, so
        // apply Theme.VoiceSrs explicitly before inflating Material-themed layouts.
        app.setTheme(R.style.Theme_VoiceSrs)
        return LayoutInflater.from(app).inflate(layout, null)
    }

    private fun measureAndLayout(
        root: View,
        width: Int = 1080,
        height: Int = 2400,
    ): View {
        val wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
        val hSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        root.measure(wSpec, hSpec)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    private fun renderPng(root: View): Bitmap {
        val w = root.width
        val h = root.height
        val bitmap = Bitmap.createBitmap(
            if (w > 0) w else 1080,
            if (h > 0) h else 2400,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        root.draw(canvas)
        return bitmap
    }

    // Robolectric test workers may run with the module dir as the CWD; walk up to the
    // Gradle project root (marked by settings.gradle[kts]) so screenshots land at the
    // project-relative path app/build/screenshots/<name>.png regardless of CWD.
    private fun screenshotsDir(): File {
        var dir = File(".").absoluteFile
        repeat(6) {
            if (File(dir, "settings.gradle").exists() ||
                File(dir, "settings.gradle.kts").exists()
            ) {
                return File(dir, "app/build/screenshots")
            }
            val parent = dir.parentFile
            if (parent == null) {
                throw IllegalStateException("project root not found from $dir")
            }
            dir = parent
        }
        throw IllegalStateException("project root not found from ${File(".").absoluteFile}")
    }

    private fun savePng(bitmap: Bitmap, name: String) {
        val dir = screenshotsDir()
        assertTrue("screenshots dir created", dir.exists() || dir.mkdirs())
        File(dir, "$name.png").outputStream().use { out ->
            assertTrue("PNG written", bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
        }
    }

    @Test
    fun drillScreenIdle() {
        val root = measureAndLayout(inflate(R.layout.activity_main))
        assertTrue("drill width>0", root.width > 0)
        assertTrue("drill height>0", root.height > 0)
        savePng(renderPng(root), "drill_idle")
    }

    @Test
    fun chatListOneUserOneAi() {
        // item_chat_ask.xml contains both the user ask bubble and the AI reply bubble.
        val root = inflate(R.layout.item_chat_ask)
        val askQuestion = root.findViewById<TextView>(R.id.askQuestion)
        val askReply = root.findViewById<TextView>(R.id.askReply)
        askQuestion.setText("What is a lakehouse?")
        askReply.text = "A lakehouse is a modern data architecture that combines the low-cost " +
            "object storage of a data lake with the schema and transactional querying of a warehouse."
        askReply.visibility = View.VISIBLE
        savePng(renderPng(measureAndLayout(root)), "chat_list")
    }

    @Test
    fun settingsScreen() {
        val root = measureAndLayout(inflate(R.layout.activity_settings))
        assertTrue("settings width>0", root.width > 0)
        assertTrue("settings height>0", root.height > 0)
        savePng(renderPng(root), "settings")
    }
}
