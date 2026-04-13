package com.drivesync.app.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drivesync.app.data.local.AppDatabase
import com.drivesync.app.data.repository.FolderMappingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Reschedules periodic sync after device reboot or app update.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED
            )
        ) return

        CoroutineScope(Dispatchers.IO).launch {
            val prefs = context.getSharedPreferences("drive_sync_prefs", Context.MODE_PRIVATE)
            val syncEnabled = prefs.getBoolean("sync_enabled", false)
            val intervalHours = prefs.getInt("sync_interval_hours", 24)
            val wifiOnly = prefs.getBoolean("wifi_only", true)

            if (syncEnabled) {
                SyncWorker.schedule(context, intervalHours, wifiOnly)
            }
        }
    }
}
