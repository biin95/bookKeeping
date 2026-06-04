package com.biin95.bookkeeping.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

/**
 * 应用内日志工具 — 同时写入 Android Logcat 和内存缓冲区
 * 在设置页面可查看最近的日志
 */
object AppLog {

    private const val MAX_LINES = 500

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        append("D/$tag: $msg")
    }

    fun e(tag: String, msg: String, e: Throwable? = null) {
        Log.e(tag, msg, e)
        append("E/$tag: $msg${e?.let { "\n  ${it.message}" } ?: ""}")
    }

    fun i(tag: String, msg: String) {
        Log.i(tag, msg)
        append("I/$tag: $msg")
    }

    private fun append(line: String) {
        val time = timeFormat.format(Date())
        val entry = "$time $line"
        synchronized(this) {
            val current = _logs.value.toMutableList()
            current.add(entry)
            if (current.size > MAX_LINES) {
                current.removeAt(0)
            }
            _logs.value = current
        }
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
