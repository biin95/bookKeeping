package com.biin95.bookkeeping.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val floatingButtonEnabled: Boolean = false,
    val autoScreenshotOcr: Boolean = false,
    val backTapEnabled: Boolean = false,
    val notificationCaptureEnabled: Boolean = false,
    val smsCaptureEnabled: Boolean = false,
    val accessibilityEnabled: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TransactionRepository
) : ViewModel() {

    private val dataStore = context.settingsDataStore

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val gson = Gson()

    // DataStore keys
    companion object {
        val KEY_FLOATING_BUTTON = booleanPreferencesKey("floating_button_enabled")
        val KEY_AUTO_SCREENSHOT_OCR = booleanPreferencesKey("auto_screenshot_ocr")
        val KEY_BACK_TAP = booleanPreferencesKey("back_tap_enabled")
        val KEY_NOTIFICATION_CAPTURE = booleanPreferencesKey("notification_capture_enabled")
        val KEY_SMS_CAPTURE = booleanPreferencesKey("sms_capture_enabled")
        val KEY_ACCESSIBILITY = booleanPreferencesKey("accessibility_enabled")
    }

    init {
        // 从 DataStore 读取设置
        viewModelScope.launch {
            dataStore.data.first().let { prefs ->
                _settings.value = AppSettings(
                    floatingButtonEnabled = prefs[KEY_FLOATING_BUTTON] ?: false,
                    autoScreenshotOcr = prefs[KEY_AUTO_SCREENSHOT_OCR] ?: false,
                    backTapEnabled = prefs[KEY_BACK_TAP] ?: false,
                    notificationCaptureEnabled = prefs[KEY_NOTIFICATION_CAPTURE] ?: false,
                    smsCaptureEnabled = prefs[KEY_SMS_CAPTURE] ?: false,
                    accessibilityEnabled = prefs[KEY_ACCESSIBILITY] ?: false
                )
            }
        }
    }

    fun updateSetting(transform: AppSettings.() -> AppSettings) {
        val newSettings = _settings.value.transform()
        _settings.value = newSettings
        // 保存到 DataStore
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[KEY_FLOATING_BUTTON] = newSettings.floatingButtonEnabled
                prefs[KEY_AUTO_SCREENSHOT_OCR] = newSettings.autoScreenshotOcr
                prefs[KEY_BACK_TAP] = newSettings.backTapEnabled
                prefs[KEY_NOTIFICATION_CAPTURE] = newSettings.notificationCaptureEnabled
                prefs[KEY_SMS_CAPTURE] = newSettings.smsCaptureEnabled
                prefs[KEY_ACCESSIBILITY] = newSettings.accessibilityEnabled
            }
        }
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
