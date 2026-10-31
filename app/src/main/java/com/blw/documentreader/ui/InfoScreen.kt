package com.blw.documentreader.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun InfoScreen(title:String,onBack:()->Unit) {
    val context=LocalContext.current
    val content=remember(title){context.assets.open(if(title=="Privacy Policy")"privacy_policy.md" else "help.md").bufferedReader().use{it.readText()}.split("\n\n")}
    BackHandler {onBack()}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row {IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Back")};Text(title,Modifier.padding(top=12.dp),fontSize=18.sp,fontWeight=FontWeight.Bold)}
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(content){paragraph->
                    val heading=paragraph.startsWith("#")
                    val text=paragraph.trimStart('#',' ').replace("**","").replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"),"$1 ($2)")
                    Text(text,fontSize=if(heading)16.sp else 13.sp,lineHeight=if(heading)22.sp else 19.sp,fontWeight=if(heading)FontWeight.Bold else FontWeight.Normal)
                }
                item {TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:bokimk.ap@gmail.com")))}}){Text("Contact support")}}
            }
        }
    }
}
