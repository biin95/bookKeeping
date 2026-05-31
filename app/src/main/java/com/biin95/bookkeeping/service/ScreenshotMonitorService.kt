package com.biin95.bookkeeping.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import com.biin95.bookkeeping.BookKeepingApp
import com.biin95.bookkeeping.MainActivity
import com.biin95.bookkeeping.R
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import com.biin95.bookkeeping.ocr.OcrEngine
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ScreenshotMonitorService : Service() {

    @Inject lateinit var ocrEngine: OcrEngine
    @Inject lateinit var repository: TransactionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var screenshotObserver: ContentObserver? = null
    private var lastScreenshotTime = 0L

    companion object {
        private const val TAG = "ScreenshotMonitor"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ScreenshotMonitorService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenshotMonitorService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, BookKeepingApp.CHANNEL_SERVICE)
            .setContentTitle("bookKeeping")
            .setContentText("截图监听服务运行中")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startMonitoring() {
        val handler = Handler(Looper.getMainLooper())
        screenshotObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                uri ?: return

                // 防抖：2秒内不重复处理
                val now = System.currentTimeMillis()
                if (now - lastScreenshotTime < 2000) return
                lastScreenshotTime = now

                scope.launch {
                    try {
                        handleNewScreenshot(uri)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing screenshot", e)
                    }
                }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            screenshotObserver!!
        )

        Log.d(TAG, "Screenshot monitoring started")
    }

    private fun stopMonitoring() {
        screenshotObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        screenshotObserver = null
        Log.d(TAG, "Screenshot monitoring stopped")
    }

    private suspend fun handleNewScreenshot(uri: Uri) {
        // 查询是否为截图文件
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED
        )

        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                ) ?: return

                // 检查是否为截图目录
                if (!isScreenshotPath(path)) return

                Log.d(TAG, "New screenshot detected: $path")

                // 使用 OCR 识别
                val screenshotUri = Uri.parse("file://$path")
                val result = ocrEngine.recognizeFromUri(this, screenshotUri)

                if (result.amount != null && result.amount > 0) {
                    val transaction = Transaction(
                        amount = -result.amount,
                        category = guessCategory(result.merchant ?: ""),
                        merchant = result.merchant ?: "",
                        description = result.items.joinToString(", "),
                        paymentMethod = result.paymentMethod ?: "",
                        source = "screenshot",
                        isIncome = false,
                        screenshotPath = path,
                        rawText = result.rawText
                    )

                    repository.insert(transaction)
                    Log.d(TAG, "Auto-saved screenshot transaction: $transaction")

                    // 发送通知
                    sendCaptureNotification(transaction)
                }
            }
        }
    }

    private fun isScreenshotPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.contains("screenshot") ||
                lower.contains("screenshots") ||
                lower.contains("截图") ||
                lower.contains("screen_capture")
    }

    private fun sendCaptureNotification(transaction: Transaction) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, BookKeepingApp.CHANNEL_CAPTURE)
            .setContentTitle("已识别账单")
            .setContentText("${transaction.merchant} ¥%.2f".format(kotlin.math.abs(transaction.amount)))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun guessCategory(merchant: String): String {
        val lower = merchant.lowercase()
        return when {
            lower.containsAny("餐", "饭", "食", "外卖", "美团", "饿了么") -> "餐饮"
            lower.containsAny("滴滴", "打车", "地铁", "公交") -> "交通"
            lower.containsAny("淘宝", "京东", "拼多多", "超市") -> "购物"
            lower.containsAny("电影", "游戏") -> "娱乐"
            else -> "其他"
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it) }
    }
}
