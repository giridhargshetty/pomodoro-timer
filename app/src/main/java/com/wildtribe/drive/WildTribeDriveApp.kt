package com.wildtribe.drive

import android.app.Application
import com.wildtribe.drive.util.LogHelper
import com.wildtribe.drive.util.PreferenceHelper

/**
 * Application entry point. Handles global initialisation.
 */
class WildTribeDriveApp : Application() {

    override fun onCreate() {
        super.onCreate()
        LogHelper.i("App", "Wild Tribe Drive started – v${BuildConfig.VERSION_NAME}")
        // Record first install date for review eligibility tracking
        PreferenceHelper.setFirstInstallDate(this, System.currentTimeMillis())
    }
}
