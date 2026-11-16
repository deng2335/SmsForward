package com.smsforward.app.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.smsforward.app.BuildConfig
import com.smsforward.app.data.model.ForwardState
import com.smsforward.app.ui.AppViewModel
import com.smsforward.app.ui.LocalNotifier
import com.smsforward.app.ui.components.ScreenHeader
import com.smsforward.app.ui.components.SectionHeader
import com.smsforward.app.ui.components.SettingRow
import com.smsforward.app.ui.components.StatusPill
import com.smsforward.app.ui.components.PillTone
import com.smsforward.app.ui.permission.rememberAppPermissions
import com.smsforward.app.ui.guide.KeepAliveCheck
import com.smsforward.app.ui.guide.rememberBatteryOptimizationState
import com.smsforward.app.ui.guide.rememberPowerSaveMode
import com.smsforward.app.ui.theme.BrandAmber
import com.smsforward.app.ui.theme.BrandBlue
import com.smsforward.app.ui.theme.BrandTeal
import com.smsforward.app.util.OemSettings
import com.smsforward.app.util.TextFormat

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onOpenAutoStartGuide: () -> Unit,
) {
    val serviceEnabled by viewModel.serviceEnabled.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val bootStart by viewModel.bootStart.collectAsStateWithLifecycle()
    val onlyOtp by viewModel.onlyOtp.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val permissions = rememberAppPermissions()

    val context = LocalContext.current
    val ignoringBattery = rememberBatteryOptimizationState()
    val powerSaveMode = rememberPowerSaveMode()
    val notifier = LocalNotifier.current
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }
    var showResetProcessed by remember { mutableStateOf(false) }
    val processedCount by viewModel.processedCount.collectAsStateWithLifecycle()
    val failedCount = messages.count { it.forwardState == ForwardState.FAILED }
    val keepAlivePassed = KeepAliveCheck.passedCount(
        smsPermission = permissions.smsGranted,
        notificationPermission = permissions.notificationGranted,
        batteryUnrestricted = ignoringBattery,
        serviceRunning = serviceRunning,
        lastSyncAt = lastSyncAt,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp),
    ) {
        ScreenHeader(
            title = "设置",
            subtitle = "运行状态、权限与本地数据",
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(16.dp))

        StatusHero(
            running = serviceRunning,
            enabled = serviceEnabled,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(22.dp))

        SectionBlock(title = "运行") {
            SettingRow(
                icon = Icons.Rounded.Notifications,
                title = "后台监听",
                subtitle = "常驻通知栏，尽量保证短信不漏收",
                trailing = {
                    Switch(checked = serviceEnabled, onCheckedChange = viewModel::setServiceEnabled)
                },
            )
            SettingRow(
                icon = Icons.Rounded.Send,
                title = "开机自启",
                subtitle = "重启手机后自动恢复监听",
                trailing = {
                    Switch(checked = bootStart, onCheckedChange = viewModel::setBootStart)
                },
            )
            SettingRow(
                icon = Icons.Rounded.Lock,
                title = "只转发验证码",
                subtitle = "开启后，正文里没有验证码的短信会被跳过",
                trailing = {
                    Switch(checked = onlyOtp, onCheckedChange = viewModel::setOnlyOtp)
                },
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionBlock(title = "权限") {
            SettingRow(
                icon = Icons.Rounded.Lock,
                title = "短信权限",
                subtitle = when {
                    permissions.smsGranted -> "已授予读取与接收短信权限"
                    permissions.smsBlocked -> "已被拒绝，需要到系统设置里手动开启"
                    else -> "未授予，无法读取或接收短信"
                },
                trailing = {
                    when {
                        permissions.smsGranted -> StatusPill("已授予", PillTone.Success)
                        permissions.smsBlocked -> TextButton(onClick = permissions.openSettings) {
                            Text("去设置")
                        }
                        else -> TextButton(onClick = permissions.requestSms) { Text("去授权") }
                    }
                },
            )
            SettingRow(
                icon = Icons.Rounded.Notifications,
                title = "通知权限",
                subtitle = when {
                    permissions.notificationGranted -> "已开启，监听状态会显示在通知栏"
                    permissions.notificationBlocked -> "已被拒绝，需要到系统设置里手动开启"
                    else -> "未开启，前台服务通知不会显示"
                },
                trailing = {
                    when {
                        permissions.notificationGranted -> StatusPill("已开启", PillTone.Success)
                        permissions.notificationBlocked -> TextButton(
                            onClick = permissions.openNotificationSettings,
                        ) { Text("去设置") }
                        else -> TextButton(onClick = permissions.requestNotification) { Text("去开启") }
                    }
                },
            )
            SettingRow(
                icon = Icons.Rounded.Warning,
                title = "电池优化",
                subtitle = if (ignoringBattery) "已加入白名单，后台更稳定" else "未加入白名单，部分机型可能杀进程",
                trailing = {
                    if (ignoringBattery) {
                        StatusPill("已优化", PillTone.Success)
                    } else {
                        TextButton(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }.onFailure {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }
                                }
                            },
                        ) { Text("去设置") }
                    }
                },
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionBlock(title = "保活自检") {
            SettingRow(
                icon = Icons.Rounded.Info,
                title = "系统版本",
                subtitle = "Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）· ${Build.MANUFACTURER} ${Build.MODEL}",
            )
            SettingRow(
                icon = Icons.Rounded.Send,
                title = "后台监听服务",
                subtitle = if (serviceRunning) {
                    "正在运行，收到短信会立即入库并转发"
                } else {
                    "未运行，只能靠系统广播，实时性没保障"
                },
                trailing = {
                    if (serviceRunning) {
                        StatusPill("运行中", PillTone.Success)
                    } else {
                        TextButton(onClick = { viewModel.setServiceEnabled(true) }) { Text("启动") }
                    }
                },
            )
            SettingRow(
                icon = Icons.Rounded.Refresh,
                title = "上次同步",
                subtitle = "服务每 90 秒对齐一次短信库，回到前台也会补同步",
                trailing = {
                    StatusPill(
                        text = TextFormat.relativeTime(lastSyncAt),
                        tone = PillTone.Neutral,
                        dot = false,
                    )
                },
            )
            SettingRow(
                icon = Icons.Rounded.Warning,
                title = "系统省电模式",
                subtitle = if (powerSaveMode) {
                    "已开启，后台投递可能被延迟；请完成下面的白名单设置"
                } else {
                    "未开启，后台限制较少"
                },
                tint = if (powerSaveMode) BrandAmber else MaterialTheme.colorScheme.primary,
                trailing = {
                    if (powerSaveMode) {
                        StatusPill("已开启", PillTone.Warning)
                    } else {
                        StatusPill("未开启", PillTone.Neutral, dot = false)
                    }
                },
            )
            SettingRow(
                icon = Icons.Rounded.Settings,
                title = "自启动管理",
                subtitle = "按机型给出保活步骤，自检通过 $keepAlivePassed/${KeepAliveCheck.TOTAL}",
                onClick = onOpenAutoStartGuide,
            )
            SettingRow(
                icon = Icons.Rounded.Settings,
                title = "打开应用设置",
                subtitle = "手动管理权限、通知与电池",
                onClick = permissions.openSettings,
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionBlock(title = "数据") {
            SettingRow(
                icon = Icons.Rounded.Refresh,
                title = "重试失败的转发",
                subtitle = if (failedCount > 0) "有 $failedCount 条失败记录" else "暂无失败记录",
                onClick = viewModel::retryFailed,
            )
            SettingRow(
                icon = Icons.Rounded.Delete,
                title = "清理已转发记录",
                subtitle = "只删除本地记录，不影响已送达的消息",
                onClick = viewModel::deleteForwarded,
            )
            SettingRow(
                icon = Icons.Rounded.Delete,
                title = "清空全部短信记录",
                subtitle = "本机数据库中的短信全部删除；已处理记录会保留，重新导入不会重复推送",
                tint = MaterialTheme.colorScheme.error,
                onClick = { showClearConfirm = true },
            )
            SettingRow(
                icon = Icons.Rounded.Refresh,
                title = "重置推送去重记录",
                subtitle = "已记录 $processedCount 条处理过的短信；重置后重新导入的历史短信会再次推送",
                tint = BrandAmber,
                onClick = { showResetProcessed = true },
            )
        }

        Spacer(Modifier.height(22.dp))

        SectionBlock(title = "关于") {
            SettingRow(
                icon = Icons.Rounded.Info,
                title = "版本",
                subtitle = "SmsForward ${BuildConfig.VERSION_NAME}",
            )
            SettingRow(
                icon = Icons.Rounded.Settings,
                title = "工作方式",
                subtitle = "短信只在本机解析：先入本地数据库，再按规则推送到你配置的渠道，不经过任何中转服务器。",
            )
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = "短信权限属于敏感权限，Google Play 仅允许默认短信应用申请；\n本项目面向自用安装（侧载），请勿上传商店。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(28.dp))
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "清空短信记录",
            message = "将删除本机保存的所有短信记录，此操作不可撤销。\n\n已处理的短信会被记住：之后重新导入收件箱里这些旧短信时，只会恢复记录，不会再次推送。",
            confirmText = "清空",
            onConfirm = {
                viewModel.clearAll()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }

    if (showResetProcessed) {
        ConfirmDialog(
            title = "重置推送去重记录",
            message = "重置后，App 不再记得哪些短信处理过。\n\n如果再清空短信记录并重新导入收件箱，里面的旧短信可能会被重新推送一次。通常只在调试时需要。",
            confirmText = "重置",
            onConfirm = {
                viewModel.resetProcessed()
                showResetProcessed = false
            },
            onDismiss = { showResetProcessed = false },
        )
    }
}

@Composable
private fun StatusHero(running: Boolean, enabled: Boolean, modifier: Modifier = Modifier) {
    // 状态切换时渐变整体过渡，避免颜色突变
    val topColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (running) BrandBlue else BrandAmber,
        animationSpec = androidx.compose.animation.core.tween(420),
        label = "heroTop",
    )
    val bottomColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (running) Color(0xFF6B8BFF) else Color(0xFFF0B45C),
        animationSpec = androidx.compose.animation.core.tween(420),
        label = "heroBottom",
    )
    val colors = listOf(topColor, bottomColor)
    val title = when {
        running -> "正在监听"
        enabled -> "监听已停止"
        else -> "监听已关闭"
    }
    val subtitle = when {
        running -> "收到短信会立即入库，并按规则推送"
        enabled -> "后台服务被系统回收了，点下方开关重新开启"
        else -> "打开开关后才会持续接收新短信"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(colors)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text(
                "监听状态",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f),
            )
        }
    }
}

@Composable
private fun SectionBlock(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        SectionHeader(title = title)
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                content()
            }
        }
    }
}

