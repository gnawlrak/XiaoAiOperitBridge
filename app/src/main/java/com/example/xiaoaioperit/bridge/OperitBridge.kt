package com.example.xiaoaioperit.bridge

import android.util.Log
import com.example.xiaoaioperit.config.OperitConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Operit HTTP 通信层。
 *
 * Operit 外部 HTTP 调用 API 格式：
 * POST http://{host}:{port}/api/external-chat
 * Body: {"message":"...","response_mode":"sync","show_floating":false,
 *        "initial_mode":"WINDOW","return_tool_status":false,"stream":true}
 */
object OperitBridge {

    private const val TAG = "XiaoAiOperit"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private class OperitHttpError(val code: Int, val body: String) : RuntimeException("HTTP $code: $body")

    interface StreamCallback {
        fun onStart(requestId: String, chatId: String?)
        fun onDelta(delta: String)
        fun onDone(fullResponse: String)
        fun onError(error: String)
    }

    /**
     * 流式发送聊天请求到 Operit AI。
     * 通过 SSE 协议逐段接收 delta，并实时回调给调用方。
     */
    fun chatStream(
        config: OperitConfig,
        userText: String,
        callback: StreamCallback
    ) {
        val body = buildExternalChatBody(config, userText, stream = true)
        val url = buildUrl(config)
        postSse(url, config.token, body, callback)
    }

    /**
     * 测试连接：发送一个简单的 ping 请求（同步模式）。
     */
    fun testConnection(config: OperitConfig): String {
        val body = buildExternalChatBody(config, "ping", stream = false)
        val url = buildUrl(config)
        val response = postJson(url, config.token, body)
        return parseExternalChatResponse(response)
            .ifBlank { "连接成功（返回为空）" }
    }

    private fun buildExternalChatBody(config: OperitConfig, message: String, stream: Boolean): JSONObject {
        return JSONObject()
            .put("message", message)
            .put("response_mode", "sync")
            .put("stream", stream)
            .put("show_floating", false)
            .put("initial_mode", "WINDOW")
            .put("return_tool_status", false)
            .put("create_new_chat", true)
    }

    private fun parseExternalChatResponse(response: JSONObject): String {
        val aiResponse = response.optString("ai_response", "").trim()
        if (aiResponse.isNotBlank()) return aiResponse
        listOf("message", "content", "text", "result").forEach { key ->
            response.optString(key, "").trim().takeIf { it.isNotBlank() }?.let { return it }
        }
        if (response.optBoolean("success", false)) return ""
        response.optString("error", "").trim().takeIf { it.isNotBlank() }?.let {
            throw RuntimeException("Operit 返回错误: $it")
        }
        return ""
    }

    private fun buildUrl(config: OperitConfig): String {
        val endpoint = config.effectiveEndpoint.trimEnd('/')
        val path = config.effectiveApiPath.trimStart('/')
        return "$endpoint/$path"
    }

    private fun postJson(url: String, token: String, body: JSONObject): JSONObject {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Accept", "application/json")
        if (token.isNotBlank()) requestBuilder.addHeader("Authorization", "Bearer $token")
        val request = requestBuilder.build()
        val response = try { client.newCall(request).execute() } catch (t: Throwable) {
            throw RuntimeException("Operit 连接失败: ${t.message}", t)
        }
        val responseBody = response.body?.string() ?: ""
        response.close()
        if (!response.isSuccessful) throw OperitHttpError(response.code, responseBody)
        return JSONObject(responseBody)
    }

    private fun postSse(url: String, token: String, body: JSONObject, callback: StreamCallback) {
        val requestBuilder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Accept", "text/event-stream")
        if (token.isNotBlank()) requestBuilder.addHeader("Authorization", "Bearer $token")
        val request = requestBuilder.build()

        val response = try { client.newCall(request).execute() } catch (t: Throwable) {
            callback.onError("Operit 连接失败: ${t.message}")
            return
        }
        if (!response.isSuccessful) {
            val bodyStr = response.body?.string() ?: ""
            response.close()
            callback.onError("Operit HTTP ${response.code}: $bodyStr")
            return
        }

        val source = response.body?.source() ?: run { response.close(); callback.onError("响应体为空"); return }
        val buffer = StringBuilder()
        try {
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("event: ") -> {
                        // SSE event type ignored; parsed from JSON payload below
                    }
                    line.startsWith("data: ") -> {
                        val payload = line.substringAfter("data: ")
                        if (payload.isBlank()) continue
                        try {
                            val json = JSONObject(payload)
                            when (json.optString("event")) {
                                "start" -> callback.onStart(
                                    json.optString("request_id", ""),
                                    json.optString("chat_id").takeIf { it.isNotBlank() }
                                )
                                "delta" -> {
                                    val delta = json.optString("delta", "")
                                    if (delta.isNotEmpty()) {
                                        buffer.append(delta)
                                        callback.onDelta(delta)
                                    }
                                }
                                "done" -> {
                                    val full = json.optString("ai_response", "").ifBlank { buffer.toString() }
                                    callback.onDone(full)
                                    return
                                }
                                "error" -> {
                                    callback.onError(json.optString("error", "未知错误"))
                                    return
                                }
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "failed to parse SSE payload: $payload")
                        }
                    }
                }
            }
            // 流正常结束但无 done（防护）
            callback.onDone(buffer.toString())
        } catch (t: Throwable) {
            Log.w(TAG, "SSE read failed: $t")
            callback.onError("SSE 读取失败: ${t.message}")
        } finally {
            response.close()
        }
    }
}

data class ChatTurn(val question: String, val answer: String, val time: Long)
