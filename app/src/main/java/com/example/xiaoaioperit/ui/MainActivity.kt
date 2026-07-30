package com.example.xiaoaioperit.ui

import android.graphics.Color as AColor
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.xiaoaioperit.ui.components.IOSIcon
import com.example.xiaoaioperit.ui.components.T
import com.example.xiaoaioperit.ui.pages.HomePage
import com.example.xiaoaioperit.ui.pages.LogPage
import com.example.xiaoaioperit.ui.pages.SettingsPage
import com.example.xiaoaioperit.ui.theme.IOSIcons
import com.example.xiaoaioperit.ui.theme.IOSType
import com.example.xiaoaioperit.ui.theme.LocalIOSColors
import com.example.xiaoaioperit.ui.theme.LocalReducedMotion
import com.example.xiaoaioperit.ui.theme.XiaoAiOperitTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

private data class Tab(val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("首页", IOSIcons.Home),
    Tab("记录", IOSIcons.List),
    Tab("设置", IOSIcons.Gear),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(AColor.TRANSPARENT))
        setContent {
            // 系统关闭动画（无障碍 → 移除动画）时，同步关闭装饰性动效
            val reducedMotion = remember {
                Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
            XiaoAiOperitTheme {
                CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
                    MainScreen()
                }
            }
        }
    }
}

private val TopBarHeight = 44.dp

@Composable
private fun MainScreen() {
    val colors = LocalIOSColors.current
    var tab by remember { mutableIntStateOf(0) }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val tabBarSpace = 49.dp + bottomInset
    val topBarSpace = topInset + TopBarHeight
    val hazeState = remember { HazeState() }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        AnimatedContent(
            targetState = tab,
            modifier = Modifier.fillMaxSize().hazeSource(hazeState),
            transitionSpec = {
                (fadeIn(tween(240)) + slideInVertically(tween(240)) { it / 28 }) togetherWith
                    fadeOut(tween(160))
            },
            label = "tab",
        ) { t ->
            when (t) {
                0 -> HomePage(topPadding = topBarSpace, bottomPadding = tabBarSpace)
                1 -> LogPage(topPadding = topBarSpace, bottomPadding = tabBarSpace)
                else -> SettingsPage(topPadding = topBarSpace, bottomPadding = tabBarSpace)
            }
        }
        IOSTopBar(
            title = tabs[tab].label,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        IOSTabBar(tab, { tab = it }, hazeState, Modifier.align(Alignment.BottomCenter))
    }
}

/** 顶/底栏共用的磨砂材质：背景模糊 + 半透明 surface 染色 */
@Composable
private fun hazeBarStyle(): HazeStyle {
    val colors = LocalIOSColors.current
    return HazeStyle(
        backgroundColor = colors.surface,
        tint = HazeTint(colors.surface.copy(alpha = 0.6f)),
        blurRadius = 24.dp,
        noiseFactor = 0f,
    )
}

@Composable
private fun IOSTopBar(title: String, hazeState: HazeState, modifier: Modifier = Modifier) {
    val colors = LocalIOSColors.current
    Column(modifier.fillMaxWidth().hazeEffect(hazeState, hazeBarStyle())) {
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().height(TopBarHeight),
            contentAlignment = Alignment.Center,
        ) {
            T(title, IOSType.headline, colors.label)
        }
        Box(Modifier.fillMaxWidth().height(0.4.dp).background(colors.separator))
    }
}

@Composable
private fun IOSTabBar(tab: Int, onTabChange: (Int) -> Unit, hazeState: HazeState, modifier: Modifier = Modifier) {
    val colors = LocalIOSColors.current
    Column(modifier.fillMaxWidth().hazeEffect(hazeState, hazeBarStyle())) {
        Box(Modifier.fillMaxWidth().height(0.4.dp).background(colors.separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(49.dp)) {
            tabs.forEachIndexed { i, t ->
                IOSTabItem(
                    label = t.label,
                    icon = t.icon,
                    selected = tab == i,
                    onClick = { onTabChange(i) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun IOSTabItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalIOSColors.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint by animateColorAsState(if (selected) colors.accent else colors.gray, tween(180), label = "tint")
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, tween(120), label = "scale")

    Box(
        modifier.clickable(interactionSource = interaction, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            onClick()
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        ) {
            IOSIcon(icon, tint, 24.dp)
            Box(Modifier.height(1.dp))
            T(label, IOSType.caption2.copy(fontSize = 10.sp), tint)
        }
    }
}
