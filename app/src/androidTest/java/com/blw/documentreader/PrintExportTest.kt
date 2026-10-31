package com.blw.documentreader

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blw.documentreader.data.*
import com.blw.documentreader.printing.*
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
class PrintExportTest {
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = inst.targetContext
    private suspend fun js(web: WebView, code: String): String = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont -> web.evaluateJavascript(code) { if(cont.isActive) cont.resume(it) } }
    }
    private fun fixture(name: String): Uri {
        val dir = File(context.filesDir,"library").also { it.mkdirs() }
        val file = File(dir,name)
        inst.context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return FileProvider.getUriForFile(context,"${context.packageName}.files",file)
    }
    private fun text(pdf: File): Pair<Int,String> = PDDocument.load(pdf).use {
        it.numberOfPages to PDFTextStripper().getText(it).replace(Regex("\\s+")," ")
    }
    @Test fun officeAndTextPdfKeepAllPagesAtDifferentReaderZooms() = runBlocking {
        val output = File(context.getExternalFilesDir(null),"print-qa").also { it.mkdirs() }
        for ((name,type,first,last) in listOf(
            listOf("word.docx",DocumentTypes.WORD,"WORD_START","WORD_END"),
            listOf("excel.xlsx",DocumentTypes.EXCEL,"EXCEL_START","EXCEL_END"),
            listOf("text.txt",DocumentTypes.TXT,"TXT_START","TXT_END"))) {
            val uri=fixture(name)
            val scenario=ActivityScenario.launch(PrintTestActivity::class.java)
            lateinit var web: WebView
            var txtReady=false
            scenario.onActivity { activity ->
                web=if(type==DocumentTypes.TXT) WebView(activity).apply {
                    settings.javaScriptEnabled=false
                    settings.blockNetworkLoads=true
                    webViewClient=object: WebViewClient(){ override fun onPageFinished(v:WebView?,url:String?){txtReady=true} }
                    val txt=inst.context.assets.open(name).use(TextContent::read)
                    loadDataWithBaseURL("https://appassets.androidplatform.net/",TextHtml.document(txt),"text/html","UTF-8",null)
                } else OfficeWebView.create(activity,uri,type)
                activity.setContentView(web)
            }
            try {
                withTimeout(90_000) { while(if(type==DocumentTypes.TXT) !txtReady else js(web,"window.officeReady===true")!="true") delay(250) }
                val results=mutableListOf<Pair<Int,String>>()
                for(zoom in listOf(0.5,2.0)) {
                    if(type!=DocumentTypes.TXT) {
                        js(web,"window.setOfficeZoom($zoom)")
                        assertEquals("null",js(web,"window.prepareOfficePrint(${type==DocumentTypes.EXCEL})"))
                    } else withContext(Dispatchers.Main){web.settings.textZoom=(zoom*100).toInt()}
                    val dest=File(output,"${name.substringBeforeLast('.')}-${zoom}.pdf")
                    WebPdf.export(web,name,dest,type==DocumentTypes.EXCEL)
                    val info=text(dest)
                    assertTrue("Missing beginning for $name: ${info.second}",info.second.contains(first))
                    assertTrue("Missing ending for $name",info.second.contains(last))
                    assertTrue("Missing Cyrillic for $name",info.second.contains("Кирилица") || info.second.contains("Табела"))
                    assertTrue("Not paginated: $name",info.first>=2)
                    if(type==DocumentTypes.EXCEL) assertFalse(info.second.contains("OTHER_SHEET_NOT_SELECTED"))
                    if(type==DocumentTypes.TXT) assertTrue(info.second.contains("<tag>"))
                    results.add(info)
                }
                assertEquals("Zoom changed PDF page count: $name",results[0].first,results[1].first)
                assertEquals("Zoom changed printed content: $name",results[0].second,results[1].second)
                if(type==DocumentTypes.EXCEL) {
                    js(web,"document.getElementById('sheet').value='3';document.getElementById('sheet').onchange()")
                    assertTrue(js(web,"window.prepareOfficePrint(true)").contains("exceeds"))
                }
            } finally { scenario.onActivity{web.destroy()};scenario.close() }
        }
    }
    @Test fun libraryImportPersistsAndClearRecentKeepsCopy() = runBlocking {
        val uri=fixture("word.docx")
        val vm=withContext(Dispatchers.Main){MainViewModel(context.applicationContext as android.app.Application)}
        withContext(Dispatchers.Main){vm.saveToLibrary(uri)}
        withTimeout(30_000){while(withContext(Dispatchers.Main){vm.busy})delay(100)}
        val saved=withContext(Dispatchers.Main){vm.recent.first{it.name=="word.docx" && it.savedLocally}}
        assertEquals(0L,saved.openedAt);assertTrue(saved.addedAt>0)
        withContext(Dispatchers.Main){vm.clearRecent()}
        assertTrue(DocumentStore(context).load().any{it.uri==saved.uri && it.savedLocally})
        context.contentResolver.openInputStream(Uri.parse(saved.uri))!!.use { assertTrue(it.readBytes().isNotEmpty()) }
    }
}
