package com.blw.documentreader.ui

import android.content.Context
import android.net.Uri
import android.webkit.*
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.blw.documentreader.formats.LocalFormats
import com.blw.documentreader.printing.TextHtml
import com.blw.documentreader.data.TextContent
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import kotlin.math.abs
import kotlin.math.ceil

private class BookView(context: Context) : WebView(context) {
    var onPosition: (Float) -> Unit = {}
    var onTurn: (Int) -> Unit = {}
    var pages = false
    private var startX = 0f; private var startY = 0f; private var multiple = false
    fun maximum() = (computeVerticalScrollRange() - height).coerceAtLeast(0)
    fun position() = if (maximum() == 0) 0f else scrollY.toFloat()/maximum()
    fun go(fraction: Float) { scrollTo(0, (maximum()*fraction.coerceIn(0f,1f)).toInt()) }
    fun turn(direction: Int) { smoothScrollTo(0, (scrollY + direction*(height-32).coerceAtLeast(1)).coerceIn(0,maximum())) }
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) { super.onScrollChanged(l,t,oldl,oldt); onPosition(position()) }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX=event.x; startY=event.y; multiple=false }
            MotionEvent.ACTION_POINTER_DOWN -> multiple=true
            MotionEvent.ACTION_UP -> {
                val dx=event.x-startX; val dy=event.y-startY
                if(pages && !multiple && abs(dx)>64*resources.displayMetrics.density && abs(dx)>abs(dy)*1.5) {
                    onTurn(if(dx<0)1 else -1)
                    val cancel=MotionEvent.obtain(event); cancel.action=MotionEvent.ACTION_CANCEL; super.onTouchEvent(cancel); cancel.recycle(); return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
}

/** Local EPUB/TXT reading controls. JavaScript, remote resources and file access stay disabled. */
@Composable
fun BookReaderScreen(uri: Uri, name: String, type: String, rememberPosition: Boolean,
    onBack: () -> Unit, onShare: () -> Unit, onReady: () -> Unit, onDocumentView: () -> Unit,
    report: (String) -> Unit) {
    val context=LocalContext.current
    val prefs=remember { context.getSharedPreferences("book-reader",Context.MODE_PRIVATE) }
    val key=remember(uri) { java.security.MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray()).joinToString(""){"%02x".format(it)} }
    var size by remember { mutableFloatStateOf(prefs.getFloat("font",18f).coerceIn(14f,30f)) }
    var spacing by remember { mutableStateOf(prefs.getString("spacing","Comfortable") ?: "Comfortable") }
    var margin by remember { mutableStateOf(prefs.getString("margin","Normal") ?: "Normal") }
    var theme by remember { mutableStateOf(prefs.getString("theme","System") ?: "System") }
    var pages by remember { mutableStateOf(prefs.getBoolean("pages",true)) }
    var appearance by remember { mutableStateOf(false) }; var marksOpen by remember { mutableStateOf(false) }
    var source by remember(uri) { mutableStateOf("") }; var error by remember(uri) { mutableStateOf<String?>(null) }
    var fraction by rememberSaveable(uri.toString()) { mutableFloatStateOf(if(rememberPosition)prefs.getFloat("position-$key",0f).coerceIn(0f,1f) else 0f) }
    var marks by remember(uri) { mutableStateOf(prefs.getString("marks-$key","").orEmpty().split(',').mapNotNull { it.toFloatOrNull()?.coerceIn(0f,1f) }.distinct().take(30)) }
    var count by remember { mutableIntStateOf(1) }; var page by remember { mutableIntStateOf(1) }
    var restoring by remember { mutableStateOf(true) }
    val dark=when(theme){"Dark"->true;"Light","Sepia"->false;else->MaterialTheme.colorScheme.background.luminance()<.4f}
    val bg=when{theme=="Sepia"->"#f5ead4";dark->"#131b27";else->"#fffdf8"}
    val fg=when{theme=="Sepia"->"#443727";dark->"#e8edf4";else->"#273044"}
    val ready by rememberUpdatedState(onReady)
    val positionWriter by rememberUpdatedState<(Float)->Unit>({ value ->
        if(!restoring) {
            fraction=value.coerceIn(0f,1f)
            if(rememberPosition)prefs.edit().putFloat("position-$key",fraction).apply()
        }
    })
    val web=remember(uri) { BookView(context).apply {
        settings.javaScriptEnabled=false;settings.domStorageEnabled=false;settings.allowFileAccess=false;settings.allowContentAccess=false
        settings.blockNetworkLoads=true;settings.setSupportZoom(false)
        webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?)=true
            override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest?)=
                if(request?.url?.scheme=="data")null else WebResourceResponse("text/plain","UTF-8",byteArrayOf().inputStream())
            override fun onPageFinished(view:WebView?,url:String?) { postDelayed({
                go(fraction); restoring=false
                count=ceil((maximum()+height).toFloat()/height.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                ready()
            },120) }
        }
        onPosition={ value -> positionWriter(value); page=if(count==1)1 else (value*(count-1)).toInt()+1 }
        onTurn={ turn(it) }
    } }
    DisposableEffect(web) { onDispose { web.onPosition={};web.stopLoading();web.destroy() } }
    LaunchedEffect(uri) {
        try {
            source=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use {
                if(type=="TXT")TextHtml.document(TextContent.read(it)) else LocalFormats.read(type,it).html
            } ?: error("Book access expired. Locate the file again.") }
        } catch(cancel:CancellationException){throw cancel}
        catch(ex:Exception){error=ex.message ?: "Could not open book."}
    }
    LaunchedEffect(source,size,spacing,margin,theme,dark) {
        if(source.isNotEmpty()) {
            restoring=true
            val html=withContext(Dispatchers.Default) {
                val doc=Jsoup.parse(source)
                val leading=when(spacing){"Compact"->1.45;"Wide"->1.95;else->1.7}
                val padding=when(margin){"Narrow"->14;"Wide"->36;else->24}
                doc.head().appendElement("style").appendText("html,body{background:$bg!important;color:$fg!important}body{font:${size.toInt()}px/$leading Georgia,serif!important;padding:${padding}px!important}pre{font:inherit;white-space:pre-wrap}p,li,td{font-size:inherit}section.chapter{margin-bottom:2em}img{max-width:100%;height:auto}")
                doc.outerHtml()
            }
            web.setBackgroundColor(android.graphics.Color.parseColor(bg))
            web.loadDataWithBaseURL("https://appassets.androidplatform.net/",html,"text/html","UTF-8",null)
        }
        prefs.edit().putFloat("font",size).putString("spacing",spacing).putString("margin",margin).putString("theme",theme).apply()
    }
    fun setMarks(values:List<Float>){marks=values.take(30);prefs.edit().putString("marks-$key",marks.joinToString(",")).apply()}
    val marked=marks.any{abs(it-fraction)<.005f}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")}
            Column(Modifier.weight(1f)){Text(name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleSmall);Text("$type · Book mode",style=MaterialTheme.typography.labelSmall)}
            IconButton(onClick={if(marked)setMarks(marks.filterNot{abs(it-fraction)<.005f})else if(marks.size<30)setMarks(marks+fraction)else report("Up to 30 bookmarks per book.")}){Icon(if(marked)Icons.Default.Bookmark else Icons.Default.BookmarkBorder,"Bookmark page")}
            TextButton(onClick={appearance=true},contentPadding=PaddingValues(6.dp)){Text("Aa")}
            var menu by remember {mutableStateOf(false)}
            Box { IconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Book actions")};DropdownMenu(menu,{menu=false}){
                DropdownMenuItem(text={Text("Bookmarks (${marks.size})")},onClick={menu=false;marksOpen=true})
                DropdownMenuItem(text={Text("Share file")},onClick={menu=false;onShare()})
                DropdownMenuItem(text={Text("Document view / Print / Save PDF")},onClick={menu=false;onDocumentView()})
            } }
        }
        if(error!=null)Text(error!!,Modifier.padding(20.dp))
        else AndroidView(factory={web},update={it.pages=pages},modifier=Modifier.weight(1f).fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            Text("${(fraction*100).toInt()}%",style=MaterialTheme.typography.labelSmall)
            Slider(fraction,{fraction=it;web.go(it)},modifier=Modifier.weight(1f))
            Text("$page / $count",style=MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(enabled=fraction>.001f,onClick={web.turn(-1)}){Text("‹ Previous")}
            TextButton(onClick={marksOpen=true}){Text("Bookmarks")}
            TextButton(enabled=fraction<.999f && count>1,onClick={web.turn(1)}){Text("Next ›")}
        }
    }
    if(appearance)AlertDialog(onDismissRequest={appearance=false},title={WReaderDialogTitle("Reading appearance")},text={Column(Modifier.verticalScroll(rememberScrollState())){
        Row {listOf("System","Light","Sepia","Dark").forEach{value->FilterChip(theme==value,{theme=value},label={Text(value,style=MaterialTheme.typography.labelSmall)})}}
        Text("Text size: ${size.toInt()}");Slider(size,{size=it},valueRange=14f..30f,steps=15)
        Text("Line spacing");Row{listOf("Compact","Comfortable","Wide").forEach{value->FilterChip(spacing==value,{spacing=value},label={Text(value,style=MaterialTheme.typography.labelSmall)})}}
        Text("Margins");Row{listOf("Narrow","Normal","Wide").forEach{value->FilterChip(margin==value,{margin=value},label={Text(value)})}}
        Row{Switch(pages,{pages=it;prefs.edit().putBoolean("pages",it).apply()});Text(if(pages)"Page turns / swipe" else "Scroll",Modifier.padding(12.dp))}
    }},confirmButton={TextButton(onClick={appearance=false}){Text("Done")}})
    if(marksOpen)AlertDialog(onDismissRequest={marksOpen=false},title={WReaderDialogTitle("Bookmarks")},text={Column(Modifier.heightIn(max=320.dp).verticalScroll(rememberScrollState())){
        if(marks.isEmpty())Text("Tap the bookmark icon to save your place.")
        marks.sorted().forEach{value->Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={fraction=value;web.go(value);marksOpen=false},modifier=Modifier.weight(1f)){Text("${(value*100).toInt()}% through book")};IconButton(onClick={setMarks(marks-value)}){Icon(Icons.Default.DeleteOutline,"Remove bookmark")}}}
    }},confirmButton={TextButton(onClick={marksOpen=false}){Text("Done")}})
}
