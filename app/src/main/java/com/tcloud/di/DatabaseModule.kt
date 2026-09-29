package com.tcloud.di

import android.content.Context
import androidx.room.Room
import com.tcloud.core.common.Constants
import com.tcloud.core.database.TCloudDatabase
import com.tcloud.core.database.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TCloudDatabase {
        return Room.databaseBuilder(
            context,
            TCloudDatabase::class.java,
            Constants.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides fun provideFileEntryDao(db: TCloudDatabase): FileEntryDao = db.fileEntryDao()
    @Provides fun provideTelegramObjectDao(db: TCloudDatabase): TelegramObjectDao = db.telegramObjectDao()
    @Provides fun provideChunkEntryDao(db: TCloudDatabase): ChunkEntryDao = db.chunkEntryDao()
    @Provides fun provideUploadTaskDao(db: TCloudDatabase): UploadTaskDao = db.uploadTaskDao()
    @Provides fun provideDownloadTaskDao(db: TCloudDatabase): DownloadTaskDao = db.downloadTaskDao()
    @Provides fun provideFolderDao(db: TCloudDatabase): FolderDao = db.folderDao()
    @Provides fun provideBackupRuleDao(db: TCloudDatabase): BackupRuleDao = db.backupRuleDao()
    @Provides fun provideSyncStateDao(db: TCloudDatabase): SyncStateDao = db.syncStateDao()
}
