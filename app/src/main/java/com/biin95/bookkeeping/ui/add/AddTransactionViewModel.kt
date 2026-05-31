package com.biin95.bookkeeping.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biin95.bookkeeping.data.local.entity.Category
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.CategoryRepository
import com.biin95.bookkeeping.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _isIncome = MutableStateFlow(false)
    val isIncome: StateFlow<Boolean> = _isIncome.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _selectedCategory = MutableStateFlow("")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _merchant = MutableStateFlow("")
    val merchant: StateFlow<String> = _merchant.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _paymentMethod = MutableStateFlow("")
    val paymentMethod: StateFlow<String> = _paymentMethod.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private var editingTransactionId: Long? = null

    val categories: StateFlow<List<Category>> = _isIncome.flatMapLatest { isIncome ->
        categoryRepository.getByType(if (isIncome) "income" else "expense")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setIsIncome(value: Boolean) {
        _isIncome.value = value
        _selectedCategory.value = ""
    }

    fun setAmount(value: String) {
        // 只允许数字和一个小数点
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _amount.value = value
        }
    }

    fun setSelectedCategory(value: String) { _selectedCategory.value = value }
    fun setMerchant(value: String) { _merchant.value = value }
    fun setDescription(value: String) { _description.value = value }
    fun setPaymentMethod(value: String) { _paymentMethod.value = value }

    fun loadTransaction(id: Long) {
        viewModelScope.launch {
            transactionRepository.getById(id)?.let { t ->
                editingTransactionId = t.id
                _isIncome.value = t.isIncome
                _amount.value = "%.2f".format(kotlin.math.abs(t.amount))
                _selectedCategory.value = t.category
                _merchant.value = t.merchant
                _description.value = t.description
                _paymentMethod.value = t.paymentMethod
            }
        }
    }

    fun save() {
        val amountValue = _amount.value.toDoubleOrNull() ?: return
        if (amountValue <= 0) return
        if (_selectedCategory.value.isBlank()) return

        viewModelScope.launch {
            val finalAmount = if (_isIncome.value) amountValue else -amountValue
            val transaction = Transaction(
                id = editingTransactionId ?: 0,
                amount = finalAmount,
                category = _selectedCategory.value,
                merchant = _merchant.value,
                description = _description.value,
                paymentMethod = _paymentMethod.value,
                source = "manual",
                isIncome = _isIncome.value,
                timestamp = if (editingTransactionId != null) {
                    transactionRepository.getById(editingTransactionId!!)?.timestamp
                        ?: System.currentTimeMillis()
                } else System.currentTimeMillis()
            )
            if (editingTransactionId != null) {
                transactionRepository.update(transaction)
            } else {
                transactionRepository.insert(transaction)
            }
            _saveSuccess.value = true
        }
    }
}
