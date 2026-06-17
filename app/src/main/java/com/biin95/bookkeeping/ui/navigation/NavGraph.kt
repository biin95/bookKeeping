package com.biin95.bookkeeping.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.biin95.bookkeeping.ui.add.AddTransactionScreen
import com.biin95.bookkeeping.ui.detail.TransactionDetailScreen
import com.biin95.bookkeeping.ui.home.HomeScreen
import com.biin95.bookkeeping.ui.nlp.NlpInputScreen
import com.biin95.bookkeeping.ui.settings.SettingsScreen

@Composable
fun NavGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
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
        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
        composable(Screen.NlpInput.route) {
            NlpInputScreen(navController = navController)
        }
    }
}
