package com.tcloud.sync.rebuild

import android.util.Log
import com.tcloud.core.common.Constants
import com.tcloud.core.common.FileCategory
import com.tcloud.core.common.FileStatus
import com.tcloud.core.common.TaskStatus
import com.tcloud.core.database.dao.ChunkEntryDao
import com.tcloud.core.database.dao.FileEntryDao
import com.tcloud.core.database.dao.TelegramObjectDao
import com.tcloud.core.database.entity.ChunkEntry
import com.tcloud.core.database.entity.FileEntry
import com.tcloud.core.database.entity.TelegramObject
import com.tcloud.core.security.SecureStorage
import com.tcloud.telegram.TelegramClient
import com.tcloud.telegram.metadata.TCloudMetadataEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rebuilds the local database index from the remote Telegram storage channel.
 *
 * When a user logs in on a new device, this scanner crawls the TCloud channel's
 * message history, decodes JSON metadata embedded in captions, and restores
 * all FileEntry, TelegramObject, and ChunkEntry records.
 */
@Singleton
class CloudIndexRebuilder @Inject constructor(
    private val telegramClient: TelegramClient,
    private val fileEntryDao: FileEntryDao,
    private val telegramObjectDao: TelegramObjectDao,
    private val chunkEntryDao: ChunkEntryDao,
    private val secureStorage: SecureStorage
) {
    companion object {
        private const val TAG = "CloudIndexRebuilder"
        private const val BATCH_SIZE = 100
    }

    data class RebuildProgress(
        val messagesScanned: Int = 0,
        val filesDiscovered: Int = 0,
        val isComplete: Boolean = false,
        val error: String? = null
    )

    fun rebuildIndex(): Flow<RebuildProgress> = flow {
        val chatId = secureStorage.getLong(Constants.PREF_TELEGRAM_CHAT_ID)
        if (chatId == 0L) {
            emit(RebuildProgress(isComplete = true, error = "TCloud storage channel not found"))
            return@flow
        }

        var scannedCount = 0
        var discoveredFiles = 0
        var fromMessageId = 0L

        emit(RebuildProgress(messagesScanned = 0, filesDiscovered = 0, isComplete = false))

        try {
            while (true) {
                val result = telegramClient.getChatHistory(
                    chatId = chatId,
                    fromMessageId = fromMessageId,
                    offset = 0,
                    limit = BATCH_SIZE
                )

                val messages = result.getOrNull()?.messages
                if (messages.isNullOrEmpty()) {
                    break
                }

                for (msg in messages) {
                    scannedCount++
                    fromMessageId = msg.id

                    val document = msg.content as? TdApi.MessageDocument ?: continue
                    val caption = document.caption?.text ?: continue

                    if (!TCloudMetadataEncoder.isTCloudObject(caption)) continue

                    val metadata = TCloudMetadataEncoder.decode(caption) ?: continue
                    val file = document.document.document

                    // 1. Restore FileEntry
                    val existingFile = fileEntryDao.getById(metadata.objectId)
                    if (existingFile == null) {
                        val category = try {
                            FileCategory.valueOf(metadata.category.uppercase())
                        } catch (e: Exception) {
                            FileCategory.DOCUMENTS
                        }

                        val fileEntry = FileEntry(
                            id = metadata.objectId,
                            name = metadata.filename,
                            path = metadata.filename,
                            mimeType = metadata.mimeType,
                            size = metadata.size,
                            createdAt = metadata.createdAt,
                            modifiedAt = metadata.modifiedAt,
                            sha256 = metadata.sha256,
                            status = FileStatus.BACKED_UP,
                            category = category,
                            parentFolderId = metadata.parentFolderId,
                            duration = metadata.duration,
                            width = metadata.width,
                            height = metadata.height,
                            isFolder = false
                        )
                        fileEntryDao.insert(fileEntry)
                        discoveredFiles++
                    }

                    // 2. Restore TelegramObject
                    val telegramObject = TelegramObject(
                        fileEntryId = metadata.objectId,
                        telegramChatId = chatId,
                        telegramMessageId = msg.id,
                        telegramFileId = file.id.toString(),
                        telegramRemoteFileId = file.remote?.id ?: "",
                        chunkIndex = metadata.chunkIndex,
                        chunkCount = metadata.chunkCount,
                        size = file.size.toLong(),
                        checksum = metadata.sha256
                    )
                    telegramObjectDao.insert(telegramObject)

                    // 3. Restore ChunkEntry if chunked
                    if (metadata.chunkCount > 1) {
                        val chunkSize = metadata.size / metadata.chunkCount
                        val offsetStart = metadata.chunkIndex * chunkSize
                        val offsetEnd = if (metadata.chunkIndex == metadata.chunkCount - 1) {
                            metadata.size
                        } else {
                            offsetStart + chunkSize
                        }

                        val chunkEntry = ChunkEntry(
                            fileEntryId = metadata.objectId,
                            chunkIndex = metadata.chunkIndex,
                            chunkCount = metadata.chunkCount,
                            chunkSize = offsetEnd - offsetStart,
                            offsetStart = offsetStart,
                            offsetEnd = offsetEnd,
                            sha256 = metadata.sha256,
                            telegramMessageId = msg.id,
                            telegramFileId = file.id.toString(),
                            telegramRemoteFileId = file.remote?.id ?: "",
                            status = TaskStatus.COMPLETED
                        )
                        chunkEntryDao.insert(chunkEntry)
                    }

                    if (scannedCount % 10 == 0) {
                        emit(RebuildProgress(scannedCount, discoveredFiles, isComplete = false))
                    }
                }
            }

            emit(RebuildProgress(scannedCount, discoveredFiles, isComplete = true))
        } catch (e: Exception) {
            Log.e(TAG, "Error rebuilding cloud index: ${e.message}", e)
            emit(RebuildProgress(scannedCount, discoveredFiles, isComplete = true, error = e.message))
        }
    }.flowOn(Dispatchers.IO)
}
