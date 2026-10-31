package com.blw.documentreader

import android.app.Activity
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.blw.documentreader.updates.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.*
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** SDK simulation and real Android lifecycle/UI; NOT a Play-distributed upgrade test. */
@RunWith(AndroidJUnit4::class)
class UpdateFlowTest {
    private val inst get()=InstrumentationRegistry.getInstrumentation()
    private val version=BuildConfig.VERSION_CODE+1
    private lateinit var fake:ObservedManager
    private class ObservedManager(context:Context):FakeAppUpdateManager(context) {
        var queries=0
        val listeners=mutableSetOf<InstallStateUpdatedListener>()
        var next:Task<AppUpdateInfo>?=null
        override fun getAppUpdateInfo():Task<AppUpdateInfo> {
            queries++
            val task=next;next=null
            return task ?: super.getAppUpdateInfo()
        }
        override fun registerListener(listener:InstallStateUpdatedListener) {listeners.add(listener);super.registerListener(listener)}
        override fun unregisterListener(listener:InstallStateUpdatedListener) {listeners.remove(listener);super.unregisterListener(listener)}
    }
    @Before fun prepare() {
        fake=ObservedManager(inst.targetContext)
        fake.setUpdateAvailable(version,AppUpdateType.FLEXIBLE)
        UpdateTestEnvironment.manager=fake
        UpdateTestEnvironment.canLaunch=true
        UpdateTestEnvironment.messages.clear()
    }
    @After fun cleanup() {UpdateTestEnvironment.manager=null;UpdateTestEnvironment.canLaunch=true}
    private fun await(s:ActivityScenario<UpdateTestActivity>, condition:(UpdateTestActivity)->Boolean) {
        val end=System.currentTimeMillis()+15000
        do {var ok=false;s.onActivity {ok=condition(it)};if(ok)return;Thread.sleep(50)} while(System.currentTimeMillis()<end)
        fail("Update state did not arrive")
    }
    private fun click(text:String) {
        val d=UiDevice.getInstance(inst)
        val node=d.wait(Until.findObject(By.text(text)),10000)
        assertNotNull("Missing UI: $text",node);node!!.click();d.waitForIdle()
    }
    private fun screenshot(name:String) {
        val f=File("/sdcard/Download/WReaderUpdateQA").also{it.mkdirs()}
        UiDevice.getInstance(inst).takeScreenshot(File(f,"$name.png"))
    }
    private fun begin(s:ActivityScenario<UpdateTestActivity>) {
        await(s){it.updates.prompt=="Available" && it.hasWindowFocus()}
        click("Update")
        s.onActivity {
            assertTrue(fake.isConfirmationDialogVisible)
            fake.userAcceptsUpdate()
            it.updates.onFlowResult(Activity.RESULT_OK)
            fake.downloadStarts();fake.setTotalBytesToDownload(100);fake.setBytesDownloaded(25)
        }
        await(s){it.updates.transfer.stage==UpdateStage.Downloading}
    }
    @Test fun realBannerStaysNonModalAndSdkDownloadOnlyRestartsAfterDownloaded() {
        ActivityScenario.launch(UpdateTestActivity::class.java).use {s->
            await(s){it.updates.prompt=="Available"};screenshot("available")
            begin(s)
            await(s){it.updates.transfer.fraction==0.25f}
            click("Open sample document")
            assertTrue(UiDevice.getInstance(inst).hasObject(By.text("Document remains usable")))
            screenshot("downloading")
            s.onActivity {fake.setBytesDownloaded(100)}
            await(s){it.updates.transfer.fraction==1f}
            s.onActivity {assertNotEquals("Ready",it.updates.prompt);assertFalse(fake.isInstallSplashScreenVisible);fake.downloadCompletes()}
            await(s){it.updates.prompt=="Ready"};screenshot("restart-ready")
            click("Restart to update")
            await(s){it.updates.transfer.stage==UpdateStage.Installing}
            s.onActivity {assertTrue(fake.isInstallSplashScreenVisible);fake.installCompletes()}
            await(s){it.updates.transfer.stage==UpdateStage.Installed && it.updates.prompt==null}
        }
        assertTrue(fake.listeners.isEmpty())
    }
    @Test fun laterSurvivesRotationAndReturnsOnNextForegroundWithout24HourPreference() {
        ActivityScenario.launch(UpdateTestActivity::class.java).use {s->
            await(s){it.updates.prompt=="Available"};click("Later")
            lateinit var old:PlayUpdateState
            s.onActivity {old=ViewModelProvider(it)[PlayUpdateState::class.java];assertNull(it.updates.prompt);it.updates.check()}
            s.recreate()
            await(s){fake.queries>=2}
            s.onActivity {assertSame(old,ViewModelProvider(it)[PlayUpdateState::class.java]);assertNull(it.updates.prompt);assertEquals(1,fake.listeners.size)}
            s.moveToState(Lifecycle.State.CREATED);assertTrue(fake.listeners.isEmpty())
            s.moveToState(Lifecycle.State.RESUMED)
            await(s){it.updates.prompt=="Available"}
        }
        assertTrue(fake.listeners.isEmpty())
    }
    @Test fun failedCheckAndConsentGatingDoNotSuppressFutureChecksOrDocumentUse() {
        fake.next=Tasks.forException(IllegalStateException("offline test"))
        UpdateTestEnvironment.canLaunch=false
        ActivityScenario.launch(UpdateTestActivity::class.java).use {s->
            await(s){fake.queries>=1}
            click("Open sample document")
            s.onActivity {it.updates.check()}
            await(s){it.updates.prompt=="Available"}
            s.onActivity {it.updates.download();assertFalse(fake.isConfirmationDialogVisible);UpdateTestEnvironment.canLaunch=true}
            await(s){it.hasWindowFocus()}
            click("Update")
            s.onActivity {assertTrue(fake.isConfirmationDialogVisible);fake.userRejectsUpdate();it.updates.onFlowResult(Activity.RESULT_CANCELED)}
            await(s){it.updates.prompt==null}
        }
    }
    @Test fun oldQueryCannotEraseNewerDownloadEventAndResumeRecoversReady() {
        ActivityScenario.launch(UpdateTestActivity::class.java).use {s->
            begin(s)
            val pending=TaskCompletionSource<AppUpdateInfo>()
            lateinit var stale:AppUpdateInfo
            s.onActivity {
                stale=fake.appUpdateInfo.result
                fake.next=pending.task;it.updates.check()
                fake.downloadCompletes()
            }
            await(s){it.updates.prompt=="Ready"}
            s.onActivity {pending.setResult(stale)}
            inst.waitForIdleSync()
            s.onActivity {assertEquals("Ready",it.updates.prompt)}
            s.moveToState(Lifecycle.State.CREATED)
            assertTrue(fake.listeners.isEmpty())
            s.moveToState(Lifecycle.State.RESUMED)
            await(s){it.updates.prompt=="Ready"}
        }
        // Fresh Activity/ViewModel simulates a new process UI state; Play remains the authority.
        ActivityScenario.launch(UpdateTestActivity::class.java).use {s->await(s){it.updates.prompt=="Ready"}}
    }
    @Test fun lateResponseFromStoppedActivityIsIgnoredAndNoUpdateNeverShowsOffer() {
        fake.setUpdateNotAvailable()
        val pending=TaskCompletionSource<AppUpdateInfo>()
        lateinit var noUpdate:AppUpdateInfo
        inst.runOnMainSync {noUpdate=fake.appUpdateInfo.result;fake.next=pending.task}
        ActivityScenario.launch(UpdateTestActivity::class.java).use {s->
            await(s){fake.queries>=2}
            s.moveToState(Lifecycle.State.CREATED)
            pending.setResult(noUpdate)
            inst.waitForIdleSync()
            s.onActivity {assertNull(it.updates.prompt)}
            s.moveToState(Lifecycle.State.RESUMED)
            await(s){fake.queries>=3}
            s.onActivity {assertNull(it.updates.prompt)}
            click("Open sample document")
        }
    }
    @Test fun failedInstallationKeepsDownloadedUpdateAndAllowsRetry() {
        ActivityScenario.launch(UpdateTestActivity::class.java).use {s->
            begin(s);s.onActivity {fake.downloadCompletes()};await(s){it.updates.prompt=="Ready"}
            s.onActivity {fake.setInstallErrorCode(InstallErrorCode.ERROR_INTERNAL_ERROR)}
            click("Restart to update")
            await(s){it.updates.installFailed && it.updates.prompt=="Ready"}
            s.onActivity {fake.setInstallErrorCode(InstallErrorCode.NO_ERROR)}
            click("Restart to update")
            await(s){it.updates.transfer.stage==UpdateStage.Installing}
        }
    }
}
