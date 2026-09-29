package com.tcloud.sync.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.tcloud.core.common.Constants
import com.tcloud.sync.engine.SyncEngine
import com.tcloud.sync.scanner.MediaScanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for background sync.
 * Survives app process death and device restarts.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val mediaScanner: MediaScanner,
    private val syncEngine: SyncEngine
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"

        /**
         * Enqueue periodic sync work.
         */
        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<SyncWorker>(
                Constants.PERIODIC_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    Constants.INITIAL_RETRY_DELAY_MS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(Constants.SYNC_WORK_NAME)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    Constants.SYNC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWork
                )

            Log.d(TAG, "Periodic sync enqueued")
        }

        /**
         * Enqueue a one-time immediate sync.
         */
        fun enqueueImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeWork = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "${Constants.SYNC_WORK_NAME}_immediate",
                    ExistingWorkPolicy.REPLACE,
                    oneTimeWork
                )
        }

        /**
         * Cancel all sync work.
         */
        fun cancelSync(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(Constants.SYNC_WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Sync worker starting")

        return try {
            // Step 1: Scan for new files
            setProgress(workDataOf("step" to "scanning"))
            val newFiles = mediaScanner.scanForNewFiles()
            Log.d(TAG, "Found ${newFiles.size} new files")

            // Step 2: Queue new files for backup
            if (newFiles.isNotEmpty()) {
                syncEngine.queueFilesForBackup(newFiles)
            }

            // Step 3: Process upload queue
            setProgress(workDataOf("step" to "uploading"))
            val result = syncEngine.processUploadQueue()

            Log.d(TAG, "Sync complete: ${result.uploaded} uploaded, ${result.failed} failed")

            if (result.failed > 0) {
                Result.retry()
            } else {
                Result.success(
                    workDataOf(
                        "uploaded" to result.uploaded,
                        "failed" to result.failed
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync worker failed: ${e.message}")
            Result.retry()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = android.app.Notification.Builder(
            applicationContext,
            com.tcloud.TCloudApplication.CHANNEL_SYNC
        )
            .setContentTitle("TCloud")
            .setContentText("Syncing files…")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .build()

        return ForegroundInfo(Constants.SYNC_NOTIFICATION_ID, notification)
    }
}
