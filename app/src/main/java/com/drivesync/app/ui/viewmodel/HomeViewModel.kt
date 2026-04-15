package com.drivesync.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.drivesync.app.auth.GoogleAuthManager
import com.drivesync.app.auth.SignInState
import com.drivesync.app.data.local.AppDatabase
import com.drivesync.app.data.local.FolderMapping
import com.drivesync.app.data.repository.FolderMappingRepository
import com.drivesync.app.sync.SyncWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    val authManager = GoogleAuthManager(context)

    private val repository = FolderMappingRepository(
        AppDatabase.getInstance(context).folderMappingDao()
    )

    val mappings: StateFlow<List<FolderMapping>> = repository.allMappings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val signInState: StateFlow<SignInState> = authManager.signInState

    private val _isSyncRunning = MutableStateFlow(false)
    val isSyncRunning: StateFlow<Boolean> = _isSyncRunning.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage: SharedFlow<String> = _snackbarMessage.asSharedFlow()

    init {
        observeSyncWork()
    }

    private fun observeSyncWork() {
        viewModelScope.launch {
            WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(SyncWorker.WORK_NAME_IMMEDIATE)
                .collect { workInfos ->
                    _isSyncRunning.value = workInfos.any { info ->
                        info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
                    }
                }
        }
    }

    fun syncNow() {
        val prefs = context.getSharedPreferences("drive_sync_prefs", Context.MODE_PRIVATE)
        val wifiOnly = prefs.getBoolean("wifi_only", true)
        SyncWorker.runNow(context, wifiOnly)
        viewModelScope.launch {
            _snackbarMessage.emit("Sync started")
        }
    }

    fun toggleMapping(mapping: FolderMapping, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(mapping.id, enabled)
        }
    }

    fun deleteMapping(mapping: FolderMapping) {
        viewModelScope.launch {
            repository.deleteMapping(mapping)
            _snackbarMessage.emit("Mapping deleted")
        }
    }

    fun refreshSignInState() = authManager.refreshSignInState()
}
