package com.biin95.bookkeeping.ui.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biin95.bookkeeping.data.backup.BackupManager
import com.biin95.bookkeeping.data.local.dao.CategorySummary
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import com.biin95.bookkeeping.nlp.NlpParser
import com.biin95.bookkeeping.nlp.NlpResult
import com.biin95.bookkeeping.ui.home.TimePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

private val Context.chatPrefs: DataStore<Preferences> by preferencesDataStore(name = "chat_prefs")

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val timePeriod: TimePeriod = TimePeriod.WEEK,
    val totalExpense: Double? = null,
    val categorySummary: List<CategorySummary> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val parser: NlpParser,
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var nextMsgId = 1L
    private val _timeRange = MutableStateFlow(getTimeRange(TimePeriod.WEEK))

    init {
        viewModelScope.launch {
            val saved = context.chatPrefs.data.first()[KEY_TIME_PERIOD] ?: TimePeriod.WEEK.ordinal
            val period = TimePeriod.entries.getOrElse(saved) { TimePeriod.WEEK }
            setTimePeriod(period)
            addSystemMessage("\u8bf4\u53e5\u8bdd\u5c31\u80fd\u8bb0\u8d26\uff0c\u6bd4\u5982\u201c\u7f8e\u56e2\u4e70\u4e86\u676f\u5496\u5561\u82b15.9\u201d")
        }
    }

    private val _categorySummary = _timeRange.flatMapLatest { (start, end) ->
        repository.getCategorySummary(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _totalExpense = _timeRange.flatMapLatest { (start, end) ->
        repository.getTotalExpense(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            combine(_totalExpense, _categorySummary) { expense, summary ->
                _uiState.update { it.copy(totalExpense = expense, categorySummary = summary) }
            }.collect()
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        _uiState.update { it.copy(inputText = "") }

        val msgId = nextMsgId++
        val result = parser.parse(text)

        val userMsg = ChatMessage(id = msgId, type = "user", text = text)
        _uiState.update { it.copy(messages = it.messages + userMsg) }

        if (result.amount == null) {
            val errorMsg = ChatMessage(
                id = nextMsgId++,
                type = "response",
                text = "\u672a\u8bc6\u522b\u5230\u91d1\u989d\uff0c\u8bf7\u786e\u8ba4\u8f93\u5165\u4e86\u91d1\u989d\u4fe1\u606f",
                nlpResult = result
            )
            _uiState.update { it.copy(messages = it.messages + errorMsg) }
        } else {
            val respMsg = ChatMessage(
                id = nextMsgId++,
                type = "response",
                text = buildResponseText(result),
                nlpResult = result
            )
            _uiState.update { it.copy(messages = it.messages + respMsg) }
        }
    }

    fun confirmSave(nlpResult: NlpResult) {
        val amount = nlpResult.amount ?: return
        if (amount <= 0) return

        viewModelScope.launch {
            val transaction = Transaction(
                amount = -amount,
                category = nlpResult.category,
                merchant = nlpResult.merchant ?: "",
                description = nlpResult.description,
                source = "nlp",
                isIncome = false,
                rawText = nlpResult.rawText
            )
            val id = repository.insert(transaction)

            val text = "\u2705 \u00a5" + String.format("%.2f", amount) + " " + (nlpResult.merchant ?: nlpResult.category)
            val confirmed = ChatMessage(
                id = nextMsgId++,
                type = "confirmed",
                text = text,
                transaction = transaction.copy(id = id)
            )
            _uiState.update { state ->
                val filtered = state.messages.filter { it.nlpResult !== nlpResult }
                state.copy(messages = filtered + confirmed)
            }

            BackupManager.tryAutoBackup(context, repository)
        }
    }

    fun rejectSave(nlpResult: NlpResult) {
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.nlpResult !== nlpResult })
        }
    }

    fun setTimePeriod(period: TimePeriod) {
        _uiState.update { it.copy(timePeriod = period) }
        _timeRange.value = getTimeRange(period)
        viewModelScope.launch {
            context.chatPrefs.edit { it[KEY_TIME_PERIOD] = period.ordinal }
        }
    }

    private fun buildResponseText(result: NlpResult): String {
        val amount = "\u00a5" + String.format("%.2f", result.amount)
        val merchant = result.merchant ?: ""
        val category = result.category
        val desc = if (result.description.isNotBlank()) "(" + result.description + ")" else ""
        return "$amount $merchant $category$desc"
    }

    private fun addSystemMessage(text: String) {
        val msg = ChatMessage(id = nextMsgId++, type = "system", text = text)
        _uiState.update { it.copy(messages = it.messages + msg) }
    }

    companion object {
        private val KEY_TIME_PERIOD = intPreferencesKey("time_period")
    }

    private fun getTimeRange(period: TimePeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis
        when (period) {
            TimePeriod.DAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
            }
        }
        return calendar.timeInMillis to end
    }
}