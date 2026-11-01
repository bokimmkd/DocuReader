package com.blw.documentreader.monetization

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/** Google Play one-time product ID must be configured in Play Console before production. */
class RemoveAdsBilling(private val activity: Activity, private val status: (String) -> Unit) : PurchasesUpdatedListener {
    private val prefs = activity.getSharedPreferences("entitlement", Context.MODE_PRIVATE)
    var adFree by mutableStateOf(prefs.getBoolean("play_confirmed_offline", false)); private set
    var localizedPrice by mutableStateOf("Store price unavailable"); private set
    private var details: ProductDetails? = null
    private var offerToken: String? = null
    private val client = BillingClient.newBuilder(activity)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var connecting = false
    private var closed = false
    private var manualRestorePending = false
    private var priceLoading = false
    private val recovery = PurchaseRecovery(object : AdFreeGateway {
        override suspend fun query(): OwnedQuery = suspendCancellableCoroutine { continuation ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
                if (continuation.isActive) continuation.resume(OwnedQuery(result.responseCode,
                    purchases.filter { "remove_ads" in it.products }.map(::snapshot)))
            }
        }
        override suspend fun acknowledge(token: String): Int = suspendCancellableCoroutine { continuation ->
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()) { result ->
                if (continuation.isActive) continuation.resume(result.responseCode)
            }
        }
    }, entitlement = { enabled ->
        adFree = enabled
        prefs.edit().putBoolean("play_confirmed_offline", enabled).apply()
    }, message = status)
    private fun snapshot(p: Purchase) = OwnedAdFree(p.purchaseToken, p.isAcknowledged,
        p.purchaseState != Purchase.PurchaseState.PURCHASED)
    fun start() {
        if (closed || connecting || client.isReady) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                if (closed) return
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadPrice(); val manual = manualRestorePending; manualRestorePending = false
                    restore(silent = !manual)
                } else if (manualRestorePending) {
                    manualRestorePending = false
                    status("Google Play is temporarily unavailable (code ${result.responseCode}). Try Restore again later.")
                }
            }
            override fun onBillingServiceDisconnected() { connecting = false }
        })
    }
    /** Retries a stale/failed product query, e.g. after Play connection or resume. */
    fun refreshOffer() {
        if (closed) return
        if (!client.isReady) { start(); return }
        loadPrice()
    }
    private fun loadPrice() {
        if (closed || !client.isReady || priceLoading) return
        priceLoading = true
        if (details == null) localizedPrice = "Checking Google Play..."
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId("remove_ads")
                .setProductType(BillingClient.ProductType.INAPP).build())
        ).build()
        client.queryProductDetailsAsync(params) { result, data ->
            if (closed) return@queryProductDetailsAsync
            priceLoading = false
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = data.productDetailsList.firstOrNull { it.productId == "remove_ads" }
                val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull {
                    it.rentalDetails == null && it.preorderDetails == null
                } ?: details?.oneTimePurchaseOfferDetails
                offerToken = offer?.offerToken
                localizedPrice = offer?.formattedPrice ?: "Store price unavailable"
            } else {
                details = null
                offerToken = null
                localizedPrice = "Store price unavailable"
            }
        }
    }
    fun buy() {
        val product = details
        if (product == null || offerToken == null) { status("Ad-free is unavailable right now. Check Google Play and try again."); return }
        val entry = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product).setOfferToken(offerToken!!).build()
        val response = client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(entry)).build())
        if (response.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) restore()
        else if (response.responseCode != BillingClient.BillingResponseCode.OK) status("Google Play couldn't launch checkout (code ${response.responseCode}).")
    }
    fun restore(silent: Boolean = false) {
        if (closed) return
        if (!client.isReady) {
            manualRestorePending = manualRestorePending || !silent
            start()
            return
        }
        if (details == null) loadPrice()
        scope.launch { recovery.restore(manual = !silent) }
    }
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (closed) return
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val owned = purchases.orEmpty().filter { "remove_ads" in it.products }
                if (owned.isEmpty()) restore() else scope.launch { recovery.observe(owned.map(::snapshot)) }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore()
            BillingClient.BillingResponseCode.USER_CANCELED -> status("Purchase canceled.")
            else -> status("Purchase not confirmed (Play code ${result.responseCode}). Check Google Play or try Restore Purchase.")
        }
    }
    fun close() { closed = true; scope.cancel(); client.endConnection() }
}
