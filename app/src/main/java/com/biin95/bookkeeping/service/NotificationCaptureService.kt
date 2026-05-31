package com.biin95.bookkeeping.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import javax.inject.Inject

@AndroidEntryPoint
class NotificationCaptureService : NotificationListenerService() {

    @Inject lateinit var repository: TransactionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "NotifCapture"

        // 已知的支付相关包名
        private val PAYMENT_PACKAGES = setOf(
            "com.eg.android.AlipayGphone",        // 支付宝
            "com.tencent.mm",                      // 微信
            "com.jd.jrapp",                        // 京东金融
            "com.pingan.paces.ccard",              // 平安银行
            "com.chinamworld.bocmbci",             // 中国银行
            "com.icbc",                            // 工商银行
            "com.yitong.mbank.psbc",               // 邮储银行
            "com.unionpay"                         // 云闪付
        )

        // 金额正则
        private val AMOUNT_PATTERN = Pattern.compile("[¥￥]\\s*(\\d+\\.?\\d{0,2})|(\\d+\\.\\d{2})\\s*元|(?:支付|付款|扣款|消费)\\s*(\\d+\\.?\\d{0,2})")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val packageName = sbn.packageName

        if (packageName !in PAYMENT_PACKAGES) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullText = "$title $text $bigText"

        Log.d(TAG, "Payment notification from $packageName: $fullText")

        scope.launch {
            try {
                parseAndSave(packageName, fullText, sbn.postTime)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse notification", e)
            }
        }
    }

    private suspend fun parseAndSave(packageName: String, text: String, timestamp: Long) {
        val amount = extractAmount(text) ?: return
        val isIncome = text.contains("收入") || text.contains("到账") || text.contains("转入")
        val merchant = extractMerchant(text)
        val paymentMethod = when (packageName) {
            "com.eg.android.AlipayGphone" -> "支付宝"
            "com.tencent.mm" -> "微信"
            "com.unionpay" -> "云闪付"
            else -> extractPaymentMethod(text) ?: "其他"
        }

        val transaction = Transaction(
            amount = if (isIncome) amount else -amount,
            category = guessCategory(text),
            merchant = merchant,
            description = text.take(200),
            paymentMethod = paymentMethod,
            source = "notification",
            timestamp = timestamp,
            isIncome = isIncome
        )

        repository.insert(transaction)
        Log.d(TAG, "Saved transaction: $transaction")
    }

    private fun extractAmount(text: String): Double? {
        val matcher = AMOUNT_PATTERN.matcher(text)
        if (matcher.find()) {
            for (i in 1..matcher.groupCount()) {
                val value = matcher.group(i)
                if (value != null) {
                    return value.toDoubleOrNull()
                }
            }
        }
        return null
    }

    private fun extractMerchant(text: String): String {
        val patterns = listOf(
            Pattern.compile("(?:商户|商家|付款给|收款方|对方)[：:\\s]*([^\\s,，。]+)"),
            Pattern.compile("向(.+?)付款"),
            Pattern.compile("在(.+?)消费")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim() ?: ""
            }
        }
        return ""
    }

    private fun extractPaymentMethod(text: String): String? {
        return when {
            text.contains("支付宝") -> "支付宝"
            text.contains("微信") -> "微信"
            text.contains("银行卡") || text.contains("储蓄卡") -> "银行卡"
            text.contains("信用卡") -> "信用卡"
            else -> null
        }
    }

    private fun guessCategory(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.containsAny("餐", "饭", "食", "外卖", "美团", "饿了么", "肯德基", "麦当劳") -> "餐饮"
            lower.containsAny("滴滴", "打车", "地铁", "公交", "加油", "停车") -> "交通"
            lower.containsAny("淘宝", "京东", "拼多多", "天猫", "超市") -> "购物"
            lower.containsAny("电影", "游戏", "视频", "音乐") -> "娱乐"
            lower.containsAny("话费", "流量") -> "通讯"
            lower.containsAny("工资", "薪水") -> "工资"
            lower.containsAny("红包") -> "红包"
            lower.containsAny("退款") -> "退款"
            else -> "其他"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
