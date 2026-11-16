package com.smsforward.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.smsforward.app.data.entity.ChannelEntity
import com.smsforward.app.data.entity.RuleEntity
import com.smsforward.app.data.model.ChannelType
import com.smsforward.app.data.model.MatchType
import com.smsforward.app.ui.components.SectionHeader
import com.smsforward.app.ui.components.StatusPill
import com.smsforward.app.ui.components.PillTone
import org.json.JSONObject

// ---------------- 规则编辑 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorSheet(
    initial: RuleEntity?,
    channels: List<ChannelEntity>,
    onDismiss: () -> Unit,
    onSave: (RuleEntity) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var matchType by remember { mutableStateOf(initial?.matchType ?: MatchType.ALL) }
    var pattern by remember { mutableStateOf(initial?.pattern.orEmpty()) }
    var selectedIds by remember { mutableStateOf(initial?.targetIdList()?.toSet() ?: emptySet()) }

    val canSave = name.isNotBlank() &&
        (matchType == MatchType.ALL || pattern.isNotBlank()) &&
        selectedIds.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            SheetTitle(
                title = if (initial == null) "新建规则" else "编辑规则",
                subtitle = "选择哪些短信需要转发，以及发到哪里",
                onClose = onDismiss,
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("规则名称") },
                placeholder = { Text("例如：验证码转 Telegram") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用这条规则", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "关闭后保留配置但不参与转发",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Spacer(Modifier.height(14.dp))

            SectionHeader(title = "匹配方式", subtitle = MatchType.hint(matchType))

            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MatchType.all) { type ->
                    FilterChip(
                        selected = matchType == type,
                        onClick = { matchType = type },
                        label = { Text(MatchType.label(type)) },
                        shape = CircleShape,
                    )
                }
            }

            if (matchType != MatchType.ALL) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = {
                        Text(
                            when (matchType) {
                                MatchType.KEYWORD -> "关键词"
                                MatchType.SENDER -> "发件人号码/名称"
                                else -> "正则表达式"
                            },
                        )
                    },
                    placeholder = {
                        Text(
                            when (matchType) {
                                MatchType.KEYWORD -> "验证码,登录"
                                MatchType.SENDER -> "95588,中国银行"
                                else -> "^【银行】"
                            },
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader(
                title = "目标渠道",
                subtitle = if (channels.isEmpty()) "还没有渠道，先回到上一页添加" else "已选 ${selectedIds.size} 个",
            )

            Spacer(Modifier.height(8.dp))

            if (channels.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "规则需要绑定至少一个目标渠道才能保存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            } else {
                channels.forEach { channel ->
                    val checked = selectedIds.contains(channel.id)
                    Surface(
                        onClick = {
                            selectedIds = if (checked) selectedIds - channel.id else selectedIds + channel.id
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (checked) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ChannelBadge(channel.type)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(channel.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    ChannelType.shortLabel(channel.type) + " · " + channel.targetSummary(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            Checkbox(checked = checked, onCheckedChange = null)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(
                    onClick = {
                        onSave(
                            RuleEntity(
                                id = initial?.id ?: 0,
                                name = name.trim(),
                                enabled = enabled,
                                matchType = matchType,
                                pattern = if (matchType == MatchType.ALL) "" else pattern.trim(),
                                targetIds = selectedIds.joinToString(","),
                                priority = initial?.priority ?: 0,
                                createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                            ),
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (initial == null) "创建规则" else "保存修改")
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "提示：一条短信可以同时命中多条规则，目标渠道会合并去重。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------- 渠道编辑 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelEditorSheet(
    initial: ChannelEntity?,
    onDismiss: () -> Unit,
    onSave: (ChannelEntity) -> Unit,
    onDelete: (ChannelEntity) -> Unit,
    onTest: (ChannelEntity) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(initial?.type ?: ChannelType.TELEGRAM) }
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url().orEmpty()) }
    var token by remember { mutableStateOf(initial?.token().orEmpty()) }
    var chatId by remember { mutableStateOf(initial?.chatId().orEmpty()) }
    var channelEnabled by remember { mutableStateOf(initial?.enabled ?: true) }

    val valid = when (type) {
        ChannelType.TELEGRAM -> token.isNotBlank() && chatId.isNotBlank()
        else -> url.isNotBlank()
    }

    val buildChannel: () -> ChannelEntity = {
        val config = JSONObject()
        if (url.isNotBlank()) config.put("url", url.trim())
        if (token.isNotBlank()) config.put("token", token.trim())
        if (chatId.isNotBlank()) config.put("chatId", chatId.trim())
        ChannelEntity(
            id = initial?.id ?: 0,
            name = name.ifBlank { ChannelType.shortLabel(type) + " 推送" },
            type = type,
            config = config.toString(),
            enabled = channelEnabled,
            createdAt = initial?.createdAt ?: System.currentTimeMillis(),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            SheetTitle(
                title = if (initial == null) "添加渠道" else "编辑渠道",
                subtitle = "消息会以纯文本推送到这里",
                onClose = onDismiss,
                trailing = if (initial != null) {
                    {
                        IconButton(onClick = { onDelete(initial) }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除渠道",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                } else {
                    null
                },
            )

            Spacer(Modifier.height(14.dp))

            SectionHeader(title = "渠道类型")

            Spacer(Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ChannelType.all) { candidate ->
                    FilterChip(
                        selected = type == candidate,
                        onClick = { type = candidate },
                        label = { Text(ChannelType.shortLabel(candidate)) },
                        shape = CircleShape,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("渠道名称") },
                placeholder = { Text(ChannelType.shortLabel(type) + " 推送") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("启用这个渠道", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "关闭后规则不会往这里推送，配置会保留",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = channelEnabled, onCheckedChange = { channelEnabled = it })
            }

            Spacer(Modifier.height(10.dp))

            if (type == ChannelType.TELEGRAM) {
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it.trim() },
                    label = { Text("Bot Token") },
                    placeholder = { Text("123456789:AAH...") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it.trim() },
                    label = { Text("Chat ID") },
                    placeholder = { Text("123456789 或 @channel") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it.trim() },
                    label = { Text(urlLabel(type)) },
                    placeholder = { Text(urlPlaceholder(type)) },
                    singleLine = false,
                    maxLines = 3,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = typeHint(type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                TextButton(onClick = { onTest(buildChannel()) }, enabled = valid) { Text("测试") }
                Button(
                    onClick = { onSave(buildChannel()) },
                    enabled = valid,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (initial == null) "添加" else "保存")
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing?.invoke()
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭")
        }
    }
}

private fun urlLabel(type: Int): String = when (type) {
    ChannelType.DINGTALK -> "钉钉机器人 Webhook"
    ChannelType.WECOM -> "企业微信机器人 Webhook"
    ChannelType.BARK -> "Bark 推送地址"
    ChannelType.SERVERCHAN -> "SendKey 或推送地址"
    else -> "Webhook 地址"
}

private fun urlPlaceholder(type: Int): String = when (type) {
    ChannelType.DINGTALK -> "https://oapi.dingtalk.com/robot/send?access_token=..."
    ChannelType.WECOM -> "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=..."
    ChannelType.BARK -> "https://api.day.app/你的Key"
    ChannelType.SERVERCHAN -> "SCTxxxxx 或 https://sctapi.ftqq.com/SCTxxxxx.send"
    else -> "https://example.com/sms-hook"
}

private fun typeHint(type: Int): String = when (type) {
    ChannelType.TELEGRAM -> "在 @BotFather 创建机器人拿到 Token；先给机器人发一条消息，再访问 getUpdates 查 Chat ID。"
    ChannelType.DINGTALK -> "群设置 → 智能群助手 → 添加机器人 → 自定义。如果安全设置用了关键词，请把「短信」加入关键词。"
    ChannelType.WECOM -> "群聊 → 群机器人 → 添加 → 复制 Webhook 地址。"
    ChannelType.BARK -> "iOS 上安装 Bark，复制 App 里的推送地址；需要区分设备时可用带设备 Key 的地址。"
    ChannelType.SERVERCHAN -> "在 sct.ftqq.com 获取 SendKey，直接填 Key 或完整推送地址均可。"
    else -> "会以 POST 方式发送 JSON：{title, from, code, text, receivedAt, content}，方便接自己的服务。"
}
