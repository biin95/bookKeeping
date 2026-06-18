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
        _uiState.update { it.copy(messages = addMessageWithDateSeparator(it.messages, userMsg)) }

        if (result.amount == null || result.amount <= 0) {
            val errorMsg = ChatMessage(
                id = nextMsgId++,
                type = "response",
                text = "未识别到金额，请确认输入了金额信息",
                nlpResult = result,
                isSaved = false
            )
            _uiState.update { it.copy(messages = addMessageWithDateSeparator(it.messages, errorMsg)) }
        } else {
            autoSaveAndAddMessage(result)
        }
    }

    fun reloadHistory() {
        viewModelScope.launch {
            val history = repository.getAllForExport().sortedBy { it.timestamp }
            val msgs = mutableListOf<ChatMessage>()
            history.forEach { txn ->
                msgs.add(ChatMessage(
                    id = nextMsgId++,
                    type = "user",
                    text = txn.rawText.ifEmpty { "记账" },
                    timestamp = txn.timestamp
                ))
                msgs.add(ChatMessage(
                    id = nextMsgId++,
                    type = "response",
                    text = txn.rawText,
                    nlpResult = NlpResult(
                        rawText = txn.rawText,
                        amount = kotlin.math.abs(txn.amount),
                        merchant = txn.merchant.ifEmpty { null },
                        rawMerchant = null,
                        category = txn.category,
                        description = txn.description,
                        isIncome = txn.isIncome
                    ),
                    transaction = txn,
                    isSaved = true,
                    timestamp = txn.timestamp
                ))
            if (msgs.isEmpty()) msgs.add(ChatMessage(id = nextMsgId++, type = "system", text = "说句话就能记账，比如\"美团买了杯冰美式花了9.9\""))
            }
            _uiState.update { it.copy(messages = msgs) }
        }
    }


    private fun autoSaveAndAddMessage(result: NlpResult) {
        viewModelScope.launch {
            val transaction = Transaction(
                amount = -result.amount!!,
                category = result.category,
                merchant = result.merchant ?: "",
                description = result.description,
                source = "nlp",
                isIncome = false,
                rawText = result.rawText
            )
            val savedId = repository.insert(transaction)

            val responseMsg = ChatMessage(
                id = nextMsgId++,
                type = "response",
                text = buildResponseText(result),
                nlpResult = result,
                transaction = transaction.copy(id = savedId),
                isSaved = true
            )
            _uiState.update { it.copy(messages = addMessageWithDateSeparator(it.messages, responseMsg)) }

            BackupManager.tryAutoBackup(context, repository)
            _timeRange.value = getTimeRange(_uiState.value.timePeriod)
        }
    }

    fun updateTransaction(transactionId: Long, amount: Double, category: String, merchant: String, description: String) {
        viewModelScope.launch {
            val existing = repository.getById(transactionId) ?: return@launch
            val updated = existing.copy(
                amount = -amount,
                category = category,
                merchant = merchant,
                description = description
            )
            repository.update(updated)

            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map { msg ->
                        if (msg.transaction?.id == transactionId) {
                            msg.copy(
                                transaction = updated,
                                text = "✅ ¥" + String.format("%.2f", amount) + " " + merchant,
                                nlpResult = msg.nlpResult?.copy(
                                    amount = amount,
                                    category = category,
                                    merchant = merchant,
                                    description = description
                                )
                            )
                        } else msg
                    }
                )
            }
            _timeRange.value = getTimeRange(_uiState.value.timePeriod)
        }
    }

    fun setTimePeriod(period: TimePeriod) {
        _uiState.update { it.copy(timePeriod = period) }
        _timeRange.value = getTimeRange(period)
        viewModelScope.launch {
            context.chatPrefs.edit { it[KEY_TIME_PERIOD] = period.ordinal }
        }
    }

    private fun needsDateSeparator(messages: List<ChatMessage>, newMsg: ChatMessage): Boolean {
        if (messages.isEmpty()) return false
        val lastMsg = messages.lastOrNull { it.type != "date_separator" } ?: return false
        val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = lastMsg.timestamp }
        val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = newMsg.timestamp }
        return cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR) ||
                cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR)
    }

    private fun formatDateSeparator(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
        val now = java.util.Calendar.getInstance()
        return when {
            cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR) &&
                cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) -> "4eca5929"
            cal.apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR) &&
                cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) -> "66285929"
            else -> java.text.SimpleDateFormat("M6708d65e5", java.util.Locale.getDefault()).format(Date(timestamp))
        }
    }

    private fun addMessageWithDateSeparator(messages: List<ChatMessage>, newMsg: ChatMessage): List<ChatMessage> {
        val result = messages.toMutableList()
        if (needsDateSeparator(messages, newMsg)) {
            val dateMsg = ChatMessage(
                id = -newMsg.id,
                type = "date_separator",
                text = formatDateSeparator(newMsg.timestamp),
                timestamp = newMsg.timestamp
            )
            result.add(dateMsg)
        }
        result.add(newMsg)
        return result
    }

        private fun buildResponseText(result: NlpResult): String {
        val amount = "¥" + String.format("%.2f", result.amount)
        val merchant = result.merchant ?: ""
        val category = result.category
        val desc = if (result.description.isNotBlank()) "(" + result.description + ")" else ""
        return "$amount $merchant $category$desc"
    }

    private fun addSystemMessage(text: String) {
        val msg = ChatMessage(id = nextMsgId++, type = "system", text = text)
        _uiState.update { it.copy(messages = addMessageWithDateSeparator(it.messages, msg)) }
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
