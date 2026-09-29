package com.tcloud.sync.restore

import android.content.Context
import android.os.Environment
import android.util.Log
import com.tcloud.chunk.ChunkManager
import com.tcloud.core.common.Constants
import com.tcloud.core.common.TaskStatus
import com.tcloud.core.database.dao.ChunkEntryDao
import com.tcloud.core.database.dao.DownloadTaskDao
import com.tcloud.core.database.dao.FileEntryDao
import com.tcloud.core.database.dao.TelegramObjectDao
import com.tcloud.core.database.entity.DownloadTask
import com.tcloud.core.database.entity.FileEntry
import com.tcloud.telegram.TelegramClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages downloading and restoring files from Telegram cloud back to local device storage.
 * Supports reassembly of multi-part chunked files and offline caching.
 */
@Singleton
class RestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramClient: TelegramClient,
    private val chunkManager: ChunkManager,
    private val fileEntryDao: FileEntryDao,
    private val telegramObjectDao: TelegramObjectDao,
    private val chunkEntryDao: ChunkEntryDao,
    private val downloadTaskDao: DownloadTaskDao
) {
    companion object {
        private const val TAG = "RestoreManager"
    }

    suspend fun downloadFile(
        fileEntryId: String,
        makeOffline: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        val fileEntry = fileEntryDao.getById(fileEntryId)
            ?: return@withContext Result.failure(IllegalArgumentException("File not found"))

        // Check if existing local file already exists
        if (!fileEntry.localUri.isNullOrEmpty()) {
            val localFile = File(fileEntry.localUri)
            if (localFile.exists()) {
                return@withContext Result.success(localFile)
            }
        }

        // Create or get DownloadTask
        val existingTask = downloadTaskDao.getByFileEntryId(fileEntryId)
        val taskId = existingTask?.id ?: downloadTaskDao.insert(
            DownloadTask(
                fileEntryId = fileEntryId,
                status = TaskStatus.QUEUED,
                totalBytes = fileEntry.size
            )
        )

        downloadTaskDao.updateStatus(taskId, TaskStatus.DOWNLOADING)

        try {
            val telegramObjects = telegramObjectDao.getByFileEntryId(fileEntryId)
            if (telegramObjects.isEmpty()) {
                downloadTaskDao.updateStatus(taskId, TaskStatus.FAILED)
                return@withContext Result.failure(IllegalStateException("No remote objects found for file"))
            }

            val targetDir = if (makeOffline) {
                File(context.filesDir, "offline").apply { mkdirs() }
            } else {
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            }

            val destinationFile = File(targetDir, fileEntry.name)

            if (telegramObjects.size == 1 && telegramObjects[0].chunkCount <= 1) {
                // Single file download
                val obj = telegramObjects[0]
                val tdlibFileId = obj.telegramFileId.toIntOrNull()
                    ?: return@withContext Result.failure(IllegalStateException("Invalid Telegram file ID"))

                val downloadResult = telegramClient.downloadFile(
                    fileId = tdlibFileId,
                    priority = 10,
                    synchronous = true
                )

                downloadResult.fold(
                    onSuccess = { tdlibFile ->
                        val downloadedPath = tdlibFile.local.path
                        if (downloadedPath.isNotEmpty()) {
                            val source = File(downloadedPath)
                            source.copyTo(destinationFile, overwrite = true)

                            onDownloadSuccess(fileEntry, destinationFile, makeOffline, taskId)
                            Result.success(destinationFile)
                        } else {
                            downloadTaskDao.updateStatus(taskId, TaskStatus.FAILED)
                            Result.failure(IllegalStateException("Downloaded file path is empty"))
                        }
                    },
                    onFailure = { err ->
                        downloadTaskDao.updateStatus(taskId, TaskStatus.FAILED)
                        Result.failure(err)
                    }
                )
            } else {
                // Multi-chunk file download & reassembly
                val chunkDir = File(context.cacheDir, "download_chunks_${fileEntryId}").apply { mkdirs() }

                for (obj in telegramObjects) {
                    val tdlibFileId = obj.telegramFileId.toIntOrNull() ?: continue
                    val res = telegramClient.downloadFile(
                        fileId = tdlibFileId,
                        priority = 10,
                        synchronous = true
                    )

                    res.onSuccess { tdlibFile ->
                        val chunkPart = File(tdlibFile.local.path)
                        val targetChunk = File(chunkDir, "chunk_${fileEntryId}_${obj.chunkIndex}.tmp")
                        chunkPart.copyTo(targetChunk, overwrite = true)
                    }
                }

                val reassembled = chunkManager.reassembleFile(fileEntryId, destinationFile, chunkDir)
                chunkDir.deleteRecursively()

                if (reassembled) {
                    onDownloadSuccess(fileEntry, destinationFile, makeOffline, taskId)
                    Result.success(destinationFile)
                } else {
                    downloadTaskDao.updateStatus(taskId, TaskStatus.FAILED)
                    Result.failure(IllegalStateException("Failed to reassemble chunks"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            downloadTaskDao.updateStatus(taskId, TaskStatus.FAILED)
            Result.failure(e)
        }
    }

    private suspend fun onDownloadSuccess(
        fileEntry: FileEntry,
        destinationFile: File,
        makeOffline: Boolean,
        taskId: Long
    ) {
        downloadTaskDao.updateStatus(taskId, TaskStatus.COMPLETED)
        downloadTaskDao.updateProgress(taskId, 1.0f, fileEntry.size)

        if (makeOffline) {
            fileEntryDao.update(
                fileEntry.copy(
                    isOffline = true,
                    localUri = destinationFile.absolutePath
                )
            )
        }
    }
}
