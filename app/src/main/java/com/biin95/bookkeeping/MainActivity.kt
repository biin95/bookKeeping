package com.biin95.bookkeeping

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.biin95.bookkeeping.ui.navigation.NavGraph
import com.biin95.bookkeeping.ui.navigation.Screen
import com.biin95.bookkeeping.ui.theme.BookKeepingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startOcr = intent?.action == "com.biin95.bookkeeping.ACTION_OCR_CAPTURE"

        setContent {
            BookKeepingTheme {
                MainApp(startOcr = startOcr)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == "com.biin95.bookkeeping.ACTION_OCR_CAPTURE") {
            // 从 Quick Tap 触发，导航到 OCR 页面
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(startOcr: Boolean) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarScreens = listOf(Screen.Home.route, Screen.Statistics.route, Screen.Settings.route)
    val showBottomBar = currentRoute in bottomBarScreens

    LaunchedEffect(startOcr) {
        if (startOcr) {
            navController.navigate(Screen.OcrCapture.route)
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                        label = { Text("首页") },
                        selected = currentRoute == Screen.Home.route,
                        onClick = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.BarChart, contentDescription = "统计") },
                        label = { Text("统计") },
                        selected = currentRoute == Screen.Statistics.route,
                        onClick = {
                            navController.navigate(Screen.Statistics.route) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                        label = { Text("设置") },
                        selected = currentRoute == Screen.Settings.route,
                        onClick = {
                            navController.navigate(Screen.Settings.route) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavGraph(navController = navController)
        }
    }
}
