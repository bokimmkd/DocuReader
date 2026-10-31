package com.blw.documentreader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blw.documentreader.updates.*
import java.text.NumberFormat

/** Non-modal status: document content and navigation stay usable while Play downloads. */
@Composable
fun UpdateBanner(prompt:String?, transfer:UpdateTransfer, failed:Boolean=false,
                 enabled:Boolean=true, restartEnabled:Boolean=enabled,
                 onUpdate:()->Unit, onLater:()->Unit, onRestart:()->Unit) {
    val progress=transfer.stage in listOf(UpdateStage.Waiting,UpdateStage.Downloading,UpdateStage.Installing)
    if(prompt==null && !progress) return
    Surface(color=MaterialTheme.colorScheme.primaryContainer, tonalElevation=2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp),
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.SystemUpdate,null,Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text("W Reader",fontSize=11.sp)
                    Text(when {
                        transfer.stage==UpdateStage.Waiting->"Waiting to download…"
                        transfer.stage==UpdateStage.Downloading->"Downloading update…"
                        transfer.stage==UpdateStage.Installing->"Installing update…"
                        prompt=="Ready"->"Update ready"
                        else->"Update available"
                    },fontSize=13.sp)
                }
                if(progress) {
                    if(transfer.stage==UpdateStage.Downloading) transfer.fraction?.let {
                        Text(NumberFormat.getPercentInstance().format(it.toDouble()),fontSize=12.sp)
                    }
                } else {
                    TextButton(onClick=onLater,enabled=enabled) { Text("Later") }
                    if(prompt=="Ready") TextButton(onClick=onRestart,enabled=restartEnabled) { Text("Restart to update") }
                    else TextButton(onClick=onUpdate,enabled=enabled) { Text(if(prompt=="Store") "Open Google Play" else "Update") }
                }
            }
            if(progress) {
                val fraction=transfer.fraction
                if(transfer.stage==UpdateStage.Downloading && fraction!=null)
                    LinearProgressIndicator(progress={fraction},modifier=Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
            }
            if(failed) Text("Could not finish installing. Tap Restart to try again.",fontSize=11.sp,color=MaterialTheme.colorScheme.error)
            if(prompt=="Ready" && !restartEnabled) Text("Finish saving your work before restarting.",fontSize=11.sp)
        }
    }
}
