package com.biin95.bookkeeping.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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
class SmsReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: TransactionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "SmsReceiver"

        // 银行短信关键词
        private val BANK_KEYWORDS = listOf(
            "扣款", "消费", "支出", "转出", "付款",
            "收入", "到账", "转入", "退款",
            "余额", "信用卡", "储蓄卡"
        )

        private val AMOUNT_PATTERN = Pattern.compile(
            "(?:人民币|CNY|RMB)?\\s*[¥￥]?\\s*(\\d+[,.]?\\d*\\.?\\d{0,2})\\s*元?"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (sms in messages) {
            val body = sms.messageBody ?: continue
            val sender = sms.displayOriginatingAddress ?: ""
            val timestamp = sms.timestampMillis

            if (!isBankSms(sender, body)) continue

            Log.d(TAG, "Bank SMS from $sender: $body")

            scope.launch {
                try {
                    parseAndSave(body, sender, timestamp)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse SMS", e)
                }
            }
        }
    }

    private fun isBankSms(sender: String, body: String): Boolean {
        // 发送方为银行号码（通常为5位短号或95开头）
        val isBankSender = sender.matches(Regex("^\\d{5,6}$")) ||
                sender.startsWith("95") ||
                sender.startsWith("106")

        // 短信内容包含银行关键词
        val hasKeyword = BANK_KEYWORDS.any { body.contains(it) }

        return isBankSender && hasKeyword
    }

    private suspend fun parseAndSave(body: String, sender: String, timestamp: Long) {
        val amount = extractAmount(body) ?: return
        val isIncome = body.contains("收入") || body.contains("到账") || body.contains("转入") || body.contains("退款")
        val merchant = extractMerchant(body)
        val cardInfo = extractCardInfo(body)

        val transaction = Transaction(
            amount = if (isIncome) amount else -amount,
            category = guessCategory(body),
            merchant = merchant,
            description = body.take(200),
            paymentMethod = "银行卡${cardInfo}",
            source = "sms",
            timestamp = timestamp,
            isIncome = isIncome
        )

        repository.insert(transaction)
        Log.d(TAG, "Saved SMS transaction: $transaction")
    }

    private fun extractAmount(text: String): Double? {
        val matcher = AMOUNT_PATTERN.matcher(text)
        if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            return amountStr?.toDoubleOrNull()
        }
        return null
    }

    private fun extractMerchant(text: String): String {
        val patterns = listOf(
            Pattern.compile("(?:商户|消费于|在)([^，。,\\s]+)"),
            Pattern.compile("(?:摘要|备注)[：:\\s]*([^，。,\\s]+)")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                return matcher.group(1)?.trim() ?: ""
            }
        }
        return ""
    }

    private fun extractCardInfo(text: String): String {
        val pattern = Pattern.compile("(?:尾号|末四位|卡号)(\\d{4})")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) "(尾号${matcher.group(1)})" else ""
    }

    private fun guessCategory(text: String): String {
        return when {
            text.containsAny("餐", "饭", "食", "外卖", "美团") -> "餐饮"
            text.containsAny("滴滴", "打车", "地铁", "公交", "加油") -> "交通"
            text.containsAny("淘宝", "京东", "拼多多", "超市") -> "购物"
            text.containsAny("电影", "游戏") -> "娱乐"
            text.containsAny("话费", "流量") -> "通讯"
            text.containsAny("工资") -> "工资"
            text.containsAny("退款") -> "退款"
            else -> "其他"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
