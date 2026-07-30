package com.example.xiaoaioperit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xiaoaioperit.ui.theme.IOSColors
import com.example.xiaoaioperit.ui.theme.IOSType
import com.example.xiaoaioperit.ui.theme.LocalIOSColors
import com.example.xiaoaioperit.ui.theme.LocalReducedMotion
import kotlinx.coroutines.delay

// ═══════════════════════════════════════════
//  基础文字（不依赖 material3）
// ═══════════════════════════════════════════

@Composable
fun T(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    fontWeight: FontWeight? = null,
) {
    var merged = if (textAlign != null) {
        style.merge(TextStyle(color = color, textAlign = textAlign))
    } else {
        style.merge(TextStyle(color = color))
    }
    if (fontWeight != null) merged = merged.copy(fontWeight = fontWeight)
    BasicText(
        text = text,
        modifier = modifier,
        style = merged,
        maxLines = maxLines,
        overflow = overflow,
    )
}

// ═══════════════════════════════════════════
//  图标
// ═══════════════════════════════════════════

@Composable
fun IOSIcon(icon: ImageVector, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).paint(
            rememberVectorPainter(icon),
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
        )
    )
}

/** iOS 设置风图标瓦片：彩色圆角方块 + 白色字形 */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 29.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        IOSIcon(icon, Color.White, size * 0.66f)
    }
}

// ═══════════════════════════════════════════
//  动效
// ═══════════════════════════════════════════

private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

/** 错峰入场：透明度 + 轻微上浮。系统关闭动画时直接显示。 */
@Composable
fun Entrance(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay((index * 45L).coerceAtMost(280L))
            progress.animateTo(1f, tween(340, easing = EaseOutQuart))
        }
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 14.dp.toPx()
        }
    ) { content() }
}

/** iOS 风活动指示器：旋转弧线 */
@Composable
fun IOSSpinner(color: Color, size: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = { it })),
        label = "angle",
    )
    Canvas(Modifier.size(size).rotate(angle)) {
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 280f,
            useCenter = false,
            style = Stroke(width = size.toPx() * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

// ═══════════════════════════════════════════
//  分组列表
// ═══════════════════════════════════════════

@Composable
fun IOSGroup(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    rows: List<@Composable () -> Unit>,
) {
    val colors = LocalIOSColors.current
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            T(
                header, IOSType.footnote, colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp),
            )
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.surface)
        ) {
            rows.forEachIndexed { i, row ->
                row()
                if (i < rows.lastIndex) IOSSeparator()
            }
        }
        if (footer != null) {
            T(
                footer, IOSType.footnote, colors.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
            )
        }
    }
}

@Composable
fun IOSSeparator(insetStart: Dp = 16.dp) {
    val colors = LocalIOSColors.current
    Box(Modifier.fillMaxWidth().padding(start = insetStart).height(0.4.dp).background(colors.separator))
}

/** 行容器：最小高 44、按压高亮（无 ripple，iOS 式底色反馈） */
@Composable
fun IOSRowContainer(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = LocalIOSColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAlpha by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 1f else 0f, tween(if (pressed) 90 else 220), label = "press",
    )
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction, indication = null, onClick = onClick,
                ) else Modifier
            ),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).align(Alignment.CenterStart).fillMaxWidth()) {
            content()
        }
        if (pressAlpha > 0f) {
            Box(Modifier.matchParentSize().background(colors.fill.copy(alpha = colors.fill.alpha * pressAlpha)))
        }
    }
}

/** 标签-值 行：左标签，右灰值 */
@Composable
fun IOSValueRow(label: String, value: String, valueColor: Color? = null) {
    val colors = LocalIOSColors.current
    IOSRowContainer(onClick = null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(label, IOSType.body, colors.label)
            Spacer(Modifier.weight(1f))
            T(
                value, IOSType.body, valueColor ?: colors.secondaryLabel,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

/** 居中文字动作行（iOS 分组列表中的按钮） */
@Composable
fun IOSActionRow(
    text: String,
    onClick: () -> Unit,
    color: Color? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = LocalIOSColors.current
    val tint = color ?: colors.accent
    IOSRowContainer(onClick = if (enabled && !loading) onClick else null) {
        Row(
            Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                IOSSpinner(tint, 15.dp)
                Spacer(Modifier.width(8.dp))
            }
            T(text, IOSType.body, tint, textAlign = TextAlign.Center)
        }
    }
}

// ═══════════════════════════════════════════
//  开关
// ═══════════════════════════════════════════

/** 1:1 iOS 开关：51×31 胶囊轨道 + 27 白色圆形拇指 */
@Composable
fun IOSSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val colors = LocalIOSColors.current
    val haptic = LocalHapticFeedback.current
    val track by animateColorAsState(
        if (checked) colors.green else colors.switchOffTrack, tween(200), label = "track",
    )
    val thumbX by animateDpAsState(
        if (checked) 22.dp else 2.dp,
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "thumb",
    )
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(track)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                haptic.performHapticFeedback(if (!checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onCheckedChange(!checked)
            },
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 2.dp)
                .padding(start = 0.dp)
                .size(27.dp)
                .shadow(1.5.dp, CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** 开关行：图标瓦片 + 标题 + 副标题 + 开关 */
@Composable
fun IOSToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    iconColor: Color? = null,
) {
    val colors = LocalIOSColors.current
    IOSRowContainer(onClick = { onCheckedChange(!checked) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null && iconColor != null) {
                IconTile(icon, iconColor)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).padding(end = 12.dp, top = 3.dp, bottom = 3.dp)) {
                T(title, IOSType.body, colors.label)
                if (subtitle != null) {
                    T(subtitle, IOSType.footnote, colors.secondaryLabel)
                }
            }
            IOSSwitch(checked, onCheckedChange)
        }
    }
}

// ═══════════════════════════════════════════
//  输入行
// ═══════════════════════════════════════════

/** iOS 内联编辑行：左标签 + 右输入框（无边框） */
@Composable
fun IOSTextFieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    labelWidth: Dp = 84.dp,
) {
    val colors = LocalIOSColors.current
    IOSRowContainer(onClick = null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(label, IOSType.body, colors.label, modifier = Modifier.width(labelWidth))
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = IOSType.body.merge(TextStyle(color = colors.label)),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) T(placeholder, IOSType.body, colors.tertiaryLabel)
                        inner()
                    }
                },
            )
        }
    }
}

/** 多行输入（系统提示词 / 正则） */
@Composable
fun IOSMultilineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    minLines: Int = 4,
) {
    val colors = LocalIOSColors.current
    IOSRowContainer(onClick = null) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = IOSType.subheadline.merge(TextStyle(color = colors.label)),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier.fillMaxWidth().heightIn(min = (minLines * 21).dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) T(placeholder, IOSType.subheadline, colors.tertiaryLabel)
                    inner()
                }
            },
        )
    }
}

// ═══════════════════════════════════════════
//  主按钮（首页 CTA）
// ═══════════════════════════════════════════

@Composable
fun IOSButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color? = null,
) {
    val colors = LocalIOSColors.current
    val bg = color ?: colors.accent
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) 0.97f else 1f, tween(120), label = "btn",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.35f))
            .clickable(
                enabled = enabled && !loading,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                IOSSpinner(Color.White, 16.dp)
                Spacer(Modifier.width(8.dp))
            }
            T(text, IOSType.headline, Color.White)
        }
    }
}
