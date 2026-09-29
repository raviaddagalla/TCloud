package com.tcloud.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcloud.core.common.Constants
import com.tcloud.core.common.FileCategory
import com.tcloud.core.database.dao.BackupRuleDao
import com.tcloud.core.database.entity.BackupRule
import com.tcloud.core.security.SecureStorage
import com.tcloud.core.storage.CloudStorageBackend
import com.tcloud.telegram.AuthState
import com.tcloud.telegram.TelegramClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val telegramClient: TelegramClient,
    private val cloudStorage: CloudStorageBackend,
    private val secureStorage: SecureStorage,
    private val backupRuleDao: BackupRuleDao
) : ViewModel() {

    val authState = telegramClient.authState
    val currentUser = telegramClient.currentUser

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    init {
        telegramClient.initialize()
    }

    fun updatePhoneNumber(phone: String) {
        _phoneNumber.value = phone
    }

    fun updateCode(newCode: String) {
        _code.value = newCode
    }

    fun updatePassword(pass: String) {
        _password.value = pass
    }

    fun submitPhoneNumber() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = telegramClient.sendPhoneNumber(_phoneNumber.value)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
            )
        }
    }

    fun submitCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = telegramClient.sendCode(_code.value)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
            )
        }
    }

    fun submitPassword() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = telegramClient.sendPassword(_password.value)
            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message)
                    }
                }
            )
        }
    }

    fun initializeStorage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, storageStatus = "Setting up storage...") }
            val result = cloudStorage.initializeStorage()
            if (result.success) {
                _uiState.update {
                    it.copy(isLoading = false, storageStatus = "Storage ready", storageInitialized = true)
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, error = result.error ?: "Failed to initialize storage")
                }
            }
        }
    }

    fun updateBackupCategory(category: FileCategory, enabled: Boolean) {
        _uiState.update { state ->
            val updated = state.backupCategories.toMutableMap()
            updated[category] = enabled
            state.copy(backupCategories = updated)
        }
    }

    fun updateWifiOnly(enabled: Boolean) {
        _uiState.update { it.copy(wifiOnly = enabled) }
    }

    fun updateMobileData(enabled: Boolean) {
        _uiState.update { it.copy(mobileData = enabled) }
    }

    fun updateChargingOnly(enabled: Boolean) {
        _uiState.update { it.copy(chargingOnly = enabled) }
    }

    fun updateMinBattery(value: Int) {
        _uiState.update { it.copy(minBattery = value) }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            // Save backup rules
            val rules = _uiState.value.backupCategories.map { (category, enabled) ->
                BackupRule(category = category, enabled = enabled)
            }
            backupRuleDao.insertAll(rules)

            // Save settings
            secureStorage.putBoolean(Constants.PREF_AUTO_BACKUP, true)
            secureStorage.putBoolean(Constants.PREF_WIFI_ONLY, _uiState.value.wifiOnly)
            secureStorage.putBoolean(Constants.PREF_MOBILE_DATA, _uiState.value.mobileData)
            secureStorage.putBoolean(Constants.PREF_CHARGING_ONLY, _uiState.value.chargingOnly)
            secureStorage.putInt(Constants.PREF_MIN_BATTERY, _uiState.value.minBattery)
            secureStorage.putBoolean(Constants.PREF_ONBOARDING_COMPLETE, true)

            _uiState.update { it.copy(onboardingComplete = true) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun isOnboardingComplete(): Boolean {
        return secureStorage.getBoolean(Constants.PREF_ONBOARDING_COMPLETE)
    }
}

data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val storageStatus: String = "",
    val storageInitialized: Boolean = false,
    val onboardingComplete: Boolean = false,
    val backupCategories: Map<FileCategory, Boolean> = mapOf(
        FileCategory.PHOTOS to true,
        FileCategory.VIDEOS to true,
        FileCategory.SCREENSHOTS to true,
        FileCategory.DOCUMENTS to true,
        FileCategory.AUDIO to false,
        FileCategory.DOWNLOADS to false
    ),
    val wifiOnly: Boolean = true,
    val mobileData: Boolean = false,
    val chargingOnly: Boolean = false,
    val minBattery: Int = 20
)
