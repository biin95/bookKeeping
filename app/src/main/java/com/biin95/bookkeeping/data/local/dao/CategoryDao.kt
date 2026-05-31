package com.biin95.bookkeeping.data.local.dao

import androidx.room.*
import com.biin95.bookkeeping.data.local.entity.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: Category): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<Category>)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder ASC")
    fun getByType(type: String): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getAllSync(): List<Category>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun getCount(): Int
}
