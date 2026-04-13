package com.drivesync.app.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Stub foreground service referenced by WorkManager for foreground work.
 * WorkManager manages the actual foreground notification via ForegroundInfo.
 */
class SyncForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }
}
