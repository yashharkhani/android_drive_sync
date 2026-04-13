package com.drivesync.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivesync.app.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SyncSettings(
    val syncEnabled: Boolean = false,
    val intervalHours: Int = 24,
    val wifiOnly: Boolean = true,
    val notificationsEnabled: Boolean = true
)

val SYNC_INTERVAL_OPTIONS = listOf(
    1 to "Every hour",
    3 to "Every 3 hours",
    6 to "Every 6 hours",
    12 to "Every 12 hours",
    24 to "Once a day",
    168 to "Once a week"
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("drive_sync_prefs", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<SyncSettings> = _settings.asStateFlow()

    private fun loadSettings() = SyncSettings(
        syncEnabled = prefs.getBoolean("sync_enabled", false),
        intervalHours = prefs.getInt("sync_interval_hours", 24),
        wifiOnly = prefs.getBoolean("wifi_only", true),
        notificationsEnabled = prefs.getBoolean("notifications_enabled", true)
    )

    fun setSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit().putBoolean("sync_enabled", enabled).apply()
            _settings.value = _settings.value.copy(syncEnabled = enabled)
            if (enabled) {
                SyncWorker.schedule(
                    context,
                    _settings.value.intervalHours,
                    _settings.value.wifiOnly
                )
            } else {
                SyncWorker.cancel(context)
            }
        }
    }

    fun setInterval(hours: Int) {
        viewModelScope.launch {
            prefs.edit().putInt("sync_interval_hours", hours).apply()
            _settings.value = _settings.value.copy(intervalHours = hours)
            if (_settings.value.syncEnabled) {
                SyncWorker.schedule(context, hours, _settings.value.wifiOnly)
            }
        }
    }

    fun setWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch {
            prefs.edit().putBoolean("wifi_only", wifiOnly).apply()
            _settings.value = _settings.value.copy(wifiOnly = wifiOnly)
            if (_settings.value.syncEnabled) {
                SyncWorker.schedule(context, _settings.value.intervalHours, wifiOnly)
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefs.edit().putBoolean("notifications_enabled", enabled).apply()
            _settings.value = _settings.value.copy(notificationsEnabled = enabled)
        }
    }
}
