package com.tcloud.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcloud.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDisconnectDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        // Account section
        SettingsSection("Account") {
            SettingsItem(
                icon = Icons.Filled.AccountCircle,
                title = "Telegram Account",
                subtitle = if (uiState.isConnected) "Connected" else "Not connected",
                iconColor = TCloudPrimary
            )
        }

        // Backup section
        SettingsSection("Backup") {
            SettingsToggleItem(
                icon = Icons.Filled.Backup,
                title = "Automatic backup",
                subtitle = "Back up new files automatically",
                checked = uiState.autoBackup,
                onCheckedChange = viewModel::setAutoBackup,
                iconColor = TCloudPrimary
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            val categories = listOf(
                Triple("Photos", Icons.Filled.Image, uiState.backupPhotos),
                Triple("Videos", Icons.Filled.Videocam, uiState.backupVideos),
                Triple("Screenshots", Icons.Filled.Screenshot, uiState.backupScreenshots),
                Triple("Documents", Icons.Filled.Description, uiState.backupDocuments),
                Triple("Audio", Icons.Filled.MusicNote, uiState.backupAudio),
                Triple("Downloads", Icons.Filled.Download, uiState.backupDownloads)
            )

            categories.forEachIndexed { index, (label, icon, checked) ->
                SettingsToggleItem(
                    icon = icon,
                    title = label,
                    checked = checked,
                    onCheckedChange = { viewModel.setBackupCategory(label, it) },
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (index < categories.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                }
            }
        }

        // Network section
        SettingsSection("Network") {
            SettingsToggleItem(
                icon = Icons.Filled.Wifi,
                title = "Wi-Fi backup",
                subtitle = "Back up over Wi-Fi",
                checked = uiState.wifiOnly,
                onCheckedChange = viewModel::setWifiOnly,
                iconColor = TCloudSecondary
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsToggleItem(
                icon = Icons.Filled.CellTower,
                title = "Mobile data backup",
                subtitle = "Also back up over mobile data",
                checked = uiState.mobileData,
                onCheckedChange = viewModel::setMobileData,
                iconColor = TCloudSecondary
            )
        }

        // Battery section
        SettingsSection("Battery") {
            SettingsToggleItem(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Charging only",
                subtitle = "Only back up while charging",
                checked = uiState.chargingOnly,
                onCheckedChange = viewModel::setChargingOnly,
                iconColor = TCloudWarning
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            SettingsItem(
                icon = Icons.Filled.Battery4Bar,
                title = "Minimum battery",
                subtitle = "${uiState.minBattery}%",
                iconColor = TCloudWarning
            )
            Slider(
                value = uiState.minBattery.toFloat(),
                onValueChange = { viewModel.setMinBattery(it.toInt()) },
                valueRange = 10f..80f,
                steps = 6,
                modifier = Modifier.padding(horizontal = 56.dp),
                colors = SliderDefaults.colors(
                    thumbColor = TCloudPrimary,
                    activeTrackColor = TCloudPrimary
                )
            )
        }

        // Storage & Sync section
        SettingsSection("Storage & Sync") {
            SettingsClickableItem(
                icon = Icons.Filled.CloudSync,
                title = "Rebuild Cloud Index",
                subtitle = uiState.rebuildMessage ?: "Scan Telegram storage channel for remote files",
                onClick = viewModel::startRebuildIndex,
                iconColor = TCloudPrimary,
                isLoading = uiState.isRebuilding
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsClickableItem(
                icon = Icons.Filled.CleaningServices,
                title = "Clear Cache",
                subtitle = "Free temporary chunk and thumbnail storage",
                onClick = viewModel::clearCache,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // About section
        SettingsSection("About") {
            SettingsItem(
                icon = Icons.Filled.Info,
                title = "About TCloud",
                subtitle = "Version 1.0.0",
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsItem(
                icon = Icons.Filled.Gavel,
                title = "Disclaimer",
                subtitle = "TCloud is an independent third-party application",
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Disconnect
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { showDisconnectDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = TCloudError.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, tint = TCloudError)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Disconnect Telegram",
                    color = TCloudError,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }

    // Disconnect confirmation dialog
    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Telegram?") },
            text = {
                Text("Your files stored in Telegram will NOT automatically be deleted.\n\nTCloud will stop synchronizing this account.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnect()
                        onLogout()
                    }
                ) {
                    Text("Disconnect", color = TCloudError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = TCloudPrimary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TCloudPrimary
            )
        )
    }
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    isLoading: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}
