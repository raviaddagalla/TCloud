package com.tcloud.media.video

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.tcloud.chunk.ChunkManager
import com.tcloud.core.database.dao.FileEntryDao
import com.tcloud.core.database.dao.TelegramObjectDao
import com.tcloud.telegram.TelegramClient
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject

/**
 * Custom Media3 / ExoPlayer DataSource for streaming videos directly from Telegram
 * with range download support and local chunk caching.
 */
class TCloudDataSource(
    private val telegramClient: TelegramClient,
    private val chunkManager: ChunkManager,
    private val fileEntryDao: FileEntryDao,
    private val telegramObjectDao: TelegramObjectDao,
    private val cacheDir: File
) : BaseDataSource(/* isNetwork = */ true) {

    companion object {
        private const val TAG = "TCloudDataSource"
        const val SCHEME = "tcloud"
    }

    private var dataSpec: DataSpec? = null
    private var uri: Uri? = null
    private var currentPosition: Long = 0
    private var bytesRemaining: Long = 0
    private var fileEntryId: String? = null
    private var randomAccessFile: RandomAccessFile? = null
    private var activeLocalFile: File? = null

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.uri = dataSpec.uri
        transferInitializing(dataSpec)

        // Parse file entry ID from URI (e.g. tcloud://video/{fileEntryId})
        val pathSegments = dataSpec.uri.pathSegments
        val parsedId = dataSpec.uri.host ?: pathSegments.lastOrNull()
        this.fileEntryId = parsedId

        if (parsedId == null) {
            throw IllegalArgumentException("Invalid TCloud video URI: ${dataSpec.uri}")
        }

        val fileEntry = runBlocking { fileEntryDao.getById(parsedId) }
            ?: throw IllegalStateException("File entry not found: $parsedId")

        // If local file exists, read directly from it
        if (!fileEntry.localUri.isNullOrEmpty()) {
            val local = File(fileEntry.localUri)
            if (local.exists()) {
                activeLocalFile = local
                randomAccessFile = RandomAccessFile(local, "r")
                randomAccessFile?.seek(dataSpec.position)
                currentPosition = dataSpec.position
                bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    dataSpec.length
                } else {
                    local.length() - dataSpec.position
                }
                transferStarted(dataSpec)
                return bytesRemaining
            }
        }

        // Streaming remote file via TDLib
        val totalSize = fileEntry.size
        currentPosition = dataSpec.position
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else {
            totalSize - dataSpec.position
        }

        runBlocking {
            // Find appropriate Telegram object (chunk-aware)
            val chunk = chunkManager.getChunkForOffset(parsedId, currentPosition)
            val chunkIndex = chunk?.chunkIndex ?: 0
            val telegramObject = telegramObjectDao.getByFileEntryIdAndChunk(parsedId, chunkIndex)

            if (telegramObject != null && telegramObject.telegramFileId.isNotEmpty()) {
                val tdlibFileId = telegramObject.telegramFileId.toIntOrNull() ?: 0
                if (tdlibFileId > 0) {
                    val relativeOffset = chunk?.let { currentPosition - it.offsetStart } ?: currentPosition
                    val downloadLimit = minOf(bytesRemaining, 10 * 1024 * 1024) // 10MB chunk prefetch
                    
                    telegramClient.downloadFile(
                        fileId = tdlibFileId,
                        priority = 32,
                        offset = relativeOffset,
                        limit = downloadLimit,
                        synchronous = false
                    )
                }
            }
        }

        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val bytesToRead = minOf(length.toLong(), bytesRemaining).toInt()

        // If reading from local file
        randomAccessFile?.let { raf ->
            val read = raf.read(buffer, offset, bytesToRead)
            if (read > 0) {
                currentPosition += read
                bytesRemaining -= read
                bytesTransferred(read)
                return read
            }
        }

        // If reading from cached remote file or TDLib file directory
        val id = fileEntryId ?: return C.RESULT_END_OF_INPUT
        val chunk = runBlocking { chunkManager.getChunkForOffset(id, currentPosition) }
        val chunkIndex = chunk?.chunkIndex ?: 0
        val telegramObj = runBlocking { telegramObjectDao.getByFileEntryIdAndChunk(id, chunkIndex) }

        if (telegramObj != null && telegramObj.telegramFileId.isNotEmpty()) {
            val tdlibFileId = telegramObj.telegramFileId.toIntOrNull() ?: 0
            if (tdlibFileId > 0) {
                val fileResult = runBlocking { telegramClient.getFile(tdlibFileId) }
                fileResult.onSuccess { tdlibFile ->
                    if (tdlibFile.local.isDownloadingCompleted && tdlibFile.local.path.isNotEmpty()) {
                        val downloaded = File(tdlibFile.local.path)
                        if (downloaded.exists()) {
                            if (randomAccessFile == null) {
                                randomAccessFile = RandomAccessFile(downloaded, "r")
                            }
                            val relativeOffset = chunk?.let { currentPosition - it.offsetStart } ?: currentPosition
                            randomAccessFile?.seek(relativeOffset)
                            val read = randomAccessFile?.read(buffer, offset, bytesToRead) ?: -1
                            if (read > 0) {
                                currentPosition += read
                                bytesRemaining -= read
                                bytesTransferred(read)
                                return read
                            }
                        }
                    }
                }
            }
        }

        return C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            randomAccessFile?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing random access file: ${e.message}")
        } finally {
            randomAccessFile = null
            activeLocalFile = null
            if (dataSpec != null) {
                dataSpec = null
                transferEnded()
            }
        }
    }

    class Factory @Inject constructor(
        private val telegramClient: TelegramClient,
        private val chunkManager: ChunkManager,
        private val fileEntryDao: FileEntryDao,
        private val telegramObjectDao: TelegramObjectDao,
        private val cacheDir: File
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return TCloudDataSource(
                telegramClient,
                chunkManager,
                fileEntryDao,
                telegramObjectDao,
                cacheDir
            )
        }
    }
}
