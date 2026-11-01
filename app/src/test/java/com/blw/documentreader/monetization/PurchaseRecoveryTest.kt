package com.blw.documentreader.monetization

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PurchaseRecoveryTest {
    private class Store : AdFreeGateway {
        var calls = 0
        var query: suspend () -> OwnedQuery = { OwnedQuery(0, listOf(OwnedAdFree("owned", false))) }
        var ack: suspend (String) -> Int = { 0 }
        override suspend fun query() = query.invoke()
        override suspend fun acknowledge(token: String): Int { calls++; return ack(token) }
    }
    @Test fun duplicateCallbacksAcknowledgeOnceAndDoNotShowFalseFailure() = runBlocking {
        val store=Store();val states=mutableListOf<Boolean>();val messages=mutableListOf<String>()
        val recovery=PurchaseRecovery(store,states::add,messages::add,{})
        recovery.observe(listOf(OwnedAdFree("owned",false)))
        recovery.observe(listOf(OwnedAdFree("owned",false)))
        assertEquals(1,store.calls);assertTrue(states.all{it});assertTrue(messages.all{it=="Ad-free enabled."})
    }
    @Test fun staleEmptyRestoreCannotRevokeNewPurchase() = runBlocking {
        val result=CompletableDeferred<OwnedQuery>();val started=CompletableDeferred<Unit>()
        val store=Store();store.query={started.complete(Unit);result.await()}
        val states=mutableListOf<Boolean>();val recovery=PurchaseRecovery(store,states::add,{},{})
        val old=async{recovery.restore(false)};started.await()
        val fresh=async{recovery.observe(listOf(OwnedAdFree("owned",false)))};yield()
        result.complete(OwnedQuery(0,emptyList()));old.await();fresh.await()
        assertEquals(listOf(true),states)
    }
    @Test fun failedAckRequeriesAlreadyAcknowledgedPurchase() = runBlocking {
        val store=Store();store.ack={5};store.query={OwnedQuery(0,listOf(OwnedAdFree("owned",true)))}
        val states=mutableListOf<Boolean>();val messages=mutableListOf<String>()
        PurchaseRecovery(store,states::add,messages::add,{}).restore(true)
        assertEquals(0,store.calls) // Query already confirmed it; do not acknowledge twice.
        val other=Store();other.ack={5};other.query=store.query
        PurchaseRecovery(other,states::add,messages::add,{}).observe(listOf(OwnedAdFree("owned",false)))
        assertEquals(1,other.calls);assertTrue(states.all{it});assertTrue(messages.all{it=="Ad-free enabled."})
    }
    @Test fun transientAcknowledgementRetriesThenConfirms() = runBlocking {
        val store=Store();store.ack={if(store.calls<3)12 else 0}
        val states=mutableListOf<Boolean>();val delays=mutableListOf<Long>()
        PurchaseRecovery(store,states::add,{}, {delays.add(it)}).restore(true)
        assertEquals(3,store.calls);assertEquals(listOf(1000L,2000L),delays);assertEquals(listOf(true),states)
    }
    @Test fun queryFailureDoesNotRevokeOfflinePurchaseAndPendingNeverGrants() = runBlocking {
        val store=Store();store.query={OwnedQuery(12,emptyList())};val states=mutableListOf<Boolean>()
        val recovery=PurchaseRecovery(store,states::add,{},{})
        recovery.restore(true);assertTrue(states.isEmpty())
        recovery.observe(listOf(OwnedAdFree("pending",false,true)));assertTrue(states.isEmpty());assertEquals(0,store.calls)
    }
    @Test fun confirmedEmptyQueryRevokesAndUnrecoverableAckDoesNotGrant() = runBlocking {
        val store=Store();store.query={OwnedQuery(0,emptyList())};val states=mutableListOf<Boolean>()
        val recovery=PurchaseRecovery(store,states::add,{},{})
        recovery.restore(true);assertEquals(listOf(false),states)
        store.ack={5};recovery.observe(listOf(OwnedAdFree("unconfirmed",false)));assertEquals(listOf(false),states)
    }
}
