package com.biin95.bookkeeping.ocr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.biin95.bookkeeping.util.AppLog
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

    companion object {
        private const val TAG = "OcrEngine"
        private const val MIN_AMOUNT = 0.01
        private const val MAX_AMOUNT = 99999.0
    }

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


    // 预编译排除模式：时间、日期、年份等非金额数字
    private val excludePatterns = listOf(
        Pattern.compile("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}"),   // 日期 2026-01-15
        Pattern.compile("\\d{1,2}:\\d{2}(:\\d{2})?"),          // 时间 14:30 / 14:30:25
        Pattern.compile("\\d{4}年\\d{1,2}月"),                  // 2026年6月
        Pattern.compile("\\d{1,2}[日月][：:]?\\d{1,2}"),       // 1日:20 / 6月15 / 3日14
        Pattern.compile("第\\d+"),                               // 第X笔/第X条
        Pattern.compile("订单[号编]?[：:]?\\s*\\d+"),           // 订单号
        Pattern.compile("编号[：:]?\\s*\\d+"),                   // 编号
        Pattern.compile("手机[号]?[：:]?\\s*\\d+"),             // 手机号
        Pattern.compile("\\d{10,}")                              // 10位以上纯数字（手机号/订单号）
    )

    // 年份范围：1900-2100 的4位数字大概率是年份，不是金额
    private val yearPattern = Pattern.compile("(?<![\\d.])((?:19|20)\\d{2})(?![\\d.])")

    // 提取所有金额及其在原文中的位置
    private fun extractAllAmounts(text: String): List<Pair<Double, Int>> {
        val results = mutableListOf<Triple<Double, Int, Int>>() // amount, pos, priority

        // ===== 优先级0：关键词匹配（代码逻辑，不用正则）=====
        // 用代码直接搜索关键词+金额，比正则更可靠（尤其跨行场景）
        val keywordResults = findAmountByKeyword(text)
        for ((amount, pos) in keywordResults) {
            if (amount in MIN_AMOUNT..MAX_AMOUNT && !isYearLike("%.2f".format(amount))) {
                AppLog.d(TAG, "Keyword match: amount=$amount, pos=$pos, priority=0")
                results.add(Triple(amount, pos, 0))
            }
        }

        // ===== 优先级1-4：正则匹配 =====
        val patterns = listOf(
            // 优先级1：带货币符号的金额
            Pattern.compile("[¥￥]\\s*(\\d+\\.?\\d{0,2})(?![\\d:])") to 1,
            // 优先级1.5：OCR 把 ¥ 误识别为 * 的情况（*必须紧跟数字且带小数点）
            Pattern.compile("\\*(\\d+\\.\\d{2})(?![\\d])") to 1,
            // 优先级2：CNY/RMB 标记
            Pattern.compile("(?:CNY|RMB)\\s*(\\d+\\.?\\d{0,2})") to 2,
            // 优先级3：X元 格式
            Pattern.compile("(\\d+\\.\\d{2})\\s*元") to 3,
            // 优先级4：裸金额格式（2位以上整数 + .两位小数，如 139.00，无任何前缀）
            Pattern.compile("(?<![\\d¥￥.])(\\d{2,}\\.\\d{2})(?![\\d])") to 4
        )

        val seenRanges = mutableSetOf<Int>() // 避免同一位置重复匹配
        // 将关键词匹配的位置加入 seenRanges，避免正则重复匹配同一笔
        for ((_, pos) in keywordResults) {
            seenRanges.add(pos)
        }

        for ((pattern, priority) in patterns) {
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val amountStr = matcher.group(1) ?: continue
                val amount = amountStr.toDoubleOrNull() ?: continue

                AppLog.d(TAG, "Pattern[$priority] matched: '$amountStr' (amount=$amount) at pos=${matcher.start()}")

                // 金额合理性过滤
                if (amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
                    AppLog.d(TAG, "  -> filtered: out of range ($MIN_AMOUNT..$MAX_AMOUNT)")
                    continue
                }
                // 纯整数 1900-2100 大概率是年份，跳过
                if (isYearLike(amountStr)) {
                    AppLog.d(TAG, "  -> filtered: year-like")
                    continue
                }

                val pos = matcher.start()

                // 检查该位置是否在排除模式覆盖范围内
                if (isExcludedPosition(text, pos, matcher.end())) {
                    AppLog.d(TAG, "  -> filtered: excluded position")
                    continue
                }

                // 避免在相近位置重复（±5字符内算同一笔）
                if (seenRanges.any { Math.abs(it - pos) < 5 }) {
                    AppLog.d(TAG, "  -> filtered: duplicate position near $pos")
                    continue
                }
                seenRanges.add(pos)
                results.add(Triple(amount, pos, priority))
                AppLog.d(TAG, "  -> ACCEPTED: amount=$amount, pos=$pos, priority=$priority")
            }
        }

        // 先按优先级排序（关键词金额优先），同优先级按位置排序
        val sorted = results.sortedWith(compareBy<Triple<Double, Int, Int>> { it.third }.thenBy { it.second })
            .map { it.first to it.second }
        AppLog.d(TAG, "extractAllAmounts: ${sorted.size} final result(s): $sorted")
        return sorted
    }

    /**
     * 判断数字字符串是否像年份（1900-2100范围内的4位纯整数）
     */
    private fun isYearLike(amountStr: String): Boolean {
        // 有小数位的不可能是年份
        if (amountStr.contains(".")) return false
        val value = amountStr.toDoubleOrNull() ?: return false
        // 只有1900-2100范围内的4位整数才判定为年份
        return value in 1900.0..2100.0 && amountStr.length == 4
    }

    /**
     * 检查匹配位置是否命中排除模式（时间/日期/订单号等）
     */
    private fun isExcludedPosition(text: String, start: Int, end: Int): Boolean {
        // 取匹配位置前后各10字符的上下文
        val ctxStart = maxOf(0, start - 10)
        val ctxEnd = minOf(text.length, end + 10)
        val context = text.substring(ctxStart, ctxEnd)

        for (pattern in excludePatterns) {
            val matcher = pattern.matcher(context)
            while (matcher.find()) {
                // 排除模式覆盖了当前匹配位置
                val matchAbsStart = ctxStart + matcher.start()
                val matchAbsEnd = ctxStart + matcher.end()
                if (matchAbsStart <= start && matchAbsEnd >= end) return true
            }
        }
        return false
    }

    /**
     * 用代码逻辑搜索"实付款"等关键词，然后在后续文本中找最近的金额
     * 比纯正则更可靠，尤其处理跨行场景（如"实付款\n11.90"）
     */
    private fun findAmountByKeyword(text: String): List<Pair<Double, Int>> {
        // 关键词列表，按长度降序（长的优先匹配）
        val keywords = listOf("实付款", "实收款", "实付", "支付", "付款", "商品总价", "金额",
            "合计", "总计", "花费", "消费", "扣款", "转入", "转出")
        // 匹配金额的正则：¥/￥前缀 或 裸数字带小数点
        val amountPattern = Pattern.compile("[¥￥]?(\\d+\\.\\d{2})")
        val results = mutableListOf<Pair<Double, Int>>()
        val lines = text.split("\n")

        AppLog.d(TAG, "findAmountByKeyword: text.length=${text.length}, lines=${lines.size}")
        AppLog.d(TAG, "  contains实付款=${text.contains("实付款")}, indexOf实付款=${text.indexOf("实付款")}")

        for (keyword in keywords) {
            var keywordIdx = text.indexOf(keyword)
            if (keywordIdx >= 0) {
                AppLog.d(TAG, "  Found keyword '$keyword' at pos $keywordIdx")
            }
            while (keywordIdx >= 0) {
                // 从关键词之后开始，找最近的金额（最多往后看 5 行）
                val keywordLineIdx = getLineIndex(lines, keywordIdx)
                // 往后看 30 行（电商截图中关键词和金额可能相隔很远）
                val searchEnd = minOf(lines.size, keywordLineIdx + 30)
                val searchText = lines.subList(keywordLineIdx, searchEnd).joinToString("\n")
                // 关键词在 searchText 中的位置
                val kwLocalIdx = searchText.indexOf(keyword)
                if (kwLocalIdx < 0) {
                    AppLog.d(TAG, "    kwLocalIdx < 0, skipping")
                    keywordIdx = text.indexOf(keyword, keywordIdx + 1); continue
                }

                val afterKeyword = searchText.substring(kwLocalIdx + keyword.length)
                AppLog.d(TAG, "    afterKeyword(${afterKeyword.length}): '${afterKeyword.take(60)}'")
                val matcher = amountPattern.matcher(afterKeyword)
                if (matcher.find()) {
                    val amountStr = matcher.group(1)!!
                    val amount = amountStr.toDoubleOrNull()
                    AppLog.d(TAG, "    matcher found: '$amountStr' (amount=$amount)")
                    if (amount != null && amount >= MIN_AMOUNT && amount <= MAX_AMOUNT) {
                        // 计算在原文中的绝对位置
                        val absPos = keywordIdx + keyword.length + matcher.start()
                        if (!isExcludedPosition(text, absPos, absPos + matcher.end())) {
                            AppLog.d(TAG, "    -> ACCEPTED: amount=$amount, absPos=$absPos")
                            results.add(amount to absPos)
                        } else {
                            AppLog.d(TAG, "    -> excluded position")
                        }
                    } else {
                        AppLog.d(TAG, "    -> amount out of range or null")
                    }
                } else {
                    AppLog.d(TAG, "    no amount pattern match in afterKeyword")
                }
                // 继续找下一个同名关键词
                keywordIdx = text.indexOf(keyword, keywordIdx + keyword.length)
            }
        }
        AppLog.d(TAG, "findAmountByKeyword: ${results.size} result(s)")
        return results
    }

    /** 获取字符位置所在的行号 */
    private fun getLineIndex(lines: List<String>, charPos: Int): Int {
        var count = 0
        for ((i, line) in lines.withIndex()) {
            count += line.length + 1
            if (count > charPos) return i
        }
        return lines.size - 1
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
        // 复用 extractAllAmounts 的逻辑，取优先级最高的那笔
        val allAmounts = extractAllAmounts(text)
        return allAmounts.firstOrNull()?.first
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
