package com.blw.documentreader.reviews

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import java.time.LocalDate

class PlayReview(private val activity: ComponentActivity) {
    private val prefs = activity.getSharedPreferences("review_usage", Context.MODE_PRIVATE)
    companion object { private var attemptedThisProcess = false }
    fun observeUse() {
        val now = System.currentTimeMillis()
        val dates = (prefs.getStringSet("dates", emptySet()).orEmpty() + LocalDate.now().toString()).sorted().takeLast(3).toSet()
        prefs.edit().putLong("first", prefs.getLong("first", now)).putStringSet("dates", dates).apply()
    }
    fun maybeAsk(safe: () -> Boolean) {
        val now = System.currentTimeMillis()
        if (attemptedThisProcess || !safeNow(safe) || !ReviewPolicy.eligible(now,
                prefs.getLong("first", 0), prefs.getStringSet("dates", emptySet()).orEmpty().size,
                prefs.getLong("last", 0))) return
        attemptedThisProcess = true
        // An attempt is not evidence that the user submitted a rating.
        prefs.edit().putLong("last", now).apply()
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnSuccessListener { info ->
            if (safeNow(safe)) manager.launchReviewFlow(activity, info)
        }
    }
    private fun safeNow(safe: () -> Boolean) = !activity.isFinishing && !activity.isDestroyed &&
        activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && activity.hasWindowFocus() && safe()
    fun openStore() {
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))) }
        catch (_: Exception) { try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}"))) } catch (_: Exception) {} }
    }
}
