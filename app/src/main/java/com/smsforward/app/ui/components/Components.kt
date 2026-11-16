package com.smsforward.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsforward.app.ui.theme.AvatarPalette
import com.smsforward.app.util.TextFormat

// ---------------- 状态标签 ----------------

enum class PillTone { Success, Warning, Error, Info, Neutral }

private data class ToneColors(val bg: Color, val fg: Color)

@Composable
private fun toneColors(tone: PillTone): ToneColors {
    val dark = isSystemInDarkTheme()
    return when (tone) {
        PillTone.Success -> if (dark) {
            ToneColors(Color(0x2630D158), Color(0xFF5FE08C))
        } else {
            ToneColors(Color(0x1A12B76A), Color(0xFF0B7C47))
        }
        PillTone.Warning -> if (dark) {
            ToneColors(Color(0x26FF9500), Color(0xFFFFB65C))
        } else {
            ToneColors(Color(0x1AFF9500), Color(0xFFA35F00))
        }
        PillTone.Error -> if (dark) {
            ToneColors(Color(0x26FF6961), Color(0xFFFF9A94))
        } else {
            ToneColors(Color(0x1AFF3B30), Color(0xFFC0271F))
        }
        PillTone.Info -> ToneColors(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        PillTone.Neutral -> ToneColors(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    tone: PillTone,
    modifier: Modifier = Modifier,
    dot: Boolean = true,
) {
    val colors = toneColors(tone)
    val bg by animateColorAsState(colors.bg, tween(220), label = "pillBg")
    val fg by animateColorAsState(colors.fg, tween(220), label = "pillFg")
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = bg,
        contentColor = fg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dot) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(fg),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** 验证码胶囊：点一下复制，按住轻微下沉。 */
@Composable
fun CodeChip(code: String, onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val (interaction, scaleMod) = rememberPressScale(0.95f)
    Surface(
        modifier = modifier.then(scaleMod),
        onClick = onCopy,
        interactionSource = interaction,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "验证码", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(5.dp))
            Text(
                text = code,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}

// ---------------- 头像 ----------------

@Composable
fun SenderAvatar(sender: String, size: Dp = 42.dp, modifier: Modifier = Modifier) {
    val color = AvatarPalette[(sender.hashCode() and Int.MAX_VALUE) % AvatarPalette.size]
    val initial = TextFormat.senderInitial(sender)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(color.copy(alpha = if (isSystemInDarkTheme()) 0.26f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---------------- 卡片 ----------------

/** 卡片：22dp 圆角、纯白、无描边；可点击时轻微按压缩放。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surface
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (pressed) 0.98f else 1f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.88f),
            label = "cardScale",
        )
        Surface(
            modifier = modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            onClick = onClick,
            interactionSource = interaction,
            shape = shape,
            color = color,
        ) {
            Box(Modifier.padding(15.dp).animateContentSize(tween(240))) { content() }
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color) {
            Box(Modifier.padding(15.dp)) { content() }
        }
    }
}

@Composable
fun StatCard(
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val target = value.toIntOrNull()
    val animated by animateIntAsState(
        targetValue = target ?: 0,
        animationSpec = tween(durationMillis = 420),
        label = "statValue",
    )
    val display = if (target != null) animated.toString() else value
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 15.dp, vertical = 13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(tint),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = display,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ---------------- 标题与设置行 ----------------

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(
        targetValue = if (pressed && onClick != null) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(120),
        label = "rowBg",
    )
    val scaleMod = Modifier.pressScale(pressed, pressedScale = 0.985f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .then(scaleMod)
            .then(
                if (onClick != null) {
                    Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 2.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** 顶部区域：大字标题 + 说明 + 右侧操作 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
        }
    }
}
