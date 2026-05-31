package com.biin95.bookkeeping.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

// 权限项数据
private data class PermissionItem(
    val name: String,
    val description: String,
    val relatedFeature: String,
    val featureEnabled: Boolean,
    val granted: Boolean,
    val onNavigate: (() -> Unit)?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    // 用一个 state 来触发权限状态刷新
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // 从权限页返回时刷新
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        snapshotFlow { lifecycleOwner.lifecycle.currentState }
            .collect { state ->
                if (state == androidx.lifecycle.Lifecycle.State.RESUMED) {
                    refreshTrigger++
                }
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshTrigger++
    }

    val permissions = remember(refreshTrigger, settings) {
        buildPermissionList(context, settings, permissionLauncher)
    }

    val grantedCount = permissions.count { it.featureEnabled && it.granted }
    val totalCount = permissions.count { it.featureEnabled }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("权限状态") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 总览卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "已开启功能的权限",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "$grantedCount / $totalCount 已授权",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (totalCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { grantedCount.toFloat() / totalCount },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                        )
                    }
                }
            }

            // 权限列表
            permissions.forEach { item ->
                PermissionRow(item)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 提示
            Text(
                "点击未授权的权限可跳转到系统设置进行授权",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── 权限列表构建 ──

private fun buildPermissionList(
    context: android.content.Context,
    settings: AppSettings,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
): List<PermissionItem> {
    val items = mutableListOf<PermissionItem>()

    // 1. 悬浮窗
    items += PermissionItem(
        name = "悬浮窗",
        description = "显示悬浮球快捷按钮",
        relatedFeature = "悬浮球按钮",
        featureEnabled = settings.floatingButtonEnabled,
        granted = Settings.canDrawOverlays(context),
        onNavigate = {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }
    )

    // 2. 通知栏监听（NotificationListener）
    items += PermissionItem(
        name = "通知读取",
        description = "监听通知栏支付信息",
        relatedFeature = "通知栏监听",
        featureEnabled = settings.notificationCaptureEnabled,
        granted = isNotificationListenerEnabled(context),
        onNavigate = {
            try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {}
        }
    )

    // 3. 短信
    val smsGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECEIVE_SMS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    items += PermissionItem(
        name = "短信读取",
        description = "监听银行扣款短信",
        relatedFeature = "短信监听",
        featureEnabled = settings.smsCaptureEnabled,
        granted = smsGranted,
        onNavigate = {
            try {
                permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                })
            }
        }
    )

    // 4. 通知发送（Android 13+）
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val postNotifGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        items += PermissionItem(
            name = "通知发送",
            description = "发送识别结果通知",
            relatedFeature = "截图自动识别",
            featureEnabled = settings.autoScreenshotOcr,
            granted = postNotifGranted,
            onNavigate = {
                try {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    })
                }
            }
        )
    }

    // 5. 相册读取
    val readMediaGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_MEDIA_IMAGES
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    items += PermissionItem(
        name = "相册读取",
        description = "读取截图进行 OCR 识别",
        relatedFeature = "截图自动识别",
        featureEnabled = settings.autoScreenshotOcr,
        granted = readMediaGranted,
        onNavigate = {
            try {
                permissionLauncher.launch(readPermission)
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                })
            }
        }
    )

    // 6. 无障碍服务
    items += PermissionItem(
        name = "无障碍服务",
        description = "自动从支付APP读取账单",
        relatedFeature = "无障碍自动记账",
        featureEnabled = settings.accessibilityEnabled,
        granted = isAccessibilityServiceEnabled(context),
        onNavigate = {
            try {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {}
        }
    )

    return items
}

private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    return flat.contains(packageName)
}

private fun isAccessibilityServiceEnabled(context: android.content.Context): Boolean {
    val serviceName = "${context.packageName}/.service.BookKeepingAccessibilityService"
    val flat = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return flat.contains(serviceName, ignoreCase = true) ||
            flat.contains(context.packageName, ignoreCase = true)
}

// ── 权限行 Composable ──

@Composable
private fun PermissionRow(item: PermissionItem) {
    val isGrayedOut = !item.featureEnabled
    val statusText: String
    val statusColor: Color
    val statusIcon: androidx.compose.ui.graphics.vector.ImageVector

    when {
        isGrayedOut -> {
            statusText = "未开启"
            statusColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            statusIcon = Icons.Default.Remove
        }
        item.granted -> {
            statusText = "已授权"
            statusColor = Color(0xFF2E7D32)
            statusIcon = Icons.Default.CheckCircle
        }
        else -> {
            statusText = "未授权"
            statusColor = MaterialTheme.colorScheme.error
            statusIcon = Icons.Default.Cancel
        }
    }

    Surface(
        onClick = {
            if (!isGrayedOut && !item.granted) {
                item.onNavigate?.invoke()
            }
        },
        enabled = !isGrayedOut && !item.granted,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                statusIcon,
                contentDescription = null,
                tint = if (isGrayedOut) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                else statusColor,
                modifier = Modifier.size(22.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isGrayedOut) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    buildString {
                        append(item.description)
                        if (isGrayedOut) append(" · ${item.relatedFeature}未开启")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGrayedOut) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isGrayedOut) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                else if (item.granted) Color(0xFF2E7D32).copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
            ) {
                Text(
                    statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
