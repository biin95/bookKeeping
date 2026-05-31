package com.biin95.bookkeeping.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class BookKeepingAccessibilityService : AccessibilityService() {

    @Inject lateinit var repository: TransactionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "AccessibilityService"

        // 目标APP包名
        private val TARGET_PACKAGES = setOf(
            "com.eg.android.AlipayGphone",    // 支付宝
            "com.tencent.mm",                  // 微信
            "com.jingdong.app.mall"            // 京东
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
            // 只监听目标APP
            packageNames = TARGET_PACKAGES.toTypedArray()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return

        if (packageName !in TARGET_PACKAGES) return

        Log.d(TAG, "Event from $packageName: ${event.eventType}")

        // 当窗口内容变化时，尝试读取账单信息
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            scope.launch {
                try {
                    processWindowContent(packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing accessibility event", e)
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    private suspend fun processWindowContent(packageName: String) {
        val rootNode = rootInActiveWindow ?: return

        when (packageName) {
            "com.eg.android.AlipayGphone" -> processAlipay(rootNode)
            "com.tencent.mm" -> processWechat(rootNode)
            "com.jingdong.app.mall" -> processJingdong(rootNode)
        }
    }

    private suspend fun processAlipay(root: AccessibilityNodeInfo) {
        // 支付宝账单详情页解析
        // 这里需要根据实际 dump 的控件树来写解析规则
        // 示例：查找包含金额的节点
        val amountNodes = findNodesByText(root, Regex("\\d+\\.\\d{2}"))
        val merchantNodes = findNodesById(root, "com.eg.android.AlipayGphone:id/title")

        if (amountNodes.isNotEmpty()) {
            val amount = amountNodes.first().text?.toString()?.toDoubleOrNull()
            val merchant = merchantNodes.firstOrNull()?.text?.toString() ?: ""

            if (amount != null && amount > 0) {
                val transaction = Transaction(
                    amount = -amount,
                    category = "其他",
                    merchant = merchant,
                    paymentMethod = "支付宝",
                    source = "accessibility"
                )
                repository.insert(transaction)
                Log.d(TAG, "Saved Alipay transaction: $transaction")
            }
        }
    }

    private suspend fun processWechat(root: AccessibilityNodeInfo) {
        // 微信支付详情页解析
        val amountNodes = findNodesByText(root, Regex("¥\\d+\\.\\d{2}"))
        val merchantNodes = findNodesByText(root, Regex("付款给.*|商户.*"))

        if (amountNodes.isNotEmpty()) {
            val amountText = amountNodes.first().text?.toString()
                ?.replace("¥", "")?.toDoubleOrNull()
            val merchant = merchantNodes.firstOrNull()?.text?.toString() ?: ""

            if (amountText != null && amountText > 0) {
                val transaction = Transaction(
                    amount = -amountText,
                    category = "其他",
                    merchant = merchant,
                    paymentMethod = "微信",
                    source = "accessibility"
                )
                repository.insert(transaction)
                Log.d(TAG, "Saved WeChat transaction: $transaction")
            }
        }
    }

    private suspend fun processJingdong(root: AccessibilityNodeInfo) {
        // 京东订单详情页解析
        val amountNodes = findNodesByText(root, Regex("¥\\d+\\.\\d{2}|\\d+\\.\\d{2}元"))

        if (amountNodes.isNotEmpty()) {
            val amountText = amountNodes.first().text?.toString()
                ?.replace("¥", "")?.replace("元", "")?.toDoubleOrNull()

            if (amountText != null && amountText > 0) {
                val transaction = Transaction(
                    amount = -amountText,
                    category = "购物",
                    merchant = "京东",
                    paymentMethod = "京东",
                    source = "accessibility"
                )
                repository.insert(transaction)
                Log.d(TAG, "Saved JD transaction: $transaction")
            }
        }
    }

    private fun findNodesByText(root: AccessibilityNodeInfo, regex: Regex): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findNodesRecursive(root) { node ->
            val text = node.text?.toString() ?: ""
            if (regex.containsMatchIn(text)) {
                result.add(node)
            }
        }
        return result
    }

    private fun findNodesById(root: AccessibilityNodeInfo, viewId: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        findNodesRecursive(root) { node ->
            if (node.viewIdResourceName == viewId) {
                result.add(node)
            }
        }
        return result
    }

    private fun findNodesRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Unit
    ) {
        predicate(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { findNodesRecursive(it, predicate) }
        }
    }
}
