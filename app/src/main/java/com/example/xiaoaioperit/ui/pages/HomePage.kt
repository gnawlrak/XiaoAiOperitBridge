package com.example.xiaoaioperit.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xiaoaioperit.ModuleStatus
import com.example.xiaoaioperit.bridge.OperitBridge
import com.example.xiaoaioperit.config.ConfigClient
import com.example.xiaoaioperit.config.LogClient
import com.example.xiaoaioperit.config.OperitConfig
import com.example.xiaoaioperit.ui.components.Entrance
import com.example.xiaoaioperit.ui.components.IOSActionRow
import com.example.xiaoaioperit.ui.components.IOSGroup
import com.example.xiaoaioperit.ui.components.IOSIcon
import com.example.xiaoaioperit.ui.components.IOSRowContainer
import com.example.xiaoaioperit.ui.components.IOSSeparator
import com.example.xiaoaioperit.ui.components.IOSValueRow
import com.example.xiaoaioperit.ui.components.T
import com.example.xiaoaioperit.ui.theme.IOSIcons
import com.example.xiaoaioperit.ui.theme.IOSType
import com.example.xiaoaioperit.ui.theme.LocalIOSColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HomePage(topPadding: Dp, bottomPadding: Dp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalIOSColors.current

    var config by remember { mutableStateOf(OperitConfig()) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    LaunchedEffect(Unit) { config = ConfigClient.read(ctx) }

    val active = ModuleStatus.isActive()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(topPadding + 10.dp))
        T("首页", IOSType.largeTitle, colors.label)
        Spacer(Modifier.height(16.dp))

        Entrance(0) { StatusHero(active, config) }
        Spacer(Modifier.height(22.dp))

        Entrance(1) {
            IOSGroup(
                header = "OPERIT 连接",
                rows = buildList {
                    add { IOSValueRow("地址", "${config.host}:${config.port}") }
                    add { IOSValueRow("API 路径", config.effectiveApiPath) }
                    add {
                        IOSValueRow(
                            "Token",
                            if (config.token.isBlank()) "未设置" else "已设置",
                            if (config.token.isBlank()) colors.tertiaryLabel else null,
                        )
                    }
                    add {
                        IOSActionRow(
                            text = if (testing) "正在测试…" else "测试连接",
                            loading = testing,
                            onClick = {
                                testing = true; result = null
                                scope.launch {
                                    result = withContext(Dispatchers.IO) {
                                        try {
                                            OperitBridge.testConnection(config)
                                            LogClient.append(ctx, LogClient.LogEntry(System.currentTimeMillis(), System.currentTimeMillis(), "连接测试", "成功连接到 ${config.host}:${config.port}"))
                                            true to "已连接到 ${config.host}:${config.port}"
                                        } catch (t: Throwable) {
                                            LogClient.append(ctx, LogClient.LogEntry(System.currentTimeMillis(), System.currentTimeMillis(), "连接测试", "连接 ${config.host}:${config.port} 失败: ${t.message}", isError = true))
                                            false to (t.message ?: "未知错误")
                                        }
                                    }
                                    testing = false
                                }
                            },
                        )
                    }
                    result?.let { (ok, msg) ->
                        add {
                            IOSRowContainer(onClick = null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IOSIcon(
                                        if (ok) IOSIcons.Check else IOSIcons.XMark,
                                        if (ok) colors.green else colors.red,
                                        16.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    T(
                                        msg, IOSType.subheadline,
                                        if (ok) colors.green else colors.red,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(22.dp))

        Entrance(2) {
            IOSGroup(
                header = "使用方式",
                rows = listOf(
                    { StepRow(1, "对小爱说出你的问题") },
                    { StepRow(2, "模块拦截请求，转发给本机 Operit AI") },
                    { StepRow(3, "回答直接显示在小爱界面，并可语音播报") },
                ),
            )
        }

        Spacer(Modifier.height(28.dp))
        T(
            "XiaoAiOperitBridge · v1.0.0",
            IOSType.caption1, colors.tertiaryLabel,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(bottomPadding + 20.dp))
    }
}

// ── 状态面板 ──

@Composable
private fun StatusHero(active: Boolean, config: OperitConfig) {
    val colors = LocalIOSColors.current
    val statusColor = if (active) colors.green else colors.orange

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surface)
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(statusColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                IOSIcon(
                    if (active) IOSIcons.Check else IOSIcons.XMark,
                    statusColor, 26.dp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                T(
                    if (active) "模块运行中" else "模块未激活",
                    IOSType.title3, colors.label,
                )
                Spacer(Modifier.height(2.dp))
                T(
                    if (active) "LSPosed 已加载，正在拦截超级小爱"
                    else "在 LSPosed 中启用本模块，作用域勾选超级小爱",
                    IOSType.footnote, colors.secondaryLabel,
                )
            }
        }

        IOSSeparator(insetStart = 18.dp)

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            HeroStat("转发", if (config.enabled) "已开启" else "已关闭", if (config.enabled) colors.green else colors.secondaryLabel, Modifier.weight(1f))
            HeroStat("播报", if (config.speakAnswer) "已开启" else "已关闭", if (config.speakAnswer) colors.green else colors.secondaryLabel, Modifier.weight(1f))
            val blocks = listOf(config.blockViewJump, config.blockWebSearch).count { it }
            HeroStat("拦截", "$blocks/2 项", if (blocks > 0) colors.accent else colors.secondaryLabel, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val colors = LocalIOSColors.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        T(label, IOSType.caption1, colors.secondaryLabel)
        Spacer(Modifier.height(3.dp))
        T(value, IOSType.subheadline, valueColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StepRow(number: Int, text: String) {
    val colors = LocalIOSColors.current
    IOSRowContainer(onClick = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).clip(CircleShape).background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                T(number.toString(), IOSType.caption1, androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(12.dp))
            T(text, IOSType.subheadline, colors.label, modifier = Modifier.weight(1f))
        }
    }
}
