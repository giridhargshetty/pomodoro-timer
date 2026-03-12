package com.wildtribe.drive.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.review.ReviewManagerFactory
import com.wildtribe.drive.util.LogHelper
import com.wildtribe.drive.util.PreferenceHelper
import java.util.concurrent.TimeUnit

/**
 * Manages in-app review prompts using the Play Core Review API.
 *
 * Review is requested after:
 *  - 5 successful rides, OR
 *  - 7 days since first install
 *
 * Only prompts once per app lifetime.
 */
object ReviewManager {

    private const val RIDES_THRESHOLD = 5
    private val DAYS_THRESHOLD_MS = TimeUnit.DAYS.toMillis(7)

    /**
     * Check eligibility and trigger the Play review flow if appropriate.
     * Safe to call on every ride completion – does nothing if criteria not met.
     */
    fun checkAndRequestReview(activity: Activity) {
        val ctx = activity.applicationContext
        if (PreferenceHelper.hasBeenPromptedForReview(ctx)) return

        val rideCount = PreferenceHelper.getRideCount(ctx)
        val daysSinceInstall = System.currentTimeMillis() - PreferenceHelper.getFirstInstallDate(ctx)
        val eligible = rideCount >= RIDES_THRESHOLD || daysSinceInstall >= DAYS_THRESHOLD_MS

        if (!eligible) return

        val manager = ReviewManagerFactory.create(activity)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                manager.launchReviewFlow(activity, reviewInfo)
                    .addOnCompleteListener {
                        PreferenceHelper.setReviewPrompted(ctx, true)
                        LogHelper.d("ReviewManager", "Review flow completed")
                    }
            } else {
                LogHelper.w("ReviewManager", "Review request failed: ${task.exception?.message}")
            }
        }
    }

    /** Open app store listing directly (fallback / manual rate). */
    fun openStoreListing(activity: Activity) {
        try {
            activity.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${activity.packageName}"))
            )
        } catch (e: ActivityNotFoundException) {
            activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=${activity.packageName}")
                )
            )
        }
    }

    /** Open email client pre-filled for feedback. */
    fun sendFeedbackEmail(activity: Activity) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:support@wildtribedrive.com")
            putExtra(Intent.EXTRA_SUBJECT, "Wild Tribe Drive Feedback")
            putExtra(Intent.EXTRA_TEXT, "App version: ${getVersionName(activity)}\n\n")
        }
        try {
            activity.startActivity(Intent.createChooser(intent, "Send Feedback"))
        } catch (e: ActivityNotFoundException) {
            LogHelper.w("ReviewManager", "No email client found")
        }
    }

    private fun getVersionName(activity: Activity): String =
        try {
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
}
