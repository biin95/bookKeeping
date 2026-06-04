package com.biin95.bookkeeping.ui.home

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    Log.d("BookKeeping", "HomeScreen Composable 开始")
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val totalExpense by viewModel.totalExpense.collectAsStateWithLifecycle()
    val totalIncome by viewModel.totalIncome.collectAsStateWithLifecycle()
    val currentTimePeriod by viewModel.currentTimePeriod.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("bookKeeping") },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.OcrCapture.createRoute()) }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "截图识别")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.AddTransaction.createRoute()) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加记账")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 时间周期选择器
            TimePeriodSelector(
                currentPeriod = currentTimePeriod,
                onPeriodSelected = { viewModel.setTimePeriod(it) }
            )

            // 收支总览卡片
            SummaryCard(
                totalExpense = totalExpense ?: 0.0,
                totalIncome = totalIncome ?: 0.0,
                periodLabel = currentTimePeriod.label
            )

            // 交易列表
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "暂无记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击 + 开始记账，或使用截图识别",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(transactions, key = { it.id }) { transaction ->
                        TransactionItem(
                            transaction = transaction,
                            onClick = {
                                navController.navigate(Screen.TransactionDetail.createRoute(transaction.id))
                            }
                        )
                    }
                }
            }
        }
    }
}

enum class TimePeriod(val label: String) {
    DAY("今日"), WEEK("本周"), MONTH("本月"), YEAR("本年")
}

@Composable
fun TimePeriodSelector(
    currentPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TimePeriod.entries.forEach { period ->
            FilterChip(
                selected = currentPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.label) }
            )
        }
    }
}

@Composable
fun SummaryCard(totalExpense: Double, totalIncome: Double, periodLabel: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = periodLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "支出",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        "¥%.2f".format(totalExpense),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "收入",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        "¥%.2f".format(totalIncome),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "结余 ¥%.2f".format(totalIncome - totalExpense),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun TransactionItem(transaction: Transaction, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val icon = getCategoryIcon(transaction.category)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类图标
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (transaction.isIncome)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (transaction.isIncome)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 分类和商户名
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    transaction.category,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (transaction.merchant.isNotBlank()) {
                    Text(
                        transaction.merchant,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 金额和时间
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (transaction.isIncome) "+" else "-"}¥%.2f".format(kotlin.math.abs(transaction.amount)),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.isIncome)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
                Text(
                    dateFormat.format(Date(transaction.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun getCategoryIcon(category: String) = when (category) {
    "餐饮" -> Icons.Default.Restaurant
    "交通" -> Icons.Default.DirectionsCar
    "购物" -> Icons.Default.ShoppingBag
    "娱乐" -> Icons.Default.SportsEsports
    "居住" -> Icons.Default.Home
    "医疗" -> Icons.Default.LocalHospital
    "教育" -> Icons.Default.School
    "通讯" -> Icons.Default.Phone
    "服饰" -> Icons.Default.Checkroom
    "日用" -> Icons.Default.CleaningServices
    "水果" -> Icons.Default.Eco
    "零食" -> Icons.Default.Cookie
    "饮料" -> Icons.Default.LocalCafe
    "社交" -> Icons.Default.People
    "宠物" -> Icons.Default.Pets
    "运动" -> Icons.Default.FitnessCenter
    "美容" -> Icons.Default.Spa
    "数码" -> Icons.Default.Devices
    "办公" -> Icons.Default.Work
    "工资" -> Icons.Default.AttachMoney
    "奖金" -> Icons.Default.EmojiEvents
    "兼职" -> Icons.Default.WorkOutline
    "理财" -> Icons.Default.TrendingUp
    "红包" -> Icons.Default.CardGiftcard
    "退款" -> Icons.Default.Replay
    "转账" -> Icons.Default.SwapHoriz
    else -> Icons.Default.MoreHoriz
}
