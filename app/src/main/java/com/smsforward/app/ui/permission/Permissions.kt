package com.smsforward.app.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

data class AppPermissions(
    val smsGranted: Boolean,
    val notificationGranted: Boolean,
    /** 用户拒绝且系统不再弹窗（“不再询问”/被策略限制），只能去系统设置里开 */
    val smsBlocked: Boolean,
    val notificationBlocked: Boolean,
    val requestSms: () -> Unit,
    val requestNotification: () -> Unit,
    val openSettings: () -> Unit,
    val openNotificationSettings: () -> Unit,
)

private fun hasSms(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

private fun hasNotification(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 短信/通知权限状态。回到前台会自动重新检查，用户去系统设置授权后无需重启。
 * 同时区分“还没问过”和“被永久拒绝”，后者直接引导去系统设置，避免点了没反应。
 */
@Composable
fun rememberAppPermissions(): AppPermissions {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var smsGranted by remember { mutableStateOf(hasSms(context)) }
    var notificationGranted by remember { mutableStateOf(hasNotification(context)) }
    var askedSms by remember { mutableStateOf(false) }
    var askedNotification by remember { mutableStateOf(false) }

    val smsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        askedSms = true
        smsGranted = hasSms(context)
        notificationGranted = hasNotification(context)
    }
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        askedNotification = true
        notificationGranted = hasNotification(context)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                smsGranted = hasSms(context)
                notificationGranted = hasNotification(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val smsBlocked = askedSms && !smsGranted && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_SMS)

    val notificationBlocked = askedNotification && !notificationGranted &&
        activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        )

    return AppPermissions(
        smsGranted = smsGranted,
        notificationGranted = notificationGranted,
        smsBlocked = smsBlocked,
        notificationBlocked = notificationBlocked,
        requestSms = {
            askedSms = true
            smsLauncher.launch(
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
            )
        },
        requestNotification = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                askedNotification = true
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        openSettings = {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
        openNotificationSettings = {
            runCatching {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null))
                }
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        },
    )
}
