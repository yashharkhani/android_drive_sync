package com.drivesync.app.ui.navigation

import androidx.compose.runtime.*
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.drivesync.app.ui.screens.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object AddMapping : Screen("add_mapping") {
        fun route(id: Long? = null) = if (id != null) "add_mapping?id=$id" else "add_mapping"
        const val FULL_ROUTE = "add_mapping?id={id}"
    }
    object DriveFolderPicker : Screen("drive_folder_picker") {
        fun route(folderId: String, folderPath: String) =
            "drive_folder_picker?folderId=${folderId.ifBlank { "root" }}&folderPath=${folderPath.ifBlank { "My Drive" }}"
        const val FULL_ROUTE = "drive_folder_picker?folderId={folderId}&folderPath={folderPath}"
    }
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToAddMapping = { navController.navigate(Screen.AddMapping.route()) },
                onNavigateToEditMapping = { id -> navController.navigate(Screen.AddMapping.route(id)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.AddMapping.FULL_ROUTE,
            arguments = listOf(navArgument("id") {
                type = NavType.LongType
                defaultValue = -1L
            })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id")?.takeIf { it != -1L }
            val savedState = backStackEntry.savedStateHandle

            // Observe Drive folder picker result
            val driveFolderId by savedState.getStateFlow("drive_folder_id", "").collectAsState()
            val driveFolderPath by savedState.getStateFlow("drive_folder_path", "").collectAsState()

            AddMappingScreen(
                existingMappingId = id,
                driveFolderIdFromPicker = driveFolderId.ifBlank { null },
                driveFolderPathFromPicker = driveFolderPath.ifBlank { null },
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDrivePicker = { currentId, currentPath ->
                    navController.navigate(
                        Screen.DriveFolderPicker.route(currentId, currentPath)
                    )
                },
                onSaved = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                }
            )
        }

        composable(
            route = Screen.DriveFolderPicker.FULL_ROUTE,
            arguments = listOf(
                navArgument("folderId") {
                    type = NavType.StringType
                    defaultValue = "root"
                    nullable = false
                },
                navArgument("folderPath") {
                    type = NavType.StringType
                    defaultValue = "My Drive"
                    nullable = false
                }
            )
        ) {
            DriveFolderPickerScreen(
                onFolderSelected = { folderId, folderPath ->
                    // Pass result back to AddMapping via saved state handle
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("drive_folder_id", folderId)
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("drive_folder_path", folderPath)
                    navController.popBackStack()
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
