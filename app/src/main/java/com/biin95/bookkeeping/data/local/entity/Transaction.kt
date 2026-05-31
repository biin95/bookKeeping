package com.biin95.bookkeeping.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,                // 金额（正=收入，负=支出）
    val category: String,              // 分类名称
    val merchant: String = "",         // 商户名
    val description: String = "",      // 备注/商品名
    val paymentMethod: String = "",    // 支付方式（支付宝/微信/现金等）
    val source: String = "manual",     // 来源：manual/screenshot/notification/sms/accessibility
    val timestamp: Long = System.currentTimeMillis(),
    val isIncome: Boolean = false,     // true=收入，false=支出
    val screenshotPath: String? = null, // 截图路径（截图记账时）
    val rawText: String = "",          // OCR原始识别文本
    val isDuplicate: Boolean = false   // 是否被标记为重复
)
