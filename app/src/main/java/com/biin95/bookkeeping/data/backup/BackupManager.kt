package com.biin95.bookkeeping.data.backup

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.data.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private val Context.backupPrefs: DataStore<Preferences> by preferencesDataStore(name = "backup_prefs")

object BackupManager {
    private const val TAG = "BackupManager"
    private val KEY_LAST_BACKUP = longPreferencesKey("last_backup_ms")
    private const val BACKUP_INTERVAL_MS = 24L * 60 * 60 * 1000 // 24 hours

    /** 尝试自动备份：距离上次备份超过 24 小时则执行 */
    suspend fun tryAutoBackup(context: Context, repository: TransactionRepository) {
        val prefs = context.backupPrefs
        val lastBackup = prefs.data.first()[KEY_LAST_BACKUP] ?: 0L
        val now = System.currentTimeMillis()

        if (now - lastBackup < BACKUP_INTERVAL_MS) {
            Log.d(TAG, "跳过备份：距上次备份不足 24 小时")
            return
        }

        val transactions = repository.getAllForExport()
        if (transactions.isEmpty()) {
            Log.d(TAG, "跳过备份：无记录")
            return
        }

        try {
            val json = ExportImportHelper.exportToJson(transactions)
            val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
            val fileName = "bookKeeping_${dateFormat.format(Date())}.json"
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            file.writeText(json)

            // 更新上次备份时间
            prefs.edit { it[KEY_LAST_BACKUP] = now }

            Log.d(TAG, "自动备份成功: ${file.absolutePath} (${transactions.size} 条)")
        } catch (e: Exception) {
            Log.e(TAG, "自动备份失败", e)
        }
    }
}
