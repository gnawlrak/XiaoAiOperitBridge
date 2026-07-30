package com.example.xiaoaioperit.config

import android.content.Context
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * 跨进程日志存储客户端。
 *
 * Hook 进程（com.miui.voiceassist）通过 ContentProvider 写入日志，
 * UI 进程（本模块界面）同样通过 ContentProvider 读取日志。
 */
object LogClient {

    private val uri: Uri = Uri.parse("content://${ConfigProvider.AUTHORITY}")

    data class LogEntry(
        val id: Long,
        val time: Long,      // epoch millis
        val type: String,    // 类型：发送 / 完成 / 错误 / 连接测试
        val content: String, // 日志正文
        val isError: Boolean = false
    )

    fun read(context: Context, limit: Int = 200): List<LogEntry> {
        val result = try {
            context.contentResolver.call(uri, ConfigProvider.METHOD_LOG_READ, null, null)
        } catch (t: Throwable) {
            null
        } ?: return emptyList()
        val json = result.getString("logs") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until minOf(arr.length(), limit)).map { i ->
                val obj = arr.getJSONObject(i)
                LogEntry(
                    id = obj.optLong("id", 0L),
                    time = obj.optLong("time", 0L),
                    type = obj.optString("type", ""),
                    content = obj.optString("content", ""),
                    isError = obj.optBoolean("isError", false)
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun append(context: Context, entry: LogEntry) {
        val bundle = Bundle().apply {
            putString("entry", JSONObject().apply {
                put("id", entry.id)
                put("time", entry.time)
                put("type", entry.type)
                put("content", entry.content)
                put("isError", entry.isError)
            }.toString())
        }
        try {
            context.contentResolver.call(uri, ConfigProvider.METHOD_LOG_APPEND, null, bundle)
        } catch (t: Throwable) {
            // 静默忽略 — ContentProvider 不可用时不影响主功能
        }
    }

    fun clear(context: Context) {
        try {
            context.contentResolver.call(uri, ConfigProvider.METHOD_LOG_CLEAR, null, null)
        } catch (t: Throwable) {
            // 静默忽略
        }
    }
}
