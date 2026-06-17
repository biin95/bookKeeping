package com.biin95.bookkeeping.ui.home

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.biin95.bookkeeping.ui.chat.ChatMessage
import com.biin95.bookkeeping.ui.chat.ChatViewModel
import com.biin95.bookkeeping.ui.chat.ChatUiState
import com.biin95.bookkeeping.ui.navigation.Screen

enum class TimePeriod(val label: String) {
    DAY("今日"), WEEK("本周"), MONTH("本月"), YEAR("本年")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    chatViewModel: ChatViewModel = hiltViewModel()
) {
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new messages
    val msgCount = uiState.messages.size
    LaunchedEffect(msgCount) {
        if (msgCount > 0) {
            listState.animateScrollToItem(msgCount - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("bookKeeping") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Stats overview card
            StatsOverviewCard(
                totalExpense = uiState.totalExpense ?: 0.0,
                categorySummary = uiState.categorySummary,
                timePeriod = uiState.timePeriod,
                onPeriodSelected = { chatViewModel.setTimePeriod(it) }
            )

            // Chat messages
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg, chatViewModel = chatViewModel)
                }
            }

            // Bottom input bar
            InputBar(
                text = uiState.inputText,
                onTextChange = { chatViewModel.updateInput(it) },
                onSend = { chatViewModel.sendMessage() }
            )
        }
    }
}

@Composable
fun StatsOverviewCard(
    totalExpense: Double,
    categorySummary: List<com.biin95.bookkeeping.data.local.dao.CategorySummary>,
    timePeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Time period selector
            TimePeriodSelectorRow(
                currentPeriod = timePeriod,
                onPeriodSelected = onPeriodSelected
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Total expense
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("支出", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("¥" + String.format("%.2f", totalExpense),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Top 3 categories
            val top3 = categorySummary.take(3)
            if (top3.isNotEmpty()) {
                top3.forEach { item ->
                    CategoryBar(item.category, item.total, totalExpense)
                    Spacer(modifier = Modifier.height(4.dp))
                }
            } else {
                Text("暂无支出记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CategoryBar(category: String, amount: Double, totalExpense: Double) {
    val ratio = if (totalExpense != 0.0) (kotlin.math.abs(amount) / kotlin.math.abs(totalExpense)).toFloat().coerceIn(0f, 1f) else 0f
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(category, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("¥" + String.format("%.2f", amount),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            softWrap = false,
            maxLines = 1)
    }
}

@Composable
fun TimePeriodSelectorRow(
    currentPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimePeriod.entries.forEach { period ->
            FilterChip(
                selected = period == currentPeriod,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, chatViewModel: ChatViewModel) {
    when (message.type) {
        "user" -> UserBubble(message.text)
        "response" -> ResponseBubble(message, chatViewModel)
        "confirmed" -> ConfirmedBubble(message.text)
        "system" -> SystemMessage(message.text)
    }
}

@Composable
fun UserBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 2.dp,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 48.dp)
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ResponseBubble(message: ChatMessage, chatViewModel: ChatViewModel) {
    val result = message.nlpResult
    if (result == null) {
        SystemMessage(message.text)
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 1.dp,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, end = 48.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Amount row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("¥" + String.format("%.2f", result.amount ?: 0.0),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Merchant + category
                val merchant = result.merchant ?: ""
                val category = result.category
                if (merchant.isNotEmpty()) {
                    Row {
                        Icon(Icons.Default.Store, contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("$merchant  ·  $category",
                            style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(category, style = MaterialTheme.typography.bodySmall)
                }

                // Description
                if (result.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(result.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = { chatViewModel.rejectSave(result) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("重新输入", fontSize = 12.sp) }
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledTonalButton(
                        onClick = { chatViewModel.confirmSave(result) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("确认保存", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun ConfirmedBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.padding(top = 2.dp, bottom = 2.dp, start = 48.dp)
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun SystemMessage(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("说句话记个账") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank()
            ) {
                Icon(Icons.Default.Send, contentDescription = "发送",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TimePeriodSelector(
    currentPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    TimePeriodSelectorRow(currentPeriod = currentPeriod, onPeriodSelected = onPeriodSelected)
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
    "日用" -> Icons.Default.CleaningServices
    "其他" -> Icons.Default.Category
    else -> Icons.Default.Category
}