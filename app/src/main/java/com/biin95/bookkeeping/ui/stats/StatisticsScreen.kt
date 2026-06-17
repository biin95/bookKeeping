package com.biin95.bookkeeping.ui.stats

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.biin95.bookkeeping.data.local.dao.CategorySummary
import com.biin95.bookkeeping.ui.home.TimePeriod
import com.biin95.bookkeeping.ui.home.TimePeriodSelector
import com.biin95.bookkeeping.ui.home.getCategoryIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    navController: NavController,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val categorySummary by viewModel.categorySummary.collectAsStateWithLifecycle()
    val totalExpense by viewModel.totalExpense.collectAsStateWithLifecycle()
    
    val currentPeriod by viewModel.currentPeriod.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("统计") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                TimePeriodSelector(
                    currentPeriod = currentPeriod,
                    onPeriodSelected = { viewModel.setPeriod(it) }
                )
            }

            // 总览
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard("总支出", totalExpense ?: 0.0, MaterialTheme.colorScheme.error)
                }
            }

            // 分类占比标题
            item {
                Text(
                    "支出分类",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            if (categorySummary.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val maxAmount = categorySummary.maxOfOrNull { kotlin.math.abs(it.total) } ?: 1.0
                items(categorySummary) { item ->
                    CategorySummaryItem(
                        summary = item,
                        maxAmount = maxAmount,
                        totalExpense = totalExpense ?: 1.0
                    )
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, amount: Double, color: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = Modifier.width(110.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "¥%.2f".format(amount),
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun CategorySummaryItem(summary: CategorySummary, maxAmount: Double, totalExpense: Double) {
    val ratio = if (maxAmount > 0) (kotlin.math.abs(summary.total) / maxAmount).toFloat().coerceIn(0f, 1f) else 0f
    val icon = getCategoryIcon(summary.category)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(summary.category, modifier = Modifier.width(50.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(ratio)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "¥%.2f".format(summary.total),
                fontWeight = FontWeight.Bold,
                softWrap = false,
                maxLines = 1,
                modifier = Modifier.width(80.dp)
            )

        }
    }
}
