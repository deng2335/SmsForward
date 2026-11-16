package com.smsforward.app

import android.app.Application
import com.smsforward.app.data.Graph
import com.smsforward.app.data.sms.SmsCaptureService
import com.smsforward.app.data.sms.SmsReader

class SmsForwardApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }

    /**
     * 冷启动时如果用户开启了后台监听并已授权，就把监听服务拉起来。
     * 必须在界面可见时调用（Android 12+ 禁止后台直接启动前台服务）。
     */
    fun ensureCaptureService() {
        if (!Graph.settings.serviceEnabled.value) return
        val reader = SmsReader(this)
        if (!reader.hasPermission()) return
        runCatching { SmsCaptureService.start(this) }
    }
}
