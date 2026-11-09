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
    private fun click(text:String) { val obj=device.wait(Until.findObject(By.text(text)),15000); assertNotNull(text,obj);obj!!.click();device.waitForIdle() }
    private fun screenshot(name:String) { val b=inst.uiAutomation.takeScreenshot();try { File(evidence,name).outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)} } finally{b.recycle()} }
    @Test fun drawingPlacementReuseAndEntryPointsWorkInActualReader() {
        val old=SignatureStore.load(context); val store=DocumentStore(context);val oldEntries=store.load();val oldTheme=store.appearance()
        val input=source();val original=input.readBytes()
        try {
            SignatureStore.delete(context);store.setAppearance("Light")
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri(input),"application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)).use {
                assertNotNull(device.wait(Until.findObject(By.desc("Share options")),20000))
                device.findObject(By.desc("Share options")).click();click("Sign PDF")
                val canvas=device.wait(Until.findObject(By.desc("Draw signature here")),10000)!!.visibleBounds
                device.swipe(arrayOf(Point(canvas.left+40,canvas.centerY()),Point(canvas.centerX(),canvas.top+30),Point(canvas.centerX()-20,canvas.bottom-35),Point(canvas.right-40,canvas.centerY())),12)
                val check=device.findObject(By.clazz("android.widget.CheckBox"));assertNotNull(check);check.click()
                screenshot("signature-drawing-light.png");click("Place signature")
                assertNotNull(device.wait(Until.findObject(By.desc("Signature placement preview")),20000))
                screenshot("signature-placement-light.png")
                val preview=device.findObject(By.desc("Signature placement preview"));preview.pinchOpen(.5f)
                screenshot("signature-placement-zoomed.png")
                click("Fit")
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
            }
        } finally { store.save(oldEntries);store.setAppearance(oldTheme);if(old!=null)SignatureStore.save(context,old) else SignatureStore.delete(context) }
    }
}
