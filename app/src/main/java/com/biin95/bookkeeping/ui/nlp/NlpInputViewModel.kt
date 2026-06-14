package com.biin95.bookkeeping.ui.nlp

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biin95.bookkeeping.data.backup.BackupManager
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import com.biin95.bookkeeping.nlp.NlpParser
import com.biin95.bookkeeping.nlp.NlpResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NlpInputUiState(
    val inputText: String = "",
    val parseResult: NlpResult? = null,
    val hasError: Boolean = false,
    val errorMessage: String = "",
    val isSaved: Boolean = false
)

@HiltViewModel
class NlpInputViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: NlpParser,
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NlpInputUiState())
    val uiState: StateFlow<NlpInputUiState> = _uiState.asStateFlow()

    fun updateInput(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)

        if (text.isBlank()) {
            _uiState.value = _uiState.value.copy(
                parseResult = null,
                hasError = false,
                errorMessage = ""
            )
            return
        }

        val result = parser.parse(text)

        if (result.amount == null) {
            _uiState.value = _uiState.value.copy(
                parseResult = result,
                hasError = true,
                errorMessage = "未识别到金额，请确认输入了金额信息"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                parseResult = result,
                hasError = false,
                errorMessage = ""
            )
        }
    }

    fun save() {
        val result = _uiState.value.parseResult ?: return
        val amount = result.amount ?: return
        if (amount <= 0) return

        viewModelScope.launch {
            val transaction = Transaction(
                amount = if (result.isIncome) amount else -amount,
                category = result.category,
                merchant = result.merchant ?: "",
                description = result.description,
                source = "nlp",
                isIncome = result.isIncome,
                rawText = result.rawText
            )
            repository.insert(transaction)

            // 自动备份（每天最多一次）
            BackupManager.tryAutoBackup(context, repository)

            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }

    fun reset() {
        _uiState.value = NlpInputUiState()
    }
}
