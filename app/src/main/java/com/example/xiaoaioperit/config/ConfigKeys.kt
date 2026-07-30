package com.example.xiaoaioperit.config

object ConfigKeys {
    const val HOST = "operit_host"
    const val PORT = "operit_port"
    const val API_PATH = "operit_api_path"
    const val TOKEN = "operit_token"
    const val MODEL = "operit_model"
    const val ENABLED = "operit_enabled"
    const val SYSTEM_PROMPT = "system_prompt"
    const val SPEAK_ANSWER = "speak_answer"
    const val BLOCK_VIEW_JUMP = "block_view_jump"
    const val BLOCK_WEB_SEARCH = "block_web_search"
    const val JUMP_ALLOW_WORDS = "jump_allow_words"
    const val WEB_SEARCH_ALLOW_WORDS = "web_search_allow_words"
    const val SKIP_TAKEOVER_PATTERN = "skip_takeover_pattern"
    const val INTERCEPT_PATTERN = "intercept_pattern"
    const val FULL_INTERCEPT = "full_intercept"
    const val USE_MIUIX = "use_miuix"

    val ALL = listOf(
        HOST, PORT, API_PATH, TOKEN, MODEL, ENABLED,
        SYSTEM_PROMPT, SPEAK_ANSWER,
        BLOCK_VIEW_JUMP, BLOCK_WEB_SEARCH,
        JUMP_ALLOW_WORDS, WEB_SEARCH_ALLOW_WORDS,
        SKIP_TAKEOVER_PATTERN, INTERCEPT_PATTERN, FULL_INTERCEPT, USE_MIUIX
    )
}