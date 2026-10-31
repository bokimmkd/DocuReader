package com.blw.documentreader.updates

import com.blw.documentreader.updates.*
import org.junit.Assert.*
import org.junit.Test

class UpdateTransferTest {
    @Test fun slowDownloadRequiresPlayDownloadedBeforeRestart() {
        var state=UpdateTransfer().observe(19,20,UpdateStage.Waiting)
        assertEquals(UpdateStage.Waiting,state.beginInstall().stage)
        state=state.observe(19,20,UpdateStage.Downloading,25,100)
        assertEquals(state,state.observe(19,20,UpdateStage.Idle))
        assertEquals(0.25f,state.fraction!!,0.0001f)
        assertEquals(UpdateStage.Downloading,state.beginInstall().stage)
        state=state.observe(19,20,UpdateStage.Downloading,100,100)
        assertEquals(UpdateStage.Downloading,state.stage) // 100% is not DOWNLOADED.
        state=state.observe(19,20,UpdateStage.Ready)
        assertEquals(UpdateStage.Installing,state.beginInstall().stage)
    }
    @Test fun instantDownloadAndLatePollingCannotRegressReady() {
        val ready=UpdateTransfer().observe(19,20,UpdateStage.Ready)
        assertEquals(ready,ready.observe(19,20,UpdateStage.Downloading,1,100))
        assertEquals(ready,ready.observe(19,20,UpdateStage.Idle))
    }
    @Test fun resumeCanRebuildProgressAndCachedReadyFromPlay() {
        assertEquals(0.7f,UpdateTransfer().observe(19,20,UpdateStage.Downloading,70,100).fraction!!,0.0001f)
        assertEquals(UpdateStage.Ready,UpdateTransfer().observe(19,20,UpdateStage.Ready).stage)
    }
    @Test fun installingRejectsRepeatedRestartAndOldReadyUntilFailure() {
        val installing=UpdateTransfer().observe(19,20,UpdateStage.Ready).beginInstall()
        assertEquals(installing,installing.beginInstall())
        assertEquals(installing,installing.observe(19,20,UpdateStage.Ready))
        assertEquals(UpdateStage.Ready,installing.installFailed().stage)
        assertEquals(UpdateStage.Installing,installing.installFailed().beginInstall().stage)
    }
    @Test fun installedVersionCannotReviveOffersFromOldEvents() {
        val done=UpdateTransfer().observe(19,20,UpdateStage.Installed)
        assertEquals(done,done.observe(19,20,UpdateStage.Ready))
        val cleared=done.observe(19,0,UpdateStage.Idle)
        assertEquals(cleared,cleared.observe(19,20,UpdateStage.Ready))
        assertEquals(UpdateTransfer(),UpdateTransfer().observe(20,20,UpdateStage.Ready))
        assertEquals(UpdateStage.Waiting,done.observe(19,21,UpdateStage.Waiting).stage)
    }
    @Test fun unknownTotalsAreIndeterminateAndPercentIsBounded() {
        assertNull(UpdateTransfer(total=0).fraction)
        assertNull(UpdateTransfer(total=-1).fraction)
        assertEquals(1f,UpdateTransfer(downloaded=200,total=100).fraction!!,0f)
        assertEquals(0f,UpdateTransfer(downloaded=-1,total=100).fraction!!,0f)
    }
    @Test fun obsoleteVersionCannotReplaceANewerDownload() {
        val current=UpdateTransfer().observe(19,21,UpdateStage.Downloading,50,100)
        assertEquals(current,current.observe(19,20,UpdateStage.Ready))
    }
}
