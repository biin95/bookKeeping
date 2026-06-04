package com.biin95.bookkeeping.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import java.net.URLDecoder
import java.net.URLEncoder
import com.biin95.bookkeeping.ui.add.AddTransactionScreen
import com.biin95.bookkeeping.ui.detail.TransactionDetailScreen
import com.biin95.bookkeeping.ui.home.HomeScreen
import com.biin95.bookkeeping.ui.ocr.OcrCaptureScreen
import com.biin95.bookkeeping.ui.settings.LogViewerScreen
import com.biin95.bookkeeping.ui.settings.PermissionsScreen
import com.biin95.bookkeeping.ui.settings.SettingsScreen
import com.biin95.bookkeeping.ui.stats.StatisticsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    Log.d("BookKeeping", "NavGraph 开始构建")
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            Log.d("BookKeeping", "NavGraph: 进入 HomeScreen")
            HomeScreen(navController = navController)
        }
        composable(
            route = Screen.AddTransaction.route,
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getLong("transactionId") ?: -1L
            AddTransactionScreen(
                navController = navController,
                transactionId = if (transactionId == -1L) null else transactionId
            )
        }
        composable(Screen.TransactionDetail.route) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId")?.toLongOrNull() ?: return@composable
            TransactionDetailScreen(
                navController = navController,
                transactionId = transactionId
            )
        }
        composable(Screen.Statistics.route) {
            StatisticsScreen(navController = navController)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.Permissions.route) {
            PermissionsScreen(navController = navController)
        }
        composable(Screen.LogViewer.route) {
            LogViewerScreen(navController = navController)
        }
        composable(
            route = Screen.OcrCapture.route,
            arguments = listOf(
                navArgument("screenshotPath") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val encodedPath = backStackEntry.arguments?.getString("screenshotPath") ?: ""
            val screenshotPath = if (encodedPath.isNotEmpty()) {
                URLDecoder.decode(encodedPath, "UTF-8")
            } else ""
            OcrCaptureScreen(
                navController = navController,
                screenshotPath = screenshotPath.ifEmpty { null }
            )
        }
    }
}
