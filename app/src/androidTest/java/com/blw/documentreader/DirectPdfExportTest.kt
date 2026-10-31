package com.blw.documentreader

import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.blw.documentreader.formats.ImageDocument
import com.blw.documentreader.printing.DirectPdf
import com.blw.documentreader.printing.PdfSnapshot
import com.blw.documentreader.printing.TextHtml
import com.blw.documentreader.data.TextContent
import com.blw.documentreader.ui.OfficeWebView
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class DirectPdfExportTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private val output get() = File(context.getExternalFilesDir(null), "direct-pdf-qa").also { it.mkdirs() }
    private fun fixture(name: String): Uri {
        val f = File(context.filesDir, "library/$name"); f.parentFile!!.mkdirs()
        inst.context.assets.open(name).use { i -> f.outputStream().use { i.copyTo(it) } }
        return FileProvider.getUriForFile(context, "${context.packageName}.files", f)
    }
    private suspend fun js(web: WebView, script: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { c -> web.evaluateJavascript(script) { if (c.isActive) c.resume(it ?: "null") } }
    }
    private fun info(f: File): Pair<Int, String> = PDDocument.load(f).use { pdf ->
        pdf.numberOfPages to PDFTextStripper().getText(pdf)
    }
    @Test fun directWordExcelAndTextKeepContentCyrillicAndPaginationIndependentOfZoom() = runBlocking {
        for ((name, type, first, last) in listOf(
            listOf("word.docx", "Word", "WORD_START", "WORD_END"),
            listOf("excel.xlsx", "Excel", "EXCEL_START", "EXCEL_END"),
            listOf("text.txt", "TXT", "TXT_START", "TXT_END"))) {
            val uri = fixture(name)
            ActivityScenario.launch(PrintTestActivity::class.java).use { scenario ->
                lateinit var web: WebView
                scenario.onActivity { a -> web = OfficeWebView.create(a, uri, if (type == "TXT") "Word" else type); a.setContentView(web) }
                try {
                    if (type != "TXT") withTimeout(90000) { while (js(web, "window.officeReady===true") != "true") delay(150) }
                    val results = mutableListOf<Pair<Int, String>>()
                    for (zoom in listOf(0.5, 2.0)) {
                        val source = if (type == "TXT") {
                            val text = inst.context.assets.open(name).use { TextContent.read(it) }
                            PdfSnapshot.local(TextHtml.document(text))
                        } else {
                            js(web, "window.setOfficeZoom($zoom)")
                            assertEquals("null", js(web, "window.prepareOfficePrint(${type=="Excel"})"))
                            DirectPdf.officeSnapshot(web)
                        }
                        val f = DirectPdf.create(web.context, source, type == "Excel")
                        val copy = File(output, "${type.lowercase()}-$zoom.pdf"); f.copyTo(copy, overwrite = true); f.delete()
                        val value = info(copy)
                        assertTrue("Missing first marker $name", value.second.contains(first))
                        assertTrue("Missing last marker $name", value.second.contains(last))
                        assertTrue("Missing Cyrillic $name", value.second.contains("Кирилица"))
                        if (type != "Excel") for (symbol in "∈∉∪∩⊂≤≥ℕπΩ∞")
                            assertTrue("Missing math symbol $symbol in $name", value.second.contains(symbol))
                        assertTrue("Not paginated $name", value.first >= 2)
                        if (type == "Word") for (n in 0 until 35) assertTrue("Missing Word paragraph $n", value.second.contains("Word content $n "))
                        if (type == "TXT") for (n in 0 until 150) assertTrue("Missing TXT line $n", value.second.contains("Line $n "))
                        if (type == "Excel") {
                            assertFalse(value.second.contains("OTHER_SHEET_NOT_SELECTED"))
                            for (n in 2 until 150) assertTrue("Missing Excel row $n", value.second.contains("Item $n"))
                        }
                        PDDocument.load(copy).use { pdf ->
                            for (n in 1..pdf.numberOfPages) {
                                val stripper = PDFTextStripper().apply { startPage = n; endPage = n }
                                assertTrue("Blank page $n in $name", stripper.getText(pdf).isNotBlank())
                            }
                            assertEquals(type == "Excel", pdf.getPage(0).mediaBox.width > pdf.getPage(0).mediaBox.height)
                        }
                        results.add(value)
                    }
                    assertEquals("Reader zoom changed page count $name", results[0].first, results[1].first)
                    assertEquals("Reader zoom changed PDF text $name", results[0].second, results[1].second)
                } finally { scenario.onActivity { web.destroy() } }
            }
        }
        for (name in listOf("picture.png", "picture.jpg")) {
            val page = ImageDocument.read(context, fixture(name))
            val f = DirectPdf.create(context, PdfSnapshot.local(page.html, true), false)
            f.copyTo(File(output, name + ".pdf"), overwrite = true); f.delete()
        }
    }
    private fun click(d: UiDevice, selector: BySelector, timeout: Long = 15000) {
        val end = System.currentTimeMillis() + timeout
        do {
            val n = d.wait(Until.findObject(selector), 800)
            if (n != null) try { n.click(); d.waitForIdle(); return } catch (_: StaleObjectException) { }
        } while (System.currentTimeMillis() < end)
        fail("Missing UI: $selector")
    }
    @Test fun productionPdfButtonUsesFileSavePickerThenOffersActionsAndCancelDoesNotClaimSaved() {
        val d = UiDevice.getInstance(inst)
        val intent = Intent(context, DirectExportTestActivity::class.java).setData(fixture("word.docx"))
        ActivityScenario.launch<DirectExportTestActivity>(intent).use { s ->
            click(d, By.desc("Convert to PDF").enabled(true), 90000)
            d.wait(Until.findObject(By.clazz("android.widget.EditText")), 15000)!!.text = "WReader-direct-export.pdf"
            click(d, By.text("Save PDF"))
            val field = d.wait(Until.findObject(By.clazz("android.widget.EditText")), 120000)
            assertNotNull("Expected file-save picker, not a printer dialog", field)
            assertTrue("Wrong destination UI: ${d.currentPackageName}", d.currentPackageName.orEmpty().endsWith("documentsui"))
            assertFalse(d.hasObject(By.pkg("com.android.printspooler")))
            field!!.text = "WReader-direct-export.pdf"
            click(d, By.text(java.util.regex.Pattern.compile("SAVE", java.util.regex.Pattern.CASE_INSENSITIVE)))
            assertTrue(d.wait(Until.hasObject(By.text("PDF saved")), 30000))
            assertTrue(d.hasObject(By.text("Open"))); assertTrue(d.hasObject(By.text("Share"))); assertTrue(d.hasObject(By.text("Print")))
            d.waitForIdle(); Thread.sleep(800); d.takeScreenshot(File(output, "saved-pdf-actions.png"))
            s.onActivity { a ->
                assertNotNull(a.saved); assertFalse(a.busy)
                a.vm.clearRecent()
                assertTrue(com.blw.documentreader.data.DocumentStore(context).load().any { it.uri == a.saved.toString() && it.addedAt > 0L })
                val bytes = context.contentResolver.openInputStream(a.saved!!)!!.use { it.readBytes() }
                File(output, "ui-saved-word.pdf").writeBytes(bytes)
                assertTrue(info(File(output, "ui-saved-word.pdf")).second.contains("WORD_END"))
            }
            click(d, By.text("Share"))
            s.onActivity { assertEquals(it.saved, it.shared?.first); assertEquals("WReader-direct-export.pdf", it.shared?.second) }
            click(d, By.desc("Convert to PDF").enabled(true))
            d.wait(Until.findObject(By.clazz("android.widget.EditText")), 15000)!!.text = "WReader-open-export.pdf"
            click(d, By.text("Save PDF"))
            val nextField = d.wait(Until.findObject(By.clazz("android.widget.EditText")), 120000)
            assertNotNull(nextField); nextField!!.text = "WReader-open-export.pdf"
            click(d, By.text(java.util.regex.Pattern.compile("SAVE", java.util.regex.Pattern.CASE_INSENSITIVE)))
            assertTrue(d.wait(Until.hasObject(By.text("PDF saved")), 30000))
            click(d, By.text("Open"))
            s.onActivity { assertEquals(it.saved, it.opened) }
            click(d, By.desc("Convert to PDF").enabled(true))
            d.wait(Until.findObject(By.clazz("android.widget.EditText")), 15000)!!.text = "WReader-print-export.pdf"
            click(d, By.text("Save PDF"))
            val printField = d.wait(Until.findObject(By.clazz("android.widget.EditText")), 120000)
            assertNotNull(printField); printField!!.text = "WReader-print-export.pdf"
            click(d, By.text(java.util.regex.Pattern.compile("SAVE", java.util.regex.Pattern.CASE_INSENSITIVE)))
            assertTrue(d.wait(Until.hasObject(By.text("PDF saved")), 30000))
            click(d, By.text("Print"))
            assertTrue("Print must open only after explicit action", d.wait(Until.hasObject(By.pkg("com.android.printspooler")), 30000))
            d.pressBack()
            assertTrue(d.wait(Until.hasObject(By.desc("Convert to PDF").enabled(true)), 15000))
            click(d, By.desc("Convert to PDF").enabled(true)); click(d, By.text("Save PDF"))
            assertNotNull(d.wait(Until.findObject(By.clazz("android.widget.EditText")), 120000))
            d.pressBack()
            assertTrue(d.wait(Until.hasObject(By.text("PDF save canceled.")), 15000))
            assertFalse(d.hasObject(By.text("PDF saved")))
            s.onActivity { assertFalse(it.busy) }
        }
    }
}
