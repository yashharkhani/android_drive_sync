package com.drivesync.app.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.drivesync.app.data.local.AppDatabase
import com.drivesync.app.data.local.FolderMapping
import com.drivesync.app.data.repository.FolderMappingRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AddMappingUiState(
    val localUri: String = "",
    val localDisplayPath: String = "",
    val driveFolderId: String = "",
    val driveDisplayPath: String = "",
    val deleteOnDrive: Boolean = false,
    val isEnabled: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val isValid: Boolean
        get() = localUri.isNotBlank() && driveFolderId.isNotBlank()
}

class AddMappingViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication()

    private val repository = FolderMappingRepository(
        AppDatabase.getInstance(context).folderMappingDao()
    )

    private val _uiState = MutableStateFlow(AddMappingUiState())
    val uiState: StateFlow<AddMappingUiState> = _uiState.asStateFlow()

    private val _savedEvent = MutableSharedFlow<Unit>()
    val savedEvent: SharedFlow<Unit> = _savedEvent.asSharedFlow()

    fun loadExistingMapping(mappingId: Long) {
        viewModelScope.launch {
            val mapping = repository.getMappingById(mappingId) ?: return@launch
            _uiState.value = AddMappingUiState(
                localUri = mapping.localUri,
                localDisplayPath = mapping.localDisplayPath,
                driveFolderId = mapping.driveFolderId,
                driveDisplayPath = mapping.driveDisplayPath,
                deleteOnDrive = mapping.deleteOnDrive,
                isEnabled = mapping.isEnabled
            )
        }
    }

    fun setLocalFolder(uri: Uri, displayPath: String) {
        // Take persistent read permission
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        _uiState.value = _uiState.value.copy(
            localUri = uri.toString(),
            localDisplayPath = displayPath
        )
    }

    fun setDriveFolder(folderId: String, displayPath: String) {
        _uiState.value = _uiState.value.copy(
            driveFolderId = folderId,
            driveDisplayPath = displayPath
        )
    }

    fun setDeleteOnDrive(delete: Boolean) {
        _uiState.value = _uiState.value.copy(deleteOnDrive = delete)
    }

    fun saveMapping(existingId: Long? = null) {
        val state = _uiState.value
        if (!state.isValid) {
            _uiState.value = state.copy(error = "Please select both a local folder and a Drive folder")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null)
            try {
                val mapping = FolderMapping(
                    id = existingId ?: 0,
                    localUri = state.localUri,
                    localDisplayPath = state.localDisplayPath,
                    driveFolderId = state.driveFolderId,
                    driveDisplayPath = state.driveDisplayPath,
                    deleteOnDrive = state.deleteOnDrive,
                    isEnabled = state.isEnabled
                )
                if (existingId != null) {
                    repository.updateMapping(mapping)
                } else {
                    repository.addMapping(mapping)
                }
                _savedEvent.emit(Unit)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save"
                )
            }
        }
    }
}

/**
 * Extract a human-readable folder path from a SAF tree URI.
 * e.g. "primary:DCIM/Camera" → "DCIM/Camera"
 */
fun extractDisplayPath(context: Context, uri: Uri): String {
    return try {
        val docFile = DocumentFile.fromTreeUri(context, uri)
        val treeUriPath = uri.lastPathSegment ?: return docFile?.name ?: uri.toString()
        // SAF path format: "primary:DCIM/Camera" or "DCIM:Camera"
        val colonIndex = treeUriPath.indexOf(':')
        if (colonIndex >= 0) {
            treeUriPath.substring(colonIndex + 1)
        } else {
            docFile?.name ?: treeUriPath
        }
    } catch (_: Exception) {
        uri.lastPathSegment ?: uri.toString()
    }
}
