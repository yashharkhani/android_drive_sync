package com.drivesync.app.sync

import android.content.Context
import androidx.work.*
import com.drivesync.app.data.local.AppDatabase
import com.drivesync.app.data.repository.FolderMappingRepository
import com.drivesync.app.auth.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "drive_sync_periodic"
        const val WORK_NAME_IMMEDIATE = "drive_sync_immediate"

        fun schedule(context: Context, intervalHours: Int, wifiOnly: Boolean) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                intervalHours.toLong(), TimeUnit.HOURS,
                15, TimeUnit.MINUTES  // flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runNow(context: Context, wifiOnly: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val authManager = GoogleAuthManager(context)
        val account = authManager.getSignedInAccount()
            ?: return@withContext Result.failure(
                workDataOf("error" to "Not signed in to Google")
            )

        val db = AppDatabase.getInstance(context)
        val repository = FolderMappingRepository(db.folderMappingDao())
        val mappings = repository.getEnabledMappings()

        if (mappings.isEmpty()) {
            return@withContext Result.success()
        }

        val driveHelper = DriveApiHelper(context, account)
        val syncManager = SyncManager(context, driveHelper)

        setForeground(
            ForegroundInfo(
                NotificationHelper.NOTIFICATION_ID_PROGRESS,
                NotificationHelper.buildProgressNotification(context, "")
            )
        )

        var totalUploaded = 0
        var totalErrors = 0
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        for (mapping in mappings) {
            setForeground(
                ForegroundInfo(
                    NotificationHelper.NOTIFICATION_ID_PROGRESS,
                    NotificationHelper.buildProgressNotification(
                        context, mapping.localDisplayPath
                    )
                )
            )

            val result = syncManager.syncMapping(mapping) { file, _ ->
                setProgressAsync(workDataOf("currentFile" to file))
            }

            val syncTime = System.currentTimeMillis()
            repository.updateSyncResult(
                id = mapping.id,
                syncTime = syncTime,
                status = if (result.success)
                    "${result.statusMessage} · ${dateFormat.format(Date(syncTime))}"
                else
                    result.statusMessage,
                fileCount = result.filesUploaded
            )

            if (result.success) totalUploaded += result.filesUploaded
            else totalErrors++
        }

        NotificationHelper.cancelProgress(context)

        val summary = when {
            totalErrors > 0 && totalUploaded == 0 -> "Sync failed for $totalErrors mapping(s)"
            totalErrors > 0 -> "Synced $totalUploaded file(s), $totalErrors error(s)"
            totalUploaded == 0 -> "Everything is up to date"
            else -> "Uploaded $totalUploaded file(s) to Drive"
        }

        NotificationHelper.showResultNotification(
            context,
            title = if (totalErrors > 0) "DriveSync - Error" else "DriveSync Complete",
            message = summary,
            isError = totalErrors > 0
        )

        if (totalErrors > 0 && totalUploaded == 0) Result.retry()
        else Result.success(workDataOf("uploaded" to totalUploaded))
    }
}
