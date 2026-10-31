package com.blw.documentreader.updates

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/** Optional Google Play updates: no forced restart and no document upload. */
class PlayUpdates(private val activity: ComponentActivity,
                  private val launcher: ActivityResultLauncher<IntentSenderRequest>,
                  private val message: (String) -> Unit,
                  private val ready: (Boolean) -> Unit) {
    private val manager = AppUpdateManagerFactory.create(activity)
    private val prefs = activity.getSharedPreferences("play_updates", Context.MODE_PRIVATE)
    private var closed = false
    var allowedToPrompt: () -> Boolean = { true }
    val isBusy: Boolean get() = checking || flowOpen
    private var checking = false
    private var flowOpen = false
    private var checkedAutomatically = false
    private val listener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> ready(true)
            InstallStatus.INSTALLED -> ready(false)
            InstallStatus.FAILED -> message("Update download failed. You can try again in Settings.")
        }
    }
    init { manager.registerListener(listener) }

    fun check(manual: Boolean = false, offerUpdate: Boolean = true) {
        if (checking || flowOpen || !allowedToPrompt()) return
        if (!manual && offerUpdate && checkedAutomatically) return
        checking = true
        if (!manual && offerUpdate) checkedAutomatically = true
        manager.appUpdateInfo.addOnSuccessListener { info ->
            checking = false
            if (closed) return@addOnSuccessListener
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                ready(true)
            } else if (info.installStatus() == InstallStatus.DOWNLOADING || info.installStatus() == InstallStatus.PENDING) {
                if (manual) message("The update is downloading in Google Play.")
            } else if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                if (!offerUpdate) return@addOnSuccessListener
                if (!allowedToPrompt() || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    checkedAutomatically = false
                    return@addOnSuccessListener
                }
                val version = info.availableVersionCode()
                val lastVersion = prefs.getInt("prompt_version", -1)
                val lastTime = prefs.getLong("prompt_time", 0L)
                if (!manual && lastVersion == version && System.currentTimeMillis() - lastTime < 24L * 60 * 60 * 1000)
                    return@addOnSuccessListener
                if (info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    try {
                        flowOpen = manager.startUpdateFlowForResult(info, launcher,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
                        if (flowOpen) prefs.edit().putInt("prompt_version", version)
                            .putLong("prompt_time", System.currentTimeMillis()).apply()
                        else if (manual) message("Open W Reader in Google Play to update.")
                    } catch (_: Exception) {
                        flowOpen = false
                        if (manual) message("Open W Reader in Google Play to update.")
                    }
                } else if (manual) message("An update is available. Open W Reader in Google Play.")
            } else if (manual) message("You're up to date with the version available to your Google Play account.")
        }.addOnFailureListener {
            checking = false
            // Offline or sideloaded installs must remain usable without an update check.
            if (!closed && manual) message("Couldn't check Google Play. Check your connection and try again.")
        }
    }
    fun onFlowResult(resultCode: Int) {
        flowOpen = false
        if (resultCode != Activity.RESULT_OK && resultCode != Activity.RESULT_CANCELED)
            message("Update couldn't start. You can try again in Settings.")
    }
    fun refreshDownloaded() {
        manager.appUpdateInfo.addOnSuccessListener { info ->
            if (!closed && info.installStatus() == InstallStatus.DOWNLOADED) ready(true)
        }
    }
    fun complete() {
        manager.completeUpdate().addOnFailureListener {
            if (!closed) message("Couldn't install the update. Try restarting W Reader or update in Google Play.")
        }
    }
    fun close() { closed = true; manager.unregisterListener(listener) }
}
