package com.tcloud.core.database.dao

import androidx.room.*
import com.tcloud.core.common.FileCategory
import com.tcloud.core.common.FileStatus
import com.tcloud.core.common.TaskStatus
import com.tcloud.core.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FileEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FileEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<FileEntry>)

    @Update
    suspend fun update(entry: FileEntry)

    @Delete
    suspend fun delete(entry: FileEntry)

    @Query("DELETE FROM file_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM file_entries WHERE id = :id")
    suspend fun getById(id: String): FileEntry?

    @Query("SELECT * FROM file_entries WHERE id = :id")
    fun observeById(id: String): Flow<FileEntry?>

    @Query("SELECT * FROM file_entries WHERE sha256 = :sha256 LIMIT 1")
    suspend fun findBySha256(sha256: String): FileEntry?

    @Query("SELECT * FROM file_entries WHERE path = :path LIMIT 1")
    suspend fun findByPath(path: String): FileEntry?

    @Query("SELECT * FROM file_entries WHERE isFolder = 0 ORDER BY modifiedAt DESC")
    fun observeAllFiles(): Flow<List<FileEntry>>

    @Query("SELECT * FROM file_entries WHERE isFolder = 0 ORDER BY modifiedAt DESC LIMIT :limit")
    fun observeRecentFiles(limit: Int = 20): Flow<List<FileEntry>>

    @Query("SELECT * FROM file_entries WHERE category = :category AND isFolder = 0 ORDER BY modifiedAt DESC")
    fun observeByCategory(category: FileCategory): Flow<List<FileEntry>>

    @Query("SELECT * FROM file_entries WHERE parentFolderId = :folderId ORDER BY isFolder DESC, name ASC")
    fun observeByFolder(folderId: String?): Flow<List<FileEntry>>

    @Query("SELECT * FROM file_entries WHERE isStarred = 1 ORDER BY modifiedAt DESC")
    fun observeStarred(): Flow<List<FileEntry>>

    @Query("SELECT * FROM file_entries WHERE isOffline = 1 ORDER BY modifiedAt DESC")
    fun observeOffline(): Flow<List<FileEntry>>

    @Query("SELECT * FROM file_entries WHERE status = :status ORDER BY modifiedAt DESC")
    fun observeByStatus(status: FileStatus): Flow<List<FileEntry>>

    @Query("SELECT * FROM file_entries WHERE name LIKE '%' || :query || '%' ORDER BY modifiedAt DESC")
    fun search(query: String): Flow<List<FileEntry>>

    @Query("SELECT COUNT(*) FROM file_entries WHERE isFolder = 0")
    fun observeTotalFileCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size), 0) FROM file_entries WHERE status = 'BACKED_UP' AND isFolder = 0")
    fun observeBackedUpSize(): Flow<Long>

    @Query("SELECT COALESCE(SUM(size), 0) FROM file_entries WHERE isFolder = 0")
    fun observeTotalSize(): Flow<Long>

    @Query("SELECT COUNT(*) FROM file_entries WHERE status = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    @Query("UPDATE file_entries SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: FileStatus)

    @Query("UPDATE file_entries SET isStarred = :starred WHERE id = :id")
    suspend fun updateStarred(id: String, starred: Boolean)

    @Query("UPDATE file_entries SET isOffline = :offline WHERE id = :id")
    suspend fun updateOffline(id: String, offline: Boolean)

    @Query("UPDATE file_entries SET thumbnailUri = :uri WHERE id = :id")
    suspend fun updateThumbnail(id: String, uri: String)

    @Query("SELECT * FROM file_entries WHERE status IN ('LOCAL_ONLY', 'MODIFIED_SINCE_BACKUP') AND isFolder = 0")
    suspend fun getPendingBackupFiles(): List<FileEntry>

    @Query("SELECT COUNT(*) FROM file_entries WHERE status IN ('LOCAL_ONLY', 'MODIFIED_SINCE_BACKUP', 'QUEUED', 'UPLOADING') AND isFolder = 0")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(size), 0) FROM file_entries WHERE status IN ('LOCAL_ONLY', 'MODIFIED_SINCE_BACKUP', 'QUEUED', 'UPLOADING') AND isFolder = 0")
    fun observePendingSize(): Flow<Long>
}

@Dao
interface TelegramObjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(obj: TelegramObject)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(objects: List<TelegramObject>)

    @Delete
    suspend fun delete(obj: TelegramObject)

    @Query("SELECT * FROM telegram_objects WHERE fileEntryId = :fileEntryId ORDER BY chunkIndex ASC")
    suspend fun getByFileEntryId(fileEntryId: String): List<TelegramObject>

    @Query("SELECT * FROM telegram_objects WHERE fileEntryId = :fileEntryId AND chunkIndex = :chunkIndex LIMIT 1")
    suspend fun getByFileEntryIdAndChunk(fileEntryId: String, chunkIndex: Int): TelegramObject?

    @Query("SELECT * FROM telegram_objects WHERE fileEntryId = :fileEntryId ORDER BY chunkIndex ASC")
    fun observeByFileEntryId(fileEntryId: String): Flow<List<TelegramObject>>

    @Query("SELECT * FROM telegram_objects WHERE telegramMessageId = :messageId LIMIT 1")
    suspend fun getByMessageId(messageId: Long): TelegramObject?

    @Query("DELETE FROM telegram_objects WHERE fileEntryId = :fileEntryId")
    suspend fun deleteByFileEntryId(fileEntryId: String)

    @Query("SELECT COUNT(*) FROM telegram_objects WHERE fileEntryId = :fileEntryId")
    suspend fun getChunkCount(fileEntryId: String): Int
}

@Dao
interface ChunkEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(chunk: ChunkEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chunks: List<ChunkEntry>)

    @Update
    suspend fun update(chunk: ChunkEntry)

    @Query("SELECT * FROM chunk_entries WHERE fileEntryId = :fileEntryId ORDER BY chunkIndex ASC")
    suspend fun getByFileEntryId(fileEntryId: String): List<ChunkEntry>

    @Query("SELECT * FROM chunk_entries WHERE fileEntryId = :fileEntryId ORDER BY chunkIndex ASC")
    fun observeByFileEntryId(fileEntryId: String): Flow<List<ChunkEntry>>

    @Query("SELECT * FROM chunk_entries WHERE fileEntryId = :fileEntryId AND status != 'COMPLETED' ORDER BY chunkIndex ASC LIMIT 1")
    suspend fun getNextPendingChunk(fileEntryId: String): ChunkEntry?

    @Query("UPDATE chunk_entries SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus)

    @Query("DELETE FROM chunk_entries WHERE fileEntryId = :fileEntryId")
    suspend fun deleteByFileEntryId(fileEntryId: String)

    @Query("SELECT COUNT(*) FROM chunk_entries WHERE fileEntryId = :fileEntryId AND status = 'COMPLETED'")
    suspend fun getCompletedChunkCount(fileEntryId: String): Int
}

@Dao
interface UploadTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: UploadTask): Long

    @Update
    suspend fun update(task: UploadTask)

    @Delete
    suspend fun delete(task: UploadTask)

    @Query("SELECT * FROM upload_tasks WHERE id = :id")
    suspend fun getById(id: Long): UploadTask?

    @Query("SELECT * FROM upload_tasks WHERE fileEntryId = :fileEntryId LIMIT 1")
    suspend fun getByFileEntryId(fileEntryId: String): UploadTask?

    @Query("SELECT * FROM upload_tasks ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<UploadTask>>

    @Query("SELECT * FROM upload_tasks WHERE status IN ('QUEUED', 'RETRYING') ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getNextPending(limit: Int = 1): List<UploadTask>

    @Query("SELECT * FROM upload_tasks WHERE status = 'UPLOADING'")
    suspend fun getActive(): List<UploadTask>

    @Query("SELECT * FROM upload_tasks WHERE status = 'FAILED'")
    fun observeFailed(): Flow<List<UploadTask>>

    @Query("SELECT COUNT(*) FROM upload_tasks WHERE status IN ('QUEUED', 'UPLOADING', 'PREPARING', 'HASHING', 'RETRYING')")
    fun observeActiveCount(): Flow<Int>

    @Query("UPDATE upload_tasks SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus, now: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET progress = :progress, bytesUploaded = :bytes, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, bytes: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE upload_tasks SET status = 'PAUSED' WHERE status IN ('QUEUED', 'UPLOADING', 'PREPARING', 'HASHING', 'RETRYING')")
    suspend fun pauseAll()

    @Query("UPDATE upload_tasks SET status = 'QUEUED' WHERE status = 'PAUSED'")
    suspend fun resumeAll()

    @Query("UPDATE upload_tasks SET status = 'QUEUED', retryCount = retryCount + 1 WHERE status = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("DELETE FROM upload_tasks WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}

@Dao
interface DownloadTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: DownloadTask): Long

    @Update
    suspend fun update(task: DownloadTask)

    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTask>>

    @Query("SELECT * FROM download_tasks WHERE fileEntryId = :fileEntryId LIMIT 1")
    suspend fun getByFileEntryId(fileEntryId: String): DownloadTask?

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'RETRYING') ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getNextPending(limit: Int = 1): List<DownloadTask>

    @Query("UPDATE download_tasks SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TaskStatus, now: Long = System.currentTimeMillis())

    @Query("UPDATE download_tasks SET progress = :progress, bytesDownloaded = :bytes, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: Long, progress: Float, bytes: Long, now: Long = System.currentTimeMillis())
}

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder)

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeAll(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentId = :parentId ORDER BY name ASC")
    fun observeByParent(parentId: String?): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: String): Folder?
}

@Dao
interface BackupRuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: BackupRule)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<BackupRule>)

    @Query("SELECT * FROM backup_rules")
    fun observeAll(): Flow<List<BackupRule>>

    @Query("SELECT * FROM backup_rules WHERE category = :category")
    suspend fun getByCategory(category: FileCategory): BackupRule?

    @Query("SELECT * FROM backup_rules WHERE enabled = 1")
    suspend fun getEnabledRules(): List<BackupRule>
}

@Dao
interface SyncStateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: SyncState)

    @Query("SELECT value FROM sync_state WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Query("DELETE FROM sync_state WHERE `key` = :key")
    suspend fun delete(key: String)
}
