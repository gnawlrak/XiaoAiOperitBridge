package com.example.xiaoaioperit

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.xiaoaioperit.bridge.ChatTurn
import com.example.xiaoaioperit.bridge.OperitBridge
import com.example.xiaoaioperit.config.ConfigClient
import com.example.xiaoaioperit.config.LogClient
import com.example.xiaoaioperit.config.OperitConfig
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "XiaoAiOperit"
private const val TARGET_PKG = "com.miui.voiceassist"
private const val SELF_PKG = "com.example.xiaoaioperit"
private const val SETTINGS_PKG = "com.android.settings"

// 混淆类名 - 需根据实际超级小爱版本调整
private const val OPERATION_MANAGER_CLASS = "com.xiaomi.voiceassistant.instruction.base.OperationManager"
private const val RN_CARD_CLASS = "com.xiaomi.voiceassistant.instruction.card.TemplateReactNativeCard"
private const val BRIDGE_CLASS = "r70.a"
private const val AUDIO_TRACK_MANAGER_CLASS = "v20.e"
private const val OUR_AUDIO_TRACK = "toastStreamTts"
private const val TOAST_STREAM_PLAYER_CLASS = "la0.n1"
private const val TTS_BRIDGE_CLASS = "com.xiaomi.voiceassistant.u1"
private const val ASR_PROCESSOR_CLASS = "z10.a"
private const val ASR_RECOGNIZE_RESULT = "SpeechRecognizer.RecognizeResult"
private const val AGENT_ACTION_CLASS = "kh0.s0"
private const val TOAST_OPERATION_CLASS = "jb0.vd"
private const val SPEAK_CONTENT_CLASS = "com.xiaomi.voiceassistant.instruction.utils.b2"
private const val INTENT_UTILS_WRAPPER_CLASS = "com.xiaomi.voiceassistant.instruction.utils.IntentUtilsWrapper"
private const val INTENT_UTILS_CLASS = "com.xiaomi.voiceassistant.utils.m2"
private const val QUICK_SEARCH_PKG = "com.android.quicksearchbox"
private const val CHAT_DB_MANAGER_CLASS = "com.xiaomi.voiceassistant.skills.model.chat.a"
private const val FLOW_TOAST_CARD_CLASS = "com.xiaomi.voiceassistant.instruction.card.stream.FlowTemplateToastCard"
private const val FLOW_CONTROLLER_CLASS = "com.xiaomi.voiceassistant.mainui.flowableresult.d"
private const val FLOAT_MANAGER_CLASS = "com.xiaomi.voiceassistant.widget.d"

private const val MAX_SPEAK_CHARS = 220
private const val UTTERANCE_WINDOW_MS = 15_000L

// 强制接管模式：打开/启动 xxx 并执行操作（不等待小爱回落，直接劫持）
// [^\n] 避免跨行匹配；{0,30} 适配较长指令（如"打开哔哩哔哩动画app搜索"）
private val FORCE_TAKEOVER_PATTERN = Regex(
    "(?:帮我|给我)?\\s*(?:打开|开启|启动|进入|去|跳转)[^\n]{0,30}?" +
    "(?:发消息|发信息|搜索|查找|看|找|查|写|输|播放|下载|安装|卸载|" +
    "复制|粘贴|保存|转发|分享|评论|点赞|收藏|创建|编辑|删除|添加|修改|" +
    "设置|调整|切换|导入|导出|上传|提交|发送|接收|更新|升级|清理|" +
    "操作|执行|运行|截屏|截图|录屏|录音|拍照|扫描|识别|" +
    "预订|购买|支付|退款|预约|签到|打卡|回复|举报|屏蔽|拉黑|关注|取关|订阅|退订)"
)

// 默认规则："使用 operit 执行 xxx" / "让 operit 做 xxx" 等，直接接管并把 xxx 作为实际请求发给 Operit
private val OPERIT_PREFIX_PATTERN = Regex(
    "^\\s*(?:用|使用|让|叫)?\\s*operit\\s*(?:来|帮我|给我|执行|做|干|处理)?\\s*(?<cmd>.+)$",
    RegexOption.IGNORE_CASE
)

class HookEntry : IXposedHookLoadPackage {

    private fun hookSelfProbe(cl: ClassLoader) {
        try {
            val clazz = cl.loadClass("com.example.xiaoaioperit.ModuleStatus")
            XposedBridge.hookAllMethods(clazz, "isActive", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = true
                }
            })
            Log.i(TAG, "self probe installed, module is active")
        } catch (t: Throwable) {
            Log.w(TAG, "self probe failed: $t")
        }
    }

    private val queryTexts = ConcurrentHashMap<String, String>()
    private val aiAnswers = ConcurrentHashMap<String, String>()
    private val bridgeRefs = ConcurrentHashMap<String, WeakReference<Any>>()
    private val cardRefs = ConcurrentHashMap<String, WeakReference<Any>>()
    private val takeOver = ConcurrentHashMap.newKeySet<String>()
    private val injected = ConcurrentHashMap.newKeySet<String>()
    private val injectingNow = ThreadLocal.withInitial { false }
    private var sendStreamDataMethod: java.lang.reflect.Method? = null

    @Volatile private var lastQueryText: String = ""
    @Volatile private var lastQueryTime: Long = 0L
    @Volatile private var lastDialogId: String = ""
    @Volatile private var lastConfig: OperitConfig? = null
    @Volatile private var lastAsrText: String = ""
    @Volatile private var lastAsrTime: Long = 0L
    @Volatile private var targetClassLoader: ClassLoader? = null
    private val pendingViewAnswer = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var activeCardSink: WeakReference<Any>? = null
    private val answerCards = ConcurrentHashMap<String, WeakReference<Any>>()
    private val commandeeredCards = ConcurrentHashMap.newKeySet<Int>()
    private val answerCardSinks = ConcurrentHashMap<String, WeakReference<Any>>()
    private val detachedCards = ConcurrentHashMap.newKeySet<Int>()
    private val ourCardTexts = ConcurrentHashMap<Int, String>()
    private val THINKING_PLACEHOLDER = "🤖 正在思考…"
    private val historyPending = ConcurrentHashMap.newKeySet<String>()
    private val historyWritten = ConcurrentHashMap.newKeySet<String>()
    private val writingHistory = ThreadLocal.withInitial { false }

    // 待回落检测：小爱先处理，如果返回"小爱暂不支持该功能"再触发 Operit
    private val pendingFallbackDialogs = ConcurrentHashMap.newKeySet<String>()

    // 强制接管（打开app+干xxx事）：不等待回落，直接劫持并完全阻止小爱输出
    private val forceTakenOver = ConcurrentHashMap.newKeySet<String>()

    private val utteranceLastSeen = ConcurrentHashMap<String, Pair<String, Long>>()
    private val utteranceDialogs = ConcurrentHashMap<String, MutableSet<String>>()
    private val utteranceAnswers = ConcurrentHashMap<String, String>()
    private val utterancePartial = ConcurrentHashMap<String, String>()
    private val utteranceCalling = ConcurrentHashMap.newKeySet<String>()
    private val spokenUtterances = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var mutePumpUntil = 0L
    @Volatile private var mutePumpDialogId = ""
    @Volatile private var mutePumpRunning = false

    @Volatile private var cachedVhField: java.lang.reflect.Field? = null

    override fun handleLoadPackage(lpparam: de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == SELF_PKG) {
            hookSelfProbe(lpparam.classLoader)
            return
        }
        if (lpparam.packageName == SETTINGS_PKG) return
        if (lpparam.packageName != TARGET_PKG) return
        Log.i(TAG, "loaded into $TARGET_PKG process=${lpparam.processName}")
        targetClassLoader = lpparam.classLoader

        hookOperationManager(lpparam.classLoader)
        hookRnCard(lpparam.classLoader)
        hookRnCardStop(lpparam.classLoader)
        hookRnJsReady(lpparam.classLoader)
        hookBridge(lpparam.classLoader)
        hookCardBaseDiagnostic(lpparam.classLoader)
        hookSettingsJump(lpparam.classLoader)
        hookWebSearchFallback(lpparam.classLoader)
        hookChatHistory(lpparam.classLoader)
        hookCardSinks(lpparam.classLoader)
        hookAsrResult(lpparam.classLoader)
        hookAgentAction(lpparam.classLoader)
        hookToastCard(lpparam.classLoader)
        hookIntentLaunch(lpparam.classLoader)
        hookToastCardBind(lpparam.classLoader)
        // hookBackgroundAppsNav removed - no longer needed with fallback-only approach
    }

    // ============== Hook methods (same as XiaoAiplug) ==============

    private fun hookSettingsJump(cl: ClassLoader) {
        val clazz = try { cl.loadClass(INTENT_UTILS_WRAPPER_CLASS) } catch (e: Throwable) { Log.i(TAG, "INTENT_UTILS_WRAPPER not found"); return }
        for (m in clazz.declaredMethods) {
            if (m.name != "startActivitySafely") continue
            val types = m.parameterTypes
            if (types.isEmpty() || types[0] != android.content.Intent::class.java) continue
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val intent = param.args[0] as? android.content.Intent ?: return
                            if (!ownsCurrentTurn()) return
                            Log.i(TAG, "block view-jump: query=\"$lastQueryText\" intent=$intent")
                            param.result = when (m.returnType) { java.lang.Boolean.TYPE -> true; Integer.TYPE -> 0; else -> null }
                            onViewJumpBlocked(lastDialogId)
                        } catch (t: Throwable) { Log.i(TAG, "hookSettingsJump error: $t") }
                    }
                })
                Log.i(TAG, "hooked IntentUtilsWrapper.startActivitySafely")
            } catch (t: Throwable) { Log.i(TAG, "hook startActivitySafely fail: $t") }
        }
    }

    private fun hookAsrResult(cl: ClassLoader) {
        val clazz = try { cl.loadClass(ASR_PROCESSOR_CLASS) } catch (e: Throwable) { Log.i(TAG, "ASR_PROCESSOR not found"); return }
        val method = clazz.declaredMethods.firstOrNull { it.name == "processed" && it.parameterTypes.size == 1 } ?: run { Log.i(TAG, "z10.a.processed not found"); return }
        try {
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val instruction = param.args[0] ?: return
                        val fullName = instruction.javaClass.getMethod("getFullName").invoke(instruction) as? String ?: return
                        if (fullName != ASR_RECOGNIZE_RESULT) return
                        val payload = instruction.javaClass.getMethod("getPayload").invoke(instruction) ?: return
                        val isFinal = payload.javaClass.getMethod("isFinal").invoke(payload) as? Boolean ?: false
                        if (!isFinal) return
                        val results = payload.javaClass.getMethod("getResults").invoke(payload) as? List<*> ?: return
                        val text = results.filterNotNull().joinToString("") { (it.javaClass.getMethod("getText").invoke(it) as? String).orEmpty() }
                        if (text.isBlank()) return
                        val dialogId = optionalString(instruction.javaClass.getMethod("getDialogId").invoke(instruction)).orEmpty()
                        val ctx = currentApplicationContext()
                        val config = if (ctx != null) ConfigClient.read(ctx) else null
                        if (config != null) lastConfig = config
                        val elapsed = System.currentTimeMillis() - lastQueryTime
                        val junk = text.length <= 3
                        if (junk && elapsed < 5_000L && utteranceCalling.isNotEmpty()) { Log.i(TAG, "ignore ASR fragment \"$text\""); return }
                        if (text != lastQueryText) stopMutePump()
                        lastQueryText = text; lastQueryTime = System.currentTimeMillis(); lastAsrText = text; lastAsrTime = lastQueryTime
                        if (dialogId.isNotBlank()) lastDialogId = dialogId
                        Log.i(TAG, "asr final: dialogId=$dialogId text=$text")
                    } catch (t: Throwable) { Log.i(TAG, "hookAsrResult error: $t") }
                }
            })
            Log.i(TAG, "hooked z10.a.processed (asr)")
        } catch (t: Throwable) { Log.i(TAG, "hook asr fail: $t") }
    }

    private fun hookToastCard(cl: ClassLoader) {
        val clazz = try { cl.loadClass(TOAST_OPERATION_CLASS) } catch (e: Throwable) { return }
        val targets = clazz.declaredMethods.filter { (it.name == "g0" || it.name == "i0") && it.parameterTypes.size == 1 && it.parameterTypes[0] == Integer.TYPE }
        if (targets.isEmpty()) { Log.i(TAG, "vd.g0/i0 not found"); return }
        for (m in targets) {
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val card = param.result ?: return
                            val op = param.thisObject
                            val opDialogId = try { op.javaClass.getMethod("getDialogId").invoke(op) as? String } catch (t: Throwable) { null }.orEmpty()
                            claimToastCard(card, opDialogId, via = "vd.${m.name}")
                        } catch (t: Throwable) { Log.i(TAG, "hookToastCard error: $t") }
                    }
                })
                Log.i(TAG, "hooked vd.${m.name}(int)")
            } catch (t: Throwable) { Log.i(TAG, "hook vd.${m.name} fail: $t") }
        }
    }

    // hookBackgroundAppsNav removed - no longer needed with fallback-only approach

    private fun optionalString(opt: Any?): String? {
        if (opt == null) return null
        return try { val present = opt.javaClass.getMethod("isPresent").invoke(opt) as? Boolean ?: false; if (!present) null else opt.javaClass.getMethod("get").invoke(opt)?.toString() } catch (t: Throwable) { null }
    }

    private fun hookAgentAction(cl: ClassLoader) {
        val clazz = try { cl.loadClass(AGENT_ACTION_CLASS) } catch (e: Throwable) { return }
        val targets = clazz.declaredMethods.filter { it.name == "executeActionsAsync" || it.name == "execute" }
        if (targets.isEmpty()) return
        for (m in targets) {
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val action = param.args.firstOrNull { it != null && it.javaClass.name.endsWith("Agent\$Action") } ?: return
                            @Suppress("UNCHECKED_CAST")
                            val specs = (action.javaClass.getMethod("getAction").invoke(action) as? List<String>).orEmpty()
                            if (!shouldBlockAgentAction(specs)) return
                            Log.i(TAG, "block agent action: query=\"$lastQueryText\" specs=$specs")
                            param.result = if (m.returnType == java.lang.Boolean.TYPE) true else null
                            onViewJumpBlocked(lastDialogId)
                        } catch (t: Throwable) { Log.i(TAG, "hookAgentAction error: $t") }
                    }
                })
                Log.i(TAG, "hooked s0.${m.name}")
            } catch (t: Throwable) { Log.i(TAG, "hook s0.${m.name} fail: $t") }
        }
    }

    private fun shouldBlockAgentAction(specs: List<String>): Boolean = specs.isNotEmpty() && ownsCurrentTurn()

    // 白名单直通命中的正则,编译失败(用户手写的正则语法有误)就当不生效处理,别把整个接管功能拖垮
    @Volatile private var skipTakeoverRegexCache: Pair<String, Regex?>? = null

    // 判定这句问话是不是命中了"白名单直通"正则:命中就完全不接管,原生行为照旧
    private fun isAiTakeoverSkip(q: String, cfg: OperitConfig): Boolean {
        if (!cfg.enabled) return false
        val pattern = cfg.skipTakeoverPattern
        if (pattern.isBlank()) return false
        val cached = skipTakeoverRegexCache
        val regex = if (cached != null && cached.first == pattern) {
            cached.second
        } else {
            val compiled = runCatching { Regex(pattern) }
                .onFailure { Log.w(TAG, "skip-takeover pattern invalid, ignored: $pattern", it) }
                .getOrNull()
            skipTakeoverRegexCache = pattern to compiled
            compiled
        }
        return regex?.containsMatchIn(q) == true
    }

    // 默认规则：识别 "使用 operit 执行 xxx" 类前缀，把 xxx 作为实际请求返回；未命中返回 null
    private fun extractOperitCommand(q: String): String? {
        val match = OPERIT_PREFIX_PATTERN.find(q) ?: return null
        return match.groups["cmd"]?.value?.trim()?.takeIf { it.isNotBlank() }
    }

    // 自定义拦截正则的编译缓存,编译失败(用户手写的正则语法有误)同样当不生效处理
    @Volatile private var interceptRegexCache: Pair<String, Regex?>? = null

    // 判定这句问话是不是命中了"自定义拦截"正则:命中就不等小爱回落,直接接管
    private fun isInterceptHit(q: String, cfg: OperitConfig): Boolean {
        if (!cfg.enabled) return false
        val pattern = cfg.interceptPattern
        if (pattern.isBlank()) return false
        val cached = interceptRegexCache
        val regex = if (cached != null && cached.first == pattern) {
            cached.second
        } else {
            val compiled = runCatching { Regex(pattern) }
                .onFailure { Log.w(TAG, "intercept pattern invalid, ignored: $pattern", it) }
                .getOrNull()
            interceptRegexCache = pattern to compiled
            compiled
        }
        return regex?.containsMatchIn(q) == true
    }

    // 强制接管到 Operit 的公共逻辑（去重，同时被全局拦截/自定义规则/内置规则复用）
    private fun takeoverToOperit(dialogId: String, userQuery: String, config: OperitConfig) {
        val actualQuery = extractOperitCommand(userQuery) ?: userQuery
        if (!queryTexts.containsKey(dialogId)) {
            queryTexts[dialogId] = actualQuery
        }
        forceTakenOver.add(dialogId)
        takeOver.add(dialogId)
        pendingViewAnswer.add(dialogId)
        historyPending.add(dialogId)
        bridgeRefs[dialogId] = WeakReference(null)
        if (config.speakAnswer) startMutePump(dialogId)
        val key = utteranceKeyFor(actualQuery)
        utteranceDialogs.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(dialogId)
        if (utteranceCalling.add(key)) {
            startAiCall(key, actualQuery, config)
        }
    }

    // 当前是否有已接管的对话，用于拦截小爱后续的原生跳转
    private fun ownsCurrentTurn(): Boolean {
        val cfg = lastConfig ?: return false
        if (!cfg.enabled) return false
        if (System.currentTimeMillis() - lastQueryTime > 30_000L) return false
        return takeOver.isNotEmpty()
    }

    private fun hookWebSearchFallback(cl: ClassLoader) {
        val clazz = try { cl.loadClass(INTENT_UTILS_CLASS) } catch (e: Throwable) { return }
        val method = try { clazz.getDeclaredMethod("startActivitySafely", android.content.Intent::class.java, String::class.java) } catch (e: Throwable) { return }
        try {
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val intent = param.args[0] as? android.content.Intent ?: return
                        if (!shouldBlockWebSearch(intent)) return
                        Log.i(TAG, "block web-search fallback")
                        param.result = 0
                        onViewJumpBlocked(lastDialogId)
                    } catch (t: Throwable) { Log.i(TAG, "hookWebSearchFallback error: $t") }
                }
            })
            Log.i(TAG, "hooked m2.startActivitySafely")
        } catch (t: Throwable) { Log.i(TAG, "hook m2.startActivitySafely fail: $t") }
    }

    private fun hookIntentLaunch(cl: ClassLoader) {
        val clazz = try { cl.loadClass(INTENT_UTILS_CLASS) } catch (e: Throwable) { return }
        val targets = clazz.declaredMethods.filter { val p = it.parameterTypes; p.size == 2 && p[0] == Context::class.java && p[1] == android.content.Intent::class.java }
        if (targets.isEmpty()) return
        for (m in targets) {
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val intent = param.args[1] as? android.content.Intent ?: return
                            if (!ownsCurrentTurn()) return
                            Log.i(TAG, "block intent launch")
                            param.result = when (m.returnType) { java.lang.Boolean.TYPE -> true; Integer.TYPE -> 0; else -> null }
                            onViewJumpBlocked(lastDialogId)
                        } catch (t: Throwable) { Log.i(TAG, "hookIntentLaunch error: $t") }
                    }
                })
                Log.i(TAG, "hooked m2.${m.name}(Context, Intent)")
            } catch (t: Throwable) { Log.i(TAG, "hook m2.${m.name} fail: $t") }
        }
    }

    private fun hookChatHistory(cl: ClassLoader) {
        val clazz = try { cl.loadClass(CHAT_DB_MANAGER_CLASS) } catch (e: Throwable) { return }
        val methods = clazz.declaredMethods.filter { it.name == "insert" && it.parameterTypes.size == 1 }
        if (methods.isEmpty()) return
        for (method in methods) {
            try {
                XposedBridge.hookMethod(method, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            if (writingHistory.get() == true) return
                            val bean = param.args[0] ?: return
                            val isSend = try { bean.javaClass.getMethod("getIsSend").invoke(bean) as? Int ?: 0 } catch (t: Throwable) { 0 }
                            if (isSend > 0) return
                            val text = try { bean.javaClass.getMethod("getUserContent").invoke(bean) as? String } catch (t: Throwable) { null } ?: return
                            if (!shouldSuppressHistory(text)) return
                            Log.i(TAG, "suppress history insert: ${text.take(40)}")
                            param.result = false
                        } catch (t: Throwable) { Log.i(TAG, "hookChatHistory error: $t") }
                    }
                })
                Log.i(TAG, "hooked ChatDbManager.insert")
            } catch (t: Throwable) { Log.i(TAG, "hook insert fail: $t") }
        }
    }

    private fun shouldSuppressHistory(text: String): Boolean {
        val cfg = lastConfig ?: return false
        if (!cfg.blockWebSearch || !cfg.enabled) return false
        if (System.currentTimeMillis() - lastQueryTime > 60_000L) return false
        return historyPending.isNotEmpty()
    }

    private fun writeAnswerToHistory(dialogId: String, answer: String) {
        if (dialogId !in historyPending) return
        if (!historyWritten.add(dialogId)) return
        val cl = targetClassLoader ?: return
        try {
            writingHistory.set(true)
            val clazz = cl.loadClass(CHAT_DB_MANAGER_CLASS)
            val instance = clazz.getMethod("getInstance").invoke(null) ?: return
            clazz.getDeclaredMethod("recordToSpeak", String::class.java).invoke(instance, answer)
            Log.i(TAG, "answer written to history dialogId=$dialogId")
        } catch (t: Throwable) {
            Log.i(TAG, "writeAnswerToHistory failed: $t")
            historyWritten.remove(dialogId)
        } finally {
            writingHistory.set(false)
            historyPending.remove(dialogId)
        }
    }

    private fun shouldBlockWebSearch(intent: android.content.Intent): Boolean {
        val cfg = lastConfig ?: return false
        if (!cfg.blockWebSearch || !cfg.enabled) return false
        if (System.currentTimeMillis() - lastQueryTime > 12_000L) return false
        if (lastQueryText.isBlank()) return false
        return isWebSearchIntent(intent)
    }

    private fun isWebSearchIntent(intent: android.content.Intent): Boolean {
        val action = intent.action.orEmpty()
        if (action == android.content.Intent.ACTION_WEB_SEARCH || action == android.content.Intent.ACTION_SEARCH) return true
        val pkg = intent.component?.packageName ?: intent.`package`.orEmpty()
        if (pkg == QUICK_SEARCH_PKG) return true
        if (intent.data?.scheme == "qsb") return true
        return false
    }

    private fun onViewJumpBlocked(dialogId: String) {
        try { startMutePump(dialogId) } catch (t: Throwable) { }
        val cfg = lastConfig ?: return
        if (!cfg.enabled) return
        if (dialogId.isBlank()) return
        pendingViewAnswer.add(dialogId)
        historyPending.add(dialogId)
        ensureAnswerCard(dialogId)
    }

    private fun hookCardSinks(cl: ClassLoader) {
        for (className in listOf(FLOW_CONTROLLER_CLASS, FLOAT_MANAGER_CLASS)) {
            val clazz = try { cl.loadClass(className) } catch (e: Throwable) { continue }
            for (m in clazz.declaredMethods) {
                if (m.name != "addCard" || m.parameterTypes.size != 1) continue
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            activeCardSink = WeakReference(param.thisObject)
                        }
                    })
                    Log.i(TAG, "hooked card sink $className.addCard")
                } catch (t: Throwable) { Log.i(TAG, "hook card sink $className fail: $t") }
            }
        }
    }

    private fun resolveFloatManager(): Any? {
        return try {
            val ctx = currentApplicationContext() ?: return null
            val uiManagerClass = ctx.classLoader.loadClass("com.xiaomi.voiceassistant.UiManager")
            val instance = uiManagerClass.getMethod("getInstance", Context::class.java).invoke(null, ctx) ?: return null
            val fm = instance.javaClass.getMethod("getFloatManager").invoke(instance)
            fm
        } catch (t: Throwable) { Log.i(TAG, "resolveFloatManager failed: $t"); null }
    }

    private fun ensureAnswerCard(dialogId: String) {
        if (dialogId !in pendingViewAnswer) return
        val cl = targetClassLoader ?: return
        val answer = aiAnswers[dialogId]
        val known = answerCards[dialogId]?.get()
        if (known != null && System.identityHashCode(known) in detachedCards) {
            detachedCards.remove(System.identityHashCode(known)); answerCards.remove(dialogId); answerCardSinks.remove(dialogId)
        }
        answerCards[dialogId]?.get()?.let { c ->
            val id = System.identityHashCode(c)
            if (id in commandeeredCards && viewHolderOf(c) == null) {
                commandeeredCards.remove(id); answerCards.remove(dialogId); answerCardSinks.remove(dialogId)
            }
        }
        val existing = answerCards[dialogId]?.get()
        if (existing != null) {
            if (answer != null) {
                Handler(Looper.getMainLooper()).post {
                    try { ourCardTexts[System.identityHashCode(existing)] = answer; existing.javaClass.getMethod("updateCardText", String::class.java).invoke(existing, answer); forceShowToastViewHolder(existing, answer) } catch (t: Throwable) { Log.i(TAG, "update answer card failed: $t") }
                    reattachIfWrongSink(cl, dialogId, existing)
                }
            }
            return
        }
        val sink = activeCardSink?.get() ?: resolveFloatManager()
        if (sink == null) { Log.i(TAG, "no active card sink yet dialogId=$dialogId"); return }
        Handler(Looper.getMainLooper()).post {
            try {
                val cardClass = cl.loadClass(FLOW_TOAST_CARD_CLASS)
                val ctor = cardClass.getConstructor(Integer.TYPE, String::class.java)
                val card = ctor.newInstance(0, answer ?: THINKING_PLACEHOLDER)
                try { cardClass.getMethod("setDialogId", String::class.java).invoke(card, dialogId) } catch (t: Throwable) { }
                answerCards[dialogId] = WeakReference(card); answerCardSinks[dialogId] = WeakReference(sink)
                ourCardTexts[System.identityHashCode(card)] = answer ?: THINKING_PLACEHOLDER
                val baseCardClass = cl.loadClass("com.xiaomi.voiceassistant.card.a")
                sink.javaClass.getMethod("addCard", baseCardClass).invoke(sink, card)
                Log.i(TAG, "answer card added dialogId=$dialogId")
            } catch (t: Throwable) { Log.i(TAG, "ensureAnswerCard add failed: $t") }
        }
    }

    private fun reattachIfWrongSink(cl: ClassLoader, dialogId: String, card: Any) {
        try {
            val used = answerCardSinks[dialogId]?.get() ?: return
            val current = activeCardSink?.get() ?: return
            if (used === current) return
            val baseCardClass = cl.loadClass("com.xiaomi.voiceassistant.card.a")
            current.javaClass.getMethod("addCard", baseCardClass).invoke(current, card)
            answerCardSinks[dialogId] = WeakReference(current)
            Log.i(TAG, "card reattached dialogId=$dialogId")
        } catch (t: Throwable) { Log.i(TAG, "reattachIfWrongSink failed: $t") }
    }

    private fun hookToastCardBind(cl: ClassLoader) {
        val clazz = try { cl.loadClass(FLOW_TOAST_CARD_CLASS) } catch (e: Throwable) { return }
        for (m in clazz.declaredMethods) {
            if (m.name != "bindView") continue
            try {
                XposedBridge.hookMethod(m, object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val card = param.thisObject
                            if (ourCardTexts[System.identityHashCode(card)] == null) claimToastCard(card, "", via = "bindView", onlyIfUnclaimed = true)
                            val text = ourCardTexts[System.identityHashCode(card)] ?: return
                            forceShowToastViewHolder(card, text)
                        } catch (t: Throwable) { Log.i(TAG, "hookToastCardBind error: $t") }
                    }
                })
                Log.i(TAG, "hooked FlowTemplateToastCard.bindView")
            } catch (t: Throwable) { Log.i(TAG, "hook FlowTemplateToastCard.bindView fail: $t") }
        }
    }

    private fun claimToastCard(card: Any, opDialogId: String, via: String, onlyIfUnclaimed: Boolean = false): Boolean {
        val ours = opDialogId in pendingViewAnswer || opDialogId in takeOver
        if (!ours && !isMutePumpActive()) return false
        val did = mutePumpDialogId.ifBlank { lastDialogId }
        if (did.isBlank()) return false
        val hash = System.identityHashCode(card)
        if (hash in commandeeredCards) return true
        if (onlyIfUnclaimed && answerCards[did]?.get() != null) return false
        val updater = try { card.javaClass.getMethod("updateCardText", String::class.java) } catch (t: Throwable) { return false }
        val text = aiAnswers[did] ?: THINKING_PLACEHOLDER
        answerCards[did] = WeakReference(card); ourCardTexts[hash] = text
        try { updater.invoke(card, text) } catch (t: Throwable) { }
        commandeeredCards.add(hash)
        Log.i(TAG, "commandeered toast card@$hash via=$via")
        return true
    }

    private fun viewHolderOf(card: Any): Any? {
        cachedVhField?.let { f -> if (f.declaringClass.isInstance(card)) return runCatching { f.get(card) }.getOrNull() }
        var c: Class<*>? = card.javaClass
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                val v = runCatching { f.isAccessible = true; f.get(card) }.getOrNull() ?: continue
                if (runCatching { v.javaClass.getMethod("getToastTv") }.isSuccess) { cachedVhField = f; return v }
            }
            c = c.superclass
        }
        return null
    }

    private fun forceShowToastViewHolder(card: Any, text: String) {
        try {
            val vh = viewHolderOf(card) ?: return
            val tv = vh.javaClass.getMethod("getToastTv").invoke(vh) as? android.widget.TextView ?: return
            tv.visibility = android.view.View.VISIBLE; tv.text = text
        } catch (t: Throwable) { Log.i(TAG, "forceShowToastViewHolder failed: $t") }
    }

    private fun hookRnJsReady(cl: ClassLoader) {
        val clazz = try { cl.loadClass(RN_CARD_CLASS) } catch (e: Throwable) { return }
        for (m in clazz.declaredMethods) {
            if (m.name == "rnStartReceiveInstruction") {
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val card = param.thisObject
                                val dialogId = card.javaClass.getMethod("getDialogId").invoke(card) as? String ?: return
                                if (dialogId.isNotBlank()) { cardRefs[dialogId] = WeakReference(card); maybeInject(dialogId) }
                            } catch (t: Throwable) { Log.i(TAG, "[rn] js ready hook error: $t") }
                        }
                    })
                    Log.i(TAG, "hooked rnStartReceiveInstruction")
                } catch (t: Throwable) { Log.i(TAG, "hook rnStartReceiveInstruction fail: $t") }
            }
        }
    }

    private fun isRnFrontReady(dialogId: String): Boolean? {
        val card = cardRefs[dialogId]?.get() ?: return null
        return try { val f = card.javaClass.getDeclaredField("Z3"); f.isAccessible = true; f.getBoolean(card) } catch (t: Throwable) { null }
    }

    private fun hookRnCardStop(cl: ClassLoader) {
        val clazz = try { cl.loadClass(RN_CARD_CLASS) } catch (e: Throwable) { return }
        for (m in clazz.declaredMethods) {
            if (m.name == "onStop") {
                try {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val dialogId = param.thisObject.javaClass.getMethod("getDialogId").invoke(param.thisObject) as? String ?: return
                                if (dialogId in takeOver && dialogId !in injected) { Log.i(TAG, "suppress onStop"); param.result = null }
                            } catch (t: Throwable) { }
                        }
                    })
                } catch (t: Throwable) { }
            }
        }
    }

    private fun hookCardBaseDiagnostic(cl: ClassLoader) {
        val clazz = try { cl.loadClass("com.xiaomi.voiceassistant.card.a") } catch (e: Throwable) { return }
        XposedBridge.hookAllConstructors(clazz, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val dialogId = try { param.thisObject.javaClass.getMethod("getDialogId").invoke(param.thisObject) } catch (t: Throwable) { null }
                Log.i(TAG, "[card] new ${param.thisObject.javaClass.name} dialogId=$dialogId")
            }
        })
        for (name in listOf("onCardDetached", "removeCard")) {
            for (m in clazz.declaredMethods) {
                if (m.name == name) {
                    try {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) { if (name == "onCardDetached") detachedCards.add(System.identityHashCode(param.thisObject)) }
                        })
                    } catch (t: Throwable) { }
                }
            }
        }
    }

    private fun hookOperationManager(cl: ClassLoader) {
        val clazz = try { cl.loadClass(OPERATION_MANAGER_CLASS) } catch (e: Throwable) { Log.i(TAG, "OPERATION_MANAGER not found"); return }
        val method = try { clazz.getDeclaredMethod("setQueryInfo", String::class.java, String::class.java, JSONObject::class.java) } catch (e: Throwable) { Log.i(TAG, "setQueryInfo not found"); return }
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    val dialogId = param.args[0] as? String ?: return
                    val queryText = param.args[1] as? String ?: return
                    if (dialogId.isBlank() || queryText.isBlank()) return
                    val ctx = currentApplicationContext()
                    val config = if (ctx != null) ConfigClient.read(ctx) else null
                    if (queryText != lastQueryText) stopMutePump()
                    lastQueryText = queryText; lastQueryTime = System.currentTimeMillis(); lastDialogId = dialogId
                    if (config != null) lastConfig = config
                    if (config == null || !config.enabled) return

                    // 全局拦截：开启后任何输入都直接交给 Operit，跳过所有正则/回落判断
                    if (config.fullIntercept) {
                        Log.i(TAG, "full intercept takeover: $queryText")
                        takeoverToOperit(dialogId, queryText, config)
                        return
                    }

                    // 白名单直通
                    if (isAiTakeoverSkip(queryText, config)) { Log.i(TAG, "skip takeover (whitelist): $queryText"); return }

                    // 强制接管：打开 xxx app + 干 xxx 事，或命中自定义拦截/operit 前缀，不等待小爱回落
                    val forceHit = FORCE_TAKEOVER_PATTERN.containsMatchIn(queryText)
                    val interceptHit = !forceHit && isInterceptHit(queryText, config)
                    val operitPrefixHit = extractOperitCommand(queryText) != null
                    if (forceHit || interceptHit || operitPrefixHit) {
                        Log.i(TAG, when {
                            operitPrefixHit -> "force takeover (operit prefix): $queryText"
                            interceptHit -> "force takeover (custom intercept): $queryText"
                            else -> "force takeover: $queryText"
                        })
                        takeoverToOperit(dialogId, queryText, config)
                        return
                    }

                    // 记录查询文本，让 XiaoAi 先处理（回落模式）
                    if (!queryTexts.containsKey(dialogId)) {
                        queryTexts[dialogId] = queryText
                    }
                    pendingFallbackDialogs.add(dialogId)
                    Log.i(TAG, "captured query, waiting for XiaoAi: $queryText")
                } catch (t: Throwable) { Log.i(TAG, "hookOperationManager error: $t") }
            }
        })
    }

    private fun hookRnCard(cl: ClassLoader) {
        val clazz = try { cl.loadClass(RN_CARD_CLASS) } catch (e: Throwable) { return }
        for (name in listOf("bindView", "onCardAttached")) {
            for (m in clazz.declaredMethods) {
                if (m.name == name) {
                    try {
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun afterHookedMethod(param: MethodHookParam) {
                                try {
                                    val card = param.thisObject
                                    val dialogId = card.javaClass.getMethod("getDialogId").invoke(card) as? String ?: return
                                    if (dialogId.isNotBlank()) { cardRefs[dialogId] = WeakReference(card); maybeForceShow(dialogId) }
                                } catch (t: Throwable) { }
                            }
                        })
                    } catch (t: Throwable) { }
                }
            }
        }
    }

    private fun hookBridge(cl: ClassLoader) {
        val clazz = try { cl.loadClass(BRIDGE_CLASS) } catch (e: Throwable) { Log.i(TAG, "BRIDGE not found"); return }
        val method = try { clazz.getDeclaredMethod("sendStreamData", String::class.java, String::class.java) } catch (e: Throwable) { Log.i(TAG, "sendStreamData not found"); return }
        sendStreamDataMethod = method
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                try {
                    if (injectingNow.get() == true) return
                    val type = param.args[0] as? String ?: return
                    val content = (param.args[1] as? String).orEmpty()
                    val dialogId = extractDialogId(content)
                    if (dialogId == null) {
                        if (takeOver.any { it !in injected }) { param.result = null }
                        return
                    }

                    // === 回落检测：小爱无法处理时，拦截并触发 Operit ===
                    if (dialogId in pendingFallbackDialogs && type == "instruction") {
                        val toastText = extractToastText(content)
                        if (toastText != null && toastText.contains("小爱暂不支持该功能")) {
                            Log.i(TAG, "fallback detected: dialogId=$dialogId text=$toastText")
                            // 阻止小爱显示"暂不支持"等消息
                            param.result = null
                            // 立即接管此对话
                            pendingFallbackDialogs.remove(dialogId)
                            pendingViewAnswer.add(dialogId)
                            historyPending.add(dialogId)
                            takeOver.add(dialogId)
                            bridgeRefs[dialogId] = WeakReference(param.thisObject)
                            if (lastConfig != null && lastConfig!!.speakAnswer) startMutePump(dialogId)
                            // 触发 Operit 调用
                            val queryText = queryTexts[dialogId] ?: lastQueryText
                            val key = utteranceKeyFor(queryText)
                            utteranceDialogs.getOrPut(key) { ConcurrentHashMap.newKeySet() }.add(dialogId)
                            if (utteranceCalling.add(key)) {
                                startAiCall(key, queryText, lastConfig ?: return@beforeHookedMethod)
                            }
                            return
                        }
                        // 小爱能正常处理，移除回落跟踪
                        pendingFallbackDialogs.remove(dialogId)
                    }

                    // === 已有接管逻辑（仅 forceTakenOver / 回落检测已添加的接管） ===
                    if (dialogId in takeOver && type == "instruction") {
                        bridgeRefs[dialogId] = WeakReference(param.thisObject)
                        if (dialogId in forceTakenOver) {
                            // 强制接管：完全阻止小爱所有输出，等待 Operit 注入
                            param.result = null
                        } else {
                            // 回落接管：只过滤 ToastStream，保留其他内容
                            val filtered = filterOutToastStream(content)
                            if (filtered == null) { param.result = null } else if (filtered != content) { param.args[1] = filtered }
                        }
                        maybeInject(dialogId)
                    } else if (dialogId in takeOver) { param.result = null }
                } catch (t: Throwable) { Log.i(TAG, "hookBridge error: $t") }
            }
        })
    }

    /**
     * 从 sendStreamData 的 content 中提取 ToastStream 文本内容。
     */
    private fun extractToastText(content: String): String? {
        return try {
            if (content.trimStart().startsWith("[")) {
                val arr = org.json.JSONArray(content)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val header = obj.optJSONObject("header")
                    if (header?.optString("name") == "ToastStream") {
                        val payload = obj.optJSONObject("payload")
                        val text = payload?.optString("markdown_text", "") ?: ""
                        if (text.isNotBlank()) return text
                    }
                }
                null
            } else if (content.trimStart().startsWith("{")) {
                val obj = org.json.JSONObject(content)
                val header = obj.optJSONObject("header")
                if (header?.optString("name") == "ToastStream") {
                    val payload = obj.optJSONObject("payload")
                    payload?.optString("markdown_text", "")
                } else null
            } else null
        } catch (t: Throwable) { null }
    }

    private fun filterOutToastStream(content: String): String? {
        val trimmed = content.trim()
        try {
            if (trimmed.startsWith("[")) {
                val arr = org.json.JSONArray(trimmed); val kept = org.json.JSONArray()
                for (i in 0 until arr.length()) { val obj = arr.optJSONObject(i) ?: continue; val name = obj.optJSONObject("header")?.optString("name"); if (name != "ToastStream") kept.put(obj) }
                return if (kept.length() == 0) null else kept.toString()
            } else if (trimmed.startsWith("{")) {
                val obj = org.json.JSONObject(trimmed); val name = obj.optJSONObject("header")?.optString("name")
                return if (name == "ToastStream") null else content
            }
        } catch (t: Throwable) { }
        return content
    }

    private fun maybeForceShow(dialogId: String) {
        if (dialogId !in takeOver) return
        val card = cardRefs[dialogId]?.get() ?: return
        Handler(Looper.getMainLooper()).post {
            try { card.javaClass.getMethod("onCardVisible").invoke(card) } catch (t: Throwable) { }
            try { card.javaClass.getMethod("onResume").invoke(card) } catch (t: Throwable) { }
        }
    }

    private fun startMutePump(dialogId: String, windowMs: Long = 15_000L) {
        mutePumpDialogId = dialogId; mutePumpUntil = System.currentTimeMillis() + windowMs
        if (mutePumpRunning) return
        mutePumpRunning = true
        val startedAt = System.currentTimeMillis()
        val h = Handler(Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                if (System.currentTimeMillis() >= mutePumpUntil) { mutePumpRunning = false; return }
                try { muteAudio() } catch (t: Throwable) { }
                h.postDelayed(this, if (System.currentTimeMillis() - startedAt < 2_000L) 60L else 250L)
            }
        }
        h.post(tick)
    }

    private fun isMutePumpActive(): Boolean = mutePumpUntil > System.currentTimeMillis()

    private fun stopMutePump() { mutePumpUntil = 0L; mutePumpDialogId = "" }

    private fun speakAnswer(key: String, answer: String) {
        val cfg = lastConfig ?: return
        if (!cfg.enabled || !cfg.speakAnswer) return
        val dialogIds = utteranceDialogs[key].orEmpty()
        if (dialogIds.none { it in pendingViewAnswer || it in takeOver }) { Log.i(TAG, "not taken over, skip speaking key=$key"); return }
        if (!spokenUtterances.add(key)) { Log.i(TAG, "already spoken, skip key=$key"); return }
        val dialogId = dialogIds.firstOrNull { it in pendingViewAnswer || it in takeOver } ?: return
        val speakable = toSpeakable(answer)
        if (speakable.isBlank()) { spokenUtterances.remove(key); return }
        val cl = targetClassLoader ?: return
        Handler(Looper.getMainLooper()).post {
            muteAudio(); syncSpeakContent(cl, dialogId, speakable)
            if (speakViaToastPlayer(cl, speakable)) { Log.i(TAG, "spoke answer via n1 dialogId=$dialogId"); return@post }
            stopMutePump()
            if (speakViaEngine(cl, speakable)) { Log.i(TAG, "spoke answer via u1 dialogId=$dialogId"); return@post }
            Log.i(TAG, "both TTS paths failed"); spokenUtterances.remove(key)
        }
    }

    private fun syncSpeakContent(cl: ClassLoader, dialogId: String, text: String) {
        try {
            val clazz = cl.loadClass(SPEAK_CONTENT_CLASS); val instance = kotlinObjectInstance(clazz) ?: return
            clazz.getMethod("clean").invoke(instance)
            clazz.getMethod("addFragment", String::class.java, String::class.java).invoke(instance, dialogId, text)
        } catch (t: Throwable) { Log.i(TAG, "syncSpeakContent failed: $t") }
    }

    private fun speakViaToastPlayer(cl: ClassLoader, text: String): Boolean {
        return try {
            val clazz = cl.loadClass(TOAST_STREAM_PLAYER_CLASS); val instance = kotlinObjectInstance(clazz) ?: return false
            clazz.getMethod("stopPlay").invoke(instance)
            val speakId = clazz.getMethod("speakTts", String::class.java).invoke(instance, text)
            speakId != null
        } catch (t: Throwable) { Log.i(TAG, "speakViaToastPlayer failed: $t"); false }
    }

    private fun kotlinObjectInstance(clazz: Class<*>): Any? {
        try { val f = clazz.getDeclaredField("INSTANCE"); f.isAccessible = true; f.get(null)?.let { return it } } catch (t: Throwable) { }
        for (f in clazz.declaredFields) {
            if (!java.lang.reflect.Modifier.isStatic(f.modifiers) || f.type != clazz) continue
            try { f.isAccessible = true; f.get(null)?.let { return it } } catch (t: Throwable) { }
        }
        return null
    }

    private fun speakViaEngine(cl: ClassLoader, text: String): Boolean {
        return try {
            val clazz = cl.loadClass(TTS_BRIDGE_CLASS); val instance = clazz.getMethod("getInstance").invoke(null) ?: return false
            clazz.getMethod("speak", String::class.java).invoke(instance, text); true
        } catch (t: Throwable) { Log.i(TAG, "speakViaEngine failed: $t"); false }
    }

    private fun toSpeakable(raw: String): String {
        var s = raw
        s = s.replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), " ")
        s = s.replace(Regex("`([^`]*)`"), "$1")
        s = s.replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), " ")
        s = s.replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        s = s.replace(Regex("https?://\\S+"), " ")
        s = s.replace(Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
        s = s.replace(Regex("^\\s*>\\s?", RegexOption.MULTILINE), "")
        s = s.replace(Regex("\\*{1,3}([^*]+)\\*{1,3}"), "$1")
        s = s.replace(Regex("~~([^~]*)~~"), "$1")
        s = s.replace(Regex("^\\s*[-*_]{3,}\\s*$", RegexOption.MULTILINE), " ")
        s = s.replace(Regex("[ \\t]+"), " ")
        s = s.replace(Regex("\\s*\\n\\s*"), "，")
        s = s.replace(Regex("，{2,}"), "，").trim().trim('，')
        return if (s.length <= MAX_SPEAK_CHARS) s else s.take(MAX_SPEAK_CHARS).trimEnd('，') + "……详细内容请看屏幕"
    }

    private fun muteAudio() {
        try {
            val ctx = currentApplicationContext() ?: return
            val clazz = ctx.classLoader.loadClass(AUDIO_TRACK_MANAGER_CLASS)
            val tracks = allAudioTracks(clazz)
            if (tracks.isEmpty()) { muteMainTrackOnly(clazz); return }
            var stopped = 0
            for ((name, track) in tracks) { if (name == OUR_AUDIO_TRACK) continue; if (stopTrack(track)) stopped++ }
            Log.i(TAG, "muted $stopped/${tracks.size} audio tracks")
        } catch (t: Throwable) { Log.i(TAG, "muteAudio failed: $t") }
    }

    @Suppress("UNCHECKED_CAST")
    private fun allAudioTracks(clazz: Class<*>): Map<String, Any> {
        for (f in clazz.declaredFields) {
            if (!java.lang.reflect.Modifier.isStatic(f.modifiers) || !Map::class.java.isAssignableFrom(f.type)) continue
            try {
                f.isAccessible = true; val map = f.get(null) as? Map<*, *> ?: continue
                val out = HashMap<String, Any>()
                for ((k, v) in map) { if (k is String && v != null && clazz.isInstance(v)) out[k] = v }
                if (out.isNotEmpty()) return out
            } catch (t: Throwable) { }
        }
        return emptyMap()
    }

    private fun stopTrack(track: Any): Boolean {
        var ok = false
        for (name in listOf("stopPlayAndClearQueue", "stop")) { try { track.javaClass.getMethod(name).invoke(track); ok = true } catch (t: Throwable) { } }
        return ok
    }

    private fun muteMainTrackOnly(clazz: Class<*>) {
        try { val track = clazz.getMethod("getMainAudioTrack").invoke(null) ?: return; stopTrack(track) } catch (t: Throwable) { }
    }

    private fun extractDialogId(content: String): String? {
        val m = Regex("\"dialog_id\"\\s*:\\s*\"([a-zA-Z0-9]+)\"").find(content)
        return m?.groupValues?.get(1)
    }

    private fun utteranceKeyFor(text: String): String {
        val now = System.currentTimeMillis()
        val prev = utteranceLastSeen[text]
        if (prev != null && now - prev.second < UTTERANCE_WINDOW_MS) { utteranceLastSeen[text] = prev.first to now; return prev.first }
        val key = "$text#$now"; utteranceLastSeen[text] = key to now; return key
    }

    private var logIdCounter: Long = 0L

    private fun logToStore(type: String, content: String, isError: Boolean = false) {
        val ctx = currentApplicationContext() ?: return
        val id = ++logIdCounter
        LogClient.append(ctx, LogClient.LogEntry(
            id = id,
            time = System.currentTimeMillis(),
            type = type,
            content = content,
            isError = isError
        ))
    }

    private fun startAiCall(key: String, queryText: String, config: OperitConfig) {
        Thread {
            try {
                Log.i(TAG, "calling Operit AI stream for key=$key ...")
                logToStore("请求", queryText.take(200))
                utterancePartial[key] = ""
                OperitBridge.chatStream(config, queryText, object : OperitBridge.StreamCallback {
                    override fun onStart(requestId: String, chatId: String?) {
                        Log.i(TAG, "Operit stream started key=$key requestId=$requestId chatId=$chatId")
                        logToStore("连接", "requestId=$requestId chatId=${chatId ?: "-"}")
                    }
                    override fun onDelta(delta: String) {
                        val partial = (utterancePartial[key] ?: "") + delta
                        utterancePartial[key] = partial
                        for (id in utteranceDialogs[key].orEmpty()) { applyStreamChunk(id, partial) }
                    }
                    override fun onDone(fullResponse: String) {
                        val answer = fullResponse.ifBlank { utterancePartial[key] ?: "" }
                        Log.i(TAG, "Operit stream done key=$key length=${answer.length}")
                        logToStore("完成", answer.take(200))
                        utteranceAnswers[key] = answer
                        for (id in utteranceDialogs[key].orEmpty()) { applyAnswer(key, id, answer) }
                        speakAnswer(key, answer)
                        utteranceCalling.remove(key)
                    }
                    override fun onError(error: String) {
                        Log.w(TAG, "Operit stream error key=$key: $error")
                        logToStore("错误", error, isError = true)
                        val answer = "抱歉，Operit AI 暂时无法响应: $error"
                        utteranceAnswers[key] = answer
                        for (id in utteranceDialogs[key].orEmpty()) { applyAnswer(key, id, answer) }
                        speakAnswer(key, answer)
                        utteranceCalling.remove(key)
                    }
                })
            } catch (t: Throwable) {
                Log.i(TAG, "AI stream failed key=$key: $t")
                logToStore("错误", t.message ?: "未知错误", isError = true)
                val answer = "抱歉，Operit AI 暂时无法响应: ${t.message}"
                utteranceAnswers[key] = answer
                for (id in utteranceDialogs[key].orEmpty()) { applyAnswer(key, id, answer) }
                speakAnswer(key, answer)
                utteranceCalling.remove(key)
            }
        }.start()
    }

    private fun applyStreamChunk(dialogId: String, partial: String) {
        try {
            aiAnswers[dialogId] = partial
            // 流式阶段只更新卡片/Toast，不调用 Finish
            ensureAnswerCard(dialogId)
            maybeInjectStream(dialogId, partial)
        } catch (t: Throwable) { Log.i(TAG, "applyStreamChunk failed: $t") }
    }

    private fun applyAnswer(key: String, dialogId: String, answer: String) {
        try {
            aiAnswers[dialogId] = answer
            injected.remove(dialogId) // 允许最终注入再次执行
            maybeInject(dialogId)
            if (dialogId in pendingViewAnswer) ensureAnswerCard(dialogId)
            writeAnswerToHistory(dialogId, answer)
        } catch (t: Throwable) { Log.i(TAG, "applyAnswer failed: $t") }
    }

    private fun maybeInject(dialogId: String, isFinal: Boolean = true) {
        if (dialogId in injected && isFinal) { Log.i(TAG, "skip inject: already injected dialogId=$dialogId"); return }
        val answer = aiAnswers[dialogId]
        if (answer == null) { Log.i(TAG, "skip inject: no answer yet dialogId=$dialogId"); return }
        val bridge = bridgeRefs[dialogId]?.get()
        if (bridge == null) { Log.i(TAG, "skip inject: no bridge ref dialogId=$dialogId"); return }
        val method = sendStreamDataMethod
        if (method == null) { Log.i(TAG, "skip inject: no sendStreamDataMethod"); return }
        val ready = isRnFrontReady(dialogId)
        if (ready == false) { Log.i(TAG, "RN front not ready yet, defer inject dialogId=$dialogId"); return }
        if (isFinal && !injected.add(dialogId)) return
        Log.i(TAG, "injecting answer via bridge dialogId=$dialogId final=$isFinal answer=${answer.take(60)}")
        Handler(Looper.getMainLooper()).post {
            try {
                val card = cardRefs[dialogId]?.get()
                if (card != null) { try { card.javaClass.getMethod("onResume").invoke(card) } catch (t: Throwable) { } }
            } catch (t: Throwable) { }
            try {
                injectingNow.set(true)
                val transactionId = UUID.randomUUID().toString().replace("-", "")
                val instrId = UUID.randomUUID().toString().replace("-", "")
                val contentPayload = JSONObject().apply {
                    put("header", JSONObject().apply { put("name", "ToastStream"); put("namespace", "Template"); put("dialog_id", dialogId); put("id", instrId); put("transaction_id", transactionId) })
                    put("payload", JSONObject().apply { put("markdown_text", answer) })
                }
                method.invoke(bridge, "instruction", contentPayload.toString())
                if (isFinal) {
                    val finalPayload = JSONObject().apply {
                        put("header", JSONObject().apply { put("id", UUID.randomUUID().toString()); put("dialog_id", dialogId) })
                        put("payload", JSONObject().apply { put("markdown_text", "<FINAL>") })
                    }
                    method.invoke(bridge, "instruction", finalPayload.toString())
                    method.invoke(bridge, "Finish", "")
                    Log.i(TAG, "injected AI answer via bridge OK, dialogId=$dialogId")
                }
            } catch (t: Throwable) {
                Log.i(TAG, "inject failed dialogId=$dialogId: $t")
                if (isFinal) injected.remove(dialogId)
            } finally { injectingNow.set(false) }
            if (isFinal) {
                try {
                    val card = cardRefs[dialogId]?.get()
                    if (card != null) {
                        val p1 = card.javaClass.getDeclaredMethod("p1", JSONObject::class.java); p1.isAccessible = true
                        val obj = JSONObject().apply { put("totalText", answer); put("isLlmContentDisplayComplete", true); put("isIllegalContent", false) }
                        p1.invoke(card, obj)
                        Log.i(TAG, "p1() render called OK, dialogId=$dialogId")
                    }
                } catch (t: Throwable) { Log.i(TAG, "fallback p1() render failed: $t") }
            }
        }
    }

    private fun maybeInjectStream(dialogId: String, partial: String) {
        // 流式阶段不设置 injected，每次都推送最新文本；小爱端会自动覆盖 ToastStream 文本
        val bridge = bridgeRefs[dialogId]?.get() ?: return
        val method = sendStreamDataMethod ?: return
        if (dialogId in injected) return // 最终注入已发生，不再流式更新
        Handler(Looper.getMainLooper()).post {
            try {
                injectingNow.set(true)
                val contentPayload = JSONObject().apply {
                    put("header", JSONObject().apply { put("name", "ToastStream"); put("namespace", "Template"); put("dialog_id", dialogId); put("id", UUID.randomUUID().toString().replace("-", "")); put("transaction_id", UUID.randomUUID().toString().replace("-", "")) })
                    put("payload", JSONObject().apply { put("markdown_text", partial) })
                }
                method.invoke(bridge, "instruction", contentPayload.toString())
            } catch (t: Throwable) { Log.i(TAG, "stream inject failed dialogId=$dialogId: $t") } finally { injectingNow.set(false) }
        }
    }

    private fun currentApplicationContext(): Context? {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass.getMethod("currentApplication")
            currentApplication.invoke(null) as? Context
        } catch (t: Throwable) { Log.i(TAG, "currentApplicationContext failed: $t"); null }
    }
}