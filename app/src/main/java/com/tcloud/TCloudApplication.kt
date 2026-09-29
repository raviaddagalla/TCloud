package com.tcloud

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TCloudApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // Sync progress channel
        val syncChannel = NotificationChannel(
            CHANNEL_SYNC,
            "Sync Progress",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows backup and sync progress"
            setShowBadge(false)
        }

        // Sync complete channel
        val completeChannel = NotificationChannel(
            CHANNEL_COMPLETE,
            "Backup Complete",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when backup is complete"
        }

        // Error channel
        val errorChannel = NotificationChannel(
            CHANNEL_ERROR,
            "Sync Errors",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies about sync failures"
        }

        manager.createNotificationChannels(listOf(syncChannel, completeChannel, errorChannel))
    }

    companion object {
        const val CHANNEL_SYNC = "tcloud_sync"
        const val CHANNEL_COMPLETE = "tcloud_complete"
        const val CHANNEL_ERROR = "tcloud_error"
    }
}
