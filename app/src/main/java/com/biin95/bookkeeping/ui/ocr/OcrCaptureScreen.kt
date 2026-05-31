package com.biin95.bookkeeping.ui.ocr

import android.net.Uri
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
import com.biin95.bookkeeping.ocr.OcrResult
import com.biin95.bookkeeping.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrCaptureScreen(
    navController: NavController,
    viewModel: OcrViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.recognizeImage(context, it) }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("截图识别") },
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = uiState) {
                is OcrUiState.Idle -> {
                    Spacer(modifier = Modifier.height(40.dp))

                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "选择账单截图进行识别",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "支持支付宝、微信、银行等账单截图",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { imagePicker.launch("image/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从相册选择截图")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = { viewModel.captureFromClipboard(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("从剪贴板识别")
                    }
                }

                is OcrUiState.Processing -> {
                    Spacer(modifier = Modifier.height(100.dp))
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在识别中...")
                }

                is OcrUiState.Result -> {
                    OcrResultContent(
                        result = state.result,
                        amount = viewModel.editableAmount.collectAsStateWithLifecycle().value,
                        merchant = viewModel.editableMerchant.collectAsStateWithLifecycle().value,
                        category = viewModel.editableCategory.collectAsStateWithLifecycle().value,
                        isIncome = viewModel.isIncome.collectAsStateWithLifecycle().value,
                        onAmountChange = { viewModel.setAmount(it) },
                        onMerchantChange = { viewModel.setMerchant(it) },
                        onCategoryChange = { viewModel.setCategory(it) },
                        onIncomeChange = { viewModel.setIsIncome(it) },
                        onSave = { viewModel.saveTransaction() },
                        onRetry = { viewModel.reset() }
                    )
                }

                is OcrUiState.MultiResult -> {
                    // 多笔交易头部提示
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "识别到 ${state.results.size} 笔交易",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "${state.currentIndex + 1} / ${state.results.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 翻页按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.previousResult() },
                            enabled = state.currentIndex > 0
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = null)
                            Text("上一笔")
                        }
                        OutlinedButton(
                            onClick = { viewModel.nextResult() },
                            enabled = state.currentIndex < state.results.size - 1
                        ) {
                            Text("下一笔")
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 当前交易编辑
                    OcrResultContent(
                        result = state.currentResult,
                        amount = viewModel.editableAmount.collectAsStateWithLifecycle().value,
                        merchant = viewModel.editableMerchant.collectAsStateWithLifecycle().value,
                        category = viewModel.editableCategory.collectAsStateWithLifecycle().value,
                        isIncome = viewModel.isIncome.collectAsStateWithLifecycle().value,
                        onAmountChange = { viewModel.setAmount(it) },
                        onMerchantChange = { viewModel.setMerchant(it) },
                        onCategoryChange = { viewModel.setCategory(it) },
                        onIncomeChange = { viewModel.setIsIncome(it) },
                        onSave = { viewModel.saveTransaction() },
                        onRetry = { viewModel.reset() },
                        extraButtons = {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.saveAllTransactions() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("一键保存全部 ${state.results.size} 笔")
                            }
                        }
                    )
                }

                is OcrUiState.Error -> {
                    Spacer(modifier = Modifier.height(60.dp))
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.reset() }) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
fun OcrResultContent(
    result: OcrResult,
    amount: String,
    merchant: String,
    category: String,
    isIncome: Boolean,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onIncomeChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    extraButtons: @Composable (() -> Unit)? = null
) {
    Text(
        "识别结果",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 收入/支出切换
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        FilterChip(
            selected = !isIncome,
            onClick = { onIncomeChange(false) },
            label = { Text("支出") }
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = isIncome,
            onClick = { onIncomeChange(true) },
            label = { Text("收入") }
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 金额
    OutlinedTextField(
        value = amount,
        onValueChange = onAmountChange,
        label = { Text("金额") },
        prefix = { Text("¥") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 商户
    OutlinedTextField(
        value = merchant,
        onValueChange = onMerchantChange,
        label = { Text("商户名") },
        leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 分类
    OutlinedTextField(
        value = category,
        onValueChange = onCategoryChange,
        label = { Text("分类") },
        leadingIcon = { Icon(Icons.Default.Category, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(12.dp))

    // 支付方式
    result.paymentMethod?.let { method ->
        Text(
            "支付方式: $method",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 原始文本折叠
    var showRawText by remember { mutableStateOf(false) }
    TextButton(onClick = { showRawText = !showRawText }) {
        Text(if (showRawText) "隐藏原始文本" else "查看原始文本")
    }
    if (showRawText) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                result.rawText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // 额外按钮（一键保存全部等）
    extraButtons?.invoke()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier.weight(1f)
        ) {
            Text("重新识别")
        }
        Button(
            onClick = onSave,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("保存")
        }
    }
}

sealed class OcrUiState {
    data object Idle : OcrUiState()
    data object Processing : OcrUiState()
    data class Result(val result: OcrResult) : OcrUiState()
    data class MultiResult(
        val results: List<OcrResult>,
        val currentIndex: Int,
        val currentResult: OcrResult
    ) : OcrUiState()
    data class Error(val message: String) : OcrUiState()
}
