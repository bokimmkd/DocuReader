package com.blw.documentreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.blw.documentreader.ui.UpdateBanner
import com.blw.documentreader.updates.PlayUpdates
import com.google.android.play.core.appupdate.AppUpdateManager

/** Instrumentation-only injection. No fake manager or test activity in the release variant. */
object UpdateTestEnvironment {
    var manager:AppUpdateManager?=null
    var canLaunch by mutableStateOf(true)
    val messages=mutableListOf<String>()
}
class UpdateTestActivity:ComponentActivity() {
    lateinit var updates:PlayUpdates
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        updates=PlayUpdates(this, {UpdateTestEnvironment.messages.add(it)}, requireNotNull(UpdateTestEnvironment.manager))
        updates.allowedToPrompt={UpdateTestEnvironment.canLaunch}
        setContent {
            var documentOpen by remember {mutableStateOf(false)}
            MaterialTheme {
                Column {
                    UpdateBanner(updates.prompt,updates.transfer,updates.installFailed,
                        enabled=UpdateTestEnvironment.canLaunch,
                        onUpdate=updates::download,onLater=updates::later,onRestart=updates::restart)
                    TextButton(onClick={documentOpen=!documentOpen}) {Text(if(documentOpen) "Document remains usable" else "Open sample document")}
                }
            }
        }
    }
}
