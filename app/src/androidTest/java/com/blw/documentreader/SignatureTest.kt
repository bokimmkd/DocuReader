package com.blw.documentreader

import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.blw.documentreader.data.DocumentStore
import com.blw.documentreader.pdf.*
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SignatureTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private val device get()=UiDevice.getInstance(inst)
    private val evidence get()=File(context.getExternalFilesDir(null),"workflow-qa").also{it.mkdirs()}
    private fun uri(f:File)=FileProvider.getUriForFile(context,"${context.packageName}.files",f)
    private fun source(landscape:Boolean=false, rotation:Int=0):File {
        val f=File(context.filesDir,"library/signature-source.pdf").also{it.parentFile!!.mkdirs()}
        PDDocument().use { doc ->
            repeat(2) { doc.addPage(PDPage(if(landscape) PDRectangle(320f,220f) else PDRectangle(220f,320f))) }
            doc.getPage(1).apply { cropBox=if(landscape) PDRectangle(20f,30f,260f,160f) else PDRectangle(20f,30f,160f,260f); this.rotation=rotation }
            doc.save(f)
        }; return f
    }
    private fun ink():Bitmap=Bitmap.createBitmap(120,40,Bitmap.Config.ARGB_8888).also { b ->
        val c=Canvas(b); c.drawRect(0f,0f,120f,40f,Paint().apply{color=Color.BLUE})
        c.drawRect(0f,0f,20f,15f,Paint().apply{color=Color.RED})
    }
    private fun render(f:File,index:Int):Bitmap = PdfRenderer(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
        r.openPage(index).use { p -> Bitmap.createBitmap(p.width*2,p.height*2,Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.WHITE);p.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_PRINT) } }
    }
    @Test fun signatureMatchesPreviewOnRotatedCroppedPagesAndLeavesOtherPagesUntouched()=runBlocking {
        val mark=ink()
        try { for(landscape in listOf(false,true)) for(rotation in listOf(0,90,180,270)) {
            val input=source(landscape,rotation); val original=input.readBytes()
            val preview=PdfSignatures.preview(context,uri(input),1)
            val pos=SignaturePlacement(.21f,.35f,.32f)
            val output=PdfSignatures.sign(context,uri(input),1,mark,pos)
            try {
                assertArrayEquals(original,input.readBytes())
                val untouched=render(output,0)
                for(y in 0 until untouched.height step 8) for(x in 0 until untouched.width step 8) assertEquals(Color.WHITE,untouched.getPixel(x,y))
                untouched.recycle()
                val image=render(output,1)
                assertEquals(preview.bitmap.width.toFloat()/preview.bitmap.height,image.width.toFloat()/image.height,.01f)
                var left=image.width; var top=image.height; var right=0; var bottom=0; var marks=0
                for(y in 0 until image.height) for(x in 0 until image.width) {
                    val color=image.getPixel(x,y)
                    if(Color.green(color)<100 && (Color.blue(color)>180 || Color.red(color)>180)) { left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x);bottom=maxOf(bottom,y);marks++ }
                }
                assertTrue("No visible signature $landscape/$rotation",marks>100)
                assertEquals(pos.left*image.width,left.toFloat(),3f); assertEquals(pos.top*image.height,top.toFloat(),3f)
                assertEquals(pos.width*image.width,(right-left+1).toFloat(),3f)
                assertEquals(pos.width*image.width/3f,(bottom-top+1).toFloat(),3f)
                assertTrue("Signature was flipped",Color.red(image.getPixel(left+5,top+5))>200)
                output.copyTo(File(evidence,"signature-$landscape-$rotation.pdf"),true)
                image.recycle(); preview.bitmap.recycle()
                PDDocument.load(output).use { doc -> assertEquals(2,doc.numberOfPages);assertEquals(rotation,doc.getPage(1).rotation) }
            } finally { output.delete() }
        } } finally { mark.recycle() }
    }
    @Test fun reusableSignatureIsPrivateOptionalAndCanBeDeletedWithoutChangingSignedCopy()=runBlocking {
        val old=SignatureStore.load(context); val signature=ink(); val input=source()
        try {
            SignatureStore.delete(context); assertNull(SignatureStore.load(context))
            val output=PdfSignatures.sign(context,uri(input),0,signature,SignaturePlacement())
            try {
                assertNull("Signing must not implicitly save reusable ink",SignatureStore.load(context))
                val bytes=output.readBytes(); SignatureStore.save(context,signature)
                val restored=SignatureStore.load(context)!!; assertTrue(signature.sameAs(restored)); restored.recycle()
                SignatureStore.delete(context);assertNull(SignatureStore.load(context)); assertArrayEquals(bytes,output.readBytes())
                val tall=SignaturePlacement(width=.8f).bounded(.025f,2f)
                assertTrue(tall.width*2f/.025f <= .801f)
            } finally { output.delete() }
        } finally { if(old!=null) SignatureStore.save(context,old) else SignatureStore.delete(context); signature.recycle() }
    }
    @Test fun imageImportAndPngExportPreserveColorsTransparencyAndOriginalBytes() = runBlocking {
        val mark = Bitmap.createBitmap(100,60,Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawRect(10f,10f,50f,50f,Paint().apply { color = Color.RED })
            canvas.drawRect(50f,10f,90f,50f,Paint().apply { color = Color.BLUE })
        }
        try {
            for ((extension, format) in listOf("png" to Bitmap.CompressFormat.PNG, "jpg" to Bitmap.CompressFormat.JPEG)) {
                val input = File(context.filesDir,"library/import-signature.$extension")
                input.outputStream().use { assertTrue(mark.compress(format,100,it)) }
                val bytes = input.readBytes()
                val expected = BitmapFactory.decodeFile(input.path)
                val loaded = SignatureImages.load(context,uri(input))
                assertTrue("Import must not recolor or crop $extension", expected.sameAs(loaded))
                assertEquals(100,loaded.width); assertEquals(60,loaded.height)
                if (extension == "png") assertEquals(0,Color.alpha(loaded.getPixel(0,0)))
                val output = File(context.filesDir,"library/export-signature-$extension.png")
                SignatureImages.export(context,uri(output),loaded)
                val exported = BitmapFactory.decodeFile(output.path)
                assertTrue("PNG export must preserve decoded colors and alpha", loaded.sameAs(exported))
                val signed = PdfSignatures.sign(context,uri(source()),0,loaded,SignaturePlacement())
                PDDocument.load(signed).use { doc ->
                    val resources = doc.getPage(0).resources
                    val image = resources.getXObject(resources.xObjectNames.first()) as com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
                    val embedded = image.image
                    assertEquals(loaded.width,embedded.width);assertEquals(loaded.height,embedded.height)
                    for (y in 0 until loaded.height) for (x in 0 until loaded.width) {
                        val expectedPixel=loaded.getPixel(x,y); val actualPixel=embedded.getPixel(x,y)
                        assertEquals(Color.alpha(expectedPixel),Color.alpha(actualPixel))
                        if(Color.alpha(expectedPixel)>0) assertEquals(expectedPixel,actualPixel)
                    }
                }
                assertArrayEquals(bytes,input.readBytes())
                expected.recycle(); loaded.recycle(); exported.recycle(); signed.delete(); input.delete(); output.delete()
            }
            val invalid = File(context.filesDir,"library/not-signature.png").also { it.writeText("not an image") }
            assertTrue(runCatching { SignatureImages.load(context,uri(invalid)) }.isFailure)
            invalid.delete()
            val huge = Bitmap.createBitmap(4097,1,Bitmap.Config.ARGB_8888)
            val largeFile = File(context.filesDir,"library/too-wide.png")
            largeFile.outputStream().use { huge.compress(Bitmap.CompressFormat.PNG,100,it) }; huge.recycle()
            assertTrue(runCatching { SignatureImages.load(context,uri(largeFile)) }.isFailure); largeFile.delete()
        } finally { mark.recycle() }
    }
    @Test fun drawnInkExportsEachSelectedColorWithTransparentBackground() {
        for (color in listOf(Color.BLACK,0xff1565c0.toInt(),0xffc62828.toInt())) {
            val bitmap = com.blw.documentreader.ui.signatureBitmap(listOf(listOf(
                androidx.compose.ui.geometry.Offset(.2f,.3f),androidx.compose.ui.geometry.Offset(.8f,.6f))),2f,color)
            try {
                assertEquals(0,Color.alpha(bitmap.getPixel(0,0)))
                var solid = 0
                for (y in 0 until bitmap.height) for(x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x,y)
                    if(Color.alpha(pixel)==255) { assertEquals(color,pixel); solid++ }
                }
                assertTrue("Selected ink color must be rendered",solid>100)
            } finally { bitmap.recycle() }
        }
    }
    private fun click(text:String) { var obj=device.wait(Until.findObject(By.text(text)),3000)
        if(obj==null && device.hasObject(By.scrollable(true))) {
            runCatching { UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(text)) }
            obj=device.wait(Until.findObject(By.text(text)),12000)
        }
        assertNotNull(text,obj);obj!!.click();device.waitForIdle() }
    private fun screenshot(name:String) { device.waitForIdle();Thread.sleep(250);val b=inst.uiAutomation.takeScreenshot();try { File(evidence,name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)} } finally{b.recycle()} }
    private fun outline():Rect {
        device.waitForIdle()
        val bounds=device.findObject(By.desc("Signature placement preview")).visibleBounds
        val image=inst.uiAutomation.takeScreenshot()
        try {
            var left=bounds.right;var top=bounds.bottom;var right=bounds.left;var bottom=bounds.top
            for(y in bounds.top until bounds.bottom) for(x in bounds.left until bounds.right) {
                val c=image.getPixel(x,y)
                if(Color.red(c) in 15..35 && Color.green(c) in 90..125 && Color.blue(c) in 170..215) {
                    left=minOf(left,x);top=minOf(top,y);right=maxOf(right,x);bottom=maxOf(bottom,y)
                }
            }
            assertTrue("Signature outline is not visible",right>left && bottom>top)
            return Rect(left,top,right,bottom)
        } finally {image.recycle()}
    }
    @Test fun drawingPlacementReuseAndEntryPointsWorkInActualReader() {
        val old=SignatureStore.load(context); val store=DocumentStore(context);val oldEntries=store.load();val oldTheme=store.appearance()
        val oldScale=device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: 1f
        val input=source();val original=input.readBytes()
        try {
            SignatureStore.delete(context);store.setAppearance("Light")
            store.save(listOf(com.blw.documentreader.data.DocumentEntry(uri(input).toString(), "signature-source.pdf", "PDF", inLibrary=true)))
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
                scenario.onActivity { a -> androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java].openDocument(uri(input)) }
                assertNotNull(device.wait(Until.findObject(By.desc("Share options")),20000))
                device.findObject(By.desc("Share options")).click();click("Sign PDF")
                click("Blue")
                val canvas=device.wait(Until.findObject(By.desc("Draw signature here")),10000)!!.visibleBounds
                device.swipe(arrayOf(Point(canvas.left+40,canvas.centerY()),Point(canvas.centerX(),canvas.top+30),Point(canvas.centerX()-20,canvas.bottom-35),Point(canvas.right-40,canvas.centerY())),12)
                val check=device.findObject(By.clazz("android.widget.CheckBox"));assertNotNull(check);check.click()
                screenshot("signature-drawing-light.png");click("Place signature")
                assertNotNull(device.wait(Until.findObject(By.desc("Signature placement preview")),20000))
                screenshot("signature-placement-light.png")
                val savedInk=SignatureStore.load(context)!!
                assertEquals(0xff1565c0.toInt(),run {
                    (0 until savedInk.height).asSequence().flatMap { y -> (0 until savedInk.width).asSequence().map { x -> savedInk.getPixel(x,y) } }.first { Color.alpha(it)==255 }
                }); savedInk.recycle()
                click("Export PNG")
                val pngField=device.wait(Until.findObject(By.pkg(java.util.regex.Pattern.compile("com\\..*documentsui")).clazz("android.widget.EditText")),30000)
                assertNotNull(pngField);pngField!!.text="WReader-signature-${System.nanoTime()}.png"
                device.findObject(By.text(java.util.regex.Pattern.compile("SAVE",java.util.regex.Pattern.CASE_INSENSITIVE))).click()
                assertTrue(device.wait(Until.hasObject(By.text("Signature PNG saved")),15000))
                screenshot("signature-png-exported.png")
                val preview=device.findObject(By.desc("Signature placement preview"))
                val initial=outline();val target=preview.visibleBounds
                device.swipe(initial.centerX(),initial.centerY(),target.centerX(),target.centerY(),25)
                val moved=outline();assertTrue("One-finger signature placement did not move",moved.centerY()<initial.centerY()-20)
                preview.pinchOpen(.35f)
                val enlarged=outline();assertTrue("Preview pinch did not zoom signature",enlarged.width()>moved.width()*1.1f)
                device.swipe(enlarged.centerX(),enlarged.centerY(),enlarged.centerX()-60,enlarged.centerY(),25)
                val dragged=outline();assertTrue("Signature must move with a fresh finger after zoom",dragged.centerX()<enlarged.centerX()-20)
                screenshot("signature-placement-zoomed.png")
                click("Fit")
                click("Save signed copy")
                assertNotNull("System PDF save picker must open",device.wait(Until.findObject(By.pkg(java.util.regex.Pattern.compile("com\\..*documentsui")).clazz("android.widget.EditText")),30000))
                device.pressBack();assertNotNull(device.wait(Until.findObject(By.desc("Signature placement preview")),10000))
                assertFalse(device.hasObject(By.text("PDF saved")));assertArrayEquals(original,input.readBytes())
                device.findObject(By.desc("Next signature page")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Page 2 / 2")),10000))
                click("Draw new signature");click("My signature")
                assertNotNull(device.wait(Until.findObject(By.desc("Signature placement preview")),10000))
                click("Draw new signature");click("Delete saved signature")
                assertTrue(device.wait(Until.gone(By.text("My signature")),10000));click("Cancel")
                assertArrayEquals(original,input.readBytes())
                device.findObject(By.desc("Back")).click();click("Tools");click("Sign PDF")
                // Tools goes through the system picker; cancellation returns to Tools.
                assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")),10000) || device.hasObject(By.pkg("com.android.documentsui")))
                device.pressBack();device.waitForIdle();click("Library")
                val actions=device.wait(Until.findObject(By.desc("Document actions")),10000);assertNotNull(actions);actions!!.click()
                screenshot("library-compact-menu-light.png");click("Sign PDF")
                assertNotNull(device.wait(Until.findObject(By.desc("Draw signature here")),10000));click("Cancel")
                screenshot("library-compact-tabs-light.png")
                // The MAIN launch stays identical across font-scale recreation, so
                // ActivityScenario can track and close the recreated Activity.
                device.executeShellCommand("settings put system font_scale 1.5")
                Thread.sleep(1200);device.waitForIdle()
                click("Library");assertTrue(device.wait(Until.hasObject(By.text("Saved")),10000))
                screenshot("library-compact-tabs-large.png")
                device.findObject(By.desc("Document actions")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Sign PDF")),10000));screenshot("library-compact-menu-large.png")
                device.pressBack();device.executeShellCommand("settings put system font_scale $oldScale")
                Thread.sleep(1200);device.waitForIdle();click("Library")
                scenario.onActivity { a -> androidx.lifecycle.ViewModelProvider(a)[MainViewModel::class.java].updateAppearance("Dark") }
                device.waitForIdle();screenshot("library-compact-tabs-dark.png")
                device.findObject(By.desc("Document actions")).click();screenshot("library-compact-menu-dark.png");click("Sign PDF")
                click("Red")
                val second=device.wait(Until.findObject(By.desc("Draw signature here")),10000)!!.visibleBounds
                // Deliberately start near the edge: drawing must not trigger system Back.
                device.swipe(second.left+35,second.centerY(),second.right-35,second.centerY()-40,20)
                screenshot("signature-drawing-dark.png");click("Place signature")
                assertNotNull(device.wait(Until.findObject(By.desc("Signature placement preview")),20000))
                screenshot("signature-placement-dark.png")
                click("Save signed copy")
                val field=device.wait(Until.findObject(By.pkg(java.util.regex.Pattern.compile("com\\..*documentsui")).clazz("android.widget.EditText")),30000)
                assertNotNull("Signature must use file-save picker",field)
                field!!.text="WReader-signature-${System.nanoTime()}.pdf"
                device.findObject(By.text(java.util.regex.Pattern.compile("SAVE",java.util.regex.Pattern.CASE_INSENSITIVE))).click()
                assertTrue(device.wait(Until.hasObject(By.text("PDF saved")),30000))
                assertTrue(device.hasObject(By.text("Share")));assertTrue(device.hasObject(By.text("Print")))
                screenshot("signature-saved-actions.png");click("Open")
                assertNotNull(device.wait(Until.findObject(By.desc("Print")),20000))
                screenshot("signature-reopened.png")
                device.findObject(By.desc("Print")).click()
                assertTrue(device.wait(Until.hasObject(By.pkg("com.android.printspooler")),20000));device.pressBack()
                assertNotNull(device.wait(Until.findObject(By.desc("Share options")),10000))
                device.findObject(By.desc("Share options")).click();click("Share file")
                assertTrue(device.wait(Until.hasObject(By.pkg("com.android.intentresolver")),20000) || device.hasObject(By.pkg("android")))
                device.pressBack();assertArrayEquals(original,input.readBytes())
            }
        } catch (failure: Throwable) {
            screenshot("signature-ui-failure.png")
            device.dumpWindowHierarchy(File(evidence,"signature-ui-failure.xml"))
            throw failure
        } finally { device.executeShellCommand("settings put system font_scale $oldScale");store.save(oldEntries);store.setAppearance(oldTheme);if(old!=null)SignatureStore.save(context,old) else SignatureStore.delete(context) }
    }
}
