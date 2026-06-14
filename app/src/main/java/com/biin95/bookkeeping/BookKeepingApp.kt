package com.biin95.bookkeeping

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BookKeepingApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("BookKeeping", "Application.onCreate 开始")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("BookKeeping", "未捕获异常: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        createNotificationChannels()
        Log.d("BookKeeping", "Application.onCreate 完成")
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val channel = NotificationChannel(
            CHANNEL_DEFAULT,
            "bookKeeping",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "bookKeeping 通知"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_DEFAULT = "bookkeeping_default"
    }
}
