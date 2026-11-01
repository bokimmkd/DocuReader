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
    private var priceQueryInFlight = false
    private var priceQueryRevision = 0L
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
    private fun loadPrice(manual: Boolean = false) {
        if (closed) return
        if (!client.isReady) {
            if (manual) status("Connecting to Google Play. Try Retry after connection.")
            start()
            return
        }
        if (priceQueryInFlight) {
            if (manual) status("Still checking the Play Store price. Please try again.")
            return
        }
        priceQueryInFlight = true
        val revision = ++priceQueryRevision
        localizedPrice = "Loading store price…"
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId("remove_ads")
                .setProductType(BillingClient.ProductType.INAPP).build())
        ).build()
        client.queryProductDetailsAsync(params) { result, data ->
            activity.runOnUiThread {
                if (closed || revision != priceQueryRevision) return@runOnUiThread
                priceQueryInFlight = false
                details = null
                offerToken = null
                val product = if (result.responseCode == BillingClient.BillingResponseCode.OK)
                    data.productDetailsList.firstOrNull { it.productId == "remove_ads" } else null
                val offer = product?.oneTimePurchaseOfferDetailsList?.firstOrNull {
                    it.rentalDetails == null && it.preorderDetails == null
                } ?: product?.oneTimePurchaseOfferDetails
                if (product != null && offer != null) {
                    details = product
                    offerToken = offer.offerToken
                    localizedPrice = offer.formattedPrice
                } else {
                    localizedPrice = "Store price unavailable"
                    if (manual) status(
                        "Play Store cannot offer remove_ads (code ${result.responseCode}). Check product, region and test-account eligibility."
                    )
                }
            }
        }
    }
    /** A failed/empty product query must never strand Buy as a dead grey button. */
    fun retryPrice() {
        if (closed) return
        loadPrice(manual = true)
    }
    fun buy() {
        val product = details
        if (product == null || offerToken == null) {
            retryPrice()
            return // Price recovery is NOT permission to charge.
        }
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
        if (details == null && !priceQueryInFlight) loadPrice(manual = false)
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
    fun close() { closed = true; priceQueryRevision++; scope.cancel(); client.endConnection() }
}
