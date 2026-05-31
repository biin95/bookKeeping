package com.biin95.bookkeeping

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BookKeepingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
