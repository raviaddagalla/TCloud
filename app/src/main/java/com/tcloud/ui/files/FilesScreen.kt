package com.tcloud.ui.files

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcloud.core.common.FileCategory
import com.tcloud.core.common.FileStatus
import com.tcloud.core.common.formatFileSize
import com.tcloud.core.common.formatDateTime
import com.tcloud.core.database.entity.FileEntry
import com.tcloud.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    category: FileCategory? = null,
    isSearchMode: Boolean = false,
    onFileClick: (String) -> Unit = {},
    onBack: () -> Unit = {},
    viewModel: FilesViewModel = hiltViewModel()
) {
    val files by viewModel.files.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    LaunchedEffect(category) {
        viewModel.setCategory(category)
    }

    val title = when {
        isSearchMode -> "Search"
        category != null -> category.name.lowercase()
            .replaceFirstChar { it.uppercase() }
        else -> "All Files"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                if (category != null || isSearchMode) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            actions = {
                var showSortMenu by remember { mutableStateOf(false) }
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Filled.Sort, contentDescription = "Sort")
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Name") },
                        onClick = { viewModel.setSortOrder(SortOrder.NAME_ASC); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Date modified") },
                        onClick = { viewModel.setSortOrder(SortOrder.DATE_DESC); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Size") },
                        onClick = { viewModel.setSortOrder(SortOrder.SIZE_DESC); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Type") },
                        onClick = { viewModel.setSortOrder(SortOrder.TYPE_ASC); showSortMenu = false }
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Search bar
        if (isSearchMode || category == null) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search files…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TCloudPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        }

        // File list
        if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (searchQuery.isNotBlank()) "No results found" else "No files",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(files, key = { it.id }) { file ->
                    FileListItem(
                        file = file,
                        onClick = { onFileClick(file.id) },
                        onStarToggle = { viewModel.toggleStar(file.id, !file.isStarred) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileListItem(
    file: FileEntry,
    onClick: () -> Unit,
    onStarToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File type icon
            val (icon, color) = getFileCategoryIcon(file.category)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = file.size.formatFileSize(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        " · ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = file.modifiedAt.formatDateTime(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Star button
            IconButton(
                onClick = onStarToggle,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (file.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "Star",
                    tint = if (file.isStarred) TCloudWarning else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Status
            val statusColor = when (file.status) {
                FileStatus.BACKED_UP -> TCloudSuccess
                FileStatus.UPLOADING -> TCloudSecondary
                FileStatus.FAILED -> TCloudError
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, shape = RoundedCornerShape(4.dp))
            )
        }
    }
}

private fun getFileCategoryIcon(category: FileCategory): Pair<ImageVector, Color> {
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
