package com.blw.documentreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.blw.documentreader.ui.*

/** Preview-only billing display; never starts a checkout or requests live ads. */
class SettingsTestActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val vm=MainViewModel(application)
        setContent {
            val dark=intent.getBooleanExtra("dark",false)
            var page by remember {mutableStateOf<String?>(null)}
            MaterialTheme(colorScheme=if(dark)darkColorScheme(primary=Color(0xFF67B6FF),background=Color(0xFF101723),surface=Color(0xFF151C29)) else lightColorScheme(primary=Color(0xFF0869E8))) {
                Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Text("W Reader · Settings",Modifier.padding(16.dp))
                    SettingsScreen(vm,removeAds={},restore={},price="$2.99",adFree=false,
                        privacy={page="Privacy Policy"},privacyOptions={},feedback={page="Help & Feedback"},checkUpdates={},rateApp={},billingAvailable=true)
                }}
                if(page!=null) InfoScreen(page!!){page=null}
            }
        }
    }
}
