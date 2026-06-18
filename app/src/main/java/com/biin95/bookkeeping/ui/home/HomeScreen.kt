package com.biin95.bookkeeping.ui.home

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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

enum class TimePeriod(val label: String) {
    DAY("\u4eca\u65e5"), WEEK("\u672c\u5468"), MONTH("\u672c\u6708"), YEAR("\u672c\u5e74")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, chatViewModel: ChatViewModel = hiltViewModel()) {
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { chatViewModel.reloadHistory() }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.size > 0) listState.animateScrollToItem(uiState.messages.size - 1)
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            StatsOverviewCard(totalExpense = uiState.totalExpense ?: 0.0, categorySummary = uiState.categorySummary, timePeriod = uiState.timePeriod, onPeriodSelected = { chatViewModel.setTimePeriod(it) })
            LazyColumn(modifier = Modifier.weight(1f), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                items(uiState.messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg, chatViewModel = chatViewModel, onEditClick = { editingMsg -> showEditDialog = true; editingMessage = editingMsg })
                }
            }
            InputBar(text = uiState.inputText, onTextChange = { chatViewModel.updateInput(it) }, onSend = { chatViewModel.sendMessage() })
        }
    }
    if (showEditDialog && editingMessage != null) {
        val msg = editingMessage!!
        val result = msg.nlpResult
        if (result != null) {
            EditTransactionDialog(currentAmount = result.amount ?: 0.0, currentCategory = result.category, currentMerchant = result.merchant ?: "", currentDescription = result.description, onDismiss = { showEditDialog = false; editingMessage = null }, onSave = { amount, category, merchant, description ->
                val txnId = msg.transaction?.id ?: return@EditTransactionDialog
                chatViewModel.updateTransaction(txnId, amount, category, merchant, description)
                showEditDialog = false; editingMessage = null
            })
        } else { showEditDialog = false; editingMessage = null }
    }
}

@Composable
fun StatsOverviewCard(totalExpense: Double, categorySummary: List<com.biin95.bookkeeping.data.local.dao.CategorySummary>, timePeriod: TimePeriod, onPeriodSelected: (TimePeriod) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            TimePeriodSelectorRow(currentPeriod = timePeriod, onPeriodSelected = onPeriodSelected)
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("\u652f\u51fa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("\u00a5" + String.format("%.2f", totalExpense), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(8.dp))
            val top3 = categorySummary.take(3)
            if (top3.isNotEmpty()) {
                top3.forEach { item -> CategoryBar(item.category, item.total, totalExpense); Spacer(modifier = Modifier.height(4.dp)) }
            }
        }
    }
}

@Composable
fun CategoryBar(category: String, amount: Double, totalExpense: Double) {
    val ratio = if (totalExpense != 0.0) (kotlin.math.abs(amount) / kotlin.math.abs(totalExpense)).toFloat().coerceIn(0f, 1f) else 0f
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(category, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(40.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Box(modifier = Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primaryContainer)) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(ratio).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primary)) }
        Spacer(modifier = Modifier.width(8.dp))
        Text("\u00a5" + String.format("%.2f", amount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, softWrap = false, maxLines = 1, modifier = Modifier.width(80.dp))
    }
}

@Composable
fun TimePeriodSelectorRow(currentPeriod: TimePeriod, onPeriodSelected: (TimePeriod) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimePeriod.entries.forEach { period -> FilterChip(selected = period == currentPeriod, onClick = { onPeriodSelected(period) }, label = { Text(period.label, fontSize = 12.sp) }) }
    }
}

@Composable
fun ChatBubble(message: ChatMessage, chatViewModel: ChatViewModel, onEditClick: (ChatMessage) -> Unit = {}) {
    when (message.type) {
        "user" -> UserBubble(message.text)
        "response" -> ResponseBubble(message, chatViewModel, onEditClick)
        "date_separator" -> DateSeparator(message.text)
        "system" -> SystemMessage(message.text)
    }
}

@Composable
fun UserBubble(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp), color = MaterialTheme.colorScheme.primary, tonalElevation = 2.dp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp, start = 48.dp)) {
            Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ResponseBubble(message: ChatMessage, chatViewModel: ChatViewModel, onEditClick: (ChatMessage) -> Unit = {}) {
    val result = message.nlpResult
    if (result == null) { SystemMessage(message.text); return }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Surface(shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), color = MaterialTheme.colorScheme.surfaceVariant, tonalElevation = 1.dp, modifier = Modifier.padding(top = 4.dp, bottom = 2.dp, end = 48.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("\u00a5" + String.format("%.2f", result.amount ?: 0.0), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { onEditClick(message) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Edit, contentDescription = "\u4fee\u6539", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val merchant = result.merchant ?: ""; val category = result.category
                    if (merchant.isNotEmpty()) { Row { Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(modifier = Modifier.width(4.dp)); Text("$merchant  \u00b7  $category", style = MaterialTheme.typography.bodySmall) } }
                    else { Text(category, style = MaterialTheme.typography.bodySmall) }
                    if (result.description.isNotBlank()) { Spacer(modifier = Modifier.height(2.dp)); Text(result.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (message.isSaved) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 0.dp, end = 48.dp), horizontalArrangement = Arrangement.Start) {
                Text("\u2705 \u5df2\u4fdd\u5b58", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun DateSeparator(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
fun SystemMessage(text: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(text, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun InputBar(text: String, onTextChange: (String) -> Unit, onSend: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = onTextChange, placeholder = { Text("\u8bf4\u53e5\u8bdd\u8bb0\u4e2a\u8d26") }, modifier = Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Color.Transparent))
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onSend, enabled = text.isNotBlank()) { Icon(Icons.Default.Send, contentDescription = "\u53d1\u9001", tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun TimePeriodSelector(currentPeriod: TimePeriod, onPeriodSelected: (TimePeriod) -> Unit) {
    TimePeriodSelectorRow(currentPeriod = currentPeriod, onPeriodSelected = onPeriodSelected)
}

@Composable
fun EditTransactionDialog(currentAmount: Double, currentCategory: String, currentMerchant: String, currentDescription: String, onDismiss: () -> Unit, onSave: (amount: Double, category: String, merchant: String, description: String) -> Unit) {
    var amountText by remember { mutableStateOf(String.format("%.2f", currentAmount)) }
    var categoryText by remember { mutableStateOf(currentCategory) }
    var merchantText by remember { mutableStateOf(currentMerchant) }
    var descriptionText by remember { mutableStateOf(currentDescription) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("\u4fee\u6539\u8bb0\u8d26") }, text = {
        Column {
            OutlinedTextField(value = amountText, onValueChange = { amountText = it }, label = { Text("\u91d1\u989d") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = merchantText, onValueChange = { merchantText = it }, label = { Text("\u5546\u6237") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = categoryText, onValueChange = { categoryText = it }, label = { Text("\u5206\u7c7b") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = descriptionText, onValueChange = { descriptionText = it }, label = { Text("\u5907\u6ce8") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
        }
    }, confirmButton = { Button(onClick = { val amount = amountText.toDoubleOrNull() ?: currentAmount; onSave(amount, categoryText, merchantText, descriptionText) }) { Text("\u4fdd\u5b58") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("\u53d6\u6d88") } })
}

fun getCategoryIcon(category: String) = when (category) {
    "\u9910\u996e" -> Icons.Default.Restaurant; "\u4ea4\u901a" -> Icons.Default.DirectionsCar; "\u8d2d\u7269" -> Icons.Default.ShoppingBag; "\u5a31\u4e50" -> Icons.Default.SportsEsports
    "\u5c45\u4f4f" -> Icons.Default.Home; "\u533b\u7597" -> Icons.Default.LocalHospital; "\u6559\u80b2" -> Icons.Default.School; "\u901a\u8baf" -> Icons.Default.Phone
    "\u65e5\u7528" -> Icons.Default.CleaningServices; "\u5176\u4ed6" -> Icons.Default.Category; else -> Icons.Default.Category
}
