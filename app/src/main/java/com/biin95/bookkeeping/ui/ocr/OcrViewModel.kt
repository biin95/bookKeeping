package com.biin95.bookkeeping.ui.ocr

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import com.biin95.bookkeeping.ocr.OcrEngine
import com.biin95.bookkeeping.ocr.OcrResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OcrViewModel @Inject constructor(
    private val ocrEngine: OcrEngine,
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val uiState: StateFlow<OcrUiState> = _uiState.asStateFlow()

    private val _editableAmount = MutableStateFlow("")
    val editableAmount: StateFlow<String> = _editableAmount.asStateFlow()

    private val _editableMerchant = MutableStateFlow("")
    val editableMerchant: StateFlow<String> = _editableMerchant.asStateFlow()

    private val _editableCategory = MutableStateFlow("其他")
    val editableCategory: StateFlow<String> = _editableCategory.asStateFlow()

    private val _isIncome = MutableStateFlow(false)
    val isIncome: StateFlow<Boolean> = _isIncome.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    // 多笔交易
    private var allResults: List<OcrResult> = emptyList()
    private var currentIndex = 0
    private var currentResult: OcrResult? = null

    fun recognizeImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = OcrUiState.Processing
            try {
                val text = ocrEngine.recognizeTextFromUri(context, uri)
                processOcrText(text)
            } catch (e: Exception) {
                _uiState.value = OcrUiState.Error("识别失败: ${e.message}")
            }
        }
    }

    fun captureFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString() ?: ""
            if (text.isNotBlank()) {
                processOcrText(text)
            } else {
                _uiState.value = OcrUiState.Error("剪贴板为空")
            }
        } else {
            _uiState.value = OcrUiState.Error("剪贴板为空")
        }
    }

    private fun processOcrText(text: String) {
        val results = ocrEngine.parseMultipleTransactions(text)
        allResults = results
        currentIndex = 0

        if (results.isEmpty()) {
            _uiState.value = OcrUiState.Error("未识别到任何内容")
            return
        }

        loadResult(results[0])

        if (results.size > 1) {
            _uiState.value = OcrUiState.MultiResult(
                results = results,
                currentIndex = 0,
                currentResult = results[0]
            )
        } else {
            _uiState.value = OcrUiState.Result(results[0])
        }
    }

    private fun loadResult(result: OcrResult) {
        currentResult = result
        _editableAmount.value = result.amount?.let { "%.2f".format(it) } ?: ""
        _editableMerchant.value = result.merchant ?: ""
        _editableCategory.value = guessCategory(result)
        _isIncome.value = false
    }

    // 切换到下一笔
    fun nextResult() {
        if (currentIndex < allResults.size - 1) {
            currentIndex++
            loadResult(allResults[currentIndex])
            _uiState.value = OcrUiState.MultiResult(
                results = allResults,
                currentIndex = currentIndex,
                currentResult = allResults[currentIndex]
            )
        }
    }

    // 切换到上一笔
    fun previousResult() {
        if (currentIndex > 0) {
            currentIndex--
            loadResult(allResults[currentIndex])
            _uiState.value = OcrUiState.MultiResult(
                results = allResults,
                currentIndex = currentIndex,
                currentResult = allResults[currentIndex]
            )
        }
    }

    fun setAmount(value: String) {
        if (value.isEmpty() || value.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _editableAmount.value = value
        }
    }

    fun setMerchant(value: String) { _editableMerchant.value = value }
    fun setCategory(value: String) { _editableCategory.value = value }
    fun setIsIncome(value: Boolean) { _isIncome.value = value }

    fun saveTransaction() {
        val amountValue = _editableAmount.value.toDoubleOrNull() ?: return
        if (amountValue <= 0) return

        viewModelScope.launch {
            val transaction = Transaction(
                amount = if (_isIncome.value) amountValue else -amountValue,
                category = _editableCategory.value,
                merchant = _editableMerchant.value,
                description = currentResult?.items?.joinToString(", ") ?: "",
                paymentMethod = currentResult?.paymentMethod ?: "",
                source = "screenshot",
                isIncome = _isIncome.value,
                rawText = currentResult?.rawText ?: ""
            )
            repository.insert(transaction)

            // 如果是多笔，保存当前这笔后自动跳到下一笔
            if (allResults.size > 1 && currentIndex < allResults.size - 1) {
                nextResult()
            } else {
                _saveSuccess.value = true
            }
        }
    }

    // 一键保存所有识别到的交易
    fun saveAllTransactions() {
        viewModelScope.launch {
            for (result in allResults) {
                val amount = result.amount ?: continue
                val transaction = Transaction(
                    amount = -amount,
                    category = guessCategory(result),
                    merchant = result.merchant ?: "",
                    description = result.items.joinToString(", "),
                    paymentMethod = result.paymentMethod ?: "",
                    source = "screenshot",
                    isIncome = false,
                    rawText = result.rawText
                )
                repository.insert(transaction)
            }
            _saveSuccess.value = true
        }
    }

    fun reset() {
        _uiState.value = OcrUiState.Idle
        allResults = emptyList()
        currentIndex = 0
        currentResult = null
        _editableAmount.value = ""
        _editableMerchant.value = ""
        _editableCategory.value = "其他"
        _isIncome.value = false
    }

    private fun guessCategory(result: OcrResult): String {
        val text = (result.merchant ?: "") + " " + result.items.joinToString(" ")
        val lower = text.lowercase()
        return when {
            lower.containsAny("餐", "饭", "食", "外卖", "美团", "饿了么", "肯德基", "麦当劳", "奶茶") -> "餐饮"
            lower.containsAny("滴滴", "打车", "地铁", "公交", "高铁", "火车", "飞机", "加油", "停车") -> "交通"
            lower.containsAny("淘宝", "京东", "拼多多", "天猫", "购物", "超市") -> "购物"
            lower.containsAny("电影", "游戏", "视频", "音乐", "KTV", "娱乐") -> "娱乐"
            lower.containsAny("房租", "水电", "物业", "燃气", "居住") -> "居住"
            lower.containsAny("医院", "药", "诊所", "医疗") -> "医疗"
            lower.containsAny("话费", "流量", "宽带") -> "通讯"
            lower.containsAny("工资", "薪水") -> "工资"
            lower.containsAny("红包") -> "红包"
            lower.containsAny("退款") -> "退款"
            else -> "其他"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
