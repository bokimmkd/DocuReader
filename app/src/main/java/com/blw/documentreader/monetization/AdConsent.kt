package com.blw.documentreader.monetization

import android.app.Activity
import com.blw.documentreader.BuildConfig
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    var settled by mutableStateOf(false); private set
    var privacyOpen by mutableStateOf(false); private set
    private var adsRequested = false
    private var started = false
    fun start() {
        if (started || activity.isFinishing || activity.isDestroyed) return
        started = true
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { settled = true; maybeEnable() }
            maybeEnable()
        }, { settled = true; maybeEnable() })
    }
    private fun maybeEnable() {
        if (info.canRequestAds() && !adsRequested) {
            adsRequested = true
            MobileAds.initialize(activity) {
                activity.runOnUiThread { if (!activity.isFinishing && !activity.isDestroyed) onAllowed() }
            }
        }
    }
    fun showPrivacyOptions() {
        privacyOpen = true
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { privacyOpen = false }
    }
}

@Composable
fun BottomBanner() = ReaderBanner(false)

@Composable
fun InlineBanner() = ReaderBanner(true)

@Composable
private fun ReaderBanner(inline: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val width = maxWidth.value.toInt().coerceAtLeast(1)
    var loaded by remember { mutableStateOf(false) }
    val view = remember(context, width, inline) {
        AdView(context).apply {
            adUnitId = if (inline) BuildConfig.ADMOB_INLINE_ID else BuildConfig.ADMOB_BANNER_ID
            setAdSize(if (inline) AdSize.getInlineAdaptiveBannerAdSize(width, 160) else AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, width))
            adListener = object : AdListener() {
                override fun onAdLoaded() { loaded = true }
                override fun onAdFailedToLoad(error: LoadAdError) { loaded = false }
            }
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(view, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_RESUME -> view.resume()
            Lifecycle.Event.ON_PAUSE -> view.pause()
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume() else view.pause()
        onDispose { lifecycle.removeObserver(observer); view.pause(); view.destroy() }
    }
    // Its own layout cell below app content; never overlays a page or toolbar.
    Column(Modifier.fillMaxWidth().heightIn(max = if (inline) 184.dp else 110.dp)) {
        if (inline && loaded) Text("Advertisement", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth())
    }
    }
}
