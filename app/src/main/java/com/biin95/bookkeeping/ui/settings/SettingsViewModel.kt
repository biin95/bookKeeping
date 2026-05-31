package com.biin95.bookkeeping.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

data class AppSettings(
    val floatingButtonEnabled: Boolean = true,
    val autoScreenshotOcr: Boolean = false,
    val backTapEnabled: Boolean = false,
    val notificationCaptureEnabled: Boolean = false,
    val smsCaptureEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val gson = Gson()

    fun updateSetting(transform: AppSettings.() -> AppSettings) {
        _settings.value = _settings.value.transform()
    }

    fun exportData(context: Context) {
        viewModelScope.launch {
            try {
                val transactions = repository.getAllForExport()
                val json = gson.toJson(transactions)
                val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "bookKeeping_${dateFormat.format(Date())}.json"

                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                file.writeText(json)

                Toast.makeText(context, "已导出到 ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun importData(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri) ?: return@launch
                val content = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                val type = object : TypeToken<List<Transaction>>() {}.type
                val transactions: List<Transaction> = gson.fromJson(content, type)

                if (transactions.isNotEmpty()) {
                    repository.insertAll(transactions)
                    Toast.makeText(context, "成功导入 ${transactions.size} 条记录", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "文件中没有记录", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
