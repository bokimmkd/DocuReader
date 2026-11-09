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
    @Test fun zoomedPdfMovesHorizontallyWithANewSingleFingerGesture() {
        val source=File(context.filesDir,"library/single-finger-pan.pdf").also { it.parentFile!!.mkdirs() }
        val pdf=PdfDocument()
        try {
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,1).create())
            page.canvas.drawColor(Color.WHITE)
            for (column in 0..11) page.canvas.drawRect(column*50f,0f,(column+1)*50f,842f,
                Paint().apply { color=if(column%2==0) Color.rgb(25,85,180) else Color.rgb(235,175,35) })
            pdf.finishPage(page);source.outputStream().use { pdf.writeTo(it) }
        } finally { pdf.close() }
        val intent=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_VIEW)
            .setDataAndType(uri(source),"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ActivityScenario.launch<MainActivity>(intent).use {
            val page=device.wait(Until.findObject(By.desc("Page 1")),20000)
            assertNotNull("PDF page did not open",page)
            page!!.pinchOpen(.65f)
            device.waitForIdle();Thread.sleep(500)
            val rect=device.findObject(By.desc("Page 1")).visibleBounds
            val before=inst.uiAutomation.takeScreenshot()
            try {
                // All pinch pointers are up. This is a new, one-finger drag.
                device.swipe(rect.centerX(),rect.centerY(),rect.centerX()+rect.width()/5,rect.centerY(),30)
                device.waitForIdle();Thread.sleep(500)
                val after=inst.uiAutomation.takeScreenshot()
                try {
                    var changed=0
                    for(y in rect.top+20 until rect.bottom-20 step 3)
                        for(x in rect.left+20 until rect.right-20 step 3)
                            if(before.getPixel(x,y)!=after.getPixel(x,y))changed++
                    assertTrue("New one-finger drag did not move the zoomed PDF",changed>1000)
                    val dir=File(context.getExternalFilesDir(null),"workflow-qa").also{it.mkdirs()}
                    File(dir,"pan-before.png").outputStream().use{before.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                    File(dir,"pan-after.png").outputStream().use{after.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
                } finally { after.recycle() }
            } finally { before.recycle() }
        }
    }
    @Test fun floatingPdfNavigationKeepsNeighborUnzoomedAndSinglePageFits() {
        val source = pdf()
        val store = DocumentStore(context); val oldEntries = store.load()
        val entitlement = context.getSharedPreferences("entitlement", android.content.Context.MODE_PRIVATE)
        val oldEntitlement = entitlement.getBoolean("play_confirmed_offline", false)
        // Pixel comparisons need a fixed viewport and an explicit starting page;
        // asynchronous ad slots and remembered test documents are unrelated inputs.
        store.save(listOf(DocumentEntry(uri(source).toString(), source.name, "PDF", lastPage=0)))
        entitlement.edit().putBoolean("play_confirmed_offline", true).commit()
        try {
            val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW)
                .setDataAndType(uri(source), "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ActivityScenario.launch<MainActivity>(intent).use {
                assertTrue(device.wait(Until.hasObject(By.desc("Page 1")), 20000))
                device.findObject(By.desc("Next page")).click()
                assertTrue(device.wait(Until.hasObject(By.desc("Page 2")), 10000))
                device.waitForIdle(); Thread.sleep(700)
                val rect = device.findObject(By.desc("Page 2")).visibleBounds
                val before = inst.uiAutomation.takeScreenshot()
                val dir = File(context.getExternalFilesDir(null), "workflow-qa").also { it.mkdirs() }
                File(dir,"pdf-neighbor-before.png").outputStream().use { before.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                try {
                    device.findObject(By.desc("Previous page")).click()
                    val first = device.wait(Until.findObject(By.desc("Page 1")), 10000)!!
                    first.pinchOpen(.65f); device.waitForIdle(); Thread.sleep(400)
                    device.findObject(By.desc("Next page")).click()
                    assertTrue(device.wait(Until.hasObject(By.desc("Page 2")), 10000))
                    device.waitForIdle(); Thread.sleep(700)
                    val after = inst.uiAutomation.takeScreenshot()
                    File(dir,"pdf-neighbor-after.png").outputStream().use { after.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    device.dumpWindowHierarchy(File(dir,"pdf-neighbor-after.xml"))
                    try {
                        var changed = 0; var samples = 0
                        for (y in rect.top + 10 until rect.bottom - 80 step 2) for (x in rect.left + 10 until rect.right - 10 step 2) {
                            samples++; if (before.getPixel(x,y) != after.getPixel(x,y)) changed++
                        }
                        assertTrue("Zoom on page 1 changed page 2 pixels ($changed/$samples)", changed < samples / 200)
                    } finally { after.recycle() }
                    device.findObject(By.desc("Share options")).click(); click("Single page")
                    assertTrue(device.wait(Until.hasObject(By.desc("Page 2")), 10000))
                    assertFalse(device.hasObject(By.desc("Page 1")))
                    val dir = File(context.getExternalFilesDir(null), "workflow-qa").also { it.mkdirs() }
                    device.takeScreenshot(File(dir, "pdf-single-page-floating.png"))
                    device.dumpWindowHierarchy(File(dir, "pdf-single-page-floating.xml"))
                    assertFalse("Last-page Next control is enabled", device.findObject(By.desc("Next page")).isEnabled)
                    device.findObject(By.desc("Previous page")).click()
                    assertTrue(device.wait(Until.hasObject(By.desc("Page 1")), 10000))
                    assertFalse(device.findObject(By.desc("Previous page")).isEnabled)
                } finally { before.recycle() }
            }
        } finally {
            store.save(oldEntries)
            entitlement.edit().putBoolean("play_confirmed_offline",oldEntitlement).commit()
        }
    }

    @Test fun bookDarkThemeCoversChromeAndRestoresAppInBothNavigationModes() {
        val store=DocumentStore(context);val oldTheme=store.appearance()
        val prefs=context.getSharedPreferences("book-reader",android.content.Context.MODE_PRIVATE)
        val oldBookTheme=prefs.getString("theme","System")
        val entitlement=context.getSharedPreferences("entitlement",android.content.Context.MODE_PRIVATE)
        val oldEntitlement=entitlement.getBoolean("play_confirmed_offline",false)
        val modeId=context.resources.getIdentifier("config_navBarInteractionMode","integer","android")
        val oldMode=context.resources.getInteger(modeId)
        val source=File(context.filesDir,"library/theme-check.txt").also { it.parentFile!!.mkdirs();it.writeText((1..50).joinToString("\n\n") { "Theme paragraph $it. Reading after dark should stay comfortable." }) }
        val intent=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri(source),"text/plain")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        fun navigation(name: String) {
            device.executeShellCommand("cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.$name")
            device.waitForIdle();Thread.sleep(500)
        }
        fun frame(label: String, expected: Int, y: Int) {
            device.waitForIdle();Thread.sleep(300)
            val bitmap=inst.uiAutomation.takeScreenshot()
            try {
                val actual=bitmap.getPixel(8,y.coerceIn(0,bitmap.height-1))
                assertTrue("$label background is not themed: $actual", kotlin.math.abs(Color.red(actual)-Color.red(expected))<22 && kotlin.math.abs(Color.green(actual)-Color.green(expected))<22 && kotlin.math.abs(Color.blue(actual)-Color.blue(expected))<22)
                val dir=File(context.getExternalFilesDir(null),"workflow-qa").also{it.mkdirs()}
                File(dir,"$label.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            } finally { bitmap.recycle() }
        }
        try {
            store.setAppearance("Light");entitlement.edit().putBoolean("play_confirmed_offline",true).commit()
            for(mode in listOf("threebutton","gestural")) {
                navigation(mode)
                prefs.edit().putString("theme","Light").commit()
                ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                    assertTrue(device.wait(Until.hasObject(By.desc("Book mode")),20000))
                    device.findObject(By.desc("Book mode")).click();click("Aa");click("Dark");click("Done")
                    assertTrue(device.wait(Until.hasObject(By.text("Bookmarks")),10000))
                    val toolbar=device.findObject(By.desc("Back")).visibleBounds
                    val footer=device.findObject(By.text("Bookmarks")).visibleBounds
                    frame("book-dark-toolbar-$mode",Color.rgb(19,27,39),toolbar.centerY())
                    frame("book-dark-footer-$mode",Color.rgb(19,27,39),footer.centerY())
                    frame("book-dark-status-$mode",Color.rgb(19,27,39),5)
                    frame("book-dark-navigation-$mode",Color.rgb(19,27,39),device.displayHeight-5)
                    scenario.onActivity { a ->
                        val bars=androidx.core.view.WindowCompat.getInsetsController(a.window,a.window.decorView)
                        assertFalse(bars.isAppearanceLightStatusBars);assertFalse(bars.isAppearanceLightNavigationBars)
                    }
                    click("Aa");click("Sepia");click("Done")
                    frame("book-sepia-footer-$mode",Color.rgb(245,234,212),device.findObject(By.text("Bookmarks")).visibleCenter.y)
                    click("Aa");click("Dark");click("Done")
                    device.findObject(By.desc("Book actions")).click();click("Document view / Print / Save PDF")
                    assertTrue(device.wait(Until.hasObject(By.desc("Book mode")),10000))
                    frame("book-exit-light-$mode",Color.rgb(249,251,255),device.findObject(By.desc("Back")).visibleCenter.y)
                    scenario.onActivity { a ->
                        val bars=androidx.core.view.WindowCompat.getInsetsController(a.window,a.window.decorView)
                        assertTrue(bars.isAppearanceLightStatusBars);assertTrue(bars.isAppearanceLightNavigationBars)
                    }
                }
            }
        } finally {
            navigation(if(oldMode==2)"gestural" else "threebutton")
            store.setAppearance(oldTheme);prefs.edit().putString("theme",oldBookTheme).commit()
            entitlement.edit().putBoolean("play_confirmed_offline",oldEntitlement).commit()
        }
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
            assertTrue(device.wait(Until.hasObject(By.text("Page 2")),10000));click("Cancel")
            device.findObject(By.desc("Share options")).click();click("Share with password")
            assertTrue(device.wait(Until.hasObject(By.text("Protect & share")),10000));click("Cancel");device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("workflow-source.pdf")),10000))
            var state:LibraryState?=null
            it.onActivity{a->state=androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java].libraryState}
            assertEquals("PDF",state!!.type)
            device.findObject(By.desc("Document actions")).click();click("Remove from Library");click("Remove")
            assertTrue(source.isFile);assertTrue(device.wait(Until.hasObject(By.text("Undo")),10000));click("Undo")
            assertTrue(device.wait(Until.hasObject(By.text("workflow-source.pdf")),10000));assertTrue(source.isFile)
            device.findObject(By.desc("Document actions")).click();assertFalse(device.hasObject(By.text("Convert to PDF")))
            click("PDF tools");click("Stamp PDF");assertTrue(device.wait(Until.hasObject(By.text("Page 1")),10000));click("Cancel")
            device.findObject(By.desc("Document actions")).click();click("PDF tools");click("Extract pages")
            assertTrue(device.wait(Until.hasObject(By.text("Extract selected pages")),15000));click("Cancel");device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("workflow-source.pdf")),10000))
        } } finally {store.save(original);store.setRememberPosition(remembered)}
    }
    @Test fun bookAppearanceAndBookmarksWorkOfflineAndResume() {
        val file=File(context.filesDir,"library/workflow-book.txt").also{it.parentFile!!.mkdirs();it.writeText((1..80).joinToString("\n\n"){i->"Paragraph $i. A quiet harbour, a book and a place to return. Кириличен текст."})}
        context.getSharedPreferences("book-reader",android.content.Context.MODE_PRIVATE).edit().putFloat("font",18f).apply()
        val intent=Intent(context,ImageReaderTestActivity::class.java).setData(uri(file)).putExtra("type","TXT")
        ActivityScenario.launch<ImageReaderTestActivity>(intent).use{
            assertTrue(device.wait(Until.hasObject(By.desc("Book mode")),15000));device.findObject(By.desc("Book mode")).click()
            assertTrue(device.wait(Until.hasObject(By.textContains("Paragraph 1.")),15000))
            click("Aa");click("Sepia");click("Done")
            val bookView=device.wait(Until.findObject(By.clazz("android.webkit.WebView")),10000)
            assertNotNull(bookView);bookView!!.pinchOpen(.5f)
            click("Aa")
            val sizeLabel=device.wait(Until.findObject(By.textStartsWith("Text size:")),10000)
            assertNotNull(sizeLabel);assertTrue("Pinch did not increase Aa text size",sizeLabel!!.text.substringAfter(":").trim().toInt()>18)
            click("Done")
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
            device.findObjects(By.desc("Document actions")).first().click();click("Select");click("All shown (2)");device.findObject(By.desc("Assign category")).click()
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
            assertTrue(device.wait(Until.hasObject(By.desc("Document actions")),10000))
            device.findObjects(By.desc("Document actions")).first().click();click("Convert to PDF")
            assertTrue("Library conversion did not open the save-first dialog",device.wait(Until.hasObject(By.text("Save PDF")),15000))
            assertFalse("Conversion opened Android printing",device.hasObject(By.pkg("com.android.printspooler")))
            click("Cancel");device.pressBack()
            s.onActivity{state=androidx.lifecycle.ViewModelProvider(it)[MainViewModel::class.java].libraryState};assertEquals("Images",state!!.type)
        }}finally{store.save(old)}
    }
    @Test fun reopeningOriginalAfterOfflineCopyKeepsOneCuratedEntry() {
        val store=DocumentStore(context);val before=store.load();val source=pdf();val originalUri=uri(source)
        val doc=DocumentEntry(originalUri.toString(),"original-name.pdf","PDF",true,0,0,"QA category","important",false,42)
        store.save(listOf(doc))
        try { ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
            scenario.onActivity { androidx.lifecycle.ViewModelProvider(it)[MainViewModel::class.java].saveToLibrary(originalUri,doc) }
            val deadline=android.os.SystemClock.uptimeMillis()+10000
            while(!store.load().any{it.savedLocally} && android.os.SystemClock.uptimeMillis()<deadline)Thread.sleep(50)
            val copy=store.load().single();assertTrue(copy.savedLocally);assertTrue(originalUri.toString() in copy.sourceUris)
            repeat(2) {
                scenario.onActivity { androidx.lifecycle.ViewModelProvider(it)[MainViewModel::class.java].openDocument(originalUri) }
                assertTrue(device.wait(Until.hasObject(By.desc("Print")),15000))
                val loaded=store.load().single();assertEquals(copy.uri,loaded.uri);assertEquals("original-name.pdf",loaded.name)
                assertEquals("QA category",loaded.category);assertEquals("important",loaded.tags);assertTrue(loaded.favorite)
                assertTrue(source.isFile);device.pressBack()
            }
        }
        val previous=store.load().single()
        store.save(listOf(previous.copy(name=DocumentTypes.displayName(context,originalUri),sourceUris=emptyList())))
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
            scenario.onActivity { androidx.lifecycle.ViewModelProvider(it)[MainViewModel::class.java].openDocument(originalUri) }
            assertTrue(device.wait(Until.hasObject(By.desc("Print")),15000))
            val restored=store.load().single();assertEquals(previous.uri,restored.uri);assertTrue(originalUri.toString() in restored.sourceUris)
            assertEquals("QA category",restored.category);assertTrue(restored.favorite)
        }
        } finally {store.save(before)}
    }
    @Test fun transientViberAttachmentSurvivesSourceRemovalWithoutDuplicate() {
        val store = DocumentStore(context)
        val previous = store.load()
        val source = pdf()
        // File URI exercises transient intake independently of our durable FileProvider.
        val sourceUri = Uri.fromFile(source)
        store.save(emptyList())
        var saved: DocumentEntry? = null
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                scenario.onActivity { a ->
                    androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java]
                        .openDocument(sourceUri, retainTemporary = true)
                }
                val deadline = android.os.SystemClock.uptimeMillis() + 20000
                while (android.os.SystemClock.uptimeMillis() < deadline) {
                    saved = store.load().singleOrNull { it.temporaryCopy }
                    if (saved != null && device.hasObject(By.desc("Print"))) break
                    Thread.sleep(100)
                }
                val doc = saved ?: error("Transient document was not copied to Library.")
                assertTrue("Original source identity was lost", sourceUri.toString() in doc.sourceUris)
                assertEquals("PDF", doc.type)
                assertEquals(1, store.load().size)
                assertTrue("Source PDF unexpectedly missing", source.delete())
                scenario.onActivity { a ->
                    val vm = androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java]
                    vm.closeDocument()
                    vm.openDocument(Uri.parse(doc.uri))
                }
                assertTrue("Saved copy failed after source deletion",
                    device.wait(Until.hasObject(By.desc("Print")), 15000))
                scenario.onActivity { a ->
                    androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java]
                        .openDocument(sourceUri, retainTemporary = true)
                }
                Thread.sleep(1300)
                assertEquals("Repeat attachment created a duplicate", 1, store.load().size)
                assertEquals(doc.uri, store.load().single().uri)
                assertFalse("Temporary attachment must not enter Saved", store.load().single().isSaved)
                scenario.onActivity { a -> androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java].favorite(doc) }
                val savedDeadline = android.os.SystemClock.uptimeMillis() + 10000
                while (!store.load().single().favorite && android.os.SystemClock.uptimeMillis() < savedDeadline) Thread.sleep(80)
                val favorite = store.load().single()
                assertTrue(favorite.favorite); assertTrue(favorite.savedLocally); assertFalse(favorite.temporaryCopy)
                assertEquals("Save must promote the existing copy", doc.uri, favorite.uri)
                scenario.onActivity { a -> androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java].clearRecent() }
                assertTrue(store.load().single().favorite)
                assertEquals(0L, store.load().single().openedAt)
            }
        } finally {
            saved?.let { entry ->
                val id=Uri.parse(entry.uri).lastPathSegment.orEmpty()
                if (id.isNotBlank() && !id.contains("/")) {
                    File(context.filesDir, "library/$id").delete()
                }
            }
            store.save(previous)
        }
    }

    @Test fun libraryPdfBookModeKeepsPageBookmarksAndNormalViewHasNoZoomBar() {
        context.getSharedPreferences("pdf-reading",android.content.Context.MODE_PRIVATE).edit().clear().apply()
        val store=DocumentStore(context);val before=store.load();val source=pdf()
        store.save(listOf(DocumentEntry(uri(source).toString(),"pdf-reading-test.pdf","PDF",addedAt=1)))
        try { ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.text("Library")),15000));click("Library")
            device.findObject(By.desc("Document actions")).click();click("Book mode")
            assertTrue(device.wait(Until.hasObject(By.desc("Bookmark page")),15000))
            device.findObject(By.desc("Bookmark page")).click();click("Next")
            assertTrue(device.wait(Until.hasObject(By.textContains("2 / 2")),10000))
            device.findObject(By.desc("Bookmark page")).click();click("Bookmarks")
            assertTrue(device.wait(Until.hasObject(By.text("Page 1")),10000));assertTrue(device.hasObject(By.text("Page 2")))
            click("Done");device.findObject(By.desc("Share options")).click();click("Document view")
            assertTrue(device.wait(Until.hasObject(By.text("Go to")),10000));assertFalse(device.hasObject(By.text("Zoom")))
            device.pressBack();assertTrue(device.wait(Until.hasObject(By.desc("Document actions")),10000))
            device.findObject(By.desc("Document actions")).click();click("Book mode");click("Bookmarks")
            assertTrue(device.wait(Until.hasObject(By.text("Page 1")),10000));assertTrue(device.hasObject(By.text("Page 2")))
        }} finally {store.save(before)}
    }
    @Test fun zoomedPdfRerendersFineTextInsteadOfEnlargingPreview() = runBlocking {
        val file=File(context.filesDir,"library/zoom-sharpness.pdf").also { it.parentFile!!.mkdirs() }
        val pdf=PdfDocument()
        try {
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(600,800,1).create())
            page.canvas.drawColor(Color.WHITE);val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=4f}
            for(y in 302..498 step 8)page.canvas.drawText("SHARP TEXT 1234567890",230f,y.toFloat(),paint)
            pdf.finishPage(page);file.outputStream().use{pdf.writeTo(it)}
        } finally { pdf.close() }
        val session=PdfSession.open(context,uri(file))
        try {
            val base=session.render(0,600);val sharp=session.renderViewport(0,600,800,4f,0f,0f)
            val enlarged=android.graphics.Bitmap.createBitmap(600,800,android.graphics.Bitmap.Config.ARGB_8888)
            Canvas(enlarged).apply {drawColor(Color.WHITE);val matrix=android.graphics.Matrix().apply{setScale(4f,4f);postTranslate(-900f,-1200f)};drawBitmap(base,matrix,Paint(Paint.FILTER_BITMAP_FLAG))}
            fun energy(b:android.graphics.Bitmap):Long {var result=0L;for(y in 1 until b.height-1)for(x in 1 until b.width-1){
                fun gray(xx:Int,yy:Int)=Color.red(b.getPixel(xx,yy))
                result+=kotlin.math.abs(4*gray(x,y)-gray(x-1,y)-gray(x+1,y)-gray(x,y-1)-gray(x,y+1))
            };return result}
            assertTrue("Zoom still looks like an enlarged low-resolution preview",energy(sharp)>energy(enlarged)*1.1)
            val dir=File(context.getExternalFilesDir(null),"workflow-qa").also{it.mkdirs()}
            File(dir,"zoom-native.png").outputStream().use{sharp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            File(dir,"zoom-old-preview.png").outputStream().use{enlarged.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            base.recycle();sharp.recycle();enlarged.recycle()
        } finally {session.close();file.delete()}
    }

}
