package com.example.xiaoaioperit.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════
//  iOS 设计语言 tokens
// ═══════════════════════════════════════════

class IOSColors(
    val accent: Color,          // systemBlue
    val green: Color,
    val red: Color,
    val orange: Color,
    val purple: Color,
    val teal: Color,
    val gray: Color,            // systemGray
    val background: Color,      // 分组背景 systemGroupedBackground
    val surface: Color,         // 卡片 secondarySystemGroupedBackground
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,            // 按压/填充 systemFill
    val switchOffTrack: Color,
)

val IOSLightColors = IOSColors(
    accent = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    purple = Color(0xFFAF52DE),
    teal = Color(0xFF5AC8FA),
    gray = Color(0xFF8E8E93),
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x293C3C43),
    fill = Color(0x14787880),
    switchOffTrack = Color(0xFFE9E9EA),
)

val IOSDarkColors = IOSColors(
    accent = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    purple = Color(0xFFBF5AF2),
    teal = Color(0xFF64D2FF),
    gray = Color(0xFF98989F),
    background = Color(0xFF000000),
    surface = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x29545458),
    fill = Color(0x20787880),
    switchOffTrack = Color(0xFF39393D),
)

val LocalIOSColors = staticCompositionLocalOf { IOSLightColors }

/** 系统「移除动态效果」：读取 animator duration scale，0 时关闭全部装饰动画 */
val LocalReducedMotion = compositionLocalOf { false }

// ── 字阶（SF 比例，字距对齐 iOS） ──
object IOSType {
    val largeTitle = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp, letterSpacing = 0.37.sp)
    val title1 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.36.sp)
    val title2 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.35.sp)
    val title3 = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 25.sp, letterSpacing = 0.38.sp)
    val headline = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp)
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.41).sp)
    val callout = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.32).sp)
    val subheadline = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.24).sp)
    val footnote = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.08).sp)
    val caption1 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.07.sp)
}

@Composable
fun XiaoAiOperitTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    CompositionLocalProvider(LocalIOSColors provides if (dark) IOSDarkColors else IOSLightColors) {
        content()
    }
}
