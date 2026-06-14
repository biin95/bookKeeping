package com.biin95.bookkeeping.nlp

data class NlpResult(
    val rawText: String,
    val amount: Double?,
    val merchant: String?,      // 归一化后的商户名
    val rawMerchant: String?,   // 用户原文中匹配到的词
    val category: String,
    val description: String,    // 备注（金额之后的内容）
    val isIncome: Boolean
)
