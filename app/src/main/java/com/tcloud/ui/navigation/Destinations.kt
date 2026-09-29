package com.tcloud.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations for TCloud.
 */
sealed class TCloudDestination(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : TCloudDestination(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Files : TCloudDestination(
        route = "files",
        title = "Files",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder
    )

    data object Uploads : TCloudDestination(
        route = "uploads",
        title = "Uploads",
        selectedIcon = Icons.Filled.CloudUpload,
        unselectedIcon = Icons.Outlined.CloudUpload
    )

    data object Settings : TCloudDestination(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val bottomNavItems = listOf(Home, Files, Uploads, Settings)
    }
}

// Routes for non-bottom-nav screens
object Routes {
    const val ONBOARDING = "onboarding"
    const val FILE_DETAIL = "file_detail/{fileId}"
    const val SEARCH = "search"
    const val FILE_CATEGORY = "files/{category}"
    const val DOWNLOADS = "downloads"

    fun fileDetail(fileId: String) = "file_detail/$fileId"
    fun fileCategory(category: String) = "files/$category"
}
