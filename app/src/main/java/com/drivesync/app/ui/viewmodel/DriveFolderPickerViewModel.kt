package com.drivesync.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivesync.app.auth.GoogleAuthManager
import com.drivesync.app.data.model.DriveFolder
import com.drivesync.app.sync.DriveApiHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DriveFolderPickerState(
    val currentFolderId: String = "root",
    val currentFolderName: String = "My Drive",
    val breadcrumbs: List<Pair<String, String>> = emptyList(), // id to name
    val folders: List<DriveFolder> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val newFolderName: String = "",
    val showNewFolderDialog: Boolean = false
)

class DriveFolderPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()
    private val authManager = GoogleAuthManager(context)

    private val _state = MutableStateFlow(DriveFolderPickerState())
    val state: StateFlow<DriveFolderPickerState> = _state.asStateFlow()

    private val driveHelper: DriveApiHelper? get() {
        val account = authManager.getSignedInAccount() ?: return null
        return DriveApiHelper(context, account)
    }

    init {
        loadFolders("root")
    }

    fun loadFolders(folderId: String, folderName: String = "My Drive") {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                currentFolderId = folderId,
                currentFolderName = if (folderId == "root") "My Drive" else folderName
            )

            val helper = driveHelper
            if (helper == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Not signed in to Google"
                )
                return@launch
            }

            try {
                val folders = helper.listFolders(folderId)
                _state.value = _state.value.copy(
                    folders = folders,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to load folders: ${e.message}"
                )
            }
        }
    }

    fun navigateTo(folder: DriveFolder) {
        val currentBreadcrumbs = _state.value.breadcrumbs.toMutableList()
        // Avoid duplicates
        if (currentBreadcrumbs.none { it.first == folder.id }) {
            currentBreadcrumbs.add(_state.value.currentFolderId to _state.value.currentFolderName)
        }
        _state.value = _state.value.copy(breadcrumbs = currentBreadcrumbs)
        loadFolders(folder.id, folder.name)
    }

    fun navigateBack() {
        val breadcrumbs = _state.value.breadcrumbs.toMutableList()
        if (breadcrumbs.isEmpty()) return
        val (parentId, parentName) = breadcrumbs.removeAt(breadcrumbs.lastIndex)
        _state.value = _state.value.copy(breadcrumbs = breadcrumbs)
        loadFolders(parentId, parentName)
    }

    fun navigateToBreadcrumb(index: Int) {
        val breadcrumbs = _state.value.breadcrumbs.toMutableList()
        if (index < 0 || index >= breadcrumbs.size) return
        val (folderId, folderName) = breadcrumbs[index]
        val trimmed = breadcrumbs.subList(0, index)
        _state.value = _state.value.copy(breadcrumbs = trimmed)
        loadFolders(folderId, folderName)
    }

    fun showNewFolderDialog(show: Boolean) {
        _state.value = _state.value.copy(showNewFolderDialog = show, newFolderName = "")
    }

    fun setNewFolderName(name: String) {
        _state.value = _state.value.copy(newFolderName = name)
    }

    fun createFolder(onCreated: (DriveFolder) -> Unit) {
        val name = _state.value.newFolderName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, showNewFolderDialog = false)
            val helper = driveHelper ?: return@launch
            try {
                val newFolder = helper.createFolder(name, _state.value.currentFolderId)
                // Reload folders to show the new one
                loadFolders(_state.value.currentFolderId, _state.value.currentFolderName)
                onCreated(newFolder)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to create folder: ${e.message}"
                )
            }
        }
    }

    suspend fun buildCurrentPath(): String {
        val helper = driveHelper ?: return _state.value.currentFolderName
        return try {
            helper.buildFolderPath(_state.value.currentFolderId)
        } catch (_: Exception) {
            buildString {
                _state.value.breadcrumbs.forEach { (_, name) ->
                    append(name)
                    append("/")
                }
                append(_state.value.currentFolderName)
            }
        }
    }
}
