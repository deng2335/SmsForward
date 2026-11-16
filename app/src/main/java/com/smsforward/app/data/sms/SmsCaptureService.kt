package com.smsforward.app.data.sms

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Telephony
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.smsforward.app.MainActivity
import com.smsforward.app.R
import com.smsforward.app.data.Graph
import com.smsforward.app.data.model.SmsSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 常驻监听服务：
 * 1. 动态注册 SMS_RECEIVED（动态注册不受 Android 8+ 隐式广播限制）；
 * 2. ContentObserver 盯住短信库，广播缺失时也能补上；
 * 3. 前台通知让系统不轻易杀掉进程。
 */
class SmsCaptureService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var syncJob: Job? = null
    private var pollJob: Job? = null
    private var observer: SmsObserver? = null
    private var receiverRegistered = false

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val items = SmsIntentParser.parse(intent ?: return)
            if (items.isEmpty()) return
            Graph.appScope.launch {
                runCatching { Graph.smsRepository.ingest(items, SmsSource.BROADCAST) }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Graph.init(applicationContext)
        createChannel()
        startForegroundSafely()
        registerReceiverSafely()
        registerObserver()
        startSafetyPoll()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Graph.settings.setServiceEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        syncJob?.cancel()
        scope.cancel()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(smsReceiver) }
            receiverRegistered = false
        }
        observer?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        observer = null
        _isRunning.value = false
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()
        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else -> 0
        }
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        }.onFailure {
            // Android 12+ 若从后台被拉起会抛异常，此时退化为普通服务，接收器仍然有效
            android.util.Log.w(TAG, "startForeground failed", it)
        }
    }

    private fun registerReceiverSafely() {
        if (!SmsReader(this).canReceive()) return
        val filter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        filter.priority = 999
        runCatching {
            ContextCompat.registerReceiver(
                this,
                smsReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiverRegistered = true
        }.onFailure { android.util.Log.w(TAG, "registerReceiver failed", it) }
    }

    private fun registerObserver() {
        if (!SmsReader(this).hasPermission()) return
        val obs = SmsObserver { scheduleSync() }
        runCatching {
            contentResolver.registerContentObserver(Uri.parse("content://sms"), true, obs)
            observer = obs
        }.onFailure { android.util.Log.w(TAG, "registerObserver failed", it) }
    }

    /** 短信库写入会有多次回调，做 700ms 防抖再同步。 */
    private fun scheduleSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(700)
            runCatching { Graph.smsRepository.syncInbox(limit = 50, source = SmsSource.OBSERVER) }
        }
    }

    /**
     * 兜底轮询：部分 ROM（尤其 Android 10 的小米/华为）会静默吞掉短信广播，
     * ContentObserver 也可能不可靠，这里每 90 秒对齐一次短信库，保证最终不漏。
     */
    private fun startSafetyPoll() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                if (!isActive) break
                if (!SmsReader(this@SmsCaptureService).hasPermission()) continue
                runCatching { Graph.smsRepository.syncInbox(limit = 30, source = SmsSource.OBSERVER) }
            }
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, SmsCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle(getString(R.string.app_name) + "正在监听")
            .setContentText("收到短信后按规则自动转发")
            .setContentIntent(openApp)
            .addAction(0, "停止监听", stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_listener_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_listener_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "SmsCaptureService"
        private const val CHANNEL_ID = "sms_listener"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 90_000L
        const val ACTION_STOP = "com.smsforward.app.action.STOP_CAPTURE"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SmsCaptureService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SmsCaptureService::class.java))
        }
    }
}
