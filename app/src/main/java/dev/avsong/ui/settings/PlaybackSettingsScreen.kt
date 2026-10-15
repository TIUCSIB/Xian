package dev.avsong.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * 播放设置子页(「我的」页「播放设置」行进入):
 * 收纳全部播放类偏好——默认音质/播放历史上限/倍速播放/睡眠定时/热评展示。
 * 2026-10-15 从「我的」页设置卡整体搬入(用户要求:「我的」页按通用/其他归纳,
 * 不该所有项平铺导致超出一屏滚动;播放项同类,统一收进子页,「我的」页只留一行入口+摘要)。
 * 交互(就地展开/同一时间只展开一项/行尾回显)原样保留,组件自包含。
 */
@Composable
fun PlaybackSettingsScreen(
    quality: SettingsStore.Quality,
    onQualityChange: (SettingsStore.Quality) -> Unit,
    historyLimit: Int,
    onHistoryChange: (Int) -> Unit,
    playbackSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    sleepMinutesLeft: Int?,
    onSetSleepTimer: (Int) -> Unit,
    hotReplyMode: Int,
    onHotReplyModeChange: (Int) -> Unit,
    onBack: () -> Unit,
) {
    var expandedKey by remember { mutableStateOf<String?>(null) }
    // 睡眠定时自定义分钟数:数字键盘,1–720 校验
    var showSleepCustomDialog by remember { mutableStateOf(false) }
    var sleepCustomText by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // 顶栏:返回 + 标题(与已下载页同款骨架)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                "播放设置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        // 5 项常规屏一屏内放得下;仍给滚动兜底(小屏/大字体不裁切)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                SettingsExpandableItem(
                    icon = Icons.Rounded.GraphicEq,
                    title = "默认音质",
                    value = quality.label,
                    expanded = expandedKey == "quality",
                    onToggle = { expandedKey = if (expandedKey == "quality") null else "quality" },
                ) {
                    SettingsStore.Quality.entries.forEach { q ->
                        SettingsOptionRow(
                            label = q.label + if (q == SettingsStore.Quality.LOSSLESS) "(需大会员)" else "",
                            selected = q == quality,
                            onClick = {
                                onQualityChange(q)
                                expandedKey = null
                            },
                        )
                    }
                }
                SettingsExpandableItem(
                    icon = Icons.Rounded.History,
                    title = "播放历史上限",
                    value = if (historyLimit == 0) "不限" else "$historyLimit 首",
                    expanded = expandedKey == "history",
                    onToggle = { expandedKey = if (expandedKey == "history") null else "history" },
                ) {
                    listOf(20, 50, 100, 0).forEach { n ->
                        SettingsOptionRow(
                            label = if (n == 0) "不限" else "$n 首",
                            selected = n == historyLimit,
                            onClick = {
                                onHistoryChange(n)
                                expandedKey = null
                            },
                        )
                    }
                }
                SettingsExpandableItem(
                    icon = Icons.Rounded.Speed,
                    title = "倍速播放",
                    value = if (playbackSpeed == 1.0f) "常速" else "%.2fx".format(playbackSpeed).removeSuffix("0"),
                    expanded = expandedKey == "speed",
                    onToggle = { expandedKey = if (expandedKey == "speed") null else "speed" },
                ) {
                    SettingsStore.SPEED_STEPS.forEach { sp ->
                        SettingsOptionRow(
                            label = if (sp == 1.0f) "常速" else "%.2fx".format(sp).removeSuffix("0"),
                            selected = sp == playbackSpeed,
                            onClick = {
                                onSpeedChange(sp)
                                expandedKey = null
                            },
                        )
                    }
                }
                SettingsExpandableItem(
                    icon = Icons.Rounded.Bedtime,
                    title = "睡眠定时",
                    value = sleepTimerLabel(sleepMinutesLeft),
                    expanded = expandedKey == "sleep",
                    onToggle = { expandedKey = if (expandedKey == "sleep") null else "sleep" },
                ) {
                    if (sleepMinutesLeft != null) {
                        SettingsOptionRow(label = "关闭定时", selected = false, onClick = {
                            onSetSleepTimer(0)
                            expandedKey = null
                        })
                    }
                    listOf(15, 30, 60, 90).forEach { min ->
                        SettingsOptionRow(
                            label = minutesLabel(min),
                            selected = sleepMinutesLeft == min,
                            onClick = {
                                onSetSleepTimer(min)
                                expandedKey = null
                            },
                        )
                    }
                    SettingsOptionRow(
                        label = if (sleepMinutesLeft != null && sleepMinutesLeft !in listOf(15, 30, 60, 90))
                            "自定义(${sleepMinutesLeft} 分钟)" else "自定义…",
                        selected = sleepMinutesLeft != null && sleepMinutesLeft !in listOf(15, 30, 60, 90),
                        onClick = {
                            showSleepCustomDialog = true
                            expandedKey = null
                        },
                    )
                }
                SettingsExpandableItem(
                    icon = Icons.Rounded.Subtitles,
                    title = "热评展示",
                    value = when (hotReplyMode) {
                        0 -> "弹幕"
                        1 -> "列表"
                        else -> "关"
                    },
                    expanded = expandedKey == "hotreply",
                    onToggle = { expandedKey = if (expandedKey == "hotreply") null else "hotreply" },
                ) {
                    SettingsOptionRow(label = "弹幕(播放页飘动)", selected = hotReplyMode == 0, onClick = {
                        onHotReplyModeChange(0)
                        expandedKey = null
                    })
                    SettingsOptionRow(label = "列表(底部抽屉)", selected = hotReplyMode == 1, onClick = {
                        onHotReplyModeChange(1)
                        expandedKey = null
                    })
                    SettingsOptionRow(label = "关闭", selected = hotReplyMode == -1, onClick = {
                        onHotReplyModeChange(-1)
                        expandedKey = null
                    })
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }

    if (showSleepCustomDialog) {
        val v = sleepCustomText.toIntOrNull()
        val invalid = v == null || v !in 1..720
        AlertDialog(
            onDismissRequest = { showSleepCustomDialog = false },
            title = { Text("自定义睡眠定时") },
            text = {
                Column {
                    Text(
                        "播放到设定时长后自动暂停(1–720 分钟)。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = sleepCustomText,
                        onValueChange = { sleepCustomText = it.filter { c -> c.isDigit() }.take(3) },
                        singleLine = true,
                        isError = sleepCustomText.isNotEmpty() && invalid,
                        supportingText = {
                            if (sleepCustomText.isNotEmpty() && invalid) {
                                Text("请输入 1–720 之间的整数", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !invalid,
                    onClick = {
                        showSleepCustomDialog = false
                        v?.let { onSetSleepTimer(it) }
                        sleepCustomText = ""
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showSleepCustomDialog = false }) { Text("取消") }
            },
        )
    }
}

/** 睡眠定时回显文案(「我的」页入口行共用)。 */
fun sleepTimerLabel(minutesLeft: Int?): String = when {
    minutesLeft == null -> "关"
    minutesLeft < 60 -> "$minutesLeft 分钟"
    minutesLeft % 60 == 0 -> "${minutesLeft / 60} 小时"
    else -> "${minutesLeft / 60} 小时 ${minutesLeft % 60} 分钟"
}

/** 分钟数档位文案。 */
private fun minutesLabel(min: Int): String = when {
    min < 60 -> "$min 分钟"
    min % 60 == 0 -> "${min / 60} 小时"
    else -> "${min / 60} 小时 ${min % 60} 分钟"
}

/**
 * 可展开设置行:点击整行就地展开/收起选项(不弹层);箭头折叠朝下、展开转 180°。
 * 折叠态右侧显示当前值;展开的选项缩进 52dp 对齐标题(16 内边距 + 22 图标 + 14 间隔)。
 * (与 LibraryScreen 原实现同款;搬页后组件自包含。)
 */
@Composable
private fun SettingsExpandableItem(
    icon: ImageVector,
    title: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    options: @Composable () -> Unit,
) {
    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "settingsArrow")
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(arrowRotation),
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.padding(bottom = 6.dp)) { options() }
        }
    }
}

/** 展开后的选项行:选中 = primary 加粗 + 勾。 */
@Composable
private fun SettingsOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 52.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
