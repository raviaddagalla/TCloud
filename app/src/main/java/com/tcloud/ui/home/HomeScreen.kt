package com.tcloud.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcloud.core.common.FileCategory
import com.tcloud.core.common.formatFileSize
import com.tcloud.core.common.formatDateTime
import com.tcloud.core.database.entity.FileEntry
import com.tcloud.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToFiles: (FileCategory?) -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToUploads: () -> Unit = {},
    onFileClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val recentFiles by viewModel.recentFiles.collectAsStateWithLifecycle()
    val backedUpSize by viewModel.totalBackedUpSize.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val pendingSize by viewModel.pendingSize.collectAsStateWithLifecycle()
    val failedCount by viewModel.failedCount.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Header
        item {
            HomeHeader(onSearchClick = onNavigateToSearch)
        }

        // Storage card
        item {
            StorageCard(
                backedUpSize = backedUpSize,
                pendingSize = pendingSize,
                failedCount = failedCount
            )
        }

        // Sync status
        item {
            SyncStatusCard(syncStatus = syncStatus, onUploadClick = onNavigateToUploads)
        }

        // Quick access
        item {
            SectionTitle("Quick Access")
        }

        item {
            QuickAccessRow(onNavigateToFiles = onNavigateToFiles)
        }

        // Recent files
        if (recentFiles.isNotEmpty()) {
            item {
                SectionTitle("Recent Files")
            }

            items(recentFiles.take(10)) { file ->
                RecentFileItem(
                    file = file,
                    onClick = { onFileClick(file.id) }
                )
            }
        } else {
            // Empty state
            item {
                EmptyState()
            }
        }
    }
}

@Composable
private fun HomeHeader(onSearchClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "TCloud",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Your personal cloud",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSearchClick) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StorageCard(backedUpSize: Long, pendingSize: Long, failedCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(TCloudPrimary, TCloudSecondaryDark)
                    ),
                    RoundedCornerShape(20.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "Cloud Storage",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            backedUpSize.formatFileSize(),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "backed up",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White.copy(alpha = 0.3f)
                    )
                }

                if (pendingSize > 0 || failedCount > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (pendingSize > 0) {
                            StorageStat("Pending", pendingSize.formatFileSize())
                        }
                        if (failedCount > 0) {
                            StorageStat("Failed", "$failedCount files")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SyncStatusCard(syncStatus: SyncStatusInfo, onUploadClick: () -> Unit) {
    val (icon, text, color) = when (syncStatus) {
        is SyncStatusInfo.AllBackedUp -> Triple(Icons.Filled.CheckCircle, "Everything backed up", TCloudSuccess)
        is SyncStatusInfo.Syncing -> Triple(Icons.Filled.CloudUpload, "Backing up ${syncStatus.count} files…", TCloudSecondary)
        is SyncStatusInfo.Pending -> Triple(Icons.Filled.Schedule, "${syncStatus.count} files pending", TCloudWarning)
        is SyncStatusInfo.HasFailures -> Triple(Icons.Filled.ErrorOutline, "${syncStatus.count} files failed", TCloudError)
        is SyncStatusInfo.WaitingForWifi -> Triple(Icons.Filled.WifiOff, "Waiting for Wi-Fi", TCloudWarning)
        is SyncStatusInfo.WaitingForCharging -> Triple(Icons.Filled.BatteryChargingFull, "Waiting for charging", TCloudWarning)
        is SyncStatusInfo.BatteryTooLow -> Triple(Icons.Filled.BatteryAlert, "Battery too low", TCloudWarning)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable { onUploadClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = color.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun QuickAccessRow(onNavigateToFiles: (FileCategory?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { QuickAccessChip("📸", "Photos", PhotoColor) { onNavigateToFiles(FileCategory.PHOTOS) } }
        item { QuickAccessChip("🎥", "Videos", VideoColor) { onNavigateToFiles(FileCategory.VIDEOS) } }
        item { QuickAccessChip("📄", "Documents", DocumentColor) { onNavigateToFiles(FileCategory.DOCUMENTS) } }
        item { QuickAccessChip("🎵", "Audio", AudioColor) { onNavigateToFiles(FileCategory.AUDIO) } }
        item { QuickAccessChip("📦", "Archives", ArchiveColor) { onNavigateToFiles(FileCategory.ARCHIVES) } }
        item { QuickAccessChip("📁", "All Files", OtherColor) { onNavigateToFiles(null) } }
    }
}

@Composable
private fun QuickAccessChip(emoji: String, label: String, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun RecentFileItem(file: FileEntry, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon
            val (icon, color) = getFileIcon(file.category)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${file.size.formatFileSize()} · ${file.modifiedAt.formatDateTime()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Status icon
            val statusIcon = when (file.status) {
                com.tcloud.core.common.FileStatus.BACKED_UP -> Icons.Filled.CloudDone to TCloudSuccess
                com.tcloud.core.common.FileStatus.UPLOADING -> Icons.Filled.CloudUpload to TCloudSecondary
                com.tcloud.core.common.FileStatus.FAILED -> Icons.Filled.ErrorOutline to TCloudError
                com.tcloud.core.common.FileStatus.QUEUED -> Icons.Filled.Schedule to TCloudWarning
                else -> Icons.Filled.CloudOff to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Icon(
                statusIcon.first,
                contentDescription = null,
                tint = statusIcon.second,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun getFileIcon(category: FileCategory): Pair<ImageVector, Color> {
    return when (category) {
        FileCategory.PHOTOS -> Icons.Filled.Image to PhotoColor
        FileCategory.VIDEOS -> Icons.Filled.Videocam to VideoColor
        FileCategory.DOCUMENTS -> Icons.Filled.Description to DocumentColor
        FileCategory.AUDIO -> Icons.Filled.MusicNote to AudioColor
        FileCategory.ARCHIVES -> Icons.Filled.FolderZip to ArchiveColor
        FileCategory.SCREENSHOTS -> Icons.Filled.Screenshot to PhotoColor
        FileCategory.DOWNLOADS -> Icons.Filled.Download to OtherColor
        FileCategory.OTHER -> Icons.Filled.InsertDriveFile to OtherColor
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Outlined.CloudUpload,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "No files yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Your backed up files will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}
