package com.drivesync.app.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.drivesync.app.MainActivity
import com.drivesync.app.R

object NotificationHelper {

    const val CHANNEL_SYNC_PROGRESS = "drive_sync_progress"
    const val CHANNEL_SYNC_RESULT = "drive_sync_result"
    const val NOTIFICATION_ID_PROGRESS = 1001
    const val NOTIFICATION_ID_RESULT = 1002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val progressChannel = NotificationChannel(
            CHANNEL_SYNC_PROGRESS,
            "Sync Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows active backup sync progress"
            setShowBadge(false)
        }

        val resultChannel = NotificationChannel(
            CHANNEL_SYNC_RESULT,
            "Sync Results",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Shows backup sync completion results"
        }

        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(resultChannel)
    }

    fun buildProgressNotification(
        context: Context,
        currentFile: String,
        progress: Int = -1,
        max: Int = -1
    ) = NotificationCompat.Builder(context, CHANNEL_SYNC_PROGRESS)
        .setContentTitle("Syncing to Drive…")
        .setContentText(if (currentFile.isNotBlank()) "Uploading: $currentFile" else "Preparing sync…")
        .setSmallIcon(android.R.drawable.ic_popup_sync)
        .setOngoing(true)
        .setProgress(max, progress, progress < 0)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    fun showResultNotification(
        context: Context,
        title: String,
        message: String,
        isError: Boolean = false
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SYNC_RESULT)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(
                if (isError) android.R.drawable.ic_dialog_alert
                else android.R.drawable.ic_dialog_info
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_ID_RESULT, notification)
        } catch (_: SecurityException) {
            // Notification permission not granted
        }
    }

    fun cancelProgress(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PROGRESS)
    }
}
