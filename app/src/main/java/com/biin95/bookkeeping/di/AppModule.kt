package com.biin95.bookkeeping.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.biin95.bookkeeping.data.local.AppDatabase
import com.biin95.bookkeeping.data.local.dao.CategoryDao
import com.biin95.bookkeeping.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        Log.d("BookKeeping", "provideDatabase 开始构建数据库")
        try {
            val db = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "bookkeeping.db"
            )
                .addCallback(AppDatabase.createCallback())
                .build()
            Log.d("BookKeeping", "provideDatabase 数据库构建成功")
            return db
        } catch (e: Exception) {
            Log.e("BookKeeping", "provideDatabase 数据库构建失败", e)
            throw e
        }
    }

    @Provides
    fun provideTransactionDao(db: AppDatabase): TransactionDao = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: AppDatabase): CategoryDao = db.categoryDao()
}
