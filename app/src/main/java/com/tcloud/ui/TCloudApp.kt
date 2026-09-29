package com.tcloud.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.tcloud.core.common.FileCategory
import com.tcloud.ui.files.FilesScreen
import com.tcloud.ui.home.HomeScreen
import com.tcloud.ui.navigation.Routes
import com.tcloud.ui.navigation.TCloudDestination
import com.tcloud.ui.onboarding.OnboardingScreen
import com.tcloud.ui.onboarding.OnboardingViewModel
import com.tcloud.ui.settings.SettingsScreen
import com.tcloud.ui.uploads.UploadCenterScreen
import com.tcloud.ui.viewer.FileViewerHost
import com.tcloud.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TCloudApp() {
    val navController = rememberNavController()
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val isOnboardingComplete = onboardingViewModel.isOnboardingComplete()

    val startDestination = if (isOnboardingComplete) {
        TCloudDestination.Home.route
    } else {
        Routes.ONBOARDING
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomNav = currentRoute in TCloudDestination.bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    TCloudDestination.bottomNavItems.forEach { destination ->
                        val selected = currentRoute == destination.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title
                                )
                            },
                            label = {
                                Text(
                                    destination.title,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TCloudPrimary,
                                selectedTextColor = TCloudPrimary,
                                indicatorColor = TCloudPrimary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Onboarding
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(TCloudDestination.Home.route) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    }
                )
            }

            // Home
            composable(TCloudDestination.Home.route) {
                HomeScreen(
                    onNavigateToFiles = { category ->
                        if (category != null) {
                            navController.navigate(Routes.fileCategory(category.name))
                        } else {
                            navController.navigate(TCloudDestination.Files.route)
                        }
                    },
                    onNavigateToSearch = {
                        navController.navigate(Routes.SEARCH)
                    },
                    onNavigateToUploads = {
                        navController.navigate(TCloudDestination.Uploads.route)
                    },
                    onFileClick = { fileId ->
                        navController.navigate(Routes.fileDetail(fileId))
                    }
                )
            }

            // Files
            composable(TCloudDestination.Files.route) {
                FilesScreen(
                    category = null,
                    onFileClick = { fileId ->
                        navController.navigate(Routes.fileDetail(fileId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // Files by category
            composable(Routes.FILE_CATEGORY) { backStackEntry ->
                val categoryName = backStackEntry.arguments?.getString("category")
                val category = categoryName?.let {
                    try { FileCategory.valueOf(it) } catch (e: Exception) { null }
                }
                FilesScreen(
                    category = category,
                    onFileClick = { fileId ->
                        navController.navigate(Routes.fileDetail(fileId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // Uploads
            composable(TCloudDestination.Uploads.route) {
                UploadCenterScreen()
            }

            // Settings
            composable(TCloudDestination.Settings.route) {
                SettingsScreen(
                    onLogout = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // Search
            composable(Routes.SEARCH) {
                FilesScreen(
                    category = null,
                    isSearchMode = true,
                    onFileClick = { fileId ->
                        navController.navigate(Routes.fileDetail(fileId))
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            // File Detail / Media Viewer
            composable(
                route = Routes.FILE_DETAIL,
                arguments = listOf(navArgument("fileId") { type = NavType.StringType })
            ) { backStackEntry ->
                val fileId = backStackEntry.arguments?.getString("fileId") ?: ""
                FileViewerHost(
                    fileId = fileId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
