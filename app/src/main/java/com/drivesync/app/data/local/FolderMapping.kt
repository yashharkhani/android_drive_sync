package com.drivesync.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folder_mappings")
data class FolderMapping(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** SAF tree URI string for the local Android folder */
    val localUri: String,

    /** Human-readable display path, e.g. "DCIM/Camera" */
    val localDisplayPath: String,

    /** Google Drive folder ID for the target folder */
    val driveFolderId: String,

    /** Human-readable Drive path, e.g. "phone_data/camera" */
    val driveDisplayPath: String,

    /** Whether this mapping is active for syncing */
    val isEnabled: Boolean = true,

    /** Timestamp (ms) of the last successful sync, 0 = never */
    val lastSyncTime: Long = 0L,

    /** Human-readable status of the last sync attempt */
    val lastSyncStatus: String = "Never synced",

    /** Number of files uploaded in last sync */
    val lastSyncFileCount: Int = 0,

    /** Whether to delete files from Drive that no longer exist locally */
    val deleteOnDrive: Boolean = false,

    /** Created timestamp */
    val createdAt: Long = System.currentTimeMillis()
)
