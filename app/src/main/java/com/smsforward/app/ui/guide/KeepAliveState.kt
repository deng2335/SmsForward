package com.smsforward.app.ui.guide

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** 保活检测：全部由系统状态推导，不需要用户手动勾选。 */
object KeepAliveCheck {

    /** 「最近同步」超过这个时间就认为后台可能被冻结 */
    const val FRESH_WINDOW_MS = 5 * 60 * 1000L

    /** 参与自动检测的项数（自启动白名单、应用速冻没有系统接口，不在此列） */
    const val TOTAL = 5

    fun isFresh(lastSyncAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        lastSyncAt > 0 && now - lastSyncAt <= FRESH_WINDOW_MS

    fun passedCount(
        smsPermission: Boolean,
        notificationPermission: Boolean,
        batteryUnrestricted: Boolean,
        serviceRunning: Boolean,
        lastSyncAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Int = listOf(
        smsPermission,
        notificationPermission,
        batteryUnrestricted,
        serviceRunning,
        isFresh(lastSyncAt, now),
    ).count { it }
}

/** 电池优化白名单状态，回到前台自动重新检查。 */
@Composable
fun rememberBatteryOptimizationState(): Boolean {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var ignoring by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, powerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ignoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return ignoring
}

/** 系统省电模式状态，随广播实时更新。 */
@Composable
fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val powerManager = remember { context.getSystemService(PowerManager::class.java) }
    var powerSave by remember { mutableStateOf(powerManager?.isPowerSaveMode == true) }
    DisposableEffect(powerManager) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                powerSave = powerManager?.isPowerSaveMode == true
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess
        onDispose {
            if (registered) runCatching { context.unregisterReceiver(receiver) }
        }
    }
    return powerSave
}
