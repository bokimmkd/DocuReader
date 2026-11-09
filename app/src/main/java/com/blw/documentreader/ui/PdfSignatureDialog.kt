package com.blw.documentreader.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.blw.documentreader.data.DocumentNames
import com.blw.documentreader.data.DocumentTypes
import com.blw.documentreader.pdf.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PdfSignatureDialog(uri: Uri, name: String, page: Int, onDismiss: () -> Unit, onBusy: (Boolean) -> Unit,
    onSaved: (Uri, String) -> Unit, onOpen: (Uri) -> Unit, onShare: (Uri, String) -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var signature by remember { mutableStateOf<Bitmap?>(null) }
    var stored by remember { mutableStateOf<Bitmap?>(null) }
    var drawing by remember { mutableStateOf(true) }
    var reuse by remember { mutableStateOf(false) }
    var inkColor by rememberSaveable { mutableIntStateOf(android.graphics.Color.BLACK) }
    var importedUri by remember { mutableStateOf<Uri?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var strokes by remember { mutableStateOf(listOf<List<Offset>>()) }
    var drawingAspect by remember { mutableFloatStateOf(2f) }
    var active by remember { mutableStateOf(listOf<Offset>()) }
    var current by rememberSaveable { mutableIntStateOf(page) }
    var source by remember { mutableStateOf<SignaturePage?>(null) }
    var placement by remember { mutableStateOf(SignaturePlacement()) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var staged by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by rememberSaveable { mutableStateOf<String?>(null) }
    var savedName by rememberSaveable { mutableStateOf("") }
    var filename by rememberSaveable { mutableStateOf(DocumentNames.renamed(name.substringBeforeLast('.')+"_signed", "PDF")) }
    val latestStage by rememberUpdatedState(staged)
    DisposableEffect(Unit) { onDispose { if ((context as? android.app.Activity)?.isChangingConfigurations != true) latestStage?.let { File(it).delete() }; onBusy(false) } }
    LaunchedEffect(Unit) { stored = withContext(Dispatchers.IO) { SignatureStore.load(context) } }
    LaunchedEffect(uri, current, drawing) {
        if (!drawing) {
            source = null; error = null
            try { source = PdfSignatures.preview(context, uri, current) }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { error = "Could not preview this PDF. Choose an unprotected, available file." }
        }
    }
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { output ->
        if(output == null) { working = false; onBusy(false); staged?.let { File(it).delete() }; staged = null }
        else scope.launch {
            try {
                require(output != uri) { "Choose a new file for the signed copy." }
                val file = File(requireNotNull(staged)).canonicalFile
                require(file.parentFile == File(context.cacheDir,"pdf-exports").canonicalFile && file.isFile)
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(output,"wt")?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("Destination unavailable") }
                runCatching { context.contentResolver.takePersistableUriPermission(output, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                savedName = DocumentTypes.displayName(context, output); onSaved(output,savedName); saved = output.toString()
                file.delete(); staged = null
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { error = "PDF was not saved. Choose another folder." }
            finally { working = false; onBusy(false) }
        }
    }
    val importImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) scope.launch {
            working = true; onBusy(true); error = null; notice = null
            try {
                val bitmap = withContext(Dispatchers.IO) { SignatureImages.load(context, selected) }
                if (reuse) { withContext(Dispatchers.IO) { SignatureStore.save(context, bitmap) }; stored = bitmap }
                signature = bitmap; importedUri = selected; placement = SignaturePlacement(); drawing = false
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not import signature image." }
            finally { working = false; onBusy(false) }
        }
    }
    val exportImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { output ->
        if (output != null) scope.launch {
            working = true; onBusy(true); error = null; notice = null
            try {
                require(output != importedUri && output != uri) { "Choose a new file for the PNG copy." }
                val bitmap = requireNotNull(signature)
                withContext(Dispatchers.IO) { SignatureImages.export(context, output, bitmap) }
                notice = "Signature PNG saved"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not export signature PNG." }
            finally { working = false; onBusy(false) }
        }
    }
    fun close() { if(!working) onDismiss() }
    if(saved != null) {
        WReaderAlertDialog(onDismissRequest = onDismiss, title = { WReaderDialogTitle("PDF saved") }, text = { Column {
            Text(savedName)
            Row {
                TextButton(onClick = { val u=Uri.parse(saved); onDismiss(); onOpen(u) }) { Text("Open") }
                TextButton(onClick = { val u=Uri.parse(saved); onDismiss(); onShare(u,savedName) }) { Text("Share") }
                TextButton(onClick = { val u=Uri.parse(saved); onDismiss(); PdfPrint.print(context,u,savedName) }) { Text("Print") }
            }
        } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
        return
    }
    Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !working, dismissOnClickOutside = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val background = MaterialTheme.colorScheme.background
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = background.luminance() > .5f
                    isAppearanceLightNavigationBars = background.luminance() > .5f
                }
                @Suppress("DEPRECATION")
                window.statusBarColor = background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = background.toArgb()
                if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            }
        }
        Surface(Modifier.fillMaxSize(), color = background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::close, enabled = !working) { Icon(Icons.Default.Close,"Close signature") }
                    Text("Sign PDF", style = MaterialTheme.typography.titleLarge)
                }
                Text("New PDF copy · Original unchanged", style = MaterialTheme.typography.bodySmall)
                if(drawing) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importImage.launch(arrayOf("image/png", "image/jpeg")) }, enabled = !working) {
                        Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Import PNG / JPG")
                    }
                    if(stored != null) {
                        OutlinedButton(onClick = { signature = stored; importedUri = null; drawing = false; notice = null }) { Icon(Icons.Default.Draw,null); Spacer(Modifier.width(8.dp)); Text("My signature") }
                        TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { SignatureStore.delete(context) }; stored = null } }) { Text("Delete saved signature") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Draw your signature", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Black" to android.graphics.Color.BLACK, "Blue" to 0xff1565c0.toInt(), "Red" to 0xffc62828.toInt()).forEach { (label, color) ->
                            FilterChip(selected = inkColor == color, onClick = { inkColor = color }, enabled = !working,
                                label = { Text(label) },
                                leadingIcon = { Box(Modifier.size(12.dp).background(Color(color), androidx.compose.foundation.shape.CircleShape)) })
                        }
                    }
                    Canvas(Modifier.fillMaxWidth().height(180.dp).background(Color.White)
                        // Protect only the drawing pad; edge strokes must not become Back.
                        // Its 180dp height stays within Android's 200dp exclusion budget.
                        .systemGestureExclusion()
                        .onSizeChanged { drawingAspect = it.width.toFloat()/it.height.coerceAtLeast(1) }
                        .semantics { contentDescription = "Draw signature here" }
                        .pointerInput(Unit) {
                            fun normalized(p: Offset) = Offset((p.x/size.width).coerceIn(0f,1f),(p.y/size.height).coerceIn(0f,1f))
                            detectDragGestures(onDragStart = { active = listOf(normalized(it)) },
                                onDrag = { change, _ -> change.consume(); if(active.size < 8000) active = active + normalized(change.position) },
                                onDragEnd = { if(active.size>1) strokes = (strokes+listOf(active)).takeLast(100); active = emptyList() },
                                onDragCancel = { active = emptyList() })
                        }) {
                        drawLine(Color.LightGray,Offset(16f,size.height*.8f),Offset(size.width-16f,size.height*.8f),1f)
                        (strokes+listOf(active)).forEach { points -> points.zipWithNext().forEach { (a,b) ->
                            drawLine(Color(inkColor),Offset(a.x*size.width,a.y*size.height),Offset(b.x*size.width,b.y*size.height),3.dp.toPx(),StrokeCap.Round)
                        } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { strokes = emptyList(); active = emptyList() }) { Text("Clear / Retry") }
                        Checkbox(reuse,{reuse=it}); Text("Save for reuse",style=MaterialTheme.typography.bodyMedium)
                    }
                    Text("Saved only on this device. You can delete it at any time.",style=MaterialTheme.typography.bodySmall)

                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween) {
                        IconButton(onClick = { current--; placement=SignaturePlacement() },enabled=!working && current>0 && source!=null) { Icon(Icons.Default.ChevronLeft,"Previous signature page") }
                        Text("Page ${current+1} / ${source?.count ?: "…"}")
                        IconButton(onClick = { current++; placement=SignaturePlacement() },enabled=!working && source!=null && current<(source?.count ?: 0)-1) { Icon(Icons.Default.ChevronRight,"Next signature page") }
                    }
                    Text("Drag signature to place · Pinch page to zoom",style=MaterialTheme.typography.bodySmall)
                    val p = source; val ink = signature
                    if(p!=null && ink!=null) {
                        key(current) { SignaturePlacementPreview(p.bitmap,ink,placement,{placement=it},!working,Modifier.weight(1f).fillMaxWidth()) }
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Text("Size",style=MaterialTheme.typography.bodySmall)
                            Slider(placement.width,{placement=placement.copy(width=it).bounded(ink.width.toFloat()/ink.height,p.bitmap.width.toFloat()/p.bitmap.height)},
                                enabled=!working, valueRange=minOf(.08f, ink.width.toFloat()/ink.height/(p.bitmap.width.toFloat()/p.bitmap.height)*.8f)..minOf(.8f,ink.width.toFloat()/ink.height/(p.bitmap.width.toFloat()/p.bitmap.height)*.8f), modifier=Modifier.weight(1f))
                        }
                    } else Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { if(error==null) CircularProgressIndicator() }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { drawing=true; strokes=emptyList(); reuse=false; notice=null },enabled=!working) { Text("Draw new signature") }
                        TextButton(onClick = { exportImage.launch("signature.png") },enabled=!working && signature!=null) { Text("Export PNG") }
                    }
                    OutlinedTextField(filename,{filename=it},label={Text("New PDF name")},singleLine=true,enabled=!working,modifier=Modifier.fillMaxWidth())
                }
                notice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall) }
                if(working) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    TextButton(onClick=::close,enabled=!working) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled=!working && if(drawing) strokes.any{it.size>1} else source!=null && signature!=null,
                        onClick = {
                            error=null; working=true; onBusy(true)
                            scope.launch {
                                try {
                                    if(drawing) {
                                        val bitmap=signatureBitmap(strokes, drawingAspect, inkColor)
                                        if(reuse) { withContext(Dispatchers.IO) { SignatureStore.save(context,bitmap) }; stored=bitmap }
                                        signature=bitmap; importedUri=null; notice=null; drawing=false
                                    } else {
                                        staged?.let{File(it).delete()}; staged=null
                                        val file=PdfSignatures.sign(context,uri,current,requireNotNull(signature),placement)
                                        staged=file.absolutePath; filename=DocumentNames.renamed(filename,"PDF")
                                        destination.launch(filename)
                                    }
                                } catch(e: CancellationException) { throw e }
                                catch(e: Exception) { error=e.message ?: "Could not sign this PDF." }
                                finally { working=false; onBusy(false) }
                            }
                        }) { Text(if(drawing) "Place signature" else "Save signed copy") }
                }
            }
        }
    }
}

internal fun signatureBitmap(strokes: List<List<Offset>>, aspect: Float, inkColor: Int): Bitmap {
    val canvasHeight = 1000f / aspect
    val points=strokes.flatten(); require(points.size>1)
    val minX=points.minOf{it.x}; val maxX=points.maxOf{it.x}; val minY=points.minOf{it.y}; val maxY=points.maxOf{it.y}
    val w=((maxX-minX)*1000).roundToInt().coerceAtLeast(4)+24
    val h=((maxY-minY)*canvasHeight).roundToInt().coerceAtLeast(4)+24
    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    val canvas=android.graphics.Canvas(bitmap)
    val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color=inkColor; strokeWidth=8f; style=android.graphics.Paint.Style.STROKE
        strokeCap=android.graphics.Paint.Cap.ROUND; strokeJoin=android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { stroke -> if(stroke.size>1) {
        val path=android.graphics.Path(); path.moveTo((stroke[0].x-minX)*1000+12,(stroke[0].y-minY)*canvasHeight+12)
        stroke.drop(1).forEach { path.lineTo((it.x-minX)*1000+12,(it.y-minY)*canvasHeight+12) }; canvas.drawPath(path,paint)
    } }
    return bitmap
}

@Composable
private fun SignaturePlacementPreview(page: Bitmap, signature: Bitmap, placement: SignaturePlacement,
    onPlacement: (SignaturePlacement)->Unit, enabled: Boolean, modifier: Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }; var pan by remember { mutableStateOf(Offset.Zero) }
    val latest by rememberUpdatedState(placement)
    val pageAspect=page.width.toFloat()/page.height; val inkAspect=signature.width.toFloat()/signature.height
    val pageImage=remember(page){page.asImageBitmap()}; val inkImage=remember(signature){signature.asImageBitmap()}
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)
            .semantics { contentDescription="Signature placement preview" }
            .pointerInput(page,signature,enabled) {
                if(!enabled) return@pointerInput
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    val fit=minOf(size.width.toFloat()/page.width,size.height.toFloat()/page.height)
                    val dw=page.width*fit*zoom; val dh=page.height*fit*zoom
                    val origin=Offset((size.width-dw)/2,(size.height-dh)/2)+pan
                    val p=latest.bounded(inkAspect,pageAspect)
                    val local=down.position-origin
                    val moveInk=local.x in (p.left*dw)..((p.left+p.width)*dw) && local.y in (p.top*dh)..(p.top*dh+p.width*dw/inkAspect)
                    var transformed = false
                    do {
                        val event=awaitPointerEvent(); val delta=event.calculatePan()
                        if(event.changes.count{it.pressed}>=2) {
                            transformed = true
                            val oldZoom = zoom
                            val nextZoom = (oldZoom*event.calculateZoom()).coerceIn(1f,3f)
                            val center = Offset(size.width/2f, size.height/2f)
                            val focus = event.calculateCentroid(useCurrent = false)
                            pan = if(nextZoom==1f) Offset.Zero else
                                (pan + center - focus)*(nextZoom/oldZoom) + focus - center + delta
                            zoom = nextZoom
                        } else if(moveInk && !transformed) {
                            onPlacement(latest.copy(left=latest.left+delta.x/(page.width*fit*zoom),top=latest.top+delta.y/(page.height*fit*zoom)).bounded(inkAspect,pageAspect))
                        } else if(zoom>1f && !transformed) pan+=delta
                        val mx=page.width*fit*(zoom-1)/2; val my=page.height*fit*(zoom-1)/2
                        pan=Offset(pan.x.coerceIn(-mx,mx),pan.y.coerceIn(-my,my))
                        event.changes.forEach{it.consume()}
                    } while(event.changes.any{it.pressed})
                }
            }) {
            val fit=minOf(size.width/page.width,size.height/page.height)
            val w=page.width*fit*zoom; val h=page.height*fit*zoom
            val origin=Offset((size.width-w)/2,(size.height-h)/2)+pan
            drawImage(pageImage,dstOffset=IntOffset(origin.x.roundToInt(),origin.y.roundToInt()),dstSize=IntSize(w.roundToInt(),h.roundToInt()))
            val p=placement.bounded(inkAspect,pageAspect)
            val pos=origin+Offset(p.left*w,p.top*h); val iw=p.width*w; val ih=iw/inkAspect
            drawImage(inkImage,dstOffset=IntOffset(pos.x.roundToInt(),pos.y.roundToInt()),dstSize=IntSize(iw.roundToInt().coerceAtLeast(1),ih.roundToInt().coerceAtLeast(1)))
            drawRect(Color(0xff176ac4),pos,Size(iw,ih),style=Stroke(1.dp.toPx()))
        }
        TextButton(onClick={zoom=1f;pan=Offset.Zero},modifier=Modifier.align(Alignment.TopEnd),enabled=enabled) { Text("Fit") }
    }
}
