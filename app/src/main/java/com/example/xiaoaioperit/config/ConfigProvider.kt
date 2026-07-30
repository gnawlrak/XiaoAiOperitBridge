package com.example.xiaoaioperit.config

import android.content.ContentProvider
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.content.Context
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * 跨进程配置存取：配置界面（本模块进程）写配置，
 * Hook 注入到小爱进程后通过 ContentResolver.call() 读配置。
 *
 * 同时兼任日志存储，Hook 进程写入日志，UI 进程读取展示。
 */
class ConfigProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.example.xiaoaioperit.config"
        const val METHOD_GET = "get"
        const val METHOD_SET = "set"
        const val METHOD_LOG_READ = "log_read"
        const val METHOD_LOG_APPEND = "log_append"
        const val METHOD_LOG_CLEAR = "log_clear"

        private const val PREFS_NAME = "xiaoai_operit_config"
        private const val LOGS_PREFS = "xiaoai_operit_logs"
        private const val LOGS_KEY = "logs_json"
        private const val MAX_LOGS = 200

        private val ALLOWED_CALLERS = setOf(
            "com.example.xiaoaioperit",
            "com.miui.voiceassist"
        )
    }

    private fun prefs(): SharedPreferences =
        context!!.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun logPrefs(): SharedPreferences =
        context!!.getSharedPreferences(LOGS_PREFS, Context.MODE_PRIVATE)

    override fun onCreate(): Boolean = true

    private fun isCallerAllowed(): Boolean {
        val caller = callingPackage ?: return false
        return caller in ALLOWED_CALLERS
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!isCallerAllowed()) return null
        return when (method) {
            METHOD_GET -> {
                val p = prefs()
                val out = Bundle()
                for (k in ConfigKeys.ALL) {
                    out.putString(k, p.getString(k, ""))
                }
                out
            }
            METHOD_SET -> {
                val e = prefs().edit()
                if (extras != null) {
                    for (k in ConfigKeys.ALL) {
                        if (extras.containsKey(k)) {
                            e.putString(k, extras.getString(k, ""))
                        }
                    }
                }
                e.apply()
                Bundle().apply { putBoolean("ok", true) }
            }
            METHOD_LOG_READ -> {
                val json = logPrefs().getString(LOGS_KEY, "[]") ?: "[]"
                Bundle().apply { putString("logs", json) }
            }
            METHOD_LOG_APPEND -> {
                val entryJson = extras?.getString("entry") ?: return Bundle().apply { putBoolean("ok", false) }
                val prefs = logPrefs()
                val existing = prefs.getString(LOGS_KEY, "[]") ?: "[]"
                val arr = try {
                    JSONArray(existing)
                } catch (t: Throwable) {
                    JSONArray()
                }
                // 新条目插到最前面
                val newArr = JSONArray().apply {
                    put(JSONObject(entryJson))
                    for (i in 0 until minOf(arr.length(), MAX_LOGS - 1)) {
                        put(arr.get(i))
                    }
                }
                prefs.edit().putString(LOGS_KEY, newArr.toString()).apply()
                Bundle().apply { putBoolean("ok", true) }
            }
            METHOD_LOG_CLEAR -> {
                logPrefs().edit().remove(LOGS_KEY).apply()
                Bundle().apply { putBoolean("ok", true) }
            }
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}