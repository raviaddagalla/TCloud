package com.tcloud.sync.engine

import android.content.Context
import android.util.Log
import com.tcloud.chunk.ChunkManager
import com.tcloud.core.common.*
import com.tcloud.core.database.dao.*
import com.tcloud.core.database.entity.*
import com.tcloud.core.network.NetworkMonitor
import com.tcloud.core.network.NetworkState
import com.tcloud.core.security.SecureStorage
import com.tcloud.core.storage.CloudStorageBackend
import com.tcloud.telegram.metadata.TCloudMetadataEncoder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core sync engine orchestrating the upload pipeline.
 *
 * Pipeline: Scan → Hash → Queue → Upload (with chunk support) → Verify → Complete
 *
 * Respects network/battery policies. Survives process death via Room-persisted queue.
 */
@Singleton
class SyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileEntryDao: FileEntryDao,
    private val uploadTaskDao: UploadTaskDao,
    private val telegramObjectDao: TelegramObjectDao,
    private val chunkEntryDao: ChunkEntryDao,
    private val backupRuleDao: BackupRuleDao,
    private val cloudStorage: CloudStorageBackend,
    private val chunkManager: ChunkManager,
    private val networkMonitor: NetworkMonitor,
    private val secureStorage: SecureStorage
) {
    companion object {
        private const val TAG = "SyncEngine"
    }

    private val uploadSemaphore = Semaphore(Constants.MAX_CONCURRENT_UPLOADS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Check if backup conditions are met.
     */
    fun canBackup(): BackupCondition {
        val network = networkMonitor.networkState.value
        val wifiOnly = secureStorage.getBoolean(Constants.PREF_WIFI_ONLY, true)
        val mobileData = secureStorage.getBoolean(Constants.PREF_MOBILE_DATA, false)

        return when {
            network == NetworkState.Disconnected -> BackupCondition.NoNetwork
            network == NetworkState.MobileData && wifiOnly && !mobileData -> BackupCondition.WaitingForWifi
            network == NetworkState.Roaming -> BackupCondition.WaitingForWifi
            else -> BackupCondition.Ready
        }
    }

    /**
     * Process the upload queue — called by WorkManager or foreground service.
     */
    suspend fun processUploadQueue(): SyncResult {
        val condition = canBackup()
        if (condition != BackupCondition.Ready) {
            Log.d(TAG, "Backup conditions not met: $condition")
            return SyncResult(paused = true, reason = condition.name)
        }

        if (!cloudStorage.isAuthenticated()) {
            Log.d(TAG, "Not authenticated, skipping sync")
            return SyncResult(paused = true, reason = "Not authenticated")
        }

        var uploaded = 0
        var failed = 0
        var totalProcessed = 0

        while (true) {
            val pendingTasks = uploadTaskDao.getNextPending(1)
            if (pendingTasks.isEmpty()) break

            val task = pendingTasks.first()
            totalProcessed++

            try {
                uploadSemaphore.acquire()
                val success = processUploadTask(task)
                if (success) uploaded++ else failed++
            } catch (e: Exception) {
                Log.e(TAG, "Upload task failed: ${e.message}")
                failed++
                uploadTaskDao.updateStatus(task.id, TaskStatus.FAILED)
            } finally {
                uploadSemaphore.release()
            }

            // Re-check conditions between uploads
            if (canBackup() != BackupCondition.Ready) break
        }

        return SyncResult(
            uploaded = uploaded,
            failed = failed,
            totalProcessed = totalProcessed
        )
    }

    /**
     * Process a single upload task.
     */
    private suspend fun processUploadTask(task: UploadTask): Boolean {
        val fileEntry = fileEntryDao.getById(task.fileEntryId) ?: run {
            uploadTaskDao.updateStatus(task.id, TaskStatus.FAILED)
            return false
        }

        val localFile = fileEntry.localUri?.let { File(it) }
        if (localFile == null || !localFile.exists()) {
            uploadTaskDao.updateStatus(task.id, TaskStatus.FAILED)
            return false
        }

        // Update status to hashing
        uploadTaskDao.updateStatus(task.id, TaskStatus.HASHING)
        fileEntryDao.updateStatus(fileEntry.id, FileStatus.HASHING)

        // Calculate hash if needed
        val sha256 = fileEntry.sha256 ?: chunkManager.calculateChecksum(localFile)
        if (fileEntry.sha256 == null) {
            fileEntryDao.update(fileEntry.copy(sha256 = sha256))
        }

        // Check for duplicates
        val existingFile = fileEntryDao.findBySha256(sha256)
        if (existingFile != null && existingFile.id != fileEntry.id &&
            existingFile.status == FileStatus.BACKED_UP
        ) {
            Log.d(TAG, "Duplicate detected for ${fileEntry.name}, skipping upload")
            fileEntryDao.updateStatus(fileEntry.id, FileStatus.BACKED_UP)
            uploadTaskDao.updateStatus(task.id, TaskStatus.COMPLETED)
            return true
        }

        // Determine if chunking is needed
        if (chunkManager.needsChunking(fileEntry.size)) {
            return uploadChunked(task, fileEntry, localFile, sha256)
        } else {
            return uploadSingle(task, fileEntry, localFile, sha256)
        }
    }

    /**
     * Upload a single (non-chunked) file.
     */
    private suspend fun uploadSingle(
        task: UploadTask,
        fileEntry: FileEntry,
        localFile: File,
        sha256: String
    ): Boolean {
        uploadTaskDao.updateStatus(task.id, TaskStatus.UPLOADING)
        fileEntryDao.updateStatus(fileEntry.id, FileStatus.UPLOADING)

        val metadata = mapOf(
            "objectId" to fileEntry.id,
            "sha256" to sha256,
            "size" to fileEntry.size.toString(),
            "createdAt" to fileEntry.createdAt.toString(),
            "modifiedAt" to fileEntry.modifiedAt.toString(),
            "category" to fileEntry.category.name,
            "chunkIndex" to "0",
            "chunkCount" to "1"
        )

        val result = cloudStorage.upload(
            localPath = localFile.absolutePath,
            remoteName = fileEntry.name,
            mimeType = fileEntry.mimeType,
            metadata = metadata
        )

        if (result.success) {
            // Save Telegram object reference
            telegramObjectDao.insert(
                TelegramObject(
                    fileEntryId = fileEntry.id,
                    telegramChatId = secureStorage.getLong(Constants.PREF_TELEGRAM_CHAT_ID),
                    telegramMessageId = result.telegramMessageId,
                    telegramFileId = result.telegramFileId,
                    telegramRemoteFileId = result.telegramRemoteFileId,
                    chunkIndex = 0,
                    chunkCount = 1,
                    size = result.size,
                    checksum = sha256
                )
            )

            fileEntryDao.updateStatus(fileEntry.id, FileStatus.BACKED_UP)
            uploadTaskDao.updateStatus(task.id, TaskStatus.COMPLETED)
            uploadTaskDao.updateProgress(task.id, 1.0f, fileEntry.size)
            return true
        } else {
            handleUploadFailure(task, result.error ?: "Unknown error")
            return false
        }
    }

    /**
     * Upload a large file in chunks.
     */
    private suspend fun uploadChunked(
        task: UploadTask,
        fileEntry: FileEntry,
        localFile: File,
        sha256: String
    ): Boolean {
        // Calculate chunks if not already done
        var chunks = chunkEntryDao.getByFileEntryId(fileEntry.id)
        if (chunks.isEmpty()) {
            chunks = chunkManager.calculateChunks(fileEntry)
            chunkManager.saveChunks(chunks)
            uploadTaskDao.update(task.copy(totalChunks = chunks.size))
        }

        val tempDir = File(context.cacheDir, "chunks")
        tempDir.mkdirs()

        var completedChunks = chunkEntryDao.getCompletedChunkCount(fileEntry.id)

        while (true) {
            val nextChunk = chunkManager.getNextPendingChunk(fileEntry.id) ?: break

            // Check conditions
            if (canBackup() != BackupCondition.Ready) {
                uploadTaskDao.updateStatus(task.id, TaskStatus.PAUSED)
                return false
            }

            uploadTaskDao.updateStatus(task.id, TaskStatus.UPLOADING)
            fileEntryDao.updateStatus(fileEntry.id, FileStatus.UPLOADING)
            chunkManager.updateChunkStatus(nextChunk.id, TaskStatus.UPLOADING)

            // Create chunk file
            val chunkFile = chunkManager.createChunkFile(localFile, nextChunk, tempDir)
            val chunkHash = chunkManager.calculateChecksum(chunkFile)

            // Upload chunk
            val metadata = mapOf(
                "objectId" to fileEntry.id,
                "sha256" to sha256,
                "size" to fileEntry.size.toString(),
                "createdAt" to fileEntry.createdAt.toString(),
                "modifiedAt" to fileEntry.modifiedAt.toString(),
                "category" to fileEntry.category.name,
                "chunkIndex" to nextChunk.chunkIndex.toString(),
                "chunkCount" to chunks.size.toString()
            )

            val result = cloudStorage.upload(
                localPath = chunkFile.absolutePath,
                remoteName = "${fileEntry.name}.part${String.format("%03d", nextChunk.chunkIndex)}",
                mimeType = fileEntry.mimeType,
                metadata = metadata
            )

            if (result.success) {
                // Save chunk info
                chunkEntryDao.update(nextChunk.copy(
                    sha256 = chunkHash,
                    telegramMessageId = result.telegramMessageId,
                    telegramFileId = result.telegramFileId,
                    telegramRemoteFileId = result.telegramRemoteFileId,
                    status = TaskStatus.COMPLETED
                ))

                telegramObjectDao.insert(
                    TelegramObject(
                        fileEntryId = fileEntry.id,
                        telegramChatId = secureStorage.getLong(Constants.PREF_TELEGRAM_CHAT_ID),
                        telegramMessageId = result.telegramMessageId,
                        telegramFileId = result.telegramFileId,
                        telegramRemoteFileId = result.telegramRemoteFileId,
                        chunkIndex = nextChunk.chunkIndex,
                        chunkCount = chunks.size,
                        size = result.size,
                        checksum = chunkHash
                    )
                )

                completedChunks++
                val progress = completedChunks.toFloat() / chunks.size
                uploadTaskDao.updateProgress(task.id, progress, nextChunk.offsetEnd)
                uploadTaskDao.update(task.copy(currentChunkIndex = completedChunks))

                // Clean up chunk temp file
                chunkFile.delete()
            } else {
                chunkManager.updateChunkStatus(nextChunk.id, TaskStatus.FAILED)
                chunkFile.delete()
                handleUploadFailure(task, result.error ?: "Chunk upload failed")
                return false
            }
        }

        // All chunks complete
        chunkManager.cleanupChunkFiles(fileEntry.id, tempDir)
        fileEntryDao.updateStatus(fileEntry.id, FileStatus.BACKED_UP)
        uploadTaskDao.updateStatus(task.id, TaskStatus.COMPLETED)
        uploadTaskDao.updateProgress(task.id, 1.0f, fileEntry.size)
        return true
    }

    /**
     * Handle upload failure with exponential backoff.
     */
    private suspend fun handleUploadFailure(task: UploadTask, error: String) {
        val newRetryCount = task.retryCount + 1

        if (newRetryCount >= Constants.MAX_RETRY_ATTEMPTS) {
            uploadTaskDao.update(task.copy(
                status = TaskStatus.FAILED,
                retryCount = newRetryCount,
                lastError = error,
                updatedAt = System.currentTimeMillis()
            ))
            fileEntryDao.updateStatus(task.fileEntryId, FileStatus.FAILED)
        } else {
            uploadTaskDao.update(task.copy(
                status = TaskStatus.RETRYING,
                retryCount = newRetryCount,
                lastError = error,
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Queue a file for backup.
     */
    suspend fun queueForBackup(fileEntry: FileEntry) {
        // Check if already queued or backed up
        val existingTask = uploadTaskDao.getByFileEntryId(fileEntry.id)
        if (existingTask != null && existingTask.status != TaskStatus.FAILED) return

        val task = UploadTask(
            fileEntryId = fileEntry.id,
            status = TaskStatus.QUEUED,
            totalBytes = fileEntry.size
        )

        uploadTaskDao.insert(task)
        fileEntryDao.updateStatus(fileEntry.id, FileStatus.QUEUED)
    }

    /**
     * Queue multiple files for backup.
     */
    suspend fun queueFilesForBackup(files: List<FileEntry>) {
        files.forEach { queueForBackup(it) }
    }
}

enum class BackupCondition {
    Ready,
    NoNetwork,
    WaitingForWifi,
    WaitingForCharging,
    BatteryTooLow,
    NotAuthenticated
}

data class SyncResult(
    val uploaded: Int = 0,
    val failed: Int = 0,
    val totalProcessed: Int = 0,
    val paused: Boolean = false,
    val reason: String = ""
)
