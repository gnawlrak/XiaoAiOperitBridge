package com.example.xiaoaioperit.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xiaoaioperit.config.ConfigClient
import com.example.xiaoaioperit.config.OperitConfig
import com.example.xiaoaioperit.ui.components.Entrance
import com.example.xiaoaioperit.ui.components.IOSGroup
import com.example.xiaoaioperit.ui.components.IOSMultilineField
import com.example.xiaoaioperit.ui.components.IOSTextFieldRow
import com.example.xiaoaioperit.ui.components.IOSToggleRow
import com.example.xiaoaioperit.ui.components.T
import com.example.xiaoaioperit.ui.theme.IOSIcons
import com.example.xiaoaioperit.ui.theme.IOSType
import com.example.xiaoaioperit.ui.theme.LocalIOSColors
import kotlinx.coroutines.launch

@Composable
fun SettingsPage(topPadding: Dp, bottomPadding: Dp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalIOSColors.current

    var c by remember { mutableStateOf(OperitConfig()) }
    LaunchedEffect(Unit) { c = ConfigClient.read(ctx) }
    fun save(n: OperitConfig) { c = n; scope.launch { ConfigClient.write(ctx, n) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(topPadding + 10.dp))
        T("设置", IOSType.largeTitle, colors.label)
        Spacer(Modifier.height(16.dp))

        Entrance(0) {
            IOSGroup(
                header = "OPERIT 连接",
                footer = "Operit WebUI 的地址与鉴权信息，Token 在 Operit 设置中生成",
                rows = listOf(
                    { IOSTextFieldRow("Host", c.host, { save(c.copy(host = it)) }, placeholder = "127.0.0.1") },
                    { IOSTextFieldRow("端口", c.port.toString(), { v -> v.toIntOrNull()?.let { save(c.copy(port = it)) } }, placeholder = "8080", keyboardType = KeyboardType.Number) },
                    { IOSTextFieldRow("API 路径", c.apiPath, { save(c.copy(apiPath = it)) }, placeholder = "/api/external-chat") },
                    { IOSTextFieldRow("Token", c.token, { save(c.copy(token = it)) }, placeholder = "未设置") },
                ),
            )
        }
        Spacer(Modifier.height(22.dp))

        Entrance(1) {
            IOSGroup(
                header = "AI",
                footer = "全局拦截开启时，所有语音/文字输入都会直接交给 Operit，忽略正则与回落规则",
                rows = listOf(
                    { IOSToggleRow("启用 Operit 转发", "拦截小爱请求并转发给 Operit AI", c.enabled, { save(c.copy(enabled = it)) }, icon = IOSIcons.Bolt, iconColor = colors.accent) },
                    { IOSToggleRow("播报答案", "用小爱 TTS 念出 Operit 的回复", c.speakAnswer, { save(c.copy(speakAnswer = it)) }, icon = IOSIcons.Speaker, iconColor = colors.green) },
                    { IOSToggleRow("全局拦截", "任何输入都直接接管，不等待小爱处理", c.fullIntercept, { save(c.copy(fullIntercept = it)) }, icon = IOSIcons.Shield, iconColor = colors.orange) },
                ),
            )
        }
        Spacer(Modifier.height(22.dp))

        Entrance(2) {
            IOSGroup(
                header = "系统提示词",
                footer = "留空使用默认提示词",
                rows = listOf(
                    { IOSMultilineField(c.systemPrompt, { save(c.copy(systemPrompt = it)) }, placeholder = "自定义系统提示词", minLines = 4) },
                ),
            )
        }
        Spacer(Modifier.height(22.dp))

        Entrance(3) {
            IOSGroup(
                header = "行为控制",
                rows = listOf(
                    { IOSToggleRow("拦截查看类跳转", "拦截「查看 XXX」导致的页面跳转", c.blockViewJump, { save(c.copy(blockViewJump = it)) }, icon = IOSIcons.Shield, iconColor = colors.orange) },
                    { IOSToggleRow("拦截搜索兜底", "拦截小爱答不上来时的全局搜索", c.blockWebSearch, { save(c.copy(blockWebSearch = it)) }, icon = IOSIcons.Globe, iconColor = colors.purple) },
                ),
            )
        }
        Spacer(Modifier.height(22.dp))

        Entrance(4) {
            IOSGroup(
                header = "放行词",
                footer = "命中放行词的请求不会被拦截，多个词用逗号分隔",
                rows = listOf(
                    { IOSTextFieldRow("跳转放行", c.jumpAllowWords, { save(c.copy(jumpAllowWords = it)) }, placeholder = "打开,开启,进入") },
                    { IOSTextFieldRow("搜索放行", c.webSearchAllowWords, { save(c.copy(webSearchAllowWords = it)) }, placeholder = "搜索,百度,上网搜") },
                ),
            )
        }
        Spacer(Modifier.height(22.dp))

        Entrance(5) {
            IOSGroup(
                header = "自定义规则",
                footer = "命中拦截正则的问话跳过小爱、直接交给 Operit；命中放行正则的完全交由小爱原生处理。放行优先于拦截，例如放行：播放|暂停|下一首|音量",
                rows = listOf(
                    { IOSMultilineField(c.interceptPattern, { save(c.copy(interceptPattern = it)) }, placeholder = "拦截正则", minLines = 2) },
                    { IOSMultilineField(c.skipTakeoverPattern, { save(c.copy(skipTakeoverPattern = it)) }, placeholder = "放行正则", minLines = 2) },
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
