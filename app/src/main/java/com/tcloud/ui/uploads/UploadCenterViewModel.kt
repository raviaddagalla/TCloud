package com.tcloud.ui.uploads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcloud.core.database.dao.UploadTaskDao
import com.tcloud.core.database.entity.UploadTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UploadCenterViewModel @Inject constructor(
    private val uploadTaskDao: UploadTaskDao
) : ViewModel() {

    val tasks: StateFlow<List<UploadTask>> = uploadTaskDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeCount: StateFlow<Int> = uploadTaskDao.observeActiveCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun pauseAll() {
        viewModelScope.launch {
            uploadTaskDao.pauseAll()
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            uploadTaskDao.resumeAll()
        }
    }

    fun retryFailed() {
        viewModelScope.launch {
            uploadTaskDao.retryAllFailed()
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            uploadTaskDao.clearCompleted()
        }
    }
}
