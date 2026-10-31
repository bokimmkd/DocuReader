package com.blw.documentreader.updates

import com.blw.documentreader.updates.*
import org.junit.Assert.*
import org.junit.Test

class UpdatePromptTest {
    private val available=UpdateTransfer(version=21)
    private fun offer(session:UpdateOfferSession,state:UpdateTransfer=available,flexible:Boolean=true)=
        session.prompt(20,state,true,flexible)

    @Test fun laterReturnsOnNextOpeningWithoutWaiting24Hours() {
        val session=UpdateOfferSession();session.startForeground()
        assertEquals("Available",offer(session))
        session.later(21)
        repeat(3) {assertNull(offer(session))} // Includes periodic checks in the same visit.
        session.stopForeground();session.startForeground()
        assertEquals("Available",offer(session))
        assertFalse(shouldOfferUpdate(20,20,0))
        assertFalse(shouldOfferUpdate(20,19,0))
    }
    @Test fun rotationAndTransientResumeDoNotUndoLater() {
        val session=UpdateOfferSession();session.startForeground();session.later(21)
        session.startForeground() // Pause/resume, without leaving the app.
        assertNull(offer(session))
        session.stopForeground(changingConfiguration=true);session.startForeground()
        assertNull(offer(session))
    }
    @Test fun acceptedPlayConsentNeverOffersAnotherDownloadBeforeReady() {
        val session=UpdateOfferSession();session.startForeground();session.beginConsent(21)
        session.stopForeground();session.startForeground() // Play's sheet is not a new app visit.
        assertNull(offer(session))
        var transfer=available.observe(20,21,UpdateStage.Waiting)
        session.endConsent()
        assertNull(offer(session,transfer))
        transfer=transfer.observe(20,21,UpdateStage.Idle) // Late availability reply.
        assertNull(offer(session,transfer))
        transfer=transfer.observe(20,21,UpdateStage.Downloading,100,100)
        assertNull(offer(session,transfer)) // 100% is insufficient.
        transfer=transfer.observe(20,21,UpdateStage.Ready)
        assertEquals("Ready",offer(session,transfer))
        assertNull(offer(session,transfer.beginInstall()))
    }
    @Test fun readyBeforeConsentResultIsShownAfterConsentReturns() {
        val session=UpdateOfferSession();session.startForeground();session.beginConsent(21)
        val ready=available.observe(20,21,UpdateStage.Ready)
        assertNull(offer(session,ready))
        session.endConsent()
        assertEquals("Ready",offer(session,ready))
    }
    @Test fun restartLaterKeepsDownloadAndReturnsOnNextOpening() {
        val session=UpdateOfferSession();session.startForeground()
        val ready=available.observe(20,21,UpdateStage.Ready)
        session.later(21,ready=true)
        assertNull(offer(session,ready))
        session.stopForeground();session.startForeground()
        assertEquals("Ready",offer(session,ready))
        assertEquals(UpdateStage.Ready,ready.stage)
    }
    @Test fun deferralsAreSpecificToTheAvailableOrDownloadedVersion() {
        val session=UpdateOfferSession();session.startForeground();session.later(21);session.later(21,true)
        assertEquals("Available",offer(session,UpdateTransfer(version=22)))
        assertEquals("Ready",offer(session,UpdateTransfer(version=22,stage=UpdateStage.Ready)))
    }
    @Test fun stoppedUpdateCanUseStoreFallbackAndRetryInstallationStillRequiresReady() {
        val session=UpdateOfferSession();session.startForeground()
        assertEquals("Store",offer(session,flexible=false))
        assertEquals("Store",offer(session,available.observe(20,21,UpdateStage.Stopped)))
        val installing=available.observe(20,21,UpdateStage.Ready).beginInstall()
        assertNull(offer(session,installing))
        assertEquals("Ready",offer(session,installing.installFailed()))
    }
    @Test fun installedAndObsoleteEventsCannotReviveEitherPrompt() {
        val session=UpdateOfferSession();session.startForeground()
        val installed=available.observe(20,21,UpdateStage.Installed)
        assertNull(offer(session,installed))
        assertNull(offer(session,installed.observe(20,0,UpdateStage.Idle).observe(20,21,UpdateStage.Ready)))
        assertNull(offer(session,UpdateTransfer(version=20,stage=UpdateStage.Ready)))
    }
    @Test fun concurrentChecksAreCoalescedUntilTheRequestCompletes() {
        val gate=UpdateCheckGate()
        val first=gate.begin(0)!!
        assertNull(gate.begin(1000))
        assertTrue(gate.complete(first))
        assertNotNull(gate.begin(1001))
    }
    @Test fun completedFailureDoesNotPermanentlyBlockTheNextResumeCheck() {
        val gate=UpdateCheckGate()
        assertTrue(gate.complete(gate.begin(0)!!))
        assertNotNull(gate.begin(1))
    }
    @Test fun returningToTheForegroundRejectsTheOldSessionsResponse() {
        val gate=UpdateCheckGate()
        val old=gate.begin(0)!!
        gate.cancel()
        val fresh=gate.begin(1)!!
        assertFalse(gate.complete(old))
        assertNull(gate.begin(2))
        assertTrue(gate.complete(fresh))
    }
    @Test fun unansweredRequestExpiresAndCannotEraseANewerReply() {
        val gate=UpdateCheckGate()
        val old=gate.begin(0)!!
        assertNull(gate.begin(14999))
        val fresh=gate.begin(15000)!!
        assertFalse(gate.complete(old))
        assertTrue(gate.complete(fresh))
        assertFalse(gate.complete(old))
    }
    @Test fun playMustActuallyReportAvailabilityBeforeAnyDownloadOrStoreOffer() {
        val session=UpdateOfferSession();session.startForeground()
        assertNull(session.prompt(20,available,false,true))
        assertNull(session.prompt(20,available.copy(stage=UpdateStage.Stopped),false,true))
        assertEquals("Available",session.prompt(20,available,true,true))
        assertEquals("Ready",session.prompt(20,available.copy(stage=UpdateStage.Ready),false,true))
    }
}
