package com.biin95.bookkeeping.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

data class OcrResult(
    val rawText: String,
    val amount: Double?,
    val merchant: String?,
    val items: List<String>,
    val paymentMethod: String?,
    val dateTime: String?
)

@Singleton
class OcrEngine @Inject constructor() {

    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    suspend fun recognizeFromUri(context: Context, uri: Uri): OcrResult {
        val image = InputImage.fromFilePath(context, uri)
        return recognize(image)
    }

    suspend fun recognizeTextFromUri(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        return recognizeText(image)
    }

    suspend fun recognizeFromBitmap(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        return recognize(image)
    }

    private suspend fun recognize(image: InputImage): OcrResult {
        val text = recognizeText(image)
        return parseOcrText(text)
    }

    private suspend fun recognizeText(image: InputImage): String {
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                }
                .addOnFailureListener {
                    cont.resume("")
                }
        }
    }

    fun parseOcrText(text: String): OcrResult {
        val amount = extractAmount(text)
        val merchant = extractMerchant(text)
        val items = extractItems(text)
        val paymentMethod = extractPaymentMethod(text)
        val dateTime = extractDateTime(text)

        return OcrResult(
            rawText = text,
            amount = amount,
            merchant = merchant,
            items = items,
            paymentMethod = paymentMethod,
            dateTime = dateTime
        )
    }

    /**
     * 从 OCR 文本中解析多笔交易（一张截图包含多笔支付时）
     */
    fun parseMultipleTransactions(text: String): List<OcrResult> {
        val allAmounts = extractAllAmounts(text)
        if (allAmounts.isEmpty()) {
            // 没找到金额，返回一个空结果
            return listOf(OcrResult(text, null, null, emptyList(), extractPaymentMethod(text), extractDateTime(text)))
        }

        val paymentMethod = extractPaymentMethod(text)
        val dateTime = extractDateTime(text)
        val lines = text.split("\n")

        return allAmounts.map { (amount, matchIndex) ->
            // 取金额附近 ±2 行的文本作为上下文
            val contextText = getContextAroundPosition(lines, text, matchIndex)
            val merchant = extractMerchant(contextText)
            val items = extractItems(contextText)

            OcrResult(
                rawText = contextText,
                amount = amount,
                merchant = merchant,
                items = items,
                paymentMethod = paymentMethod,
                dateTime = dateTime
            )
        }
    }

    // 提取所有金额及其在原文中的位置
    private fun extractAllAmounts(text: String): List<Pair<Double, Int>> {
        val results = mutableListOf<Pair<Double, Int>>()
        val patterns = listOf(
            Pattern.compile("(?:实付|实收款|支付|付款|金额|合计|总计|花费|消费|扣款|转入|转出)[：:\\s]*[¥￥]?\\s*(\\d+\\.?\\d{0,2})"),
            Pattern.compile("[¥￥]\\s*(\\d+\\.?\\d{0,2})"),
            Pattern.compile("(?:CNY|RMB)\\s*(\\d+\\.?\\d{0,2})"),
            Pattern.compile("(\\d+\\.\\d{2})\\s*元")
        )

        val seenRanges = mutableSetOf<Int>() // 避免同一位置重复匹配

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val amountStr = matcher.group(1) ?: continue
                val amount = amountStr.toDoubleOrNull() ?: continue
                if (amount <= 0) continue
                val pos = matcher.start()
                // 避免在相近位置重复（±5字符内算同一笔）
                if (seenRanges.any { Math.abs(it - pos) < 5 }) continue
                seenRanges.add(pos)
                results.add(amount to pos)
            }
        }

        // 按位置排序，保持文本中的出现顺序
        return results.sortedBy { it.second }
    }

    // 根据金额在原文中的位置，取出附近 ±2 行作为上下文
    private fun getContextAroundPosition(lines: List<String>, fullText: String, matchIndex: Int): String {
        // 找到 matchIndex 所在的行号
        var charCount = 0
        var targetLine = 0
        for ((i, line) in lines.withIndex()) {
            charCount += line.length + 1 // +1 for \n
            if (charCount > matchIndex) {
                targetLine = i
                break
            }
        }

        val start = maxOf(0, targetLine - 2)
        val end = minOf(lines.size - 1, targetLine + 2)
        return lines.subList(start, end + 1).joinToString("\n")
    }

    private fun extractAmount(text: String): Double? {
        val patterns = listOf(
            Pattern.compile("(?:实付|实收款|支付|付款|金额|合计|总计|花费|消费|扣款|转入|转出)[：:\\s]*[¥￥]?\\s*(\\d+\\.?\\d{0,2})"),
            Pattern.compile("[¥￥]\\s*(\\d+\\.?\\d{0,2})"),
            Pattern.compile("(?:CNY|RMB)\\s*(\\d+\\.?\\d{0,2})"),
            Pattern.compile("(\\d+\\.\\d{2})\\s*元")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val amount = matcher.group(1)?.toDoubleOrNull()
                if (amount != null && amount > 0) return amount
            }
        }
        return null
    }

    private fun extractMerchant(text: String): String? {
        val patterns = listOf(
            Pattern.compile("(?:商户|商家|店铺|收款方|对方)[：:\\s]*(.+?)(?:\\n|$)"),
            Pattern.compile("(?:付款给|支付到|转账给)[：:\\s]*(.+?)(?:\\n|$)"),
            Pattern.compile("(?:交易对方|收款方)[：:\\s]*(.+?)(?:\\n|$)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim()
            }
        }
        return null
    }

    private fun extractItems(text: String): List<String> {
        val items = mutableListOf<String>()
        val lines = text.split("\n")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("×") || trimmed.contains("x") || trimmed.contains("X")) {
                if (trimmed.matches(Regex(".*[¥￥]\\s*\\d+.*"))) {
                    items.add(trimmed)
                }
            }
        }
        return items
    }

    private fun extractPaymentMethod(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("支付宝") || lower.contains("alipay") -> "支付宝"
            lower.contains("微信") || lower.contains("wechat") -> "微信"
            lower.contains("银行卡") || lower.contains("储蓄卡") -> "银行卡"
            lower.contains("信用卡") -> "信用卡"
            lower.contains("云闪付") -> "云闪付"
            else -> null
        }
    }

    private fun extractDateTime(text: String): String? {
        val pattern = Pattern.compile("(\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}[\\sT]\\d{1,2}:\\d{2}(?::\\d{2})?)")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(1) else null
    }
}
