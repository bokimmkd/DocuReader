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
        for(dark in listOf(false,true)) {
            ActivityScenario.launch<SettingsTestActivity>(Intent(context,SettingsTestActivity::class.java).putExtra("dark",dark)).use {
                val rate=d.wait(Until.findObject(By.text("Rate app")),10000)!!
                val updates=d.findObject(By.text("Check for updates"))!!
                val density=context.resources.displayMetrics.density
                assertTrue("Settings rows too far apart",(rate.visibleCenter.y-updates.visibleCenter.y)/density<=42f)
                assertTrue(d.wait(Until.hasObject(By.text("Buy")),10000));d.waitForIdle();Thread.sleep(800)
                val dir=File("/sdcard/Download/WReaderSettingsQA").also{it.mkdirs()};d.takeScreenshot(File(dir,if(dark)"settings-dark.png" else "settings-light.png"))
                d.findObject(By.text("Help & Feedback")).click()
                assertTrue(d.wait(Until.hasObject(By.text("Open documents and attachments")),10000))
                d.pressBack();d.waitForIdle()
                d.findObject(By.text("Privacy Policy")).click()
                assertTrue(d.wait(Until.hasObject(By.text("W Reader — Privacy Policy")),10000))
            }
        }
    }
}
