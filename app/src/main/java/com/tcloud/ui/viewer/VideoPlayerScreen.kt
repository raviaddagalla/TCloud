package com.tcloud.ui.viewer

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tcloud.core.database.entity.FileEntry
import java.io.File

/**
 * Fullscreen video player screen using Media3 ExoPlayer.
 * Supports streaming from TCloud storage or local playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    fileEntry: FileEntry,
    onNavigateBack: () -> Unit,
    onDownload: (FileEntry) -> Unit = {},
    onShare: (FileEntry) -> Unit = {}
) {
    val context = LocalContext.current
    var showInfoDialog by remember { mutableStateOf(false) }

    // Initialize ExoPlayer
    val exoPlayer = remember(fileEntry.id) {
        ExoPlayer.Builder(context).build().apply {
            val mediaUri = if (!fileEntry.localUri.isNullOrEmpty() && File(fileEntry.localUri).exists()) {
                Uri.fromFile(File(fileEntry.localUri))
            } else {
                Uri.parse("tcloud://video/${fileEntry.id}")
            }
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileEntry.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        Text(
                            text = com.tcloud.core.common.formatFileSize(fileEntry.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onShare(fileEntry) }) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { onDownload(fileEntry) }) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Download",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            )
        },
        containerColor = Color.Black
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showInfoDialog) {
            FileInfoDialog(
                fileEntry = fileEntry,
                onDismiss = { showInfoDialog = false }
            )
        }
    }
}

@Composable
fun FileInfoDialog(
    fileEntry: FileEntry,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "File Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Name: ${fileEntry.name}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Size: ${com.tcloud.core.common.formatFileSize(fileEntry.size)}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Type: ${fileEntry.mimeType}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Status: ${fileEntry.status}", style = MaterialTheme.typography.bodyMedium)
                fileEntry.sha256?.let {
                    Text(
                        text = "SHA-256: ${it.take(16)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
