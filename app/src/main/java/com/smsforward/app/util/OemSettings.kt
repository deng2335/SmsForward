package com.smsforward.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * 国产 ROM 的自启动 / 后台管理页面没有统一 API，只能按包名逐个尝试。
 * 失败时调用方回退到应用详情页，不会崩。
 */
object OemSettings {

    private val autoStartCandidates = listOf(
        // realme UI / ColorOS
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        ),
        ComponentName(
            "com.coloros.safecenter",
            "com.coloros.safecenter.startupapp.StartupAppListActivity",
        ),
        ComponentName(
            "com.oplus.safecenter",
            "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        ),
        ComponentName(
            "com.oplus.safecenter",
            "com.oplus.safecenter.startupapp.StartupAppListActivity",
        ),
        // 其他常见 ROM，顺手支持
        ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        ),
        ComponentName(
            "com.huawei.systemmanager",
            "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        ),
        ComponentName(
            "com.vivo.permissionmanager",
            "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        ),
    )

    /** 打开系统的「自启动管理」；所有候选都失败则返回 false。 */
    fun openAutoStartSettings(context: Context): Boolean {
        for (component in autoStartCandidates) {
            val intent = Intent().setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val started = runCatching { context.startActivity(intent) }.isSuccess
            if (started) return true
        }
        return false
    }

    /** 打开电池优化白名单列表（部分 ROM 有，没有就回退到应用详情）。 */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        val direct = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(direct) }.isSuccess) return true
        return openAppDetails(context)
    }

    fun openAppDetails(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}
