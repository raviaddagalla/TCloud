package com.tcloud.telegram.storage

import android.content.Context
import android.util.Log
import com.tcloud.BuildConfig
import com.tcloud.core.common.Constants
import com.tcloud.core.security.SecureStorage
import com.tcloud.core.storage.*
import com.tcloud.telegram.TelegramClient
import com.tcloud.telegram.metadata.TCloudMetadataEncoder
import com.tcloud.telegram.metadata.TCloudObjectMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Telegram implementation of CloudStorageBackend.
 *
 * This is the ONLY class that knows about Telegram message IDs, chat IDs,
 * and MTProto details. The rest of TCloud uses the CloudStorageBackend interface.
 */
@Singleton
class TelegramStorageBackend @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramClient: TelegramClient,
    private val secureStorage: SecureStorage
) : CloudStorageBackend {

    companion object {
        private const val TAG = "TelegramStorage"
    }

    override val maxObjectSize: Long
        get() = BuildConfig.MAX_REMOTE_OBJECT_SIZE

    override val supportsRangeDownloads: Boolean = true

    private var storageChatId: Long
        get() = secureStorage.getLong(Constants.PREF_TELEGRAM_CHAT_ID)
        set(value) = secureStorage.putLong(Constants.PREF_TELEGRAM_CHAT_ID, value)

    override suspend fun isAuthenticated(): Boolean {
        return telegramClient.authState.value is com.tcloud.telegram.AuthState.Authorized
    }

    /**
     * Initialize TCloud storage: find or create a private supergroup.
     */
    override suspend fun initializeStorage(): StorageInitResult {
        return try {
            // Check if we already have a stored chat ID
            val existingChatId = storageChatId
            if (existingChatId != 0L) {
                // Verify it still exists
                val message = telegramClient.getMessage(existingChatId, 0)
                // Chat exists, use it
                Log.d(TAG, "Using existing TCloud storage channel")
                return StorageInitResult(success = true, chatId = existingChatId)
            }

            // Try to find existing TCloud channel
            val existingResult = telegramClient.findTCloudChannel(Constants.TCLOUD_CHANNEL_TITLE)
            existingResult.onSuccess { chat ->
                if (chat != null) {
                    storageChatId = chat.id
                    Log.d(TAG, "Found existing TCloud storage channel")
                    return StorageInitResult(success = true, chatId = chat.id)
                }
            }

            // Create new private supergroup
            val createResult = telegramClient.createPrivateSupergroup(
                Constants.TCLOUD_CHANNEL_TITLE,
                Constants.TCLOUD_CHANNEL_DESCRIPTION
            )

            createResult.fold(
                onSuccess = { chat ->
                    storageChatId = chat.id
                    Log.d(TAG, "Created new TCloud storage channel")
                    StorageInitResult(success = true, chatId = chat.id)
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to create storage channel: ${error.message}")
                    StorageInitResult(success = false, error = error.message)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Storage initialization failed: ${e.message}")
            StorageInitResult(success = false, error = e.message)
        }
    }

    /**
     * Upload a file to the TCloud storage channel.
     */
    override suspend fun upload(
        localPath: String,
        remoteName: String,
        mimeType: String,
        metadata: Map<String, String>
    ): UploadResult {
        val chatId = storageChatId
        if (chatId == 0L) {
            return UploadResult(success = false, error = "Storage not initialized")
        }

        return try {
            // Build TCloud metadata caption
            val tcloudMetadata = TCloudObjectMetadata(
                objectId = metadata["objectId"] ?: "",
                filename = remoteName,
                mimeType = mimeType,
                size = metadata["size"]?.toLongOrNull() ?: 0,
                sha256 = metadata["sha256"] ?: "",
                chunkIndex = metadata["chunkIndex"]?.toIntOrNull() ?: 0,
                chunkCount = metadata["chunkCount"]?.toIntOrNull() ?: 1,
                createdAt = metadata["createdAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
                modifiedAt = metadata["modifiedAt"]?.toLongOrNull() ?: System.currentTimeMillis(),
                category = metadata["category"] ?: "",
                parentFolderId = metadata["parentFolderId"],
                duration = metadata["duration"]?.toLongOrNull(),
                width = metadata["width"]?.toIntOrNull(),
                height = metadata["height"]?.toIntOrNull()
            )
            val caption = TCloudMetadataEncoder.encode(tcloudMetadata)

            val result = telegramClient.sendDocument(chatId, localPath, caption, mimeType)
            result.fold(
                onSuccess = { message ->
                    // Extract file info from the sent message
                    val document = message.content as? TdApi.MessageDocument
                    val file = document?.document?.document

                    UploadResult(
                        success = true,
                        remoteObjectId = tcloudMetadata.objectId,
                        telegramMessageId = message.id,
                        telegramFileId = file?.id?.toString() ?: "",
                        telegramRemoteFileId = file?.remote?.id ?: "",
                        size = file?.size?.toLong() ?: 0
                    )
                },
                onFailure = { error ->
                    UploadResult(success = false, error = error.message)
                }
            )
        } catch (e: Exception) {
            UploadResult(success = false, error = e.message)
        }
    }

    /**
     * Download a file from Telegram.
     */
    override suspend fun download(remoteObjectId: String, localPath: String): DownloadResult {
        return try {
            // Look up the file by remote file ID
            val fileResult = telegramClient.getRemoteFile(remoteObjectId)
            fileResult.fold(
                onSuccess = { file ->
                    val downloadResult = telegramClient.downloadFile(
                        fileId = file.id,
                        priority = 10,
                        synchronous = true
                    )
                    downloadResult.fold(
                        onSuccess = { downloadedFile ->
                            DownloadResult(
                                success = true,
                                localPath = downloadedFile.local.path,
                                bytesDownloaded = downloadedFile.local.downloadedSize.toLong()
                            )
                        },
                        onFailure = { error ->
                            DownloadResult(success = false, error = error.message)
                        }
                    )
                },
                onFailure = { error ->
                    DownloadResult(success = false, error = error.message)
                }
            )
        } catch (e: Exception) {
            DownloadResult(success = false, error = e.message)
        }
    }

    /**
     * Download a byte range of a file (for streaming).
     */
    override suspend fun downloadRange(
        remoteObjectId: String,
        offset: Long,
        limit: Long,
        localPath: String
    ): DownloadResult {
        return try {
            val fileResult = telegramClient.getRemoteFile(remoteObjectId)
            fileResult.fold(
                onSuccess = { file ->
                    val downloadResult = telegramClient.downloadFile(
                        fileId = file.id,
                        priority = 10,
                        offset = offset,
                        limit = limit,
                        synchronous = true
                    )
                    downloadResult.fold(
                        onSuccess = { downloadedFile ->
                            DownloadResult(
                                success = true,
                                localPath = downloadedFile.local.path,
                                bytesDownloaded = downloadedFile.local.downloadedPrefixSize.toLong()
                            )
                        },
                        onFailure = { error ->
                            DownloadResult(success = false, error = error.message)
                        }
                    )
                },
                onFailure = { error ->
                    DownloadResult(success = false, error = error.message)
                }
            )
        } catch (e: Exception) {
            DownloadResult(success = false, error = e.message)
        }
    }

    /**
     * Delete a remote object by deleting its Telegram message.
     */
    override suspend fun delete(remoteObjectId: String): Boolean {
        // remoteObjectId maps to a message — we need the messageId
        // This will be called with the telegram message ID
        return try {
            val chatId = storageChatId
            val messageId = remoteObjectId.toLongOrNull() ?: return false
            val result = telegramClient.deleteMessages(chatId, longArrayOf(messageId))
            result.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get metadata for a remote object.
     */
    override suspend fun getMetadata(remoteObjectId: String): RemoteObjectMetadata? {
        return try {
            val chatId = storageChatId
            val messageId = remoteObjectId.toLongOrNull() ?: return null
            val result = telegramClient.getMessage(chatId, messageId)
            result.getOrNull()?.let { message ->
                messageToMetadata(message)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * List all TCloud objects in the storage channel.
     */
    override suspend fun listObjects(offset: Int, limit: Int): List<RemoteObjectMetadata> {
        val chatId = storageChatId
        if (chatId == 0L) return emptyList()

        val objects = mutableListOf<RemoteObjectMetadata>()
        var fromMessageId = 0L
        var remaining = limit

        while (remaining > 0) {
            val batchSize = minOf(remaining, 100)
            val result = telegramClient.getChatHistory(chatId, fromMessageId, 0, batchSize)

            result.fold(
                onSuccess = { messages ->
                    if (messages.messages == null || messages.messages.isEmpty()) {
                        return objects
                    }

                    for (message in messages.messages) {
                        val metadata = messageToMetadata(message)
                        if (metadata != null) {
                            objects.add(metadata)
                        }
                        fromMessageId = message.id
                    }
                    remaining -= messages.messages.size
                },
                onFailure = {
                    return objects
                }
            )
        }

        return objects
    }

    override suspend fun getUsedSpace(): Long {
        // Sum up sizes from listing
        val objects = listObjects(0, 10000)
        return objects.sumOf { it.size }
    }

    /**
     * Convert a Telegram message to RemoteObjectMetadata.
     */
    private fun messageToMetadata(message: TdApi.Message): RemoteObjectMetadata? {
        val document = message.content as? TdApi.MessageDocument ?: return null
        val caption = document.caption?.text ?: return null

        if (!TCloudMetadataEncoder.isTCloudObject(caption)) return null

        val file = document.document.document
        return RemoteObjectMetadata(
            remoteObjectId = message.id.toString(),
            messageId = message.id,
            filename = document.document.fileName,
            mimeType = document.document.mimeType,
            size = file.size.toLong(),
            caption = caption,
            timestamp = message.date.toLong() * 1000,
            fileId = file.id.toString(),
            remoteFileId = file.remote?.id ?: ""
        )
    }
}
