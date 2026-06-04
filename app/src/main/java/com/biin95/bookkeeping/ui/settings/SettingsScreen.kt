package com.biin95.bookkeeping.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.biin95.bookkeeping.service.FloatingButtonService
import com.biin95.bookkeeping.service.ScreenshotMonitorService
import com.biin95.bookkeeping.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importData(context, it) }
    }

    // 恢复已开启的服务（设置从 DataStore 加载后）
    LaunchedEffect(settings) {
        if (settings.floatingButtonEnabled && Settings.canDrawOverlays(context)) {
            FloatingButtonService.start(context)
        }
        if (settings.autoScreenshotOcr) {
            ScreenshotMonitorService.start(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("设置") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── 权限管理 ──
            SettingsClickable(
                title = "权限状态",
                subtitle = "查看各功能所需权限的授权状态",
                onClick = { navController.navigate(Screen.Permissions.route) },
                icon = Icons.Default.Security
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 截图识别触发方式 ──
            Text(
                "截图识别触发方式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsSwitch(
                title = "悬浮球按钮",
                subtitle = "显示悬浮球快捷按钮，点击触发截图识别",
                checked = settings.floatingButtonEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (!Settings.canDrawOverlays(context)) {
                            Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            FloatingButtonService.start(context)
                            viewModel.updateSetting { copy(floatingButtonEnabled = true) }
                        }
                    } else {
                        FloatingButtonService.stop(context)
                        viewModel.updateSetting { copy(floatingButtonEnabled = false) }
                    }
                },
                icon = Icons.Default.TouchApp
            )

            SettingsSwitch(
                title = "截图自动识别",
                subtitle = "截屏后自动识别账单并录入",
                checked = settings.autoScreenshotOcr,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        ScreenshotMonitorService.start(context)
                    } else {
                        ScreenshotMonitorService.stop(context)
                    }
                    viewModel.updateSetting { copy(autoScreenshotOcr = enabled) }
                },
                icon = Icons.Default.Screenshot
            )

            SettingsSwitch(
                title = "敲击背面触发",
                subtitle = "需在系统设置中将「快速点击背面」绑定到 bookKeeping",
                checked = settings.backTapEnabled,
                onCheckedChange = { viewModel.updateSetting { copy(backTapEnabled = it) } },
                icon = Icons.Default.BackHand
            )

            if (settings.backTapEnabled) {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.padding(start = 56.dp)
                ) {
                    Text("前往系统设置配置「快速点击背面」")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 自动记账 ──
            Text(
                "自动记账",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsSwitch(
                title = "通知栏监听",
                subtitle = "自动从通知栏捕获支付信息",
                checked = settings.notificationCaptureEnabled,
                onCheckedChange = { viewModel.updateSetting { copy(notificationCaptureEnabled = it) } },
                icon = Icons.Default.Notifications
            )

            if (settings.notificationCaptureEnabled) {
                TextButton(
                    onClick = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            }
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.padding(start = 56.dp)
                ) {
                    Text("授予通知读取权限")
                }
            }

            SettingsSwitch(
                title = "短信监听",
                subtitle = "自动从银行短信捕获扣款信息",
                checked = settings.smsCaptureEnabled,
                onCheckedChange = { viewModel.updateSetting { copy(smsCaptureEnabled = it) } },
                icon = Icons.Default.Sms
            )

            SettingsSwitch(
                title = "无障碍自动记账",
                subtitle = "从支付APP读取账单（需逐APP配置）",
                checked = settings.accessibilityEnabled,
                onCheckedChange = { viewModel.updateSetting { copy(accessibilityEnabled = it) } },
                icon = Icons.Default.Accessibility
            )

            if (settings.accessibilityEnabled) {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.padding(start = 56.dp)
                ) {
                    Text("前往无障碍设置")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 数据管理 ──
            Text(
                "数据管理",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsClickable(
                title = "导出数据",
                subtitle = "导出所有记录为 JSON 文件",
                onClick = { showExportDialog = true },
                icon = Icons.Default.Upload
            )

            SettingsClickable(
                title = "导入数据",
                subtitle = "从 JSON 文件导入记录",
                onClick = { importLauncher.launch("application/json") },
                icon = Icons.Default.Download
            )

            SettingsClickable(
                title = "导入支付宝账单",
                subtitle = "从支付宝导出的 CSV 文件导入",
                onClick = { importLauncher.launch("text/comma-separated-values") },
                icon = Icons.Default.AccountBalance
            )

            SettingsClickable(
                title = "导入微信账单",
                subtitle = "从微信导出的 CSV 文件导入",
                onClick = { importLauncher.launch("text/comma-separated-values") },
                icon = Icons.Default.Chat
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 调试 ──
            Text(
                "调试",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsClickable(
                title = "运行日志",
                subtitle = "查看截图识别等操作的详细日志",
                onClick = { navController.navigate(Screen.LogViewer.route) },
                icon = Icons.Default.BugReport
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ── 关于 ──
            Text(
                "关于",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsClickable(
                title = "bookKeeping",
                subtitle = "版本 1.0.0 · 免费开源记账APP",
                onClick = {},
                icon = Icons.Default.Info
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 导出确认对话框
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出数据") },
            text = { Text("将所有记录导出为 JSON 文件，保存到下载目录") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.exportData(context)
                    showExportDialog = false
                }) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsClickable(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
