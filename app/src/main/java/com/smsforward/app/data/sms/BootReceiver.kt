package com.smsforward.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smsforward.app.data.Graph

/** 开机或应用升级后，如果用户开着监听就自动恢复。 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext
        Graph.init(app)
        if (!Graph.settings.bootStart.value) return
        if (!Graph.settings.serviceEnabled.value) return
        if (!SmsReader(app).hasPermission()) return
        runCatching { SmsCaptureService.start(app) }
    }
}
