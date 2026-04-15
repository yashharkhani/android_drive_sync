package com.drivesync.app.data.model

data class SyncResult(
    val mappingId: Long,
    val success: Boolean,
    val filesUploaded: Int = 0,
    val filesSkipped: Int = 0,
    val filesDeleted: Int = 0,
    val bytesUploaded: Long = 0L,
    val errorMessage: String? = null
) {
    val statusMessage: String
        get() = when {
            !success -> "Failed: ${errorMessage ?: "Unknown error"}"
            filesUploaded == 0 && filesDeleted == 0 -> "Up to date"
            else -> buildString {
                if (filesUploaded > 0) append("Uploaded $filesUploaded file(s)")
                if (filesDeleted > 0) {
                    if (filesUploaded > 0) append(", ")
                    append("Deleted $filesDeleted file(s)")
                }
            }
        }
}

data class DriveFolder(
    val id: String,
    val name: String,
    val parentId: String? = null
)

sealed class SyncState {
    object Idle : SyncState()
    data class Running(val mappingId: Long, val currentFile: String = "") : SyncState()
    data class Completed(val results: List<SyncResult>) : SyncState()
    data class Error(val message: String) : SyncState()
}
