package com.smsforward.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.smsforward.app.data.model.ForwardState

/** 转发状态 → 颜色标签，UI 只关心展示。 */
@Composable
fun ForwardStatusPill(state: Int, modifier: Modifier = Modifier) {
    val tone = when (state) {
        ForwardState.SENT -> PillTone.Success
        ForwardState.PARTIAL -> PillTone.Warning
        ForwardState.FAILED -> PillTone.Error
        ForwardState.SKIPPED -> PillTone.Neutral
        else -> PillTone.Info
    }
    StatusPill(text = ForwardState.label(state), tone = tone, modifier = modifier)
}
