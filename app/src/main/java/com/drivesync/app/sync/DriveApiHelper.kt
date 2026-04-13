package com.drivesync.app.sync

import android.content.Context
import com.drivesync.app.data.model.DriveFolder
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class DriveApiHelper(
    private val context: Context,
    private val account: GoogleSignInAccount
) {

    private val driveService: Drive by lazy {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE)
        ).apply {
            selectedAccount = account.account
        }

        Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("DriveSync")
            .build()
    }

    // ---- Folder operations ----

    /**
     * List immediate child folders of the given Drive folder.
     * Pass "root" or null to list top-level folders in My Drive.
     */
    suspend fun listFolders(parentId: String = "root"): List<DriveFolder> =
        withContext(Dispatchers.IO) {
            val query = buildString {
                append("mimeType = 'application/vnd.google-apps.folder'")
                append(" and '${parentId}' in parents")
                append(" and trashed = false")
            }
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, parents)")
                .setPageSize(200)
                .execute()

            result.files.map { file ->
                DriveFolder(
                    id = file.id,
                    name = file.name,
                    parentId = file.parents?.firstOrNull()
                )
            }.sortedBy { it.name }
        }

    /**
     * Get the name of a folder by ID.
     */
    suspend fun getFolderName(folderId: String): String = withContext(Dispatchers.IO) {
        if (folderId == "root") return@withContext "My Drive"
        driveService.files().get(folderId)
            .setFields("name")
            .execute()
            .name ?: folderId
    }

    /**
     * Build a display path from root to the given folder ID.
     */
    suspend fun buildFolderPath(folderId: String): String = withContext(Dispatchers.IO) {
        if (folderId == "root") return@withContext "My Drive"
        val parts = mutableListOf<String>()
        var currentId: String? = folderId
        val visited = mutableSetOf<String>()

        while (currentId != null && currentId != "root" && currentId !in visited) {
            visited.add(currentId)
            val file = try {
                driveService.files().get(currentId)
                    .setFields("id, name, parents")
                    .execute()
            } catch (_: Exception) { break }

            parts.add(0, file.name)
            currentId = file.parents?.firstOrNull()
        }
        parts.add(0, "My Drive")
        parts.joinToString("/")
    }

    /**
     * Create a folder in Drive under the given parent.
     */
    suspend fun createFolder(name: String, parentId: String = "root"): DriveFolder =
        withContext(Dispatchers.IO) {
            val metadata = File().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentId)
            }
            val created = driveService.files().create(metadata)
                .setFields("id, name, parents")
                .execute()
            DriveFolder(id = created.id, name = created.name, parentId = parentId)
        }

    // ---- File operations ----

    /**
     * List all non-folder files in the given Drive folder.
     * Returns a map of fileName -> (fileId, modifiedTime ms)
     */
    suspend fun listFiles(folderId: String): Map<String, Pair<String, Long>> =
        withContext(Dispatchers.IO) {
            val query = buildString {
                append("mimeType != 'application/vnd.google-apps.folder'")
                append(" and '${folderId}' in parents")
                append(" and trashed = false")
            }
            val result = driveService.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, modifiedTime)")
                .setPageSize(1000)
                .execute()

            result.files.associate { file ->
                file.name to Pair(file.id, file.modifiedTime?.value ?: 0L)
            }
        }

    /**
     * Upload a new file to Drive.
     */
    suspend fun uploadFile(
        fileName: String,
        mimeType: String,
        content: InputStream,
        folderId: String,
        fileSize: Long,
        onProgress: (Long) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val metadata = File().apply {
            name = fileName
            parents = listOf(folderId)
        }
        val mediaContent = ProgressTrackingInputStreamContent(
            mimeType = mimeType,
            inputStream = content,
            length = fileSize,
            onProgress = onProgress
        )
        val created = driveService.files().create(metadata, mediaContent)
            .setFields("id")
            .execute()
        created.id
    }

    /**
     * Update an existing Drive file's content.
     */
    suspend fun updateFile(
        fileId: String,
        mimeType: String,
        content: InputStream,
        fileSize: Long,
        onProgress: (Long) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val mediaContent = ProgressTrackingInputStreamContent(
            mimeType = mimeType,
            inputStream = content,
            length = fileSize,
            onProgress = onProgress
        )
        val updated = driveService.files().update(fileId, File(), mediaContent)
            .setFields("id")
            .execute()
        updated.id
    }

    /**
     * Delete a file from Drive.
     */
    suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        driveService.files().delete(fileId).execute()
    }

    /**
     * Get or create a nested folder path on Drive, returning the leaf folder ID.
     * E.g. createFolderPath("phone_data/camera", "root") creates/finds each segment.
     */
    suspend fun getOrCreateFolderPath(path: String, rootId: String = "root"): String =
        withContext(Dispatchers.IO) {
            val segments = path.split("/").filter { it.isNotBlank() }
            var currentParentId = rootId

            for (segment in segments) {
                val query = buildString {
                    append("mimeType = 'application/vnd.google-apps.folder'")
                    append(" and name = '${segment.replace("'", "\\'")}'")
                    append(" and '${currentParentId}' in parents")
                    append(" and trashed = false")
                }
                val result = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id, name)")
                    .setPageSize(1)
                    .execute()

                currentParentId = if (result.files.isNotEmpty()) {
                    result.files[0].id
                } else {
                    val metadata = File().apply {
                        name = segment
                        mimeType = "application/vnd.google-apps.folder"
                        parents = listOf(currentParentId)
                    }
                    driveService.files().create(metadata).setFields("id").execute().id
                }
            }
            currentParentId
        }
}
