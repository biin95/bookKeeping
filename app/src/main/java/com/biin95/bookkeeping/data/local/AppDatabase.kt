package com.biin95.bookkeeping.data.local

import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.biin95.bookkeeping.data.local.dao.CategoryDao
import com.biin95.bookkeeping.data.local.dao.TransactionDao
import com.biin95.bookkeeping.data.local.entity.Category
import com.biin95.bookkeeping.data.local.entity.Transaction

@Database(
    entities = [Transaction::class, Category::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        fun createCallback() = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Log.d("BookKeeping", "数据库 onCreate 回调开始，插入默认分类")
                try {
                val expenseCategories = listOf(
                    "Canyin", "Jiaotong", "Gouwu", "Yule", "Juzhu", "Yiliao", "Jiaoyu",
                    "Tongxun", "Fushi", "Riyong", "Shuiguo", "Lingshi", "Yinliao",
                    "Shejiao", "Chongwu", "Yundong", "Meirong", "Shuma", "Bangong", "Qita"
                )
                val expenseNames = listOf(
                    "餐饮", "交通", "购物", "娱乐",
                    "居住", "医疗", "教育", "通讯",
                    "服饰", "日用", "水果", "零食",
                    "饮料", "社交", "宠物", "运动",
                    "美容", "数码", "办公", "其他"
                )
                val incomeNames = listOf(
                    "工资", "奖金", "兼职", "理财",
                    "红包", "退款", "转账", "其他"
                )
                for (i in expenseNames.indices) {
                    db.execSQL(
                        "INSERT INTO categories (name, icon, type, sortOrder, isDefault, merchantMapping) VALUES (?, 'ic_category', 'expense', ?, 1, '')",
                        arrayOf(expenseNames[i], i)
                    )
                }
                for (i in incomeNames.indices) {
                    db.execSQL(
                        "INSERT INTO categories (name, icon, type, sortOrder, isDefault, merchantMapping) VALUES (?, 'ic_category', 'income', ?, 1, '')",
                        arrayOf(incomeNames[i], i)
                    )
                }
                Log.d("BookKeeping", "数据库 onCreate 回调完成，默认分类已插入")
                } catch (e: Exception) {
                    Log.e("BookKeeping", "数据库 onCreate 回调异常", e)
                    throw e
                }
            }
        }
    }
}
