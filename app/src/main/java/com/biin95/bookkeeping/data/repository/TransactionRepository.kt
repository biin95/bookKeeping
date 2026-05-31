package com.biin95.bookkeeping.data.repository

import com.biin95.bookkeeping.data.local.dao.CategorySummary
import com.biin95.bookkeeping.data.local.dao.TransactionDao
import com.biin95.bookkeeping.data.local.entity.Transaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    fun getAll(): Flow<List<Transaction>> = transactionDao.getAll()

    suspend fun getById(id: Long): Transaction? = transactionDao.getById(id)

    fun getByTimeRange(startTime: Long, endTime: Long): Flow<List<Transaction>> =
        transactionDao.getByTimeRange(startTime, endTime)

    fun getByTimeRangeAndType(startTime: Long, endTime: Long, isIncome: Boolean): Flow<List<Transaction>> =
        transactionDao.getByTimeRangeAndType(startTime, endTime, isIncome)

    fun getCategorySummary(startTime: Long, endTime: Long): Flow<List<CategorySummary>> =
        transactionDao.getCategorySummary(startTime, endTime)

    fun getTotalExpense(startTime: Long, endTime: Long): Flow<Double?> =
        transactionDao.getTotalExpense(startTime, endTime)

    fun getTotalIncome(startTime: Long, endTime: Long): Flow<Double?> =
        transactionDao.getTotalIncome(startTime, endTime)

    suspend fun insert(transaction: Transaction): Long = transactionDao.insert(transaction)

    suspend fun insertAll(transactions: List<Transaction>) = transactionDao.insertAll(transactions)

    suspend fun update(transaction: Transaction) = transactionDao.update(transaction)

    suspend fun delete(transaction: Transaction) = transactionDao.delete(transaction)

    suspend fun deleteById(id: Long) = transactionDao.deleteById(id)

    fun searchByMerchant(keyword: String): Flow<List<Transaction>> =
        transactionDao.searchByMerchant(keyword)

    suspend fun getAllForExport(): List<Transaction> = transactionDao.getAllForExport()

    fun getCount(): Flow<Int> = transactionDao.getCount()
}
