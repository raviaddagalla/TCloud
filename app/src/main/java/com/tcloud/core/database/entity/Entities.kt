package com.tcloud.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tcloud.core.common.FileCategory
import com.tcloud.core.common.FileStatus
import com.tcloud.core.common.TaskStatus

/**
 * Represents a logical file in TCloud's filesystem.
 * This is the authoritative file index — NOT Telegram messages.
 */
@Entity(
    tableName = "file_entries",
    indices = [
        Index("sha256"),
        Index("parentFolderId"),
        Index("status"),
        Index("mimeType"),
        Index("name"),
        Index("modifiedAt"),
        Index("category")
    ]
)
data class FileEntry(
    @PrimaryKey
    val id: String, // UUID-based logical file ID
    val name: String,
    val path: String, // Original local path
    val mimeType: String,
    val size: Long,
    val createdAt: Long,
    val modifiedAt: Long,
    val localUri: String? = null,
    val sha256: String? = null,
    val status: FileStatus = FileStatus.LOCAL_ONLY,
    val isFolder: Boolean = false,
    val parentFolderId: String? = null,
    val thumbnailUri: String? = null,
    val duration: Long? = null, // For audio/video in ms
    val width: Int? = null,
    val height: Int? = null,
    val category: FileCategory = FileCategory.OTHER,
    val isStarred: Boolean = false,
    val isOffline: Boolean = false,
    val lastAccessedAt: Long = System.currentTimeMillis()
)

/**
 * Represents a Telegram-stored object (message + file).
 * Links a logical FileEntry to its physical Telegram representation.
 */
@Entity(
    tableName = "telegram_objects",
    indices = [
        Index("fileEntryId"),
        Index("telegramChatId"),
        Index("telegramMessageId")
    ],
    foreignKeys = [
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["id"],
            childColumns = ["fileEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TelegramObject(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileEntryId: String,
    val telegramChatId: Long,
    val telegramMessageId: Long,
    val telegramFileId: String, // Local file_id from TDLib
    val telegramRemoteFileId: String, // Remote file unique_id
    val chunkIndex: Int = 0,
    val chunkCount: Int = 1,
    val size: Long,
    val checksum: String? = null
)

/**
 * Represents a chunk of a large file.
 */
@Entity(
    tableName = "chunk_entries",
    indices = [
        Index("fileEntryId"),
        Index("chunkIndex")
    ],
    foreignKeys = [
        ForeignKey(
            entity = FileEntry::class,
            parentColumns = ["id"],
            childColumns = ["fileEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ChunkEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileEntryId: String,
    val chunkIndex: Int,
    val chunkCount: Int,
    val chunkSize: Long,
    val offsetStart: Long, // Byte offset in original file
    val offsetEnd: Long,
    val sha256: String? = null,
    val telegramMessageId: Long = 0,
    val telegramFileId: String = "",
    val telegramRemoteFileId: String = "",
    val status: TaskStatus = TaskStatus.QUEUED,
    val localTempPath: String? = null
)

/**
 * Persistent upload/download task in the sync queue.
 */
@Entity(
    tableName = "upload_tasks",
    indices = [
        Index("fileEntryId"),
        Index("status"),
        Index("createdAt")
    ]
)
data class UploadTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileEntryId: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Float = 0f,
    val bytesUploaded: Long = 0,
    val totalBytes: Long = 0,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Persistent download task.
 */
@Entity(
    tableName = "download_tasks",
    indices = [
        Index("fileEntryId"),
        Index("status")
    ]
)
data class DownloadTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileEntryId: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Float = 0f,
    val bytesDownloaded: Long = 0,
    val totalBytes: Long = 0,
    val currentChunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val destinationPath: String = "",
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Folder in TCloud's virtual filesystem.
 */
@Entity(
    tableName = "folders",
    indices = [Index("parentId")]
)
data class Folder(
    @PrimaryKey
    val id: String,
    val name: String,
    val parentId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val fileCount: Int = 0
)

/**
 * Backup rule for a file category.
 */
@Entity(tableName = "backup_rules")
data class BackupRule(
    @PrimaryKey
    val category: FileCategory,
    val enabled: Boolean = false,
    val customFolderPath: String? = null
)

/**
 * Sync state metadata.
 */
@Entity(tableName = "sync_state")
data class SyncState(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
