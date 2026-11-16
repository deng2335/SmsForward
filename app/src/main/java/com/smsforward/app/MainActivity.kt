package com.smsforward.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.smsforward.app.data.Graph
import com.smsforward.app.data.sms.SmsReader
import com.smsforward.app.ui.AppRoot
import com.smsforward.app.ui.AppViewModel
import com.smsforward.app.ui.theme.SmsForwardTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmsForwardTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // 用户回到了前台：确保监听服务在跑（受设置开关控制）
        (application as? SmsForwardApp)?.ensureCaptureService()

        // 部分 ROM（尤其 Android 10）后台广播不可靠，回到前台时补一次增量同步
        if (SmsReader(this).hasPermission()) {
            Graph.appScope.launch {
                runCatching { Graph.smsRepository.syncInbox(limit = 100) }
            }
        }
    }
}
