package com.biin95.bookkeeping.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.biin95.bookkeeping.data.local.dao.CategorySummary
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.ui.home.TimePeriod
import com.biin95.bookkeeping.ui.home.TimePeriodSelector
import com.biin95.bookkeeping.ui.home.getCategoryIcon
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(navController: NavController, viewModel: StatisticsViewModel = hiltViewModel()) {
    val categorySummary by viewModel.categorySummary.collectAsStateWithLifecycle()
    val totalExpense by viewModel.totalExpense.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val currentPeriod by viewModel.currentPeriod.collectAsStateWithLifecycle()
    val viewMode by viewModel.viewMode.collectAsStateWithLifecycle()

    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var isSelectMode by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("\u7edf\u8ba1") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TimePeriodSelector(currentPeriod = currentPeriod, onPeriodSelected = { viewModel.setPeriod(it) })
            TabRow(selectedTabIndex = if (viewMode == StatsViewMode.OVERVIEW) 0 else 1) {
                Tab(selected = viewMode == StatsViewMode.OVERVIEW, onClick = { viewModel.setViewMode(StatsViewMode.OVERVIEW) }, text = { Text("\u6982\u89c8") }, icon = { Icon(Icons.Default.BarChart, contentDescription = null) })
                Tab(selected = viewMode == StatsViewMode.DETAIL, onClick = { viewModel.setViewMode(StatsViewMode.DETAIL) }, text = { Text("\u660e\u7ec6") }, icon = { Icon(Icons.Default.List, contentDescription = null) })
            }
            if (viewMode == StatsViewMode.OVERVIEW) {
                OverviewContent(categorySummary, totalExpense)
            } else {
                DetailContent(
                transactions = transactions,
                navController = navController,
                isSelectMode = isSelectMode,
                selectedIds = selectedIds,
                onToggleSelect = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                onToggleSelectMode = { isSelectMode = !isSelectMode; if (!isSelectMode) selectedIds = emptySet() },
                onSelectAll = { selectedIds = transactions.map { it.id }.toSet() },
                onDeleteSelected = { showBatchDeleteDialog = true },
                onDelete = { viewModel.deleteTransaction(it) }
            )
            }
        }
    }
    // Batch delete confirmation
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除选中的 ${selectedIds.size} 条记录吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.batchDelete(selectedIds.toList())
                        showBatchDeleteDialog = false
                        isSelectMode = false
                        selectedIds = emptySet()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") } }
        )
    }


}

@Composable
private fun OverviewContent(categorySummary: List<CategorySummary>, totalExpense: Double?) {
    LazyColumn {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatCard("\u603b\u652f\u51fa", totalExpense ?: 0.0, MaterialTheme.colorScheme.error)
            }
        }
        item {
            Text("\u652f\u51fa\u5206\u7c7b", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        if (categorySummary.isEmpty()) {
            item { EmptyHint("\u6682\u65e0\u652f\u51fa\u6570\u636e", Icons.Default.BarChart) }
        } else {
            val maxAmount = categorySummary.maxOfOrNull { kotlin.math.abs(it.total) } ?: 1.0
            items(categorySummary) { item -> CategorySummaryItem(summary = item, maxAmount = maxAmount, totalExpense = totalExpense ?: 1.0) }
        }
    }
}

@Composable
private fun DetailContent(
    transactions: List<Transaction>,
    navController: NavController,
    isSelectMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Long) -> Unit = {},
    onToggleSelectMode: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onDelete: (Long) -> Unit = {}
) {
    if (transactions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyHint("\u6682\u65e0\u652f\u51fa\u8bb0\u5f55", Icons.Default.ReceiptLong) }
    } else {
        // Batch action bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isSelectMode) {
                TextButton(onClick = { onToggleSelectMode() }) {
                    Icon(Icons.Default.Checklist, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("批量操作")
                }
            } else {
                Text("已选 ${selectedIds.size} 条", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { onSelectAll() }) { Text("全选") }
                TextButton(onClick = { onDeleteSelected() }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { onToggleSelectMode() }) { Text("取消") }
            }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            items(transactions, key = { it.id }) { txn ->
                TransactionItem(
                        transaction = txn,
                        isSelectMode = isSelectMode,
                        isSelected = txn.id in selectedIds,
                        onClick = { navController.navigate("transaction_detail/${txn.id}") },
                        onToggleSelect = { onToggleSelect(txn.id) },
                        onDelete = { onDelete(txn.id) }
                    )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionItem(
    transaction: Transaction,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onToggleSelect: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val icon = getCategoryIcon(transaction.category)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var offsetXPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val deleteButtonWidthPx = with(density) { 100.dp.toPx() }
    val swipeThresholdPx = with(density) { 60.dp.toPx() }
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)) {
        Box(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(100.dp).background(MaterialTheme.colorScheme.errorContainer), contentAlignment = Alignment.Center) {
            TextButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "\u5220\u9664", tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(modifier = Modifier.width(4.dp))
                Text("\u5220\u9664", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth().offset { IntOffset(offsetXPx.toInt(), 0) }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {
                    if (offsetXPx < -swipeThresholdPx) offsetXPx = 0f else onClick()
                })
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = { offsetXPx = if (offsetXPx < -swipeThresholdPx) -deleteButtonWidthPx else 0f },
                        onHorizontalDrag = { _, dragAmount -> offsetXPx = (offsetXPx + dragAmount).coerceIn(-deleteButtonWidthPx, 0f) }
                    )
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectMode) {
                        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect() })
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(transaction.merchant.ifEmpty { transaction.category }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(transaction.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("¥%.2f".format(-transaction.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text(dateFormat.format(Date(transaction.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false; offsetXPx = 0f },
            title = { Text("\u786e\u8ba4\u5220\u9664") },
            text = { Text("\u786e\u5b9a\u8981\u5220\u9664\u8fd9\u7b14\u8bb0\u5f55\u5417\uff1f") },
            confirmButton = { Button(onClick = { showDeleteDialog = false; offsetXPx = 0f; onDelete() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("\u5220\u9664") } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false; offsetXPx = 0f }) { Text("\u53d6\u6d88") } }
        )
    }
}

@Composable
fun StatCard(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    Card(modifier = Modifier.width(130.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text("¥%.2f".format(amount), fontWeight = FontWeight.Bold, color = color, softWrap = false, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun CategorySummaryItem(summary: CategorySummary, maxAmount: Double, totalExpense: Double) {
    val ratio = if (maxAmount > 0) (kotlin.math.abs(summary.total) / maxAmount).toFloat().coerceIn(0f, 1f) else 0f
    val icon = getCategoryIcon(summary.category)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(summary.category, modifier = Modifier.width(50.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(ratio).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primary))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("¥%.2f".format(summary.total), fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1, modifier = Modifier.width(100.dp))
        }
    }
}

@Composable
private fun EmptyHint(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(modifier = Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
    }
}
