package com.blw.documentreader

import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.blw.documentreader.data.DocumentFormats
import com.blw.documentreader.formats.*
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExtendedFormatsTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=inst.targetContext
    private fun fixture(name:String):Uri {val f=File(context.filesDir,"library/$name");f.parentFile!!.mkdirs();inst.context.assets.open(name).use{i->f.outputStream().use{i.copyTo(it)}};return FileProvider.getUriForFile(context,"${context.packageName}.files",f)}
    @Test fun newFormatsRenderAndSaveRealPdfWithCyrillicAndAllContent()=runBlocking {
        val out=File(context.getExternalFilesDir(null),"print-qa").also{it.mkdirs()}
        for((name,type,marker)in listOf(Triple("sample.csv","CSV","CSV"),Triple("sample.pptx","PowerPoint","PPTX"),Triple("sample.epub","EPUB","EPUB"),Triple("sample.rtf","RTF","RTF"),Triple("sample.odt","ODT","ODT"),Triple("picture.png","PNG",""))) {
            val uri=fixture(name)
            val page=if(type in DocumentFormats.images)ImageDocument.read(context,uri) else context.contentResolver.openInputStream(uri)!!.use{LocalFormats.read(type,it)}
            var ready=false;lateinit var web:WebView
            val s=ActivityScenario.launch(PrintTestActivity::class.java)
            s.onActivity{a->web=WebView(a).apply{settings.javaScriptEnabled=false;settings.allowFileAccess=false;settings.allowContentAccess=false;settings.blockNetworkLoads=true
                webViewClient=object:WebViewClient(){override fun onPageFinished(v:WebView?,u:String?){ready=true};override fun shouldInterceptRequest(v:WebView?,r:WebResourceRequest?):WebResourceResponse?=if(r?.url?.scheme=="data")null else WebResourceResponse("text/plain","UTF-8",byteArrayOf().inputStream())}
                loadDataWithBaseURL("https://appassets.androidplatform.net/",page.html,"text/html","UTF-8",null)};a.setContentView(web)}
            try{
                withTimeout(20000){while(!ready)delay(100)}
                val f=File(out,"extended-${type.lowercase()}.pdf")
                PrintExportTest().saveViaSystemPrint(web,name,f,page.landscape)
                PDDocument.load(f).use{pdf->
                    if(marker.isNotEmpty()){val text=PDFTextStripper().getText(pdf);assertTrue("$type beginning",text.contains(marker+"_START"));assertTrue("$type end",text.contains(marker+"_END"));assertTrue("$type Cyrillic",text.contains("Кирилица"));if(type=="PowerPoint"||type=="EPUB")assertEquals(2,pdf.numberOfPages)}
                    else assertTrue(pdf.getPage(0).resources.xObjectNames.iterator().hasNext())
                }
                inst.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/WReaderPrintQA; cp ${f.absolutePath} /sdcard/Download/WReaderPrintQA/").use{android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()}
            }finally{s.onActivity{web.destroy()};s.close()}
        }
        for(ext in listOf("jpg","webp")){val page=ImageDocument.read(context,fixture("picture.$ext"));assertTrue(page.html.contains("data:image/jpeg;base64,"))}
    }
    @Test fun androidOpenWithAndShareResolveEverySupportedMimeOnly() {
        val pm=context.packageManager
        for(mime in DocumentFormats.mimeByType.values.distinct())for(action in listOf(Intent.ACTION_VIEW,Intent.ACTION_SEND)) {
            val i=Intent(action).setDataAndType(Uri.parse("content://example/document"),mime).addCategory(Intent.CATEGORY_DEFAULT).setPackage(context.packageName)
            assertTrue("No handler for $action $mime",pm.queryIntentActivities(i,android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).any{it.activityInfo.name.endsWith("MainActivity")})
        }
        assertTrue(pm.queryIntentActivities(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://example/document"),"image/vnd.adobe.photoshop").setPackage(context.packageName),0).isEmpty())
    }
    @Test fun compactSettingsAndEmbeddedHelpPrivacyWorkOffline() {
        val d=UiDevice.getInstance(inst)
        fun reveal(text: String): UiObject2 {
            repeat(8) {
                d.findObject(By.text(text))?.let { if (it.visibleBounds.height() > 12) return it }
                d.swipe(d.displayWidth/2, d.displayHeight*3/4, d.displayWidth/2, d.displayHeight/3, 25)
                d.waitForIdle()
            }
            error("Missing Settings item: $text")
        }
        for ((dark, scale, active) in listOf(Triple(false,1f,false),Triple(true,1f,false),Triple(false,1.5f,false),Triple(true,1f,true))) {
            ActivityScenario.launch<SettingsTestActivity>(Intent(context,SettingsTestActivity::class.java)
                .putExtra("dark",dark).putExtra("fontScale",scale).putExtra("adFree",active)).use {
                assertTrue(d.wait(Until.hasObject(By.text("Ads & purchase")),10000))
                if (scale == 1f) {
                    val heading = d.findObject(By.text("Ads & purchase"))
                    val dataHeading = d.findObject(By.text("Data & help"))
                    assertNotNull("Data & help should be visible below purchases", dataHeading)
                    val sectionDp = (dataHeading.visibleBounds.top - heading.visibleBounds.top) / context.resources.displayMetrics.density
                    assertTrue("Purchase section is too tall: ${sectionDp}dp", sectionDp in 1f..150f)
                }
                val offer = reveal("Ad-free Lifetime")
                val buy = reveal(if(active) "Active" else "Buy")
                assertTrue("Purchase overlaps offer", buy.visibleBounds.left >= offer.visibleBounds.right || buy.visibleBounds.top >= offer.visibleBounds.bottom)
                if(active) assertTrue(d.hasObject(By.text("Ad-free is active")))
                val dir=File("/sdcard/Download/WReaderSettingsQA").also{it.mkdirs()}
                d.takeScreenshot(File(dir,"settings-$dark-$scale-$active.png"))
                val rate=reveal("Rate app")
                val updates=d.findObject(By.text("Check for updates"))
                if(scale==1f) assertTrue("Settings rows too far apart", (rate.visibleCenter.y-updates.visibleCenter.y)/context.resources.displayMetrics.density<=42f)
                reveal("Help & Feedback").click()
                assertTrue(d.wait(Until.hasObject(By.text("Open documents and attachments")),10000))
                assertTrue(d.hasObject(By.desc("W Reader logo")))
                d.takeScreenshot(File(dir,"help-$dark-$scale-$active.png"))
                d.pressBack();d.waitForIdle()
                reveal("Privacy Policy").click()
                assertTrue(d.wait(Until.hasObject(By.text("W Reader — Privacy Policy")),10000))
                assertTrue(d.hasObject(By.desc("W Reader logo")))
                d.takeScreenshot(File(dir,"privacy-$dark-$scale-$active.png"))
            }
        }
    }
}
