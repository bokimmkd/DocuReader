package com.blw.documentreader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** On-screen evidence from the actual production Compose reader; not a replacement WebView. */
@RunWith(AndroidJUnit4::class)
class ImageDisplayTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val dir get() = File(context.getExternalFilesDir(null), "image-display-qa").also { it.mkdirs() }
    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun image(name: String, width: Int, height: Int, format: Bitmap.CompressFormat): File {
        val f = File(context.filesDir, "library/$name").also { it.parentFile!!.mkdirs() }
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawColor(Color.rgb(8, 105, 232))
            drawRect(width / 2f, 0f, width.toFloat(), height.toFloat(), Paint().apply { color = Color.rgb(245, 125, 25) })
        }
        f.outputStream().use { assertTrue(b.compress(format, 94, it)) }; b.recycle()
        return f
    }
    private fun colors(b: Bitmap): Pair<Int, Int> {
        var blue = 0; var orange = 0
        for (y in 0 until b.height step 4) for (x in 0 until b.width step 4) {
            val c = b.getPixel(x, y); val r = Color.red(c); val g = Color.green(c); val v = Color.blue(c)
            if (v > 170 && v > r + 65 && v > g + 55) blue++
            if (r > 180 && r > g + 60 && g > v + 50) orange++
        }
        return blue to orange
    }
    @Test fun productionReaderActuallyShowsJpegAndPngPixels() {
        val d = UiDevice.getInstance(inst)
        val failures = mutableListOf<String>()
        val cases = listOf(
            Triple(image("qa-small.jpg", 600, 400, Bitmap.CompressFormat.JPEG), "JPG", "jpg-small"),
            Triple(image("qa-screenshot.png", 1080, 1920, Bitmap.CompressFormat.PNG), "PNG", "png-tall"),
            Triple(image("qa-camera.jpg", 4032, 3024, Bitmap.CompressFormat.JPEG), "JPG", "jpg-camera")
        )
        for ((file, type, label) in cases) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val intent = Intent(context, ImageReaderTestActivity::class.java).setData(uri).putExtra("type", type)
            ActivityScenario.launch<ImageReaderTestActivity>(intent).use { s ->
                assertTrue("Decoder didn't finish: $label", d.wait(Until.hasObject(By.textContains("Image preview optimized")), 15000))
                d.waitForIdle(); Thread.sleep(1200)
                val snap = inst.uiAutomation.takeScreenshot()
                assertNotNull(snap); val visible = colors(snap!!)
                File(dir, "$label.png").outputStream().use { snap.compress(Bitmap.CompressFormat.PNG, 100, it) }; snap.recycle()
                val log = File(dir, "$label.txt")
                log.writeText("On-screen blue=${visible.first}, orange=${visible.second}\n")
                val latch = CountDownLatch(1)
                s.onActivity { a ->
                    val w = requireNotNull(findWeb(a.window.decorView))
                    log.appendText("WebView ${w.width}x${w.height}; contentHeight=${w.contentHeight}; scale=${w.scale}; blockNetwork=${w.settings.blockNetworkLoads}; JS=${w.settings.javaScriptEnabled}\n")
                    // Read DOM only after the unchanged-runtime screenshot; diagnostics never ship.
                    w.settings.javaScriptEnabled = true
                    w.evaluateJavascript("""JSON.stringify({viewport:[innerWidth,innerHeight],body:[document.body.clientWidth,document.body.clientHeight],images:Array.from(document.images).map(i=>({complete:i.complete,natural:[i.naturalWidth,i.naturalHeight],rect:[i.getBoundingClientRect().width,i.getBoundingClientRect().height],maxHeight:getComputedStyle(i).maxHeight,source:i.currentSrc.substring(0,35)}))})""") { value ->
                        log.appendText("DOM $value\n"); w.settings.javaScriptEnabled = false; latch.countDown()
                    }
                }
                assertTrue("DOM probe timed out", latch.await(8, TimeUnit.SECONDS))
                if (visible.first < 500 || visible.second < 500) failures.add("$label: $visible")
            }
        }
        assertTrue("Images missing from production screen: $failures", failures.isEmpty())
    }
}
