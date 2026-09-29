package com.tcloud.chunk

import com.tcloud.core.common.Constants
import com.tcloud.core.database.dao.ChunkEntryDao
import com.tcloud.core.database.dao.TelegramObjectDao
import com.tcloud.core.database.entity.ChunkEntry
import com.tcloud.core.database.entity.FileEntry
import com.tcloud.core.database.entity.TelegramObject
import com.tcloud.core.common.TaskStatus
import com.tcloud.core.storage.CloudStorageBackend
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages file chunking for large files that exceed the backend's max object size.
 *
 * The chunk layer is completely transparent — the rest of TCloud works with logical files.
 * Only this class (and the upload/download managers) know about chunks.
 */
@Singleton
class ChunkManager @Inject constructor(
    private val chunkEntryDao: ChunkEntryDao,
    private val telegramObjectDao: TelegramObjectDao,
    private val cloudStorage: CloudStorageBackend
) {
    /**
     * Determine if a file needs chunking.
     */
    fun needsChunking(fileSize: Long): Boolean {
        return fileSize > cloudStorage.maxObjectSize
    }

    /**
     * Calculate chunk boundaries for a file.
     */
    fun calculateChunks(fileEntry: FileEntry): List<ChunkEntry> {
        val maxSize = cloudStorage.maxObjectSize
        if (fileEntry.size <= maxSize) {
            return listOf(
                ChunkEntry(
                    fileEntryId = fileEntry.id,
                    chunkIndex = 0,
                    chunkCount = 1,
                    chunkSize = fileEntry.size,
                    offsetStart = 0,
                    offsetEnd = fileEntry.size
                )
            )
        }

        val chunkCount = ((fileEntry.size + maxSize - 1) / maxSize).toInt()
        return (0 until chunkCount).map { index ->
            val offsetStart = index.toLong() * maxSize
            val offsetEnd = minOf(offsetStart + maxSize, fileEntry.size)
            ChunkEntry(
                fileEntryId = fileEntry.id,
                chunkIndex = index,
                chunkCount = chunkCount,
                chunkSize = offsetEnd - offsetStart,
                offsetStart = offsetStart,
                offsetEnd = offsetEnd
            )
        }
    }

    /**
     * Create a temporary chunk file from the original file.
     */
    fun createChunkFile(
        sourceFile: File,
        chunk: ChunkEntry,
        tempDir: File
    ): File {
        val chunkFile = File(tempDir, "chunk_${chunk.fileEntryId}_${chunk.chunkIndex}.tmp")
        chunkFile.parentFile?.mkdirs()

        FileInputStream(sourceFile).use { input ->
            input.skip(chunk.offsetStart)
            chunkFile.outputStream().use { output ->
                val buffer = ByteArray(Constants.HASH_BUFFER_SIZE)
                var remaining = chunk.chunkSize
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = input.read(buffer, 0, toRead)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }

        return chunkFile
    }

    /**
     * Calculate SHA-256 hash of a file or chunk file.
     */
    fun calculateChecksum(file: File): String {
        val digest = MessageDigest.getInstance(Constants.HASH_ALGORITHM)
        FileInputStream(file).use { input ->
            val buffer = ByteArray(Constants.HASH_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Reassemble chunks into a complete file.
     */
    suspend fun reassembleFile(
        fileEntryId: String,
        outputFile: File,
        chunkDir: File
    ): Boolean {
        val chunks = chunkEntryDao.getByFileEntryId(fileEntryId)
            .sortedBy { it.chunkIndex }

        if (chunks.isEmpty()) return false
        if (chunks.any { it.status != TaskStatus.COMPLETED }) return false

        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().use { output ->
            for (chunk in chunks) {
                val chunkFile = chunk.localTempPath?.let { File(it) }
                    ?: File(chunkDir, "chunk_${fileEntryId}_${chunk.chunkIndex}.tmp")

                if (!chunkFile.exists()) return false

                chunkFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }

        return true
    }

    /**
     * Verify a chunk's integrity.
     */
    fun verifyChunk(chunkFile: File, expectedHash: String): Boolean {
        if (!chunkFile.exists()) return false
        val actualHash = calculateChecksum(chunkFile)
        return actualHash == expectedHash
    }

    /**
     * Clean up temporary chunk files for a completed operation.
     */
    fun cleanupChunkFiles(fileEntryId: String, tempDir: File) {
        tempDir.listFiles()?.filter { it.name.startsWith("chunk_${fileEntryId}_") }?.forEach {
            it.delete()
        }
    }

    /**
     * Get the next pending chunk for upload/download.
     */
    suspend fun getNextPendingChunk(fileEntryId: String): ChunkEntry? {
        return chunkEntryDao.getNextPendingChunk(fileEntryId)
    }

    /**
     * Save chunk entries to database.
     */
    suspend fun saveChunks(chunks: List<ChunkEntry>) {
        chunkEntryDao.insertAll(chunks)
    }

    /**
     * Update chunk status.
     */
    suspend fun updateChunkStatus(chunkId: Long, status: TaskStatus) {
        chunkEntryDao.updateStatus(chunkId, status)
    }

    /**
     * Determine which chunk contains a given byte offset (for streaming).
     */
    suspend fun getChunkForOffset(fileEntryId: String, byteOffset: Long): ChunkEntry? {
        val chunks = chunkEntryDao.getByFileEntryId(fileEntryId)
        return chunks.find { byteOffset >= it.offsetStart && byteOffset < it.offsetEnd }
    }

    /**
     * Get the Telegram object for a specific chunk (for download/streaming).
     */
    suspend fun getTelegramObjectForChunk(fileEntryId: String, chunkIndex: Int): TelegramObject? {
        val objects = telegramObjectDao.getByFileEntryId(fileEntryId)
        return objects.find { it.chunkIndex == chunkIndex }
    }
}
