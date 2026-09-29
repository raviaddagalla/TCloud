package com.tcloud.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcloud.core.common.Constants
import com.tcloud.core.security.SecureStorage
import com.tcloud.telegram.AuthState
import com.tcloud.telegram.TelegramClient
import android.content.Context
import com.tcloud.sync.rebuild.CloudIndexRebuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStorage: SecureStorage,
    private val telegramClient: TelegramClient,
    private val cloudIndexRebuilder: CloudIndexRebuilder
) : ViewModel() {

    private val _uiState = MutableStateFlow(loadSettings())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private fun loadSettings(): SettingsUiState {
        return SettingsUiState(
            isConnected = telegramClient.authState.value is AuthState.Authorized,
            autoBackup = secureStorage.getBoolean(Constants.PREF_AUTO_BACKUP, true),
            backupPhotos = secureStorage.getBoolean(Constants.PREF_BACKUP_PHOTOS, true),
            backupVideos = secureStorage.getBoolean(Constants.PREF_BACKUP_VIDEOS, true),
            backupScreenshots = secureStorage.getBoolean(Constants.PREF_BACKUP_SCREENSHOTS, true),
            backupDocuments = secureStorage.getBoolean(Constants.PREF_BACKUP_DOCUMENTS, true),
            backupAudio = secureStorage.getBoolean(Constants.PREF_BACKUP_AUDIO, false),
            backupDownloads = secureStorage.getBoolean(Constants.PREF_BACKUP_DOWNLOADS, false),
            wifiOnly = secureStorage.getBoolean(Constants.PREF_WIFI_ONLY, true),
            mobileData = secureStorage.getBoolean(Constants.PREF_MOBILE_DATA, false),
            chargingOnly = secureStorage.getBoolean(Constants.PREF_CHARGING_ONLY, false),
            minBattery = secureStorage.getInt(Constants.PREF_MIN_BATTERY, 20)
        )
    }

    fun setAutoBackup(enabled: Boolean) {
        secureStorage.putBoolean(Constants.PREF_AUTO_BACKUP, enabled)
        _uiState.update { it.copy(autoBackup = enabled) }
    }

    fun setBackupCategory(label: String, enabled: Boolean) {
        val key = when (label) {
            "Photos" -> Constants.PREF_BACKUP_PHOTOS
            "Videos" -> Constants.PREF_BACKUP_VIDEOS
            "Screenshots" -> Constants.PREF_BACKUP_SCREENSHOTS
            "Documents" -> Constants.PREF_BACKUP_DOCUMENTS
            "Audio" -> Constants.PREF_BACKUP_AUDIO
            "Downloads" -> Constants.PREF_BACKUP_DOWNLOADS
            else -> return
        }
        secureStorage.putBoolean(key, enabled)
        _uiState.update {
            when (label) {
                "Photos" -> it.copy(backupPhotos = enabled)
                "Videos" -> it.copy(backupVideos = enabled)
                "Screenshots" -> it.copy(backupScreenshots = enabled)
                "Documents" -> it.copy(backupDocuments = enabled)
                "Audio" -> it.copy(backupAudio = enabled)
                "Downloads" -> it.copy(backupDownloads = enabled)
                else -> it
            }
        }
    }

    fun setWifiOnly(enabled: Boolean) {
        secureStorage.putBoolean(Constants.PREF_WIFI_ONLY, enabled)
        _uiState.update { it.copy(wifiOnly = enabled) }
    }

    fun setMobileData(enabled: Boolean) {
        secureStorage.putBoolean(Constants.PREF_MOBILE_DATA, enabled)
        _uiState.update { it.copy(mobileData = enabled) }
    }

    fun setChargingOnly(enabled: Boolean) {
        secureStorage.putBoolean(Constants.PREF_CHARGING_ONLY, enabled)
        _uiState.update { it.copy(chargingOnly = enabled) }
    }

    fun setMinBattery(value: Int) {
        secureStorage.putInt(Constants.PREF_MIN_BATTERY, value)
        _uiState.update { it.copy(minBattery = value) }
    }

    fun startRebuildIndex() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRebuilding = true, rebuildMessage = "Scanning cloud storage...") }
            cloudIndexRebuilder.rebuildIndex().collect { progress ->
                if (progress.isComplete) {
                    _uiState.update {
                        it.copy(
                            isRebuilding = false,
                            rebuildMessage = if (progress.error != null) "Failed: ${progress.error}"
                            else "Done! Discovered ${progress.filesDiscovered} files."
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            rebuildMessage = "Scanned ${progress.messagesScanned} messages (${progress.filesDiscovered} files found)"
                        )
                    }
                }
            }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            context.cacheDir.deleteRecursively()
            context.cacheDir.mkdirs()
            _uiState.update { it.copy(cacheSize = "0 B") }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            telegramClient.logout()
            secureStorage.clear()
        }
    }
}

data class SettingsUiState(
    val isConnected: Boolean = false,
    val autoBackup: Boolean = true,
    val backupPhotos: Boolean = true,
    val backupVideos: Boolean = true,
    val backupScreenshots: Boolean = true,
    val backupDocuments: Boolean = true,
    val backupAudio: Boolean = false,
    val backupDownloads: Boolean = false,
    val wifiOnly: Boolean = true,
    val mobileData: Boolean = false,
    val chargingOnly: Boolean = false,
    val minBattery: Int = 20,
    val isRebuilding: Boolean = false,
    val rebuildMessage: String? = null,
    val cacheSize: String = "Cache managed"
)
