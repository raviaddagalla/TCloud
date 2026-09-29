package com.tcloud.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcloud.core.common.FileCategory
import com.tcloud.core.database.dao.*
import com.tcloud.core.database.entity.FileEntry
import com.tcloud.core.database.entity.UploadTask
import com.tcloud.core.security.SecureStorage
import com.tcloud.core.common.Constants
import com.tcloud.telegram.TelegramClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fileEntryDao: FileEntryDao,
    private val uploadTaskDao: UploadTaskDao,
    private val telegramClient: TelegramClient,
    private val secureStorage: SecureStorage
) : ViewModel() {

    val recentFiles: StateFlow<List<FileEntry>> = fileEntryDao.observeRecentFiles(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBackedUpSize: StateFlow<Long> = fileEntryDao.observeBackedUpSize()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val pendingCount: StateFlow<Int> = fileEntryDao.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingSize: StateFlow<Long> = fileEntryDao.observePendingSize()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val failedCount: StateFlow<Int> = fileEntryDao.observeFailedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeUploadCount: StateFlow<Int> = uploadTaskDao.observeActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val uploadTasks: StateFlow<List<UploadTask>> = uploadTaskDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentUser = telegramClient.currentUser

    val syncStatus: StateFlow<SyncStatusInfo> = combine(
        pendingCount,
        failedCount,
        activeUploadCount
    ) { pending, failed, active ->
        when {
            active > 0 -> SyncStatusInfo.Syncing(pending + active)
            pending > 0 -> SyncStatusInfo.Pending(pending)
            failed > 0 -> SyncStatusInfo.HasFailures(failed)
            else -> SyncStatusInfo.AllBackedUp
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatusInfo.AllBackedUp)
}

sealed class SyncStatusInfo {
    data object AllBackedUp : SyncStatusInfo()
    data class Syncing(val count: Int) : SyncStatusInfo()
    data class Pending(val count: Int) : SyncStatusInfo()
    data class HasFailures(val count: Int) : SyncStatusInfo()
    data object WaitingForWifi : SyncStatusInfo()
    data object WaitingForCharging : SyncStatusInfo()
    data object BatteryTooLow : SyncStatusInfo()
}
