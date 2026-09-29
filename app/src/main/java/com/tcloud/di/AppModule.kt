package com.tcloud.di

import com.tcloud.core.storage.CloudStorageBackend
import com.tcloud.telegram.TelegramClient
import com.tcloud.telegram.storage.TelegramStorageBackend
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideCloudStorageBackend(
        telegramStorageBackend: TelegramStorageBackend
    ): CloudStorageBackend = telegramStorageBackend
}
