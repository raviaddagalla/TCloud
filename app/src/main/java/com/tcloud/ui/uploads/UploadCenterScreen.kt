package com.tcloud.ui.uploads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcloud.core.common.TaskStatus
import com.tcloud.core.common.formatFileSize
import com.tcloud.core.database.entity.UploadTask
import com.tcloud.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadCenterScreen(
    viewModel: UploadCenterViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val activeCount by viewModel.activeCount.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Uploads", fontWeight = FontWeight.SemiBold)
                    if (activeCount > 0) {
                        Text(
                            "$activeCount active",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                if (tasks.isNotEmpty()) {
                    IconButton(onClick = viewModel::pauseAll) {
                        Icon(Icons.Filled.PauseCircle, contentDescription = "Pause all")
                    }
                    IconButton(onClick = viewModel::resumeAll) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = "Resume all")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TCloudSuccess.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No uploads",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Your upload queue is empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Failed tasks action bar
            val failedTasks = tasks.filter { it.status == TaskStatus.FAILED }
            if (failedTasks.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = TCloudError.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = TCloudError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${failedTasks.size} failed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TCloudError,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::retryFailed) {
                            Text("Retry all", color = TCloudError)
                        }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    UploadTaskItem(task = task)
                }
            }
        }
    }
}

@Composable
private fun UploadTaskItem(task: UploadTask) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status icon
                val (icon, color) = when (task.status) {
                    TaskStatus.COMPLETED -> Icons.Filled.CheckCircle to TCloudSuccess
                    TaskStatus.UPLOADING -> Icons.Filled.CloudUpload to TCloudSecondary
                    TaskStatus.FAILED -> Icons.Filled.ErrorOutline to TCloudError
                    TaskStatus.PAUSED -> Icons.Filled.PauseCircle to TCloudWarning
                    TaskStatus.QUEUED -> Icons.Filled.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
                    TaskStatus.HASHING -> Icons.Filled.Tag to TCloudPrimary
                    TaskStatus.RETRYING -> Icons.Filled.Refresh to TCloudWarning
                    else -> Icons.Filled.HourglassTop to MaterialTheme.colorScheme.onSurfaceVariant
                }

                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.fileEntryId.take(16) + "…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${task.bytesUploaded.formatFileSize()} / ${task.totalBytes.formatFileSize()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    task.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            }

            if (task.status == TaskStatus.UPLOADING || task.status == TaskStatus.HASHING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = TCloudPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            task.lastError?.let { error ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = TCloudError.copy(alpha = 0.8f)
                )
            }
        }
    }
}
