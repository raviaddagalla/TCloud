package com.tcloud.core.storage

/**
 * Abstract cloud storage backend interface.
 * TCloud's domain layer programs against this interface, NOT Telegram directly.
 * This enables future replacement of the storage backend.
 */
interface CloudStorageBackend {

    /** Maximum single object size in bytes */
    val maxObjectSize: Long

    /** Whether the backend supports partial/range downloads */
    val supportsRangeDownloads: Boolean

    /** Whether the backend is currently authenticated */
    suspend fun isAuthenticated(): Boolean

    /** Upload a file to the cloud storage */
    suspend fun upload(
        localPath: String,
        remoteName: String,
        mimeType: String,
        metadata: Map<String, String> = emptyMap()
    ): UploadResult

    /** Download a file from cloud storage */
    suspend fun download(
        remoteObjectId: String,
        localPath: String
    ): DownloadResult

    /** Download a partial range of a file */
    suspend fun downloadRange(
        remoteObjectId: String,
        offset: Long,
        limit: Long,
        localPath: String
    ): DownloadResult

    /** Delete a remote object */
    suspend fun delete(remoteObjectId: String): Boolean

    /** Get metadata for a remote object */
    suspend fun getMetadata(remoteObjectId: String): RemoteObjectMetadata?

    /** List all objects in the storage */
    suspend fun listObjects(offset: Int = 0, limit: Int = 100): List<RemoteObjectMetadata>

    /** Create the storage location if it doesn't exist */
    suspend fun initializeStorage(): StorageInitResult

    /** Get total size used */
    suspend fun getUsedSpace(): Long
}

data class UploadResult(
    val success: Boolean,
    val remoteObjectId: String = "",
    val telegramMessageId: Long = 0,
    val telegramFileId: String = "",
    val telegramRemoteFileId: String = "",
    val size: Long = 0,
    val error: String? = null
)

data class DownloadResult(
    val success: Boolean,
    val localPath: String = "",
    val bytesDownloaded: Long = 0,
    val error: String? = null
)

data class RemoteObjectMetadata(
    val remoteObjectId: String,
    val messageId: Long,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val caption: String,
    val timestamp: Long,
    val fileId: String,
    val remoteFileId: String
)

data class StorageInitResult(
    val success: Boolean,
    val chatId: Long = 0,
    val error: String? = null
)
