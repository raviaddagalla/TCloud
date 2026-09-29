package com.tcloud.ui.viewer

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tcloud.core.common.FileCategory
import com.tcloud.core.database.dao.FileEntryDao
import com.tcloud.core.database.entity.FileEntry
import com.tcloud.sync.restore.RestoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class FileViewerViewModel @Inject constructor(
    private val fileEntryDao: FileEntryDao,
    private val restoreManager: RestoreManager
) : ViewModel() {

    private val _fileEntry = MutableStateFlow<FileEntry?>(null)
    val fileEntry: StateFlow<FileEntry?> = _fileEntry.asStateFlow()

    fun loadFile(fileId: String) {
        viewModelScope.launch {
            _fileEntry.value = fileEntryDao.getById(fileId)
        }
    }

    fun downloadFile(fileId: String, context: Context) {
        viewModelScope.launch {
            Toast.makeText(context, "Starting download...", Toast.LENGTH_SHORT).show()
            val result = restoreManager.downloadFile(fileId, makeOffline = true)
            result.fold(
                onSuccess = { file ->
                    Toast.makeText(context, "Saved to ${file.name}", Toast.LENGTH_LONG).show()
                    _fileEntry.value = fileEntryDao.getById(fileId)
                },
                onFailure = { err ->
                    Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    fun shareFile(fileEntry: FileEntry, context: Context) {
        val path = fileEntry.localUri
        if (path.isNullOrEmpty() || !File(path).exists()) {
            Toast.makeText(context, "Please download file first to share", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val file = File(path)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = fileEntry.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share file"))
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun FileViewerHost(
    fileId: String,
    onNavigateBack: () -> Unit,
    viewModel: FileViewerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val fileEntry by viewModel.fileEntry.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(fileId) {
        viewModel.loadFile(fileId)
    }

    val entry = fileEntry
    if (entry == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    when (entry.category) {
        FileCategory.PHOTOS, FileCategory.SCREENSHOTS -> {
            ImageViewerScreen(
                fileEntry = entry,
                onNavigateBack = onNavigateBack,
                onDownload = { viewModel.downloadFile(it.id, context) },
                onShare = { viewModel.shareFile(it, context) }
            )
        }
        FileCategory.VIDEOS -> {
            VideoPlayerScreen(
                fileEntry = entry,
                onNavigateBack = onNavigateBack,
                onDownload = { viewModel.downloadFile(it.id, context) },
                onShare = { viewModel.shareFile(it, context) }
            )
        }
        FileCategory.AUDIO -> {
            AudioPlayerScreen(
                fileEntry = entry,
                onNavigateBack = onNavigateBack,
                onDownload = { viewModel.downloadFile(it.id, context) }
            )
        }
        else -> {
            DocumentViewerScreen(
                fileEntry = entry,
                onNavigateBack = onNavigateBack,
                onDownload = { viewModel.downloadFile(it.id, context) }
            )
        }
    }
}
