package com.biin95.bookkeeping.ui.add

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    navController: NavController,
    transactionId: Long?,
    viewModel: AddTransactionViewModel = hiltViewModel()
) {
    val isEditing = transactionId != null
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val isIncome by viewModel.isIncome.collectAsStateWithLifecycle()
    val amount by viewModel.amount.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val merchant by viewModel.merchant.collectAsStateWithLifecycle()
    val description by viewModel.description.collectAsStateWithLifecycle()
    val paymentMethod by viewModel.paymentMethod.collectAsStateWithLifecycle()
    val saveSuccess by viewModel.saveSuccess.collectAsStateWithLifecycle()

    LaunchedEffect(transactionId) {
        if (transactionId != null) {
            viewModel.loadTransaction(transactionId)
        }
    }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "编辑记录" else "记一笔") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save() }) {
                        Text("保存")
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
                .padding(16.dp)
        ) {
            // 收入/支出切换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SegmentedButton(
                    selected = !isIncome,
                    onClick = { viewModel.setIsIncome(false) },
                    label = "支出"
                )
                Spacer(modifier = Modifier.width(8.dp))
                SegmentedButton(
                    selected = isIncome,
                    onClick = { viewModel.setIsIncome(true) },
                    label = "收入"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 金额输入
            OutlinedTextField(
                value = amount,
                onValueChange = { viewModel.setAmount(it) },
                label = { Text("金额") },
                prefix = { Text("¥") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 分类选择
            Text(
                "选择分类",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            CategoryGrid(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { viewModel.setSelectedCategory(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 商户名
            OutlinedTextField(
                value = merchant,
                onValueChange = { viewModel.setMerchant(it) },
                label = { Text("商户名（可选）") },
                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 备注
            OutlinedTextField(
                value = description,
                onValueChange = { viewModel.setDescription(it) },
                label = { Text("备注（可选）") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 支付方式
            var expanded by remember { mutableStateOf(false) }
            val paymentMethods = listOf("支付宝", "微信", "现金", "银行卡", "信用卡", "其他")
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("支付方式（可选）") },
                    leadingIcon = { Icon(Icons.Default.Payment, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    paymentMethods.forEach { method ->
                        DropdownMenuItem(
                            text = { Text(method) },
                            onClick = {
                                viewModel.setPaymentMethod(method)
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 保存按钮
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEditing) "更新" else "保存")
            }
        }
    }
}

@Composable
fun SegmentedButton(selected: Boolean, onClick: () -> Unit, label: String) {
    Button(
        onClick = onClick,
        colors = if (selected) ButtonDefaults.buttonColors()
        else ButtonDefaults.outlinedButtonColors(),
        modifier = Modifier.width(100.dp)
    ) {
        Text(label)
    }
}

@Composable
fun CategoryGrid(
    categories: List<com.biin95.bookkeeping.data.local.entity.Category>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    val chunked = categories.chunked(4)
    Column {
        chunked.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                row.forEach { category ->
                    val isSelected = selectedCategory == category.name
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelected(category.name) },
                        label = { Text(category.name, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.padding(2.dp)
                    )
                }
                // 填充空位
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.width(80.dp))
                }
            }
        }
    }
}
