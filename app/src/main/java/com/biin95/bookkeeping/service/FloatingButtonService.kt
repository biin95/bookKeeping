package com.biin95.bookkeeping.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.biin95.bookkeeping.BookKeepingApp
import com.biin95.bookkeeping.MainActivity
import com.biin95.bookkeeping.R

class FloatingButtonService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    companion object {
        private const val TAG = "FloatingButton"
        private const val NOTIFICATION_ID = 1002

        fun start(context: Context) {
            val intent = Intent(context, FloatingButtonService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingButtonService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FloatingButtonService onCreate")
        startForeground(NOTIFICATION_ID, createNotification())
        createFloatingButton()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FloatingButtonService onDestroy")
        removeFloatingButton()
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, BookKeepingApp.CHANNEL_SERVICE)
            .setContentTitle("bookKeeping")
            .setContentText("悬浮球运行中")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createFloatingButton() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // 创建悬浮球 View
        val buttonSize = (56 * resources.displayMetrics.density).toInt()
        val buttonView = object : FrameLayout(this) {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isDragging = false

            init {
                // 圆形绿色背景
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0xFF2E7D32.toInt())
                }

                // 加号图标
                val iconView = android.widget.ImageView(context).apply {
                    setImageResource(android.R.drawable.ic_input_add)
                    setColorFilter(android.graphics.Color.WHITE)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                    val padding = (12 * resources.displayMetrics.density).toInt()
                    setPadding(padding, padding, padding, padding)
                }
                addView(iconView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }

            @Suppress("ClickableViewAccessibility")
            override fun onTouchEvent(event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = x.toInt()
                        initialY = y.toInt()
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDragging = true
                        }
                        if (isDragging) {
                            val params = layoutParams as WindowManager.LayoutParams
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager?.updateViewLayout(this, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            // 点击：打开 OCR 页面
                            val intent = Intent(this@FloatingButtonService, MainActivity::class.java).apply {
                                action = "com.biin95.bookkeeping.ACTION_OCR_CAPTURE"
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(intent)
                        }
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }
        }

        val params = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
            y = 0
        }

        floatingView = buttonView
        windowManager?.addView(buttonView, params)
        Log.d(TAG, "Floating button created")
    }

    private fun removeFloatingButton() {
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view", e)
            }
        }
        floatingView = null
    }
}
