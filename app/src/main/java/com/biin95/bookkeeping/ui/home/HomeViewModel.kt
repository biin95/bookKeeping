package com.biin95.bookkeeping.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    init {
        Log.d("BookKeeping", "HomeViewModel 初始化")
    }

    private val _currentTimePeriod = MutableStateFlow(TimePeriod.MONTH)
    val currentTimePeriod: StateFlow<TimePeriod> = _currentTimePeriod.asStateFlow()

    private val _timeRange = MutableStateFlow(getTimeRange(TimePeriod.MONTH))
    val timeRange: StateFlow<Pair<Long, Long>> = _timeRange.asStateFlow()

    val transactions: StateFlow<List<Transaction>> = _timeRange.flatMapLatest { (start, end) ->
        repository.getByTimeRange(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalExpense: StateFlow<Double?> = _timeRange.flatMapLatest { (start, end) ->
        repository.getTotalExpense(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalIncome: StateFlow<Double?> = _timeRange.flatMapLatest { (start, end) ->
        repository.getTotalIncome(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTimePeriod(period: TimePeriod) {
        _currentTimePeriod.value = period
        _timeRange.value = getTimeRange(period)
    }

    /** 强制刷新当前时间范围的数据（用于页面重新可见时） */
    fun refresh() {
        _timeRange.value = getTimeRange(_currentTimePeriod.value)
    }

    private fun getTimeRange(period: TimePeriod): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        val end = calendar.timeInMillis

        when (period) {
            TimePeriod.DAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
            TimePeriod.YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
            }
        }

        return calendar.timeInMillis to end
    }
}
