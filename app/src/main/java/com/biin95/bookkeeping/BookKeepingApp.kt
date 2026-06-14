package com.biin95.bookkeeping

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.provider.Settings
import android.util.Log
import com.biin95.bookkeeping.service.FloatingButtonService
import com.biin95.bookkeeping.service.ScreenshotMonitorService
import com.biin95.bookkeeping.ui.settings.SettingsViewModel
import com.biin95.bookkeeping.ui.settings.settingsDataStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class BookKeepingApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d("BookKeeping", "Application.onCreate 开始")

        // 设置全局未捕获异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("BookKeeping", "未捕获异常: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            createNotificationChannels()
            restoreServices()
            Log.d("BookKeeping", "Application.onCreate 完成")
        } catch (e: Exception) {
            Log.e("BookKeeping", "Application.onCreate 异常", e)
        }
    }

    /** 从 DataStore 读取设置，恢复已开启的服务（悬浮球、截图监听） */
    private fun restoreServices() {
        appScope.launch {
            try {
                val prefs = settingsDataStore.data.first()
                val floatingEnabled = prefs[SettingsViewModel.KEY_FLOATING_BUTTON] ?: false
                val screenshotOcrEnabled = prefs[SettingsViewModel.KEY_AUTO_SCREENSHOT_OCR] ?: false
                Log.d("BookKeeping", "restoreServices: floating=$floatingEnabled, screenshotOcr=$screenshotOcrEnabled")

                if (floatingEnabled && Settings.canDrawOverlays(this@BookKeepingApp)) {
                    FloatingButtonService.start(this@BookKeepingApp)
                    Log.d("BookKeeping", "FloatingButtonService 已恢复启动")
                }
                if (screenshotOcrEnabled) {
                    ScreenshotMonitorService.start(this@BookKeepingApp)
                    Log.d("BookKeeping", "ScreenshotMonitorService 已恢复启动")
                }
            } catch (e: Exception) {
                Log.e("BookKeeping", "restoreServices 失败", e)
            }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "bookKeeping 服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "bookKeeping 后台服务通知"
        }

        val captureChannel = NotificationChannel(
            CHANNEL_CAPTURE,
            "截图识别",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "截图 OCR 识别结果通知"
        }

        manager.createNotificationChannel(serviceChannel)
        manager.createNotificationChannel(captureChannel)
    }

    companion object {
        const val CHANNEL_SERVICE = "bookkeeping_service"
        const val CHANNEL_CAPTURE = "bookkeeping_capture"
    }
}
