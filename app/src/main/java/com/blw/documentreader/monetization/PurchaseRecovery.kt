package com.blw.documentreader.monetization

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class OwnedAdFree(val token: String, val acknowledged: Boolean, val pending: Boolean = false)
internal data class OwnedQuery(val code: Int, val purchases: List<OwnedAdFree>)
internal interface AdFreeGateway {
    suspend fun query(): OwnedQuery
    suspend fun acknowledge(token: String): Int
}

/** Serializes purchase/restore processing. Never logs or persists purchase tokens. */
internal class PurchaseRecovery(private val gateway: AdFreeGateway,
    private val entitlement: (Boolean) -> Unit, private val message: (String) -> Unit,
    private val pause: suspend (Long) -> Unit = { delay(it) }) {
    private val lock = Mutex()
    private val confirmedTokens = mutableSetOf<String>()
    private var revision = 0L
    suspend fun observe(purchases: List<OwnedAdFree>) {
        revision++ // Invalidate an older query before waiting for its processing lock.
        lock.withLock { process(purchases, true) }
    }
    suspend fun restore(manual: Boolean) = lock.withLock {
        val startedAt = revision
        val result = gateway.query()
        if (startedAt != revision) return@withLock // A newer purchase callback owns the state.
        if (result.code != 0) {
            if (manual) message("Restore is temporarily unavailable (Play code ${result.code}). Your saved access is unchanged.")
        } else process(result.purchases, manual)
    }
    private suspend fun process(purchases: List<OwnedAdFree>, notify: Boolean) {
        val owned = purchases.filterNot { it.pending }
        if (owned.isEmpty()) {
            if (purchases.any { it.pending }) {
                if (notify) message("Payment is pending. Ad-free starts after Google Play confirms it.")
                return
            }
            confirmedTokens.clear()
            entitlement(false)
            if (notify) message("No active ad-free purchase found on this Google Play account.")
            return
        }
        // Only one non-consumable entitlement is needed; never consume this purchase.
        val purchase = owned.firstOrNull { it.acknowledged } ?: owned.first()
        if (purchase.acknowledged || purchase.token in confirmedTokens) {
            confirm(purchase.token, notify); return
        }
        for (attempt in 0..2) {
            val response = gateway.acknowledge(purchase.token)
            if (response == 0) { confirm(purchase.token, notify); return }
            // A competing Play completion may already have acknowledged it. Query before
            // reporting failure or retrying a stale purchase object.
            val refreshed = gateway.query()
            if (refreshed.code == 0) {
                val active = refreshed.purchases.filterNot { it.pending }
                val acknowledged = active.firstOrNull { it.acknowledged }
                if (acknowledged != null) { confirm(acknowledged.token, notify); return }
                if (response == 8 && active.none { it.token == purchase.token }) {
                    entitlement(false)
                    if (notify) message("Google Play no longer reports this purchase as owned. Check its order status before buying again.")
                    return
                }
            }
            if (response !in setOf(-1, 2, 6, 8, 12) || attempt == 2) {
                if (notify) message("Purchase confirmation is pending (Play code $response). We will retry when you reopen the app; you do not need to buy again.")
                return
            }
            pause(1000L shl attempt)
        }
    }
    private fun confirm(token: String, notify: Boolean) {
        confirmedTokens.add(token)
        entitlement(true)
        if (notify) message("Ad-free enabled.")
    }
}
