package com.biin95.bookkeeping.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                  // 分类名称
    val icon: String = "ic_category",  // 图标名
    val type: String = "expense",      // expense/income
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,    // 是否为预置分类
    val merchantMapping: String = ""   // 商户名关键词映射，逗号分隔
)
