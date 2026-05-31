package com.biin95.bookkeeping.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    suspend fun recognizeFromBitmap(bitmap: Bitmap): OcrResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        return recognize(image)
    }

    private suspend fun recognize(image: InputImage): OcrResult {
        val text = suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                }
                .addOnFailureListener {
                    cont.resume("")
                }
        }

        return parseOcrText(text)
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

    private fun extractAmount(text: String): Double? {
        // 匹配金额模式：¥xx.xx, 支付xx.xx, 付款xx.xx, 实付xx.xx, 金额xx.xx, 合计xx.xx
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
        // 匹配商户名模式
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
            // 匹配商品行：商品名 x数量 ¥价格 或 商品名 数量x ¥价格
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
