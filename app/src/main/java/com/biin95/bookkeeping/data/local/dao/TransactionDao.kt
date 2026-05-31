package com.biin95.bookkeeping.data.local.dao

import androidx.room.*
import com.biin95.bookkeeping.data.local.entity.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): Transaction?

    @Query("""
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp DESC
    """)
    fun getByTimeRange(startTime: Long, endTime: Long): Flow<List<Transaction>>

    @Query("""
        SELECT * FROM transactions
        WHERE timestamp BETWEEN :startTime AND :endTime AND isIncome = :isIncome
        ORDER BY timestamp DESC
    """)
    fun getByTimeRangeAndType(startTime: Long, endTime: Long, isIncome: Boolean): Flow<List<Transaction>>

    @Query("""
        SELECT category, SUM(amount) as total
        FROM transactions
        WHERE timestamp BETWEEN :startTime AND :endTime AND isIncome = 0
        GROUP BY category
        ORDER BY total DESC
    """)
    fun getCategorySummary(startTime: Long, endTime: Long): Flow<List<CategorySummary>>

    @Query("""
        SELECT SUM(amount) FROM transactions
        WHERE timestamp BETWEEN :startTime AND :endTime AND isIncome = 0
    """)
    fun getTotalExpense(startTime: Long, endTime: Long): Flow<Double?>

    @Query("""
        SELECT SUM(amount) FROM transactions
        WHERE timestamp BETWEEN :startTime AND :endTime AND isIncome = 1
    """)
    fun getTotalIncome(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT * FROM transactions WHERE merchant LIKE '%' || :keyword || '%' ORDER BY timestamp DESC")
    fun searchByMerchant(keyword: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAllForExport(): List<Transaction>

    @Query("SELECT COUNT(*) FROM transactions")
    fun getCount(): Flow<Int>
}

data class CategorySummary(
    val category: String,
    val total: Double
)
