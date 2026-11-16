package com.smsforward.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsforward.app.data.entity.ChannelEntity
import com.smsforward.app.data.entity.RuleEntity
import com.smsforward.app.data.model.ChannelType
import com.smsforward.app.ui.AppViewModel
import com.smsforward.app.ui.components.AppCard
import com.smsforward.app.ui.components.ScreenHeader
import com.smsforward.app.ui.components.SectionHeader
import com.smsforward.app.ui.components.StatusPill
import com.smsforward.app.ui.components.rememberPressScale
import com.smsforward.app.ui.theme.BrandAmber
import com.smsforward.app.ui.theme.BrandBlue
import com.smsforward.app.ui.theme.BrandRose
import com.smsforward.app.ui.theme.BrandTeal
import com.smsforward.app.ui.theme.BrandViolet

@Composable
fun RulesScreen(viewModel: AppViewModel) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()

    var editingRule by remember { mutableStateOf<RuleEntity?>(null) }
    var showRuleEditor by remember { mutableStateOf(false) }
    var editingChannel by remember { mutableStateOf<ChannelEntity?>(null) }
    var showChannelEditor by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<RuleEntity?>(null) }
    var pendingChannelDelete by remember { mutableStateOf<ChannelEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp),
    ) {
        ScreenHeader(
            title = "转发规则",
            subtitle = "短信命中规则后自动推送到目标渠道",
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(16.dp))

        SectionHeader(
            title = "目标渠道",
            subtitle = if (channels.isEmpty()) "还没有添加，先去添加一个" else "已添加 ${channels.size} 个",
            modifier = Modifier.padding(horizontal = 20.dp),
            trailing = {
                TextButton(
                    onClick = {
                        editingChannel = null
                        showChannelEditor = true
                    },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("添加")
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        if (channels.isEmpty()) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "支持 Telegram、钉钉、企业微信、Server酱、Bark 和自定义 Webhook",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(channels, key = { it.id }) { channel ->
                    ChannelTile(
                        channel = channel,
                        onClick = {
                            editingChannel = channel
                            showChannelEditor = true
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        SectionHeader(
            title = "规则",
            subtitle = if (rules.isEmpty()) "还没有规则" else "${rules.count { it.enabled }} 条启用中",
            modifier = Modifier.padding(horizontal = 20.dp),
            trailing = {
                TextButton(
                    onClick = {
                        editingRule = null
                        showRuleEditor = true
                    },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("新建")
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        if (rules.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Send,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("新短信目前不会被转发", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "建一条规则，把收到的短信推到手机以外的设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = {
                    editingRule = null
                    showRuleEditor = true
                }) {
                    Text("创建第一条规则")
                }
                Spacer(Modifier.height(48.dp))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(rules, key = { _, rule -> rule.id }) { index, rule ->
                    RuleCard(
                        rule = rule,
                        position = index,
                        channels = channels,
                        canMoveUp = index > 0,
                        canMoveDown = index < rules.lastIndex,
                        modifier = Modifier.animateItem(),
                        onToggle = { enabled -> viewModel.saveRule(rule.copy(enabled = enabled)) },
                        onMoveUp = { viewModel.moveRule(rule, up = true) },
                        onMoveDown = { viewModel.moveRule(rule, up = false) },
                        onEdit = {
                            editingRule = rule
                            showRuleEditor = true
                        },
                        onDelete = { pendingDelete = rule },
                    )
                }
            }
        }
    }

    if (showRuleEditor) {
        RuleEditorSheet(
            initial = editingRule,
            channels = channels,
            onDismiss = { showRuleEditor = false },
            onSave = { rule ->
                viewModel.saveRule(rule)
                showRuleEditor = false
            },
        )
    }

    if (showChannelEditor) {
        ChannelEditorSheet(
            initial = editingChannel,
            onDismiss = { showChannelEditor = false },
            onSave = { channel ->
                viewModel.saveChannel(channel)
                showChannelEditor = false
            },
            onDelete = { channel ->
                showChannelEditor = false
                pendingChannelDelete = channel
            },
            onTest = { channel -> viewModel.testChannel(channel) },
        )
    }

    pendingDelete?.let { rule ->
        ConfirmDialog(
            title = "删除规则",
            message = "确定删除「${rule.name}」？删除后这条规则不再转发任何短信。",
            confirmText = "删除",
            onConfirm = {
                viewModel.deleteRule(rule)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    pendingChannelDelete?.let { channel ->
        ConfirmDialog(
            title = "删除渠道",
            message = "确定删除「${channel.name}」？引用它的规则会变成没有目标。",
            confirmText = "删除",
            onConfirm = {
                viewModel.deleteChannel(channel)
                pendingChannelDelete = null
            },
            onDismiss = { pendingChannelDelete = null },
        )
    }
}

@Composable
private fun ChannelTile(channel: ChannelEntity, onClick: () -> Unit) {
    val (interaction, scaleMod) = rememberPressScale(0.96f)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        modifier = scaleMod.alpha(if (channel.enabled) 1f else 0.5f),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelBadge(channel.type)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 132.dp),
                )
                Text(
                    text = if (channel.enabled) ChannelType.shortLabel(channel.type) else "已停用",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ChannelBadge(type: Int, size: Int = 32) {
    val color = channelColor(type)
    val text = channelBadgeText(type)
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape((size * 0.3f).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

fun channelColor(type: Int): Color = when (type) {
    ChannelType.TELEGRAM -> BrandBlue
    ChannelType.DINGTALK -> BrandAmber
    ChannelType.WECOM -> BrandTeal
    ChannelType.BARK -> BrandViolet
    ChannelType.SERVERCHAN -> BrandRose
    else -> BrandBlue
}

fun channelBadgeText(type: Int): String = when (type) {
    ChannelType.TELEGRAM -> "TG"
    ChannelType.DINGTALK -> "钉"
    ChannelType.WECOM -> "企"
    ChannelType.BARK -> "B"
    ChannelType.SERVERCHAN -> "SC"
    else -> "WH"
}

@Composable
private fun RuleCard(
    rule: RuleEntity,
    position: Int,
    channels: List<ChannelEntity>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val targets = rule.targetIdList().mapNotNull { id -> channels.firstOrNull { it.id == id } }

    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${position + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = rule.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                        if (!rule.enabled) {
                            StatusPill("已停用", com.smsforward.app.ui.components.PillTone.Neutral)
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = rule.summary(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (targets.isEmpty()) {
                            StatusPill("未绑定渠道", com.smsforward.app.ui.components.PillTone.Warning)
                        } else {
                            targets.take(3).forEach { channel ->
                                ChannelRefPill(channel.name, channel.type)
                            }
                            if (targets.size > 3) {
                                StatusPill("+${targets.size - 3}", com.smsforward.app.ui.components.PillTone.Neutral, dot = false)
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Switch(checked = rule.enabled, onCheckedChange = onToggle)
                    Box {
                        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = "更多",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("上移") },
                                enabled = canMoveUp,
                                leadingIcon = {
                                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onMoveUp()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("下移") },
                                enabled = canMoveDown,
                                leadingIcon = {
                                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    onMoveDown()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("编辑") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onEdit()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRefPill(name: String, type: Int) {
    val color = channelColor(type)
    Surface(shape = CircleShape, color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
