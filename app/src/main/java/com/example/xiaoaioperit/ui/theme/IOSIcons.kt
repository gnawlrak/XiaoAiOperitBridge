package com.example.xiaoaioperit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 自绘描边图标 — SF Symbols 风格，统一 24dp 视口、1.8pt 圆头描边。
 * 以黑色绘制，使用时经 ColorFilter.tint 着色。
 */
object IOSIcons {

    private fun builder() = ImageVector.Builder(
        name = "ios",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )

    private fun ImageVector.Builder.stroke(vararg paths: String, width: Float = 1.8f) = apply {
        for (p in paths) addPath(
            pathData = PathParser().parsePathString(p).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun ImageVector.Builder.fill(vararg paths: String) = apply {
        for (p in paths) addPath(
            pathData = PathParser().parsePathString(p).toNodes(),
            fill = SolidColor(Color.Black),
        )
    }

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M$cx ${cy - r} A$r $r 0 1 1 $cx ${cy + r} A$r $r 0 1 1 $cx ${cy - r} Z"

    // ── Tab 图标 ──

    val Home: ImageVector by lazy {
        builder().stroke(
            "M4.8 10.8 L12 4.2 L19.2 10.8",
            "M6.8 9.2 V18.6 A1.2 1.2 0 0 0 8 19.8 H16 A1.2 1.2 0 0 0 17.2 18.6 V9.2",
        ).build()
    }

    val List: ImageVector by lazy {
        builder()
            .stroke("M9.4 6.6 H19.6", "M9.4 12 H19.6", "M9.4 17.4 H19.6")
            .fill(circle(5.4f, 6.6f, 1.35f), circle(5.4f, 12f, 1.35f), circle(5.4f, 17.4f, 1.35f))
            .build()
    }

    val Gear: ImageVector by lazy {
        builder().stroke(
            circle(12f, 12f, 3.3f),
            "M17.6 12 H20.5", "M15.96 15.96 L18 18", "M12 17.6 V20.5", "M8.04 15.96 L6 18",
            "M6.4 12 H3.5", "M8.04 8.04 L6 6", "M12 6.4 V3.5", "M15.96 8.04 L18 6",
            width = 1.7f,
        ).build()
    }

    // ── 功能图标 ──

    val Bolt: ImageVector by lazy {
        builder().fill("M13.4 2.6 L5.2 13.4 H11.2 L10.2 21.4 L18.8 10.2 H12.8 Z").build()
    }

    val Check: ImageVector by lazy {
        builder().stroke("M5.5 12.6 L10 17.1 L18.6 7", width = 2f).build()
    }

    val XMark: ImageVector by lazy {
        builder().stroke("M6.6 6.6 L17.4 17.4", "M17.4 6.6 L6.6 17.4", width = 2f).build()
    }

    val ChevronRight: ImageVector by lazy {
        builder().stroke("M9.4 5.8 L15.6 12 L9.4 18.2", width = 2f).build()
    }

    val Globe: ImageVector by lazy {
        builder().stroke(
            circle(12f, 12f, 8.4f),
            "M12 3.6 C8.6 7.2 8.6 16.8 12 20.4 C15.4 16.8 15.4 7.2 12 3.6 Z",
            "M3.9 9.4 H20.1", "M3.9 14.6 H20.1",
            width = 1.6f,
        ).build()
    }

    val Mic: ImageVector by lazy {
        builder().stroke(
            "M12 3.2 A2.8 2.8 0 0 0 9.2 6 V10.8 A2.8 2.8 0 0 0 14.8 10.8 V6 A2.8 2.8 0 0 0 12 3.2 Z",
            "M6.8 10.6 A5.2 5.2 0 0 0 17.2 10.6",
            "M12 15.8 V19.4", "M9 19.4 H15",
            width = 1.7f,
        ).build()
    }

    val Speaker: ImageVector by lazy {
        builder().stroke(
            "M4.2 9.4 H6.9 L11.6 5.1 V18.9 L6.9 14.6 H4.2 A1 1 0 0 1 3.2 13.6 V10.4 A1 1 0 0 1 4.2 9.4 Z",
            "M14.6 9.2 A4.2 4.2 0 0 1 14.6 14.8",
            "M17 6.7 A7.8 7.8 0 0 1 17 17.3",
            width = 1.7f,
        ).build()
    }

    val Shield: ImageVector by lazy {
        builder().stroke(
            "M12 3.2 L18.8 5.9 V10.9 C18.8 15.5 16 19.1 12 20.7 C8 19.1 5.2 15.5 5.2 10.9 V5.9 Z",
            width = 1.7f,
        ).build()
    }

    val Sparkles: ImageVector by lazy {
        builder().fill(
            "M11.5 3 C12.2 6.9 13.6 8.3 17.5 9 C13.6 9.7 12.2 11.1 11.5 15 C10.8 11.1 9.4 9.7 5.5 9 C9.4 8.3 10.8 6.9 11.5 3 Z",
            "M18 14.6 C18.35 16.3 19.2 17.15 20.9 17.5 C19.2 17.85 18.35 18.7 18 20.4 C17.65 18.7 16.8 17.85 15.1 17.5 C16.8 17.15 17.65 16.3 18 14.6 Z",
        ).build()
    }

    val Refresh: ImageVector by lazy {
        builder().stroke(
            "M17.6 6.4 A7.7 7.7 0 1 0 19.6 10.8",
            "M17.6 2.8 L17.6 6.8 L21.2 6.8",
            width = 1.8f,
        ).build()
    }

    val Trash: ImageVector by lazy {
        builder().stroke(
            "M4.5 6.4 H19.5",
            "M9.6 6.4 V4.9 A1.3 1.3 0 0 1 10.9 3.6 H13.1 A1.3 1.3 0 0 1 14.4 4.9 V6.4",
            "M6.4 6.4 L7.1 18.5 A2 2 0 0 0 9.1 20.4 H14.9 A2 2 0 0 0 16.9 18.5 L17.6 6.4",
            "M10 10.4 V16.4", "M14 10.4 V16.4",
            width = 1.7f,
        ).build()
    }
}
