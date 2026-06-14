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
import com.biin95.bookkeeping.BookKeepingApp
import com.biin95.bookkeeping.MainActivity
import com.biin95.bookkeeping.R
import com.biin95.bookkeeping.ocr.OcrEngine
import com.biin95.bookkeeping.util.AppLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class ScreenshotMonitorService : Service() {

    @Inject lateinit var ocrEngine: OcrEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var screenshotObserver: ContentObserver? = null
    // 按 URI 跟踪活跃的处理任务，同一 URI 新事件到达时取消旧任务
    private val activeJobs = ConcurrentHashMap<String, Job>()

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

                val uriKey = uri.toString()

                // 同一 URI 的新事件到达（如 pending→ready），取消旧的重试循环
                activeJobs[uriKey]?.let { oldSupervisor ->
                    AppLog.d(TAG, "Cancelling previous job for $uriKey")
                    oldSupervisor.cancel()
                }

                // 用 SupervisorJob 先存入 map 再启动协程，解决取消竞态
                val supervisor = SupervisorJob()
                activeJobs[uriKey] = supervisor
                val childScope = CoroutineScope(supervisor + Dispatchers.IO)
                val job = childScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        handleNewScreenshot(uri)
                    } catch (_: kotlinx.coroutines.CancellationException) {
                        // 不要吞掉 CancellationException，让它自然传播
                        throw kotlinx.coroutines.CancellationException("Processing cancelled for $uriKey")
                    } catch (e: Exception) {
                        AppLog.e(TAG, "Error processing screenshot", e)
                    }
                }
                // 仅当自己仍是活跃任务时才清理，避免误删新任务
                job.invokeOnCompletion {
                    activeJobs.compute(uriKey) { _, current ->
                        if (current === supervisor) null else current
                    }
                }
                job.start()
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

        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.IS_PENDING,
            MediaStore.Images.Media.IS_TRASHED
        )

        // 重试机制：等待 MediaStore 文件从 pending 变为可用
        // 首次截图时系统写入较慢，IS_PENDING=1 会导致查询为空
        val maxRetries = 5
        val retryDelays = longArrayOf(500, 1000, 2000, 3000, 3000) // 递增等待，共约 9.5 秒
        var path: String? = null
        var displayName: String? = null
        var resolved = false

        for (attempt in 0 until maxRetries) {
            if (attempt > 0) {
                val waitMs = retryDelays[attempt]
                AppLog.d(TAG, "Retry #$attempt after ${waitMs}ms...")
                kotlinx.coroutines.delay(waitMs)
            } else {
                // 首次尝试前短暂等待，让写入开始
                kotlinx.coroutines.delay(500)
            }

            // 先不过滤 IS_PENDING，看看文件是否存在
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    AppLog.d(TAG, "Attempt $attempt: cursor empty, file not found yet")
                    return@use
                }

                val isPending = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_PENDING))
                val isTrashed = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.IS_TRASHED))
                path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))

                AppLog.d(TAG, "Attempt $attempt: path=$path, displayName=$displayName, isPending=$isPending, isTrashed=$isTrashed")

                if (isPending == 0 && isTrashed == 0) {
                    resolved = true
                } else {
                    AppLog.d(TAG, "File still pending/trashed, will retry...")
                }
            }

            if (resolved) break
        }

        if (!resolved || (path == null && displayName == null)) {
            AppLog.d(TAG, "File never became available after $maxRetries attempts, uri=$uri")
            return
        }

        // 用路径或文件名判断是否为截图
        val isScreenshot = isScreenshotPath(path ?: "") || isScreenshotPath(displayName ?: "")
        if (!isScreenshot) {
            AppLog.d(TAG, "Not a screenshot, skipping. path=$path, displayName=$displayName")
            return
        }

        AppLog.d(TAG, "Screenshot confirmed after retry, starting OCR...")

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
