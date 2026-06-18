package com.biin95.bookkeeping.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biin95.bookkeeping.data.local.dao.CategorySummary
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import com.biin95.bookkeeping.ui.home.TimePeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

enum class StatsViewMode { OVERVIEW, DETAIL }

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _currentPeriod = MutableStateFlow(TimePeriod.MONTH)
    val currentPeriod: StateFlow<TimePeriod> = _currentPeriod.asStateFlow()

    private val _viewMode = MutableStateFlow(StatsViewMode.OVERVIEW)
    val viewMode: StateFlow<StatsViewMode> = _viewMode.asStateFlow()

    private val _timeRange = MutableStateFlow(getTimeRange(TimePeriod.MONTH))

    val categorySummary: StateFlow<List<CategorySummary>> = _timeRange.flatMapLatest { (start, end) ->
        repository.getCategorySummary(start, end)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val totalExpense: StateFlow<Double?> = _timeRange.flatMapLatest { (start, end) ->
        repository.getTotalExpense(start, end)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val transactions: StateFlow<List<Transaction>> = _timeRange.flatMapLatest { (start, end) ->
        repository.getByTimeRange(start, end)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteTransaction(id: Long) {
        viewModelScope.launch { repository.deleteById(id) }
    }

    fun batchDelete(ids: List<Long>) {
        viewModelScope.launch {
            ids.forEach { repository.deleteById(it) }
        }
    }

    fun setPeriod(period: TimePeriod) {
        _currentPeriod.value = period
        _timeRange.value = getTimeRange(period)
    }

    fun setViewMode(mode: StatsViewMode) {
        _viewMode.value = mode
    }

    fun toggleViewMode() {
        _viewMode.value = if (_viewMode.value == StatsViewMode.OVERVIEW) StatsViewMode.DETAIL else StatsViewMode.OVERVIEW
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
