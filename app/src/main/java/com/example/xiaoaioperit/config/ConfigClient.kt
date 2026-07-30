package com.example.xiaoaioperit.config

import android.content.Context
import android.net.Uri
import android.os.Bundle

object ConfigClient {

    private val uri: Uri = Uri.parse("content://${ConfigProvider.AUTHORITY}")

    fun read(context: Context): OperitConfig {
        val result: Bundle = try {
            context.contentResolver.call(uri, ConfigProvider.METHOD_GET, null, null)
        } catch (t: Throwable) {
            null
        } ?: return OperitConfig()
        return fromBundle(result)
    }

    fun write(context: Context, config: OperitConfig): Boolean {
        val extras = Bundle().apply {
            putString(ConfigKeys.HOST, config.host)
            putString(ConfigKeys.PORT, config.port.toString())
            putString(ConfigKeys.API_PATH, config.apiPath)
            putString(ConfigKeys.TOKEN, config.token)
            putString(ConfigKeys.MODEL, config.model)
            putString(ConfigKeys.ENABLED, config.enabled.toString())
            putString(ConfigKeys.SYSTEM_PROMPT, config.systemPrompt)
            putString(ConfigKeys.SPEAK_ANSWER, config.speakAnswer.toString())
            putString(ConfigKeys.BLOCK_VIEW_JUMP, config.blockViewJump.toString())
            putString(ConfigKeys.BLOCK_WEB_SEARCH, config.blockWebSearch.toString())
            putString(ConfigKeys.JUMP_ALLOW_WORDS, config.jumpAllowWords)
            putString(ConfigKeys.WEB_SEARCH_ALLOW_WORDS, config.webSearchAllowWords)
            putString(ConfigKeys.SKIP_TAKEOVER_PATTERN, config.skipTakeoverPattern)
            putString(ConfigKeys.INTERCEPT_PATTERN, config.interceptPattern)
            putString(ConfigKeys.FULL_INTERCEPT, config.fullIntercept.toString())
            putString(ConfigKeys.USE_MIUIX, config.useMiuix.toString())
        }
        return try {
            val out = context.contentResolver.call(uri, ConfigProvider.METHOD_SET, null, extras)
            out?.getBoolean("ok") == true
        } catch (t: Throwable) {
            false
        }
    }

    private fun fromBundle(result: Bundle): OperitConfig {
        val portStr = result.getString(ConfigKeys.PORT).orEmpty()
        return OperitConfig(
            host = result.getString(ConfigKeys.HOST).orEmpty().ifBlank { "127.0.0.1" },
            port = portStr.toIntOrNull() ?: 8080,
            apiPath = result.getString(ConfigKeys.API_PATH).orEmpty().ifBlank { DEFAULT_API_PATH },
            token = result.getString(ConfigKeys.TOKEN).orEmpty(),
            model = result.getString(ConfigKeys.MODEL).orEmpty(),
            enabled = result.getString(ConfigKeys.ENABLED) == "true",
            systemPrompt = result.getString(ConfigKeys.SYSTEM_PROMPT).orEmpty(),
            speakAnswer = result.getString(ConfigKeys.SPEAK_ANSWER).orEmpty().let { it.isEmpty() || it == "true" },
            blockViewJump = result.getString(ConfigKeys.BLOCK_VIEW_JUMP).orEmpty().let { it.isEmpty() || it == "true" },
            blockWebSearch = result.getString(ConfigKeys.BLOCK_WEB_SEARCH).orEmpty().let { it.isEmpty() || it == "true" },
            jumpAllowWords = result.getString(ConfigKeys.JUMP_ALLOW_WORDS).orEmpty().ifBlank { DEFAULT_JUMP_ALLOW_WORDS },
            webSearchAllowWords = result.getString(ConfigKeys.WEB_SEARCH_ALLOW_WORDS).orEmpty().ifBlank { DEFAULT_WEB_SEARCH_ALLOW_WORDS },
            skipTakeoverPattern = result.getString(ConfigKeys.SKIP_TAKEOVER_PATTERN).orEmpty(),
            interceptPattern = result.getString(ConfigKeys.INTERCEPT_PATTERN).orEmpty(),
            fullIntercept = result.getString(ConfigKeys.FULL_INTERCEPT) == "true",
            useMiuix = result.getString(ConfigKeys.USE_MIUIX) == "true"
        )
    }
}