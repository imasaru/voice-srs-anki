package com.evan.voicesrs

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * h93: JVM PNG renders of the home deck card and the drill screen at Fold3 widths.
 *
 * Half-split 340dp, outer 411dp, inner 700dp (w700dp picks layout-w600dp). Each width renders:
 *  - deck_<w>: include_status_card in a frame with the home list's 20dp side padding (the padding
 *    layout-w600dp/activity_main.xml gives its status card), deck "Databricks DEA", "Due now: 189
 *    cards", timer "20 min", idle (elapsedChip GONE).
 *  - drill_<w>: activity_main drilling at 1:26:23 with a 180-min goal (elapsedChip VISIBLE, value
 *    "180 min", switch on).
 *
 * Each PNG is written to app/build/screenshots/fold/<name>.png and ViewClipCheck runs on the timer
 * row and its host card/header. Helpers inflated from ScreenshotSmokeTest (left untouched).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoldWidthShotsTest {

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
        val out = File(dir, "$name.png")
        out.parentFile?.mkdirs()
        out.outputStream().use { stream ->
            assertTrue("PNG written", bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }

    private val density: Float get() = app.resources.displayMetrics.density
    private fun dp(v: Int) = (v * density).toInt()

    /** include_status_card in a frame with the home list's 20dp side padding (layout-w600dp). */
    private fun deckCard(widthDp: Int, heightDp: Int): ViewGroup {
        app.setTheme(R.style.Theme_VoiceSrs)
        val host = FrameLayout(app)
        host.setPadding(dp(20), 0, dp(20), 0)
        val card = LayoutInflater.from(app)
            .inflate(R.layout.include_status_card, host, false) as ViewGroup
        host.addView(card)
        card.findViewById<TextView>(R.id.deckName).text = "Databricks DEA"
        card.findViewById<TextView>(R.id.dueCount).text = app.getString(R.string.due_format, 189)
        card.findViewById<TextView>(R.id.timerValue).text = "20 min"
        // idle: elapsed chip hidden, icon + switch stay on line 1
        card.findViewById<TextView>(R.id.elapsedChip).visibility = View.GONE
        return measureAndLayout(host, dp(widthDp), dp(heightDp)) as ViewGroup
    }

    /** activity_main drilling at 1:26:23 with a 180-min goal (widest value). */
    private fun drillRoot(widthDp: Int, heightDp: Int): View {
        val root = inflate(R.layout.activity_main)
        root.findViewById<TextView>(R.id.deckName).text = "Databricks DEA"
        root.findViewById<TextView>(R.id.dueCount).text = app.getString(R.string.due_format, 189)
        root.findViewById<TextView>(R.id.elapsedChip).apply {
            text = ElapsedChip.short(5_183_000L) // "1:26:23" (h94c)
            visibility = View.VISIBLE
        }
        root.findViewById<TextView>(R.id.timerValue).text = "180 min"
        root.findViewById<MaterialButton>(R.id.timerGoalChip).isChecked = true
        return measureAndLayout(root, dp(widthDp), dp(heightDp))
    }

    /** The timer row inside a drilled activity_main, and the card/header that hosts it. */
    private fun drillRowAndHost(root: View): Pair<ViewGroup, ViewGroup> {
        val row = root.findViewById<ViewGroup>(R.id.timerRowInclude)
            ?: root.findViewById<ViewGroup>(R.id.timerRow)
        val host = root.findViewById<ViewGroup>(R.id.slimHeader)
            ?: root.findViewById<ViewGroup>(R.id.statusCard)
            ?: row
        return row to host
    }

    @Test
    @Config(qualifiers = "w340dp-h891dp")
    fun deckW340dp() {
        val host = deckCard(340, 891)
        savePng(renderPng(host), "fold/deck_w340dp")
        ViewClipCheck.assertNoChildClipped(host.findViewById<ViewGroup>(R.id.timerRow))
        ViewClipCheck.assertNoChildClipped(host)
    }

    @Test
    @Config(qualifiers = "w340dp-h891dp")
    fun drillW340dp() {
        val root = drillRoot(340, 891)
        val (row, host) = drillRowAndHost(root)
        savePng(renderPng(root), "fold/drill_w340dp")
        ViewClipCheck.assertNoChildClipped(row)
        ViewClipCheck.assertNoChildClipped(host)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun deckW411dp() {
        val host = deckCard(411, 891)
        savePng(renderPng(host), "fold/deck_w411dp")
        ViewClipCheck.assertNoChildClipped(host.findViewById<ViewGroup>(R.id.timerRow))
        ViewClipCheck.assertNoChildClipped(host)
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun drillW411dp() {
        val root = drillRoot(411, 891)
        val (row, host) = drillRowAndHost(root)
        savePng(renderPng(root), "fold/drill_w411dp")
        ViewClipCheck.assertNoChildClipped(row)
        ViewClipCheck.assertNoChildClipped(host)
    }

    @Test
    @Config(qualifiers = "w700dp-h891dp")
    fun deckW700dp() {
        val host = deckCard(700, 891)
        savePng(renderPng(host), "fold/deck_w700dp")
        ViewClipCheck.assertNoChildClipped(host.findViewById<ViewGroup>(R.id.timerRow))
        ViewClipCheck.assertNoChildClipped(host)
    }

    @Test
    @Config(qualifiers = "w700dp-h891dp")
    fun drillW700dp() {
        val root = drillRoot(700, 891)
        val (row, host) = drillRowAndHost(root)
        savePng(renderPng(root), "fold/drill_w700dp")
        ViewClipCheck.assertNoChildClipped(row)
        ViewClipCheck.assertNoChildClipped(host)
    }
}
