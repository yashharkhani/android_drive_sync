package com.drivesync.app

import android.app.Application
import androidx.work.Configuration
import com.drivesync.app.sync.NotificationHelper

class DriveSyncApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
