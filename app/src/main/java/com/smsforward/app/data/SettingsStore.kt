package com.smsforward.app.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 轻量设置存储，全部用 StateFlow 暴露给 Compose。 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("sms_forward_settings", Context.MODE_PRIVATE)

    private val _serviceEnabled = MutableStateFlow(prefs.getBoolean(KEY_SERVICE, true))
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

    private val _bootStart = MutableStateFlow(prefs.getBoolean(KEY_BOOT, true))
    val bootStart: StateFlow<Boolean> = _bootStart.asStateFlow()

    private val _onlyOtp = MutableStateFlow(prefs.getBoolean(KEY_ONLY_OTP, false))
    val onlyOtp: StateFlow<Boolean> = _onlyOtp.asStateFlow()

    /** 最近一次成功同步短信库的时间，用来判断后台是否还活着 */
    private val _lastSyncAt = MutableStateFlow(prefs.getLong(KEY_LAST_SYNC, 0L))
    val lastSyncAt: StateFlow<Long> = _lastSyncAt.asStateFlow()

    /** 自启动管理页当前选中的品牌 key */
    private val _guideBrand = MutableStateFlow(prefs.getString(KEY_GUIDE_BRAND, null))
    val guideBrand: StateFlow<String?> = _guideBrand.asStateFlow()

    fun markSynced(at: Long = System.currentTimeMillis()) {
        _lastSyncAt.value = at
        prefs.edit { putLong(KEY_LAST_SYNC, at) }
    }

    fun setGuideBrand(key: String) {
        _guideBrand.value = key
        prefs.edit { putString(KEY_GUIDE_BRAND, key) }
    }

    fun setServiceEnabled(enabled: Boolean) {
        _serviceEnabled.value = enabled
        prefs.edit { putBoolean(KEY_SERVICE, enabled) }
    }

    fun setBootStart(enabled: Boolean) {
        _bootStart.value = enabled
        prefs.edit { putBoolean(KEY_BOOT, enabled) }
    }

    fun setOnlyOtp(enabled: Boolean) {
        _onlyOtp.value = enabled
        prefs.edit { putBoolean(KEY_ONLY_OTP, enabled) }
    }

    private companion object {
        const val KEY_SERVICE = "service_enabled"
        const val KEY_BOOT = "boot_start"
        const val KEY_ONLY_OTP = "only_otp"
        const val KEY_LAST_SYNC = "last_sync_at"
        const val KEY_GUIDE_BRAND = "guide_brand"
    }
}
