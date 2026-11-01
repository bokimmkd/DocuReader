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
        val doc=PdfDocument()
        try {repeat(2){i->val p=doc.startPage(PdfDocument.PageInfo.Builder(595,842,i+1).create());p.canvas.drawColor(Color.WHITE);p.canvas.drawText("ORIGINAL PAGE ${i+1}",40f,80f,Paint().apply{color=Color.BLACK;textSize=20f});doc.finishPage(p)};file.outputStream().use{doc.writeTo(it)}} finally {doc.close()}
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
            val evidence=File(context.getExternalFilesDir(null),"workflow-qa").also{it.mkdirs()};out.copyTo(File(evidence,"stamp-rotated.pdf"),true)
            PDDocument.load(out).use{doc->
                assertEquals(2,doc.numberOfPages);assertEquals(90,doc.getPage(1).rotation)
                val strip=PDFTextStripper();strip.startPage=1;strip.endPage=1;assertFalse(strip.getText(doc).contains("APPROVED"))
                strip.startPage=2;strip.endPage=2;val text=strip.getText(doc);val compact=text.filterNot{it.isWhitespace()}
                assertTrue("Original text missing: $text",compact.contains("ORIGINALPAGE2"));assertTrue("Stamp text missing: $text",compact.contains("APPROVED"))
            }
            Unit
        } finally {out.delete()}
    }
    private fun click(text:String){assertTrue(device.wait(Until.hasObject(By.text(text)),10000));device.findObject(By.text(text)).click();device.waitForIdle()}
    @Test fun libraryBackRetainsPdfFilterAndRemovalUndoPreservesSource() {
        val store=DocumentStore(context);val original=store.load();val remembered=store.rememberPosition();val source=pdf();val sourceUri=uri(source)
        store.save(listOf(DocumentEntry(sourceUri.toString(),"workflow-source.pdf","PDF",addedAt=1)))
        try { ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {
            assertTrue(device.wait(Until.hasObject(By.text("Library")),15000));click("Library")
            val filters=device.findObjects(By.text("PDF"));assertTrue(filters.isNotEmpty());filters.first().click()
            it.onActivity{a->androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java].updateRemember(false)}
            click("workflow-source.pdf")
            assertTrue(device.wait(Until.hasObject(By.desc("Print")),10000))
            click("Go to");val pageInput=device.wait(Until.findObject(By.clazz("android.widget.EditText")),10000);assertNotNull("Page dialog input",pageInput);pageInput!!.text="2";click("Go")
            assertTrue(device.wait(Until.hasObject(By.text("2 / 2")),10000));click("Tools");click("Stamp PDF")
            assertTrue(device.wait(Until.hasObject(By.text("Page 2")),10000));click("Cancel");device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("workflow-source.pdf")),10000))
            var state:LibraryState?=null
            it.onActivity{a->state=androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java].libraryState}
            assertEquals("PDF",state!!.type)
            device.findObject(By.desc("Document actions")).click();click("Remove from Library");click("Remove")
            assertTrue(source.isFile);assertTrue(device.wait(Until.hasObject(By.text("Undo")),10000));click("Undo")
            assertTrue(device.wait(Until.hasObject(By.text("workflow-source.pdf")),10000));assertTrue(source.isFile)
        } } finally {store.save(original);store.setRememberPosition(remembered)}
    }
    @Test fun bookAppearanceAndBookmarksWorkOfflineAndResume() {
        val file=File(context.filesDir,"library/workflow-book.txt").also{it.parentFile!!.mkdirs();it.writeText((1..80).joinToString("\n\n"){i->"Paragraph $i. A quiet harbour, a book and a place to return. Кириличен текст."})}
        val intent=Intent(context,ImageReaderTestActivity::class.java).setData(uri(file)).putExtra("type","TXT")
        ActivityScenario.launch<ImageReaderTestActivity>(intent).use{
            assertTrue(device.wait(Until.hasObject(By.desc("Book mode")),15000));device.findObject(By.desc("Book mode")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("Paragraph 1.")),15000))
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
        fun visibleFrame(label:String) {
            val bitmap=inst.uiAutomation.takeScreenshot();assertNotNull(bitmap)
            var blue=0
            for(y in 0 until bitmap!!.height step 4) for(x in 0 until bitmap.width step 4) {
                val c=bitmap.getPixel(x,y)
                if(Color.blue(c)>150 && Color.blue(c)>Color.red(c)+60 && Color.blue(c)>Color.green(c)+40)blue++
            }
            val dir=File(context.getExternalFilesDir(null),"workflow-qa").also{it.mkdirs()}
            File(dir,"startup-$label.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle();assertTrue("App frame is blank: $label ($blue)",blue>500)
        }
        repeat(4) {
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use {
                assertTrue(device.wait(Until.hasObject(By.text("Open document")),15000))
                device.waitForIdle();visibleFrame("open-${it.hashCode()}")
                device.pressHome();device.waitForIdle()
                context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
                assertTrue(device.wait(Until.hasObject(By.text("Open document")),15000))
                device.waitForIdle();visibleFrame("resume-${it.hashCode()}")
            }
        }
    }
    @Test fun bulkCategoryUsesOnlyShownPdfFilesAndUnavailableEntryStaysIndexed() {
        val store=DocumentStore(context);val old=store.load();val categories=store.categories();val source=pdf()
        val missing=File(source.parentFile,"missing-workflow.pdf");missing.delete()
        val txt=File(source.parentFile,"other-workflow.txt").also{it.writeText("Do not change")}
        val docs=listOf(DocumentEntry(uri(source).toString(),"workflow-source.pdf","PDF",addedAt=1),DocumentEntry(uri(missing).toString(),missing.name,"PDF",addedAt=1),DocumentEntry(uri(txt).toString(),txt.name,"TXT",addedAt=1))
        store.save(docs)
        try {ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use{s->
            assertTrue(device.wait(Until.hasObject(By.text("Library")),15000));s.onActivity{androidx.lifecycle.ViewModelProvider(it)[MainViewModel::class.java].showLibrary("PDF")};click("Library")
            assertTrue(device.wait(Until.hasObject(By.textContains("File unavailable")),15000))
            device.findObjects(By.desc("Document actions")).first().click();click("Select");click("All shown (2)");click("Category")
            assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")),10000));device.findObjects(By.clazz("android.widget.EditText")).last().text="QA category";click("Apply")
            val saved=store.load();assertEquals(2,saved.count{it.category=="QA category"});assertEquals("Uncategorized",saved.first{it.type=="TXT"}.category)
            assertEquals(3,saved.size);assertTrue(source.isFile);assertTrue(txt.isFile)
            s.onActivity{androidx.lifecycle.ViewModelProvider(it)[MainViewModel::class.java].deleteCategory("QA category")}
            assertTrue(store.load().all{it.category=="Uncategorized"});assertFalse("Category returns after reload",store.categories().contains("QA category"))
        }}finally{store.save(old);store.categories(categories)}
    }
    @Test fun galleryNavigationAndBackKeepTheImagesFilter() {
        val store=DocumentStore(context);val old=store.load()
        val images=(1..2).map{n->val file=File(context.filesDir,"library/gallery-$n.png").also{it.parentFile!!.mkdirs()};val b=android.graphics.Bitmap.createBitmap(600,400,android.graphics.Bitmap.Config.ARGB_8888);b.eraseColor(if(n==1)Color.BLUE else Color.RED);file.outputStream().use{b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};b.recycle();DocumentEntry(uri(file).toString(),file.name,"PNG",addedAt=n.toLong())}
        store.save(images)
        try {ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use{s->
            assertTrue(device.wait(Until.hasObject(By.text("Library")),15000));s.onActivity{androidx.lifecycle.ViewModelProvider(it)[MainViewModel::class.java].showLibrary("Images")};click("Library");click("gallery-2.png")
            assertTrue(device.wait(Until.hasObject(By.text("1 / 2")),15000));device.findObject(By.desc("Next image")).click()
            assertTrue(device.wait(Until.hasObject(By.text("gallery-1.png")),10000));assertTrue(device.wait(Until.hasObject(By.text("2 / 2")),10000))
            var pixels=0;val deadline=android.os.SystemClock.uptimeMillis()+10000
            do {
                val bitmap=inst.uiAutomation.takeScreenshot();pixels=0
                for(y in bitmap.height*35/100 until bitmap.height*70/100 step 4) for(x in bitmap.width/4 until bitmap.width*3/4 step 4) {
                    val c=bitmap.getPixel(x,y);if(Color.blue(c)>180 && Color.red(c)<70 && Color.green(c)<70)pixels++
                }
                bitmap.recycle();if(pixels<1000)Thread.sleep(150)
            }while(pixels<1000 && android.os.SystemClock.uptimeMillis()<deadline)
            assertTrue("Next image title changed but its blue bitmap was not rendered",pixels>=1000);device.pressBack()
            var state:LibraryState?=null;s.onActivity{state=androidx.lifecycle.ViewModelProvider(it)[MainViewModel::class.java].libraryState};assertEquals("Images",state!!.type)
        }}finally{store.save(old)}
    }
}
