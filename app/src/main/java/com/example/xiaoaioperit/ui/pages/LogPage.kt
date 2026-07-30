package com.example.xiaoaioperit.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.xiaoaioperit.config.LogClient
import com.example.xiaoaioperit.config.LogClient.LogEntry
import com.example.xiaoaioperit.ui.components.Entrance
import com.example.xiaoaioperit.ui.components.IOSGroup
import com.example.xiaoaioperit.ui.components.IOSIcon
import com.example.xiaoaioperit.ui.components.IOSRowContainer
import com.example.xiaoaioperit.ui.components.T
import com.example.xiaoaioperit.ui.theme.IOSIcons
import com.example.xiaoaioperit.ui.theme.IOSType
import com.example.xiaoaioperit.ui.theme.LocalIOSColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val tf = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
private fun fmt(e: Long) = if (e <= 0L) "--" else tf.format(Date(e))

@Composable
fun LogPage(topPadding: Dp, bottomPadding: Dp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalIOSColors.current

    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(refreshKey) { logs = withContext(Dispatchers.IO) { LogClient.read(ctx, limit = 100) } }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(topPadding + 10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("记录", IOSType.largeTitle, colors.label, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderAction(icon = IOSIcons.Refresh, tint = colors.accent) { refreshKey++ }
                if (logs.isNotEmpty()) {
                    HeaderAction(icon = IOSIcons.Trash, tint = colors.red) {
                        scope.launch { withContext(Dispatchers.IO) { LogClient.clear(ctx) } }
                        logs = emptyList()
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        if (logs.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 90.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        IOSIcon(IOSIcons.List, colors.tertiaryLabel, 30.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    T("暂无记录", IOSType.title3, colors.label)
                    Spacer(Modifier.height(6.dp))
                    T(
                        "对小爱说话后，拦截与转发记录会显示在这里",
                        IOSType.footnote, colors.secondaryLabel,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            Entrance(0) {
                IOSGroup(
                    footer = "共 ${logs.size} 条 · 最新的在前",
                    rows = logs.map { log -> { LogRow(log) } },
                )
            }
        }

        Spacer(Modifier.height(bottomPadding + 20.dp))
    }
}

@Composable
private fun HeaderAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val colors = LocalIOSColors.current
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        IOSIcon(icon, tint, 19.dp)
    }
}

@Composable
private fun LogRow(log: LogEntry) {
    val colors = LocalIOSColors.current
    IOSRowContainer(onClick = null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (log.isError) colors.red else colors.green)
                )
                Spacer(Modifier.width(8.dp))
                T(log.type, IOSType.subheadline, colors.label, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                T(fmt(log.time), IOSType.caption1, colors.tertiaryLabel)
            }
            Spacer(Modifier.height(4.dp))
            T(
                log.content, IOSType.footnote, colors.secondaryLabel,
                maxLines = 6, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
