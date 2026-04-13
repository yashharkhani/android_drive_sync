package com.drivesync.app.sync

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.drivesync.app.data.local.FolderMapping
import com.drivesync.app.data.model.SyncResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncManager(
    private val context: Context,
    private val driveHelper: DriveApiHelper
) {

    /**
     * Sync a single folder mapping from Android to Drive.
     */
    suspend fun syncMapping(
        mapping: FolderMapping,
        onProgress: (currentFile: String, bytesUploaded: Long) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        var filesUploaded = 0
        var filesSkipped = 0
        var filesDeleted = 0
        var bytesUploaded = 0L

        try {
            // 1. Get local files from SAF URI
            val localUri = Uri.parse(mapping.localUri)
            val localDocFile = DocumentFile.fromTreeUri(context, localUri)
                ?: return@withContext SyncResult(
                    mappingId = mapping.id,
                    success = false,
                    errorMessage = "Cannot access local folder. Please re-select it."
                )

            if (!localDocFile.exists() || !localDocFile.canRead()) {
                return@withContext SyncResult(
                    mappingId = mapping.id,
                    success = false,
                    errorMessage = "Local folder not accessible: ${mapping.localDisplayPath}"
                )
            }

            // 2. Get existing Drive files in the target folder
            val driveFiles = driveHelper.listFiles(mapping.driveFolderId)

            // 3. Get all local files (flat, one level)
            val localFiles = localDocFile.listFiles()
                .filter { it.isFile && it.name != null }

            val localFileNames = localFiles.mapTo(mutableSetOf()) { it.name!! }

            // 4. Upload new or modified files
            for (localFile in localFiles) {
                val fileName = localFile.name ?: continue
                onProgress(fileName, bytesUploaded)

                val localModified = localFile.lastModified()
                val driveEntry = driveFiles[fileName]

                val shouldUpload = when {
                    driveEntry == null -> true  // New file
                    localModified > driveEntry.second -> true  // Local is newer
                    else -> false
                }

                if (!shouldUpload) {
                    filesSkipped++
                    continue
                }

                val mimeType = getMimeType(fileName)
                val fileSize = localFile.length()

                context.contentResolver.openInputStream(localFile.uri)?.use { stream ->
                    val uploaded = try {
                        if (driveEntry == null) {
                            driveHelper.uploadFile(
                                fileName = fileName,
                                mimeType = mimeType,
                                content = stream,
                                folderId = mapping.driveFolderId,
                                fileSize = fileSize,
                                onProgress = { uploaded ->
                                    onProgress(fileName, bytesUploaded + uploaded)
                                }
                            )
                        } else {
                            driveHelper.updateFile(
                                fileId = driveEntry.first,
                                mimeType = mimeType,
                                content = stream,
                                fileSize = fileSize,
                                onProgress = { uploaded ->
                                    onProgress(fileName, bytesUploaded + uploaded)
                                }
                            )
                        }
                        true
                    } catch (e: Exception) {
                        false
                    }
                    if (uploaded) {
                        filesUploaded++
                        bytesUploaded += fileSize
                    }
                }
            }

            // 5. Optionally delete Drive files that no longer exist locally
            if (mapping.deleteOnDrive) {
                for ((driveName, driveEntry) in driveFiles) {
                    if (driveName !in localFileNames) {
                        try {
                            driveHelper.deleteFile(driveEntry.first)
                            filesDeleted++
                        } catch (_: Exception) {}
                    }
                }
            }

            SyncResult(
                mappingId = mapping.id,
                success = true,
                filesUploaded = filesUploaded,
                filesSkipped = filesSkipped,
                filesDeleted = filesDeleted,
                bytesUploaded = bytesUploaded
            )
        } catch (e: Exception) {
            SyncResult(
                mappingId = mapping.id,
                success = false,
                errorMessage = e.message ?: "Unexpected error during sync"
            )
        }
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
    }
}
