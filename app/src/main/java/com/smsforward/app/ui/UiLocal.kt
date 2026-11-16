package com.smsforward.app.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** 全局轻提示（Snackbar），由 AppRoot 提供，任意子界面可以直接调用。 */
val LocalNotifier = staticCompositionLocalOf<(String) -> Unit> { {} }
