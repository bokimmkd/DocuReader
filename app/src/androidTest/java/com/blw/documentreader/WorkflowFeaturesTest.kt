package com.blw.documentreader

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.blw.documentreader.data.*
import com.blw.documentreader.pdf.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class WorkflowFeaturesTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val device get()=UiDevice.getInstance(inst)
    private fun uri(file:File)=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
    private fun pdf():File {
        val file=File(context.filesDir,"library/workflow-source.pdf").also{it.parentFile!!.mkdirs()}
        PdfDocument().use{doc->repeat(2){i->val p=doc.startPage(PdfDocument.PageInfo.Builder(595,842,i+1).create());p.canvas.drawColor(Color.WHITE);p.canvas.drawText("ORIGINAL PAGE ${i+1}",40f,80f,Paint().apply{color=Color.BLACK;textSize=20f});doc.finishPage(p)};file.outputStream().use{doc.writeTo(it)}}
        return file
    }
    @Test fun passwordCopyActuallyRequiresPasswordAndOriginalIsUnchanged()=runBlocking {
        val source=pdf();val bytes=source.readBytes()
        val out=PdfCopies.protect(context,uri(source),"secret-123")
        try {
            assertArrayEquals(bytes,source.readBytes())
            var refused=false;try{PDDocument.load(out,"wrong").close()}catch(_:Exception){refused=true};assertTrue(refused)
            PDDocument.load(out,"secret-123").use{doc->assertEquals(2,doc.numberOfPages);assertTrue(doc.isEncrypted);assertTrue(PDFTextStripper().getText(doc).contains("ORIGINAL PAGE 2"))}
        } finally {out.delete()}
    }
    @Test fun stampKeepsOriginalTextAndAppliesOnlyToChosenRotatedPage()=runBlocking {
        val source=pdf()
        PDDocument.load(source).use{doc->doc.getPage(1).rotation=90;val staged=File(source.parentFile,"rotated.pdf");doc.save(staged);staged.copyTo(source,true);staged.delete()}
        val bytes=source.readBytes()
        val out=PdfCopies.stamp(context,uri(source),PdfStamp("APPROVED",position="Diagonal",allPages=false,page=1))
        try {
            assertArrayEquals(bytes,source.readBytes())
            PDDocument.load(out).use{doc->
                assertEquals(2,doc.numberOfPages);assertEquals(90,doc.getPage(1).rotation)
                val strip=PDFTextStripper();strip.startPage=1;strip.endPage=1;assertFalse(strip.getText(doc).contains("APPROVED"))
                strip.startPage=2;strip.endPage=2;val text=strip.getText(doc);assertTrue(text.contains("ORIGINAL PAGE 2"));assertTrue(text.contains("APPROVED"))
            }
            val evidence=File(context.getExternalFilesDir(null),"workflow-qa").also{it.mkdirs()};out.copyTo(File(evidence,"stamp-rotated.pdf"),true)
        } finally {out.delete()}
    }
    private fun click(text:String){assertTrue(device.wait(Until.hasObject(By.text(text)),10000));device.findObject(By.text(text)).click();device.waitForIdle()}
    @Test fun libraryBackRetainsPdfFilterAndRemovalUndoPreservesSource() {
        val store=DocumentStore(context);val original=store.load();val source=pdf();val sourceUri=uri(source)
        store.save(listOf(DocumentEntry(sourceUri.toString(),"workflow-source.pdf","PDF",addedAt=1)))
        try { ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {
            assertTrue(device.wait(Until.hasObject(By.text("Library")),15000));click("Library")
            val filters=device.findObjects(By.text("PDF"));assertTrue(filters.isNotEmpty());filters.first().click()
            click("workflow-source.pdf")
            assertTrue(device.wait(Until.hasObject(By.desc("Print")),10000));device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("workflow-source.pdf")),10000))
            var state:LibraryState?=null
            it.onActivity{a->state=androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java].libraryState}
            assertEquals("PDF",state!!.type)
            device.findObject(By.desc("Document actions")).click();click("Remove from Library");click("Remove")
            assertTrue(source.isFile);assertTrue(device.wait(Until.hasObject(By.text("Undo")),10000));click("Undo")
            assertTrue(device.wait(Until.hasObject(By.text("workflow-source.pdf")),10000));assertTrue(source.isFile)
        } } finally {store.save(original)}
    }
    @Test fun bookAppearanceAndBookmarksWorkOfflineAndResume() {
        val file=File(context.filesDir,"library/workflow-book.txt").also{it.parentFile!!.mkdirs();it.writeText((1..80).joinToString("\n\n"){i->"Paragraph $i. A quiet harbour, a book and a place to return. Кириличен текст."})}
        val intent=Intent(context,ImageReaderTestActivity::class.java).setData(uri(file)).putExtra("type","TXT")
        ActivityScenario.launch<ImageReaderTestActivity>(intent).use{
            assertTrue(device.wait(Until.hasObject(By.desc("Book mode")),15000));device.findObject(By.desc("Book mode")).click()
            click("Aa");click("Sepia");click("Done")
            assertTrue(device.wait(Until.hasObject(By.text("Next ›")),10000));click("Next ›")
            device.findObject(By.desc("Bookmark page")).click();click("Bookmarks")
            assertTrue(device.wait(Until.hasObject(By.textContains("through book")),10000));click("Done")
            val dir=File(context.getExternalFilesDir(null),"workflow-qa").also{it.mkdirs()}
            device.takeScreenshot(File(dir,"book-sepia.png"))
        }
        ActivityScenario.launch<ImageReaderTestActivity>(intent).use{
            assertTrue(device.wait(Until.hasObject(By.desc("Book mode")),15000));device.findObject(By.desc("Book mode")).click();click("Bookmarks")
            assertTrue(device.wait(Until.hasObject(By.textContains("through book")),10000))
        }
    }
    @Test fun repeatedProductionStartupAndRealBackgroundResumeStayVisible() {
        repeat(4) {
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {
                assertTrue(device.wait(Until.hasObject(By.text("Open document")),15000))
                device.pressHome();device.waitForIdle()
                context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                assertTrue(device.wait(Until.hasObject(By.text("Open document")),15000))
            }
        }
    }
}
