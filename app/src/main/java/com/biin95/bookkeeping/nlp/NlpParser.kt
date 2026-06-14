package com.biin95.bookkeeping.nlp

import android.util.Log
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自然语言记账解析器
 *
 * 输入: "美团买了杯冰美式花了9.9"
 * 输出: NlpResult(amount=9.9, merchant="美团", category="餐饮", description="冰美式")
 */
@Singleton
class NlpParser @Inject constructor() {

    companion object {
        private const val TAG = "NlpParser"
    }

    // 金额正则：支持 9.9、9.90、9、10、0.5 等格式
    private val amountPattern = Pattern.compile(
        "(\d+\.?\d{0,2})"
    )

    // 收入关键词（出现在金额之前或之后）
    private val incomeKeywords = listOf("收入", "入账", "收了", "收到", "赚了", "工资", "奖金", "红包")

    /**
     * 解析用户输入的自然语言记账文本
     */
    fun parse(text: String): NlpResult {
        val trimmed = text.trim()
        Log.d(TAG, "解析: '$trimmed'")

        // 1. 判断收入/支出
        val isIncome = incomeKeywords.any { trimmed.contains(it) }

        // 2. 提取金额
        val amount = extractAmount(trimmed)

        // 3. 提取商户名
        val (merchant, rawMerchant) = MerchantMapping.extractMerchant(trimmed)

        // 4. 推断分类
        val category = MerchantMapping.guessCategory(merchant, trimmed)

        // 5. 提取备注（金额之后的内容，或未匹配商户的相关文本）
        val description = extractDescription(trimmed, amount, rawMerchant)

        Log.d(TAG, "结果: amount=$amount, merchant=$merchant(raw=$rawMerchant), category=$category, desc=$description, income=$isIncome")

        return NlpResult(
            rawText = trimmed,
            amount = amount,
            merchant = merchant,
            rawMerchant = rawMerchant,
            category = category,
            description = description,
            isIncome = isIncome
        )
    }

    /**
     * 从文本中提取金额
     * 优先查找"花了X元""X元""X块钱"等格式
     */
    private fun extractAmount(text: String): Double? {
        // 优先级1: "花了\d+\.?\d{0,2}元/块钱"
        val spendPatterns = listOf(
            Pattern.compile("(?:花了|付了|支付|付款|消费|花费|用了)(\d+\.?\d{0,2})(?:元|块|块钱)?"),
            Pattern.compile("(\d+\.?\d{0,2})(?:元|块|块钱)"),
            Pattern.compile("(?:收入|入账|收到|赚了)(\d+\.?\d{0,2})(?:元|块|块钱)?"),
            Pattern.compile("(\d+\.\d{1,2})"),
            Pattern.compile("(\d+)")
        )

        for (pattern in spendPatterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amountStr = matcher.group(1)
                val amount = amountStr.toDoubleOrNull()
                if (amount != null && amount in 0.01..999999.0) {
                    // 排除年份（如2025）和明显的非金额数字
                    if (amountStr.length == 4 && amount >= 1900 && amount <= 2100) continue
                    // 排除时间（如1430）
                    if (amountStr.length == 4 && amount < 24.0) continue
                    return amount
                }
            }
        }

        return null
    }

    /**
     * 提取备注信息
     * 规则：取金额之后的文本（去掉商户名关键词），作为消费内容描述
     */
    private fun extractDescription(text: String, amount: Double?, rawMerchant: String?): String {
        var remaining = text

        // 去掉商户名关键词
        if (rawMerchant != null) {
            remaining = remaining.replace(rawMerchant, "")
        }

        // 去掉金额相关内容
        if (amount != null) {
            val amountStr = if (amount == amount.toLong().toDouble()) {
                amount.toLong().toString()
            } else {
                "%.2f".format(amount).replace(".?0*$".toRegex(), "")
            }
            // 去掉 "花了X" "X元" "X块钱" 等
            remaining = remaining.replace(Regex("花了?$amountStr"), "")
            remaining = remaining.replace(Regex("$amountStr(?:元|块|块钱)?"), "")
        }

        // 去掉常见的无用词
        remaining = remaining
            .replace(Regex("(?:买了|买了杯|买了个|买了件|买了份|买了张|买|下了单|下单|点了个|点了份|点了)"), "")
            .replace(Regex("(?:我在|在|我|今天|昨天|刚刚|刚才)"), "")
            .replace(Regex("(?:花了|付了|支付|付款|消费|花费|用了)"), "")
            .replace(Regex("[，。！？、,.!?]"), " ")
            .trim()

        return if (remaining.isBlank()) "" else remaining
    }
}
