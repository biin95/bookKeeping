package com.biin95.bookkeeping.ui.detail

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.biin95.bookkeeping.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    navController: NavController,
    transactionId: Long,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val transaction by viewModel.transaction.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    LaunchedEffect(transactionId) {
        viewModel.load(transactionId)
    }

    LaunchedEffect(deleted) {
        if (deleted) navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("交易详情") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    transaction?.let { t ->
                        IconButton(onClick = {
                            navController.navigate(Screen.AddTransaction.createRoute(t.id))
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("确认删除") },
                                text = { Text("确定要删除这条记录吗？") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.delete()
                                        showDeleteDialog = false
                                    }) { Text("删除") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        transaction?.let { t ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                // 金额
                Text(
                    "${if (t.isIncome) "+" else "-"}¥%.2f".format(kotlin.math.abs(t.amount)),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (t.isIncome) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(24.dp))

                DetailRow("分类", t.category)
                if (t.merchant.isNotBlank()) DetailRow("商户", t.merchant)
                if (t.description.isNotBlank()) DetailRow("备注", t.description)
                if (t.paymentMethod.isNotBlank()) DetailRow("支付方式", t.paymentMethod)
                DetailRow("来源", when (t.source) {
                    "manual" -> "手动记账"
                    "screenshot" -> "截图识别"
                    "notification" -> "通知栏"
                    "sms" -> "短信"
                    "accessibility" -> "无障碍"
                    else -> t.source
                })
                DetailRow("时间", dateFormat.format(Date(t.timestamp)))

                if (t.rawText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "OCR 原始文本",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        t.rawText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
