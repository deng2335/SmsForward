package com.smsforward.app.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smsforward.app.data.Graph
import com.smsforward.app.data.model.SmsSource
import kotlinx.coroutines.launch

/**
 * 清单里静态注册的短信接收器：即使 App 没在前台、监听服务没开，也能收到新短信并转发。
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val items = SmsIntentParser.parse(intent)
        if (items.isEmpty()) return

        // 广播处理有 10 秒限制，用 goAsync 把入库/转发放到后台线程
        val pending = goAsync()
        val appContext = context.applicationContext
        Graph.init(appContext)
        Graph.appScope.launch {
            try {
                Graph.smsRepository.ingest(items, SmsSource.BROADCAST)
            } catch (t: Throwable) {
                android.util.Log.w("SmsReceiver", "ingest failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
