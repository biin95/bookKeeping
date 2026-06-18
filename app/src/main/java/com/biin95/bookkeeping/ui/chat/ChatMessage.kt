package com.biin95.bookkeeping.ui.chat

import com.biin95.bookkeeping.data.local.entity.Transaction
import com.biin95.bookkeeping.nlp.NlpResult

/**
 * 聊天消息
 *
 * @param type    user=用户说的话, response=App解析回复(可编辑), system=系统消息
 * @param text    展示文本
 * @param nlpResult   NLP解析结果（response消息用）
 * @param transaction  已保存的交易记录（保存后设置）
 * @param isSaved      是否已保存到数据库
 * @param timestamp    时间戳
 */
data class ChatMessage(
    val id: Long,
    val type: String,
    val text: String,
    val nlpResult: NlpResult? = null,
    val transaction: Transaction? = null,
    val isSaved: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
