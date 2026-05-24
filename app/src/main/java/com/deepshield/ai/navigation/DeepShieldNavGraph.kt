package com.deepshield.ai.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.deepshield.ai.ui.components.BottomNavBar
import com.deepshield.ai.ui.screens.audio.AudioScreen
import com.deepshield.ai.ui.screens.dashboard.DashboardScreen
import com.deepshield.ai.ui.screens.forensics.ForensicsScreen
import com.deepshield.ai.ui.screens.gallery.GalleryScreen
import com.deepshield.ai.ui.screens.heatmap.HeatmapScreen
import com.deepshield.ai.ui.screens.livecamera.LiveCameraScreen
import com.deepshield.ai.ui.screens.performance.PerformanceScreen
import com.deepshield.ai.ui.screens.privacy.PrivacyScreen
import com.deepshield.ai.ui.screens.settings.SettingsScreen
import com.deepshield.ai.ui.screens.watermark.WatermarkScreen
import com.deepshield.ai.ui.theme.CyberBlack

/**
 * DeepShield Navigation — 10 independent screen routes.
 * Each module has its own route, ViewModel, and composable.
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object LiveCamera : Screen("live_camera")
    object Gallery : Screen("gallery")
    object Audio : Screen("audio")
    object Heatmap : Screen("heatmap/{scanId}") {
        fun createRoute(scanId: String) = "heatmap/$scanId"
    }
    object Watermark : Screen("watermark")
    object Privacy : Screen("privacy")
    object Performance : Screen("performance")
    object Forensics : Screen("forensics/{scanId}") {
        fun createRoute(scanId: String) = "forensics/$scanId"
    }
    object Settings : Screen("settings")
}

@Composable
fun DeepShieldNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route

    Scaffold(
        containerColor = CyberBlack,
        bottomBar = {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        // Pop up to dashboard to avoid deep back stack
                        popUpTo(Screen.Dashboard.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) +
                slideInVertically(
                    initialOffsetY = { 30 },
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            }
        ) {
            // ============================================================
            // Module 1: Dashboard (Home)
            // ============================================================
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToScanner = {
                        navController.navigate(Screen.Gallery.route)
                    },
                    onNavigateToLiveCamera = {
                        navController.navigate(Screen.LiveCamera.route)
                    },
                    onNavigateToAudio = {
                        navController.navigate(Screen.Audio.route)
                    },
                    onNavigateToWatermark = {
                        navController.navigate(Screen.Watermark.route)
                    },
                    onNavigateToHeatmap = { scanId ->
                        navController.navigate(Screen.Heatmap.createRoute(scanId))
                    },
                    onNavigateToForensics = { scanId ->
                        navController.navigate(Screen.Forensics.createRoute(scanId))
                    }
                )
            }

            // ============================================================
            // Module 2: Live Camera Detection
            // ============================================================
            composable(Screen.LiveCamera.route) {
                LiveCameraScreen()
            }

            // ============================================================
            // Module 3: Gallery Scanner
            // ============================================================
            composable(Screen.Gallery.route) {
                GalleryScreen(
                    onNavigateToHeatmap = { scanId ->
                        navController.navigate(Screen.Heatmap.createRoute(scanId))
                    },
                    onNavigateToForensics = { scanId ->
                        navController.navigate(Screen.Forensics.createRoute(scanId))
                    }
                )
            }

            // ============================================================
            // Module 4: Audio Deepfake Detection
            // ============================================================
            composable(Screen.Audio.route) {
                AudioScreen()
            }

            // ============================================================
            // Module 5: Heatmap Visualization
            // ============================================================
            composable(
                route = Screen.Heatmap.route,
                arguments = listOf(navArgument("scanId") { type = NavType.StringType })
            ) { backStackEntry ->
                val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
                HeatmapScreen(scanId = scanId)
            }

            // ============================================================
            // Module 6: Authenticity Watermarking
            // ============================================================
            composable(Screen.Watermark.route) {
                WatermarkScreen()
            }

            // ============================================================
            // Module 7: Privacy Auditor
            // ============================================================
            composable(Screen.Privacy.route) {
                PrivacyScreen()
            }

            // ============================================================
            // Module 8: Performance Dashboard (NPU Optimization)
            // ============================================================
            composable(Screen.Performance.route) {
                PerformanceScreen()
            }

            // ============================================================
            // Module 9: Forensic Report
            // ============================================================
            composable(
                route = Screen.Forensics.route,
                arguments = listOf(navArgument("scanId") { type = NavType.StringType })
            ) { backStackEntry ->
                val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
                ForensicsScreen(scanId = scanId)
            }

            // ============================================================
            // Module 10: Settings & Optimization
            // ============================================================
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToAudio = {
                        navController.navigate(Screen.Audio.route)
                    },
                    onNavigateToPrivacy = {
                        navController.navigate(Screen.Privacy.route)
                    },
                    onNavigateToPerformance = {
                        navController.navigate(Screen.Performance.route)
                    }
                )
            }
        }
    }
}
