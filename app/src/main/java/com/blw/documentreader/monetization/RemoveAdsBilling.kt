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

    fun start() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    loadPrice(); restore(silent = true)
                }
            }
            override fun onBillingServiceDisconnected() { /* automatic reconnection enabled */ }
        })
    }
    private fun loadPrice() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(QueryProductDetailsParams.Product.newBuilder().setProductId("remove_ads")
                .setProductType(BillingClient.ProductType.INAPP).build())
        ).build()
        client.queryProductDetailsAsync(params) { result, data ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                details = data.productDetailsList.firstOrNull()
                val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull {
                    it.rentalDetails == null && it.preorderDetails == null
                }
                    ?: details?.oneTimePurchaseOfferDetails
                offerToken = offer?.offerToken
                localizedPrice = offer?.formattedPrice ?: "Store price unavailable"
            }
        }
    }
    fun buy() {
        val product = details
        if (product == null || offerToken == null) { status("Product unavailable. Configure 'remove_ads' in Play Console."); return }
        val entry = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product).setOfferToken(offerToken!!).build()
        val response = client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(entry)).build())
        if (response.responseCode != BillingClient.BillingResponseCode.OK) status("Google Play couldn't launch checkout.")
    }
    fun restore(silent: Boolean = false) {
        if (!client.isReady) { if (!silent) status("Google Play is not connected. Try again later."); return }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP).build()) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val purchased = purchases.filter { "remove_ads" in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (purchased.isNotEmpty()) purchased.forEach(::processPurchase)
                else {
                    adFree = false
                    prefs.edit().putBoolean("play_confirmed_offline", false).apply()
                    if (!silent) status("No active ad-free purchase found on this Google Play account.")
                }
            } else if (!silent) status("Restore unavailable. Offline entitlement remains active if previously confirmed.")
        }
    }
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> purchases?.forEach(::processPurchase)
            BillingClient.BillingResponseCode.USER_CANCELED -> status("Purchase canceled.")
            else -> status("Purchase not confirmed. Check Google Play or try Restore Purchase.")
        }
    }
    private fun processPurchase(purchase: Purchase) {
        if ("remove_ads" !in purchase.products) return
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> status("Purchase pending. Ads remain until Google Play confirms payment.")
            Purchase.PurchaseState.PURCHASED -> {
                // Play client reports PURCHASED; for fraud-resistant release, verify the
                // purchaseToken using Play Developer API on a trusted server before granting.
                if (!purchase.isAcknowledged) {
                    client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken).build()) { response ->
                        if (response.responseCode == BillingClient.BillingResponseCode.OK) confirm()
                        else status("Payment found, but acknowledgment failed. Use Restore Purchase.")
                    }
                } else confirm()
            }
        }
    }
    private fun confirm() {
        adFree = true
        prefs.edit().putBoolean("play_confirmed_offline", true).apply()
        status("Ad-free enabled.")
    }
    fun close() = client.endConnection()
}
