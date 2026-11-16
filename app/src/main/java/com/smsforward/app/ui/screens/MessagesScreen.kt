package com.smsforward.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsforward.app.data.entity.SmsEntity
import com.smsforward.app.data.model.ForwardState
import com.smsforward.app.ui.AppViewModel
import com.smsforward.app.ui.LocalNotifier
import com.smsforward.app.ui.components.AppCard
import com.smsforward.app.ui.components.CodeChip
import com.smsforward.app.ui.components.ForwardStatusPill
import com.smsforward.app.ui.components.ScreenHeader
import com.smsforward.app.ui.components.SenderAvatar
import com.smsforward.app.ui.components.StatCard
import com.smsforward.app.ui.components.pressScale
import com.smsforward.app.ui.components.rememberPressScale
import com.smsforward.app.ui.permission.rememberAppPermissions
import com.smsforward.app.ui.theme.BrandAmber
import com.smsforward.app.ui.theme.BrandTeal
import com.smsforward.app.util.TextFormat

private enum class MessageFilter(val label: String) {
    ALL("全部"),
    OTP("验证码"),
    SENT("已转发"),
    FAILED("失败"),
}

@Composable
fun MessagesScreen(viewModel: AppViewModel) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val serviceEnabled by viewModel.serviceEnabled.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val permissions = rememberAppPermissions()
    val notifier = LocalNotifier.current
    val context = LocalContext.current
    var filter by remember { mutableStateOf(MessageFilter.ALL) }
    var actionMessage by remember { mutableStateOf<SmsEntity?>(null) }

    val copyToClipboard: (String, String) -> Unit = { text, label ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("sms", text))
        notifier("已复制$label")
    }

    val todayStart = remember { TextFormat.startOfToday() }
    val todayMessages = remember(messages, todayStart) { messages.filter { it.receivedAt >= todayStart } }
    val todayCount = todayMessages.size
    val sentCount = todayMessages.count {
        it.forwardState == ForwardState.SENT || it.forwardState == ForwardState.PARTIAL
    }
    val problemCount = todayMessages.count { it.forwardState == ForwardState.FAILED }

    val visible = remember(messages, filter) {
        when (filter) {
            MessageFilter.ALL -> messages
            MessageFilter.OTP -> messages.filter { !it.code.isNullOrBlank() }
            MessageFilter.SENT -> messages.filter {
                it.forwardState == ForwardState.SENT || it.forwardState == ForwardState.PARTIAL
            }
            MessageFilter.FAILED -> messages.filter { it.forwardState == ForwardState.FAILED }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp),
    ) {
        ScreenHeader(
            title = "收件箱",
            subtitle = buildString {
                append(if (serviceRunning) "实时监听中" else "监听未开启")
                append(" · 今天捕获 ")
                append(todayCount)
                append(" 条")
            },
            modifier = Modifier.padding(horizontal = 20.dp),
            actions = {
                IconButton(onClick = { viewModel.refreshInbox() }, enabled = !syncing) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = "扫描收件箱",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )

        Spacer(Modifier.height(14.dp))

        AnimatedVisibility(
            visible = !permissions.smsGranted,
            enter = fadeIn(tween(200)) + expandVertically(tween(240)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(220)),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                PermissionBanner(
                    blocked = permissions.smsBlocked,
                    onGrant = {
                        if (permissions.smsBlocked) permissions.openSettings() else permissions.requestSms()
                    },
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        AnimatedVisibility(
            visible = permissions.smsGranted && serviceEnabled && !serviceRunning,
            enter = fadeIn(tween(200)) + expandVertically(tween(240)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(220)),
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                ServiceBanner(onStart = { viewModel.setServiceEnabled(true) })
                Spacer(Modifier.height(12.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatCard(
                value = todayCount.toString(),
                label = "今日捕获",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = sentCount.toString(),
                label = "已转发",
                tint = BrandTeal,
                modifier = Modifier.weight(1f),
            )
            StatCard(
                value = problemCount.toString(),
                label = "转发失败",
                tint = BrandAmber,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MessageFilter.entries.forEach { entry ->
                FilterChip(
                    selected = filter == entry,
                    onClick = { filter = entry },
                    label = { Text(entry.label) },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = filter == entry,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (visible.isNotEmpty()) {
            Text(
                text = "长按短信可复制全文、复制验证码或删除记录",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (visible.isEmpty()) {
                item {
                    EmptyInbox(
                        filtered = filter != MessageFilter.ALL && messages.isNotEmpty(),
                        onRefresh = { viewModel.refreshInbox() },
                        modifier = Modifier.fillParentMaxHeight(0.72f),
                    )
                }
            } else {
                items(visible, key = { it.id }) { message ->
                    SmsCard(
                        message = message,
                        onCopyCode = { code -> copyToClipboard(code, "验证码 $code") },
                        onLongPress = { actionMessage = message },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    actionMessage?.let { message ->
        MessageActionSheet(
            message = message,
            onDismiss = { actionMessage = null },
            onCopyBody = {
                copyToClipboard(message.body, "短信全文")
                actionMessage = null
            },
            onCopyCode = { code ->
                copyToClipboard(code, "验证码 $code")
                actionMessage = null
            },
            onDelete = {
                viewModel.deleteMessage(message.id)
                actionMessage = null
            },
        )
    }
}

@Composable
private fun SmsCard(
    message: SmsEntity,
    onCopyCode: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 按住有轻微下沉反馈，长按触发操作面板
    var holding by remember(message.id) { mutableStateOf(false) }
    AppCard(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(holding, pressedScale = 0.985f)
            .pointerInput(message.id) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    },
                    onLongPress = { onLongPress() },
                )
            },
    ) {
        Row(Modifier.fillMaxWidth()) {
            SenderAvatar(message.sender)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = TextFormat.senderLabel(message.sender),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = TextFormat.messageTime(message.receivedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = TextFormat.bodyPreview(message.body, max = 180),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    message.code?.takeIf { it.isNotBlank() }?.let { code ->
                        CodeChip(code = code, onCopy = { onCopyCode(code) })
                    }
                    ForwardStatusPill(message.forwardState)
                }
                if (message.forwardState == ForwardState.FAILED && !message.forwardNote.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message.forwardNote.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (message.forwardState == ForwardState.SKIPPED &&
                    !message.forwardNote.isNullOrBlank()
                ) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message.forwardNote.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionSheet(
    message: SmsEntity,
    onDismiss: () -> Unit,
    onCopyBody: () -> Unit,
    onCopyCode: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember(message.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SenderAvatar(message.sender)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = TextFormat.senderLabel(message.sender),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = TextFormat.messageTime(message.receivedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }

            Spacer(Modifier.height(14.dp))

            SheetAction(text = "复制短信全文", onClick = onCopyBody)
            message.code?.takeIf { it.isNotBlank() }?.let { code ->
                SheetAction(text = "复制验证码 $code", onClick = { onCopyCode(code) })
            }
            SheetAction(
                text = "删除这条记录",
                tint = MaterialTheme.colorScheme.error,
                onClick = { confirmDelete = true },
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除短信记录",
            message = "只删除本机保存的这条记录，不会删除系统短信，也不影响已经转发出去的内容。",
            confirmText = "删除",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun SheetAction(
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val (interaction, scaleMod) = rememberPressScale(0.97f)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = scaleMod
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PermissionBanner(
    modifier: Modifier = Modifier,
    blocked: Boolean,
    onGrant: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(
                            MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    if (blocked) "短信权限被系统拒绝了" else "还差一步：授予短信权限",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
                Spacer(Modifier.height(6.dp))
            Text(
                if (blocked) {
                    "请在「设置 → 应用 → 短信转发 → 权限」里手动打开短信权限，否则读不到收件箱、也收不到新短信。"
                } else {
                    "读取收件箱历史和实时接收新短信都需要它。权限只用于本地处理，不会上传到任何服务器。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onGrant,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(if (blocked) "打开系统设置" else "立即授予")
            }
        }
    }
}

@Composable
private fun ServiceBanner(modifier: Modifier = Modifier, onStart: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("后台监听没有运行", style = MaterialTheme.typography.titleSmall)
                Text(
                    "现在只能靠系统广播收短信，部分机型可能会漏收",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onStart,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text("开启", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun EmptyInbox(filtered: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        InboxIllustration(Modifier.size(132.dp))
        Spacer(Modifier.height(18.dp))
        Text(
            text = if (filtered) "这个分类下还没有短信" else "还没有捕获到短信",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (filtered) {
                "换个筛选条件看看"
            } else {
                "收到新短信会自动出现在这里，也可以手动扫描一遍收件箱"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        if (!filtered) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRefresh) { Text("扫描收件箱") }
        }
    }
}

/** 空状态插图：同心圆 + 信封 + 转发箭头，纯 Canvas 画，不占资源。 */
@Composable
private fun InboxIllustration(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val accent = MaterialTheme.colorScheme.secondary
    Canvas(modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(primary.copy(alpha = 0.06f), radius = size.minDimension * 0.48f, center = center)
        drawCircle(primary.copy(alpha = 0.09f), radius = size.minDimension * 0.34f, center = center)
        drawCircle(
            color = primary.copy(alpha = 0.16f),
            radius = size.minDimension * 0.34f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx()),
        )

        val w = size.width * 0.46f
        val h = w * 0.68f
        val left = center.x - w / 2f
        val top = center.y - h / 2f - size.height * 0.04f
        val corner = 9.dp.toPx()

        drawRoundRect(
            color = surface,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(corner),
        )
        drawRoundRect(
            color = primary,
            topLeft = Offset(left, top),
            size = Size(w, h),
            cornerRadius = CornerRadius(corner),
            style = Stroke(width = 2.dp.toPx()),
        )

        val flap = Path().apply {
            moveTo(left + 6.dp.toPx(), top + 8.dp.toPx())
            lineTo(center.x, top + h * 0.58f)
            lineTo(left + w - 6.dp.toPx(), top + 8.dp.toPx())
        }
        drawPath(flap, color = primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

        val badgeCenter = Offset(left + w - 2.dp.toPx(), top + h - 2.dp.toPx())
        drawCircle(accent, radius = 13.dp.toPx(), center = badgeCenter)
        val arrow = Path().apply {
            moveTo(badgeCenter.x - 4.dp.toPx(), badgeCenter.y - 5.dp.toPx())
            lineTo(badgeCenter.x + 3.dp.toPx(), badgeCenter.y)
            lineTo(badgeCenter.x - 4.dp.toPx(), badgeCenter.y + 5.dp.toPx())
        }
        drawPath(
            arrow,
            color = surface,
            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}
