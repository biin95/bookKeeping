package com.biin95.bookkeeping

import android.content.Intent
import android.os.Bundle
import android.util.Log
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
import java.net.URLEncoder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d("BookKeeping", "MainActivity.onCreate 开始")
        try {
            super.onCreate(savedInstanceState)
            Log.d("BookKeeping", "super.onCreate 完成")
            enableEdgeToEdge()
            Log.d("BookKeeping", "enableEdgeToEdge 完成")

            val action = intent?.action
            val startOcr = action == "com.biin95.bookkeeping.ACTION_OCR_CAPTURE"
            val screenshotPath = if (action == "ACTION_OCR_CONFIRM") {
                intent?.getStringExtra("screenshot_path")
            } else null
            Log.d("BookKeeping", "startOcr=$startOcr, screenshotPath=$screenshotPath")

            setContent {
                Log.d("BookKeeping", "setContent 开始")
                BookKeepingTheme {
                    Log.d("BookKeeping", "BookKeepingTheme 开始")
                    MainApp(startOcr = startOcr, screenshotPath = screenshotPath)
                }
            }
            Log.d("BookKeeping", "MainActivity.onCreate 完成")
        } catch (e: Exception) {
            Log.e("BookKeeping", "MainActivity.onCreate 异常", e)
            throw e
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == "ACTION_OCR_CONFIRM") {
            // 截图通知跳转，重新创建以导航到 OCR 页面
            recreate()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(startOcr: Boolean, screenshotPath: String? = null) {
    Log.d("BookKeeping", "MainApp Composable 开始")
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarScreens = listOf(Screen.Home.route, Screen.Statistics.route, Screen.Settings.route)
    val showBottomBar = currentRoute in bottomBarScreens

    LaunchedEffect(startOcr, screenshotPath) {
        Log.d("BookKeeping", "LaunchedEffect startOcr=$startOcr, screenshotPath=$screenshotPath")
        if (startOcr || !screenshotPath.isNullOrBlank()) {
            val encodedPath = if (!screenshotPath.isNullOrBlank()) {
                URLEncoder.encode(screenshotPath, "UTF-8")
            } else null
            navController.navigate(Screen.OcrCapture.createRoute(encodedPath))
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
