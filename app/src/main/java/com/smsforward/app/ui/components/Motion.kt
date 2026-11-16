package com.smsforward.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 统一按压反馈：按住下沉到 pressedScale，松手用带一点弹性的弹簧回弹。
 * 所有自绘的可点击组件都用它，避免有的缩 0.98、有的缩 0.96、有的不缩。
 */
@Composable
fun Modifier.pressScale(
    pressed: Boolean,
    pressedScale: Float = 0.96f,
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            stiffness = Spring.StiffnessMediumLow,
            dampingRatio = Spring.DampingRatioMediumBouncy,
        ),
        label = "pressScale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 一步拿到 interactionSource + 已带缩放的 modifier，直接喂给 Surface(onClick = ...)。
 *
 * 用法：
 *   val (interaction, scaleMod) = rememberPressScale()
 *   Surface(onClick = ..., interactionSource = interaction, modifier = scaleMod) { ... }
 */
@Composable
fun rememberPressScale(pressedScale: Float = 0.96f): Pair<MutableInteractionSource, Modifier> {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    return interaction to Modifier.pressScale(pressed, pressedScale)
}
