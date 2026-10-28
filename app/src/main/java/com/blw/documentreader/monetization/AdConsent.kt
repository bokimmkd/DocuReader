package com.blw.documentreader.monetization

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Ads aren't initialized or requested before UMP confirms they may be requested. */
class AdConsent(private val activity: Activity, private val onAllowed: () -> Unit) {
    private val info = UserMessagingPlatform.getConsentInformation(activity)
    private var adsRequested = false
    fun start() {
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { maybeEnable() }
            maybeEnable()
        }, { maybeEnable() })
    }
    private fun maybeEnable() {
        if (info.canRequestAds() && !adsRequested) {
            adsRequested = true
            MobileAds.initialize(activity) { }
            onAllowed()
        }
    }
    fun showPrivacyOptions() {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { }
    }
}

@Composable
fun BottomBanner() {
    val context = LocalContext.current
    val width = LocalConfiguration.current.screenWidthDp.coerceAtLeast(320)
    var loaded by remember { mutableStateOf(false) }
    val view = remember(context, width) {
        AdView(context).apply {
            adUnitId = "ca-app-pub-3940256099942544/9214589741" // TEST ONLY
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width))
            adListener = object : AdListener() {
                override fun onAdLoaded() { loaded = true }
                override fun onAdFailedToLoad(error: LoadAdError) { loaded = false }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(view) { onDispose { view.destroy() } }
    // Its own layout cell below app content; never overlays a page or toolbar.
    Box(Modifier.fillMaxWidth()) {
        AndroidView(factory = { view }, modifier = if (loaded) Modifier.fillMaxWidth() else Modifier.fillMaxWidth())
    }
}
