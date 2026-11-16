package com.smsforward.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsforward.app.ui.AppViewModel
import com.smsforward.app.ui.LocalNotifier
import com.smsforward.app.ui.guide.GuideAction
import com.smsforward.app.ui.guide.KeepAliveCheck
import com.smsforward.app.ui.guide.RomGuide
import com.smsforward.app.ui.guide.RomGuides
import com.smsforward.app.ui.guide.rememberBatteryOptimizationState
import com.smsforward.app.ui.guide.rememberPowerSaveMode
import com.smsforward.app.ui.components.rememberPressScale
import com.smsforward.app.ui.permission.rememberAppPermissions
import com.smsforward.app.util.OemSettings
import com.smsforward.app.util.TextFormat
import kotlinx.coroutines.delay

/**
 * 「自启动管理」二级页：按厂商给出保活步骤，并提供跳转与勾选进度。
 * 设计稿见 demo/autostart.html。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoStartGuideScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    val permissions = rememberAppPermissions()
    val savedBrand by viewModel.guideBrand.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.serviceRunning.collectAsStateWithLifecycle()
    val lastSyncAt by viewModel.lastSyncAt.collectAsStateWithLifecycle()
    val batteryUnrestricted = rememberBatteryOptimizationState()
    val powerSaveMode = rememberPowerSaveMode()

    // 页面停留时每 15 秒刷新一次"最近同步"，用来发现后台被冻结
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }

    val passed = KeepAliveCheck.passedCount(
        smsPermission = permissions.smsGranted,
        notificationPermission = permissions.notificationGranted,
        batteryUnrestricted = batteryUnrestricted,
        serviceRunning = serviceRunning,
        lastSyncAt = lastSyncAt,
        now = now,
    )
    // 检测结果变化时进度条平滑推进，而不是瞬间跳格
    val progressAnim by animateFloatAsState(
        targetValue = passed.toFloat() / KeepAliveCheck.TOTAL,
        animationSpec = tween(durationMillis = 520),
        label = "keepAliveProgress",
    )

    val detected = remember { RomGuides.detect(Build.MANUFACTURER, Build.BRAND) }
    val guide = RomGuides.all.firstOrNull { it.key == savedBrand }
        ?: detected
        ?: RomGuides.all.first()
    var brandsExpanded by remember { mutableStateOf(detected == null) }

    // 首次进入：把识别到的品牌存下来，之后记住用户的选择
    LaunchedEffect(savedBrand, guide.key) {
        if (savedBrand != guide.key && savedBrand == null) viewModel.setGuideBrand(guide.key)
    }

    val copyPath: (String) -> Unit = { text ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("autostart-path", text))
        notifier("已复制：$text")
    }

    val runAction: (GuideAction) -> Unit = { action ->
        when (action) {
            GuideAction.AUTO_START -> {
                if (!OemSettings.openAutoStartSettings(context)) {
                    notifier("这台手机没有可跳转的自启动页面，已打开应用详情")
                    OemSettings.openAppDetails(context)
                }
            }
            GuideAction.BATTERY -> OemSettings.openBatteryOptimizationSettings(context)
            GuideAction.NOTIFICATION -> permissions.openNotificationSettings()
            GuideAction.APP_DETAILS -> permissions.openSettings()
            GuideAction.MULTI_TASK -> notifier("打开最近任务，把「短信转发」卡片下拉加锁即可")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "自启动管理",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { HeroCard() }

            item {
                CardBlock {
                    CardTitle("选择你的手机系统", "已自动识别")
                    DetectedRow(detected)
                    Spacer(Modifier.height(10.dp))
                    AnimatedVisibility(
                        visible = !brandsExpanded,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(120)),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandChip(
                                guide = guide,
                                selected = true,
                                onClick = { brandsExpanded = true },
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { brandsExpanded = true }) {
                                Text("切换机型")
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = brandsExpanded,
                        enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RomGuides.all.forEach { item ->
                                BrandChip(
                                    guide = item,
                                    selected = item.key == guide.key,
                                    onClick = {
                                        viewModel.setGuideBrand(item.key)
                                        brandsExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }

            item {
                CardBlock {
                    CardTitle("运行检测", "自动检测，无需手动勾选")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = passed.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "/ ${KeepAliveCheck.TOTAL} 项通过",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progressAnim },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    StatusRow(
                        label = "短信权限",
                        passed = permissions.smsGranted,
                        detail = if (permissions.smsGranted) "已授予" else "未授予",
                    )
                    StatusRow(
                        label = "通知权限",
                        passed = permissions.notificationGranted,
                        detail = if (permissions.notificationGranted) "已开启" else "未开启",
                    )
                    StatusRow(
                        label = "电池优化白名单",
                        passed = batteryUnrestricted,
                        detail = if (batteryUnrestricted) "不受限制" else "仍受系统限制",
                    )
                    StatusRow(
                        label = "后台监听服务",
                        passed = serviceRunning,
                        detail = if (serviceRunning) "运行中" else "未运行",
                    )
                    StatusRow(
                        label = "最近同步",
                        passed = KeepAliveCheck.isFresh(lastSyncAt, now),
                        detail = TextFormat.relativeTime(lastSyncAt, now),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "「自启动白名单」「应用速冻」没有系统接口，无法自动检测；" +
                            "如果「后台监听服务」「最近同步」长期不通过，优先检查这两项。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (powerSaveMode) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE08A2E).copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "系统省电模式已开启，后台网络可能被延迟；建议把本应用加入白名单后再开省电模式。",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7A4A10),
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            item {
                CardBlock {
                    CardTitle(guide.name + " 设置步骤", guide.systems)
                    guide.steps.forEachIndexed { index, step ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        StepBlock(
                            index = index,
                            title = step.title,
                            path = step.path,
                            desc = step.desc,
                            onOpen = { runAction(step.action) },
                            onCopy = { copyPath(step.path.joinToString(" › ")) },
                        )
                    }
                    guide.note?.let { note ->
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFE08A2E).copy(alpha = 0.10f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                Color(0xFFE08A2E).copy(alpha = 0.28f),
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF7A4A10),
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }

            item {
                CardBlock {
                    CardTitle("快捷入口", "直接跳到系统页面")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickAction("应用详情") { permissions.openSettings() }
                        QuickAction("电池优化") { OemSettings.openBatteryOptimizationSettings(context) }
                        QuickAction("通知设置") { permissions.openNotificationSettings() }
                        QuickAction("自启动管理") { runAction(GuideAction.AUTO_START) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "按钮会按包名依次尝试跳转，全部失败时回退到「应用详情」，不会白点。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                CardBlock {
                    CardTitle("全部机型路径", "点一下切换")
                    RomGuides.all.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        BrandRow(
                            guide = item,
                            selected = item.key == guide.key,
                            onClick = { viewModel.setGuideBrand(item.key) },
                        )
                    }
                }
            }

            item {
                Text(
                    text = "不同系统版本菜单名会变（例如 realme UI 1.0/2.0 叫「自启动管理」，3.0+ 叫「自启动」），" +
                        "找不到时用系统设置顶部的搜索框搜关键词，效果一样。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2F5BEA), Color(0xFF6B8BFF))))
            .padding(20.dp),
    ) {
        Column {
            Text(
                text = "让短信在后台活下来",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "国产系统的「自启动白名单」「耗电管理」「应用速冻」会把 App 冻结。" +
                    "下面 5 项逐一放行，转发才不会被系统掐断。",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.88f),
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("自启动", "后台运行", "电池白名单", "关闭速冻", "任务加锁").forEach { tag ->
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.16f)) {
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardBlock(content: @Composable () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun CardTitle(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun DetectedRow(detected: RomGuide?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (detected != null) {
                    "检测到 ${Build.MANUFACTURER} ${Build.MODEL}".trim()
                } else {
                    "没识别出厂商，请手动选择"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (detected != null) {
                Surface(shape = CircleShape, color = Color(0xFF0F9D8F).copy(alpha = 0.12f)) {
                    Text(
                        text = "已匹配 " + detected.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF0B7C71),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandChip(guide: RomGuide, selected: Boolean, onClick: () -> Unit) {
    val (interaction, scaleMod) = rememberPressScale(0.94f)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = androidx.compose.foundation.BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = scaleMod,
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(guide.color, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = guide.badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = guide.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, passed: Boolean, detail: String) {
    val boxColor by animateColorAsState(
        targetValue = if (passed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(240),
        label = "statusBox",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(boxColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (passed) Icons.Rounded.Check else Icons.Rounded.Close,
                contentDescription = null,
                tint = if (passed) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepBlock(
    index: Int,
    title: String,
    path: List<String>,
    desc: String,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = buildAnnotatedString {
                        path.forEachIndexed { i, segment ->
                            if (i > 0) append(" › ")
                            if (i == path.lastIndex) {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                    append(segment)
                                }
                            } else {
                                append(segment)
                            }
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (openInteraction, openScale) = rememberPressScale(0.94f)
                Surface(
                    onClick = onOpen,
                    interactionSource = openInteraction,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = openScale,
                ) {
                    Text(
                        text = "去设置",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    )
                }
                val (copyInteraction, copyScale) = rememberPressScale(0.94f)
                Surface(
                    onClick = onCopy,
                    interactionSource = copyInteraction,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = copyScale,
                ) {
                    Text(
                        text = "复制路径",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, onClick: () -> Unit) {
    val (interaction, scaleMod) = rememberPressScale(0.94f)
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
        modifier = scaleMod,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun BrandRow(guide: RomGuide, selected: Boolean, onClick: () -> Unit) {
    val (interaction, scaleMod) = rememberPressScale(0.985f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(scaleMod)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(guide.color, RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = guide.badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = guide.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = guide.systems + " · " + guide.steps.size + " 步",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Text(
                text = "当前",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
