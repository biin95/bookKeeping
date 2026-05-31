package com.biin95.bookkeeping.ocr

import com.biin95.bookkeeping.data.local.entity.Transaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExportImportHelper {

    private val gson = Gson()

    fun exportToJson(transactions: List<Transaction>): String {
        return gson.toJson(transactions)
    }

    fun importFromJson(json: String): List<Transaction> {
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type)
    }

    /**
     * 解析支付宝 CSV 账单
     * 支付宝导出格式通常为:
     * 交易号,商家订单号,交易创建时间,付款时间,最近修改时间,交易来源地,类型,交易对方,商品名称,金额（元）,收/支,交易状态,服务费,成功退款,备注,资金状态
     */
    fun parseAlipayCsv(inputStream: InputStream): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val reader = inputStream.bufferedReader()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 跳过文件头（支付宝CSV前几行为说明信息）
        var line = reader.readLine()
        while (line != null && !line.startsWith("交易号")) {
            line = reader.readLine()
        }

        // 读取表头
        val header = line ?: return transactions
        val headers = header.split(",").map { it.trim().replace("\"", "") }

        val timeIdx = headers.indexOfFirst { it.contains("交易创建时间") || it.contains("付款时间") }
        val merchantIdx = headers.indexOfFirst { it.contains("交易对方") }
        val descIdx = headers.indexOfFirst { it.contains("商品名称") }
        val amountIdx = headers.indexOfFirst { it.contains("金额") }
        val typeIdx = headers.indexOfFirst { it.contains("收/支") }

        // 逐行读取数据
        reader.forEachLine { dataLine ->
            if (dataLine.isNotBlank() && !dataLine.startsWith(",,")) {
                val fields = parseCsvLine(dataLine)
                if (fields.size > maxOf(timeIdx, merchantIdx, descIdx, amountIdx, typeIdx).coerceAtLeast(0)) {
                    val amount = fields.getOrNull(amountIdx)
                        ?.replace(",", "")
                        ?.replace("\"", "")
                        ?.toDoubleOrNull() ?: 0.0
                    val isIncome = fields.getOrNull(typeIdx)?.contains("收入") == true

                    val timestamp = try {
                        fields.getOrNull(timeIdx)?.replace("\"", "")?.let {
                            dateFormat.parse(it)?.time
                        }
                    } catch (_: Exception) { null } ?: System.currentTimeMillis()

                    if (amount > 0) {
                        transactions.add(
                            Transaction(
                                amount = if (isIncome) amount else -amount,
                                category = "其他",
                                merchant = fields.getOrNull(merchantIdx)?.replace("\"", "") ?: "",
                                description = fields.getOrNull(descIdx)?.replace("\"", "") ?: "",
                                paymentMethod = "支付宝",
                                source = "import",
                                timestamp = timestamp,
                                isIncome = isIncome
                            )
                        )
                    }
                }
            }
        }
        reader.close()
        return transactions
    }

    /**
     * 解析微信 CSV 账单
     * 微信导出格式通常为:
     * 交易时间,交易类型,交易对方,商品,收/支,金额(元),支付方式,当前状态,交易单号,商户单号,备注
     */
    fun parseWechatCsv(inputStream: InputStream): List<Transaction> {
        val transactions = mutableListOf<Transaction>()
        val reader = inputStream.bufferedReader()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // 跳过文件头
        var line = reader.readLine()
        while (line != null && !line.startsWith("交易时间")) {
            line = reader.readLine()
        }

        val header = line ?: return transactions
        val headers = header.split(",").map { it.trim().replace("\"", "") }

        val timeIdx = headers.indexOfFirst { it.contains("交易时间") }
        val merchantIdx = headers.indexOfFirst { it.contains("交易对方") }
        val descIdx = headers.indexOfFirst { it.contains("商品") }
        val typeIdx = headers.indexOfFirst { it.contains("收/支") }
        val amountIdx = headers.indexOfFirst { it.contains("金额") }
        val payMethodIdx = headers.indexOfFirst { it.contains("支付方式") }

        reader.forEachLine { dataLine ->
            if (dataLine.isNotBlank()) {
                val fields = parseCsvLine(dataLine)
                val maxIdx = maxOf(timeIdx, merchantIdx, descIdx, amountIdx, typeIdx, payMethodIdx).coerceAtLeast(0)
                if (fields.size > maxIdx) {
                    val amount = fields.getOrNull(amountIdx)
                        ?.replace("¥", "")
                        ?.replace(",", "")
                        ?.replace("\"", "")
                        ?.toDoubleOrNull() ?: 0.0
                    val isIncome = fields.getOrNull(typeIdx)?.contains("收入") == true

                    val timestamp = try {
                        fields.getOrNull(timeIdx)?.replace("\"", "")?.let {
                            dateFormat.parse(it)?.time
                        }
                    } catch (_: Exception) { null } ?: System.currentTimeMillis()

                    if (amount > 0) {
                        transactions.add(
                            Transaction(
                                amount = if (isIncome) amount else -amount,
                                category = "其他",
                                merchant = fields.getOrNull(merchantIdx)?.replace("\"", "") ?: "",
                                description = fields.getOrNull(descIdx)?.replace("\"", "") ?: "",
                                paymentMethod = fields.getOrNull(payMethodIdx)?.replace("\"", "") ?: "微信",
                                source = "import",
                                timestamp = timestamp,
                                isIncome = isIncome
                            )
                        )
                    }
                }
            }
        }
        reader.close()
        return transactions
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }
}
