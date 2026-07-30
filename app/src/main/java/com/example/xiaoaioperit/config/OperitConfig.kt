package com.example.xiaoaioperit.config

/**
 * Operit 连接配置。
 */
data class OperitConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val apiPath: String = DEFAULT_API_PATH,
    val token: String = "",
    val model: String = "",
    val enabled: Boolean = false,
    val systemPrompt: String = "",
    val speakAnswer: Boolean = true,
    val blockViewJump: Boolean = true,
    val blockWebSearch: Boolean = true,
    val jumpAllowWords: String = DEFAULT_JUMP_ALLOW_WORDS,
    val webSearchAllowWords: String = DEFAULT_WEB_SEARCH_ALLOW_WORDS,
    val skipTakeoverPattern: String = "",
    val interceptPattern: String = "",
    val fullIntercept: Boolean = false,
    val useMiuix: Boolean = false
) {
    val effectiveEndpoint: String get() = "http://$host:$port"
    val effectiveApiPath: String get() = apiPath.ifBlank { DEFAULT_API_PATH }
    val effectiveSystemPrompt: String get() = systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT }
}

const val DEFAULT_API_PATH = "/api/external-chat"

const val DEFAULT_JUMP_ALLOW_WORDS = "打开,开启,进入,去,跳转,启动"
const val DEFAULT_WEB_SEARCH_ALLOW_WORDS = "搜索,搜一下,搜下,搜搜,百度,上网搜,网上搜"

val DEFAULT_SYSTEM_PROMPT = """
你是运行在这台 Android 设备上的本地智能助手，
通过 LSPosed 模块接管了系统原有的「小爱同学」语音入口。

# 你的运行环境
- 你直接运行在用户的手机系统内部，不是云端的通用助手。
- 用户通过语音与你交互，回答要口语化、简洁、可直接听懂。
- 你的回答会被 TTS 念出来，所以不要使用 markdown 格式。

# 行为准则
- 回答简洁直接，别啰嗦客套。
- 如果用户问的是设备相关问题，请如实告知无法获取，不要编造。
- 回答默认用简体中文。
""".trim()