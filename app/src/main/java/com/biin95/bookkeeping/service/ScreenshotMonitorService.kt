package com.biin95.bookkeeping.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.biin95.bookkeeping.ocr.OcrEngine
import com.biin95.bookkeeping.util.AppLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH

@AndroidEntryPoint
class ScreenshotMonitorService : Service() {

    @Inject lateinit var ocrEngine: OcrEngine

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
                AppLog.d(TAG, "ContentObserver.onChange triggered, uri=$uri, selfChange=$selfChange")
                uri ?: return

                // 防抖：2秒内不重复处理
                val now = System.currentTimeMillis()
                if (now - lastScreenshotTime < 2000) {
                    AppLog.d(TAG, "Debounced, skipping")
                    return
                }
                lastScreenshotTime = now

                scope.launch {
                    try {
                        handleNewScreenshot(uri)
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Error processing screenshot", e)
                    }
                }
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            screenshotObserver!!
        )

        AppLog.d(TAG, "Screenshot monitoring started")
    }

    private fun stopMonitoring() {
        screenshotObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        screenshotObserver = null
        AppLog.d(TAG, "Screenshot monitoring stopped")
    }

    private suspend fun handleNewScreenshot(uri: Uri) {
        AppLog.d(TAG, "handleNewScreenshot called, uri=$uri")

        // 等待文件写入完成（MediaStore 插入时文件可能还在写）
        kotlinx.coroutines.delay(1000)

        // 查询是否为截图文件，过滤掉 pending 和 trashed 状态
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.IS_PENDING,
            MediaStore.Images.Media.IS_TRASHED
        )

        // 只查询非 pending、非 trashed 的文件
        val selection = "${MediaStore.Images.Media.IS_PENDING} = 0 AND ${MediaStore.Images.Media.IS_TRASHED} = 0"

        contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                AppLog.d(TAG, "Cursor is empty or file is pending/trashed")
                return
            }

            val path = cursor.getString(
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            )
            val displayName = cursor.getString(
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            )
            val isPending = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_PENDING))
            AppLog.d(TAG, "File detected: path=$path, displayName=$displayName, isPending=$isPending")

            if (path == null && displayName == null) {
                AppLog.d(TAG, "Both path and displayName are null, skipping")
                return
            }

            // 用路径或文件名判断是否为截图
            val isScreenshot = isScreenshotPath(path ?: "") || isScreenshotPath(displayName ?: "")
            if (!isScreenshot) {
                AppLog.d(TAG, "Not a screenshot, skipping. path=$path, displayName=$displayName")
                return
            }

            AppLog.d(TAG, "Screenshot confirmed, starting OCR...")

            try {
                // 直接用 content URI（MediaStore URI），不要转 file://
                val text = ocrEngine.recognizeTextFromUri(this, uri)
                AppLog.d(TAG, "OCR text length: ${text.length}")
                AppLog.d(TAG, "OCR full text:\n$text")

                val results = ocrEngine.parseMultipleTransactions(text)
                AppLog.d(TAG, "parseMultipleTransactions returned ${results.size} result(s)")
                for ((i, r) in results.withIndex()) {
                    AppLog.d(TAG, "  result[$i]: amount=${r.amount}, merchant=${r.merchant}")
                }

                val validResults = results.filter { it.amount != null && it.amount > 0 }

                if (validResults.isNotEmpty()) {
                    AppLog.d(TAG, "Sending notification for ${validResults.size} transaction(s)")
                    sendConfirmNotification(validResults, path ?: displayName ?: "screenshot")
                } else {
                    AppLog.d(TAG, "No valid amount found in OCR text")
                    sendHintNotification("截图已识别，但未找到金额", text.take(100))
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "OCR failed", e)
                sendHintNotification("截图识别失败", e.message ?: "未知错误")
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

    private fun sendConfirmNotification(results: List<com.biin95.bookkeeping.ocr.OcrResult>, screenshotPath: String) {
        // 跳转到 OCR 编辑页面，传递截图路径
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_OCR_CONFIRM"
            putExtra("screenshot_path", screenshotPath)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val content: String
        if (results.size == 1) {
            val r = results[0]
            title = "识别到账单，点击确认"
            val merchantPart = if (!r.merchant.isNullOrBlank()) "${r.merchant} " else ""
            content = "${merchantPart}¥%.2f".format(r.amount!!)
        } else {
            title = "识别到 ${results.size} 笔账单，点击确认"
            val total = results.sumOf { it.amount ?: 0.0 }
            content = "合计 ¥%.2f".format(total)
        }

        val notification = Notification.Builder(this, BookKeepingApp.CHANNEL_CAPTURE)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun sendHintNotification(title: String, detail: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, BookKeepingApp.CHANNEL_CAPTURE)
            .setContentTitle(title)
            .setContentText(detail)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

}
