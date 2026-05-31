package com.biin95.bookkeeping.data.repository

import com.biin95.bookkeeping.data.local.dao.CategoryDao
import com.biin95.bookkeeping.data.local.entity.Category
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {
    fun getAll(): Flow<List<Category>> = categoryDao.getAll()

    suspend fun getAllSync(): List<Category> = categoryDao.getAllSync()

    fun getByType(type: String): Flow<List<Category>> = categoryDao.getByType(type)

    suspend fun insert(category: Category): Long = categoryDao.insert(category)

    suspend fun insertAll(categories: List<Category>) = categoryDao.insertAll(categories)

    suspend fun update(category: Category) = categoryDao.update(category)

    suspend fun delete(category: Category) = categoryDao.delete(category)

    suspend fun getCount(): Int = categoryDao.getCount()
}
