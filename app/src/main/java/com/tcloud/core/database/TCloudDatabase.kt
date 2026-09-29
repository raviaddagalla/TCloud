package com.tcloud.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.tcloud.core.common.FileCategory
import com.tcloud.core.common.FileStatus
import com.tcloud.core.common.TaskStatus
import com.tcloud.core.database.dao.*
import com.tcloud.core.database.entity.*

@Database(
    entities = [
        FileEntry::class,
        TelegramObject::class,
        ChunkEntry::class,
        UploadTask::class,
        DownloadTask::class,
        Folder::class,
        BackupRule::class,
        SyncState::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(TCloudTypeConverters::class)
abstract class TCloudDatabase : RoomDatabase() {
    abstract fun fileEntryDao(): FileEntryDao
    abstract fun telegramObjectDao(): TelegramObjectDao
    abstract fun chunkEntryDao(): ChunkEntryDao
    abstract fun uploadTaskDao(): UploadTaskDao
    abstract fun downloadTaskDao(): DownloadTaskDao
    abstract fun folderDao(): FolderDao
    abstract fun backupRuleDao(): BackupRuleDao
    abstract fun syncStateDao(): SyncStateDao
}

class TCloudTypeConverters {

    @TypeConverter
    fun fromFileStatus(status: FileStatus): String = status.name

    @TypeConverter
    fun toFileStatus(value: String): FileStatus = FileStatus.valueOf(value)

    @TypeConverter
    fun fromTaskStatus(status: TaskStatus): String = status.name

    @TypeConverter
    fun toTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun fromFileCategory(category: FileCategory): String = category.name

    @TypeConverter
    fun toFileCategory(value: String): FileCategory = FileCategory.valueOf(value)
}
