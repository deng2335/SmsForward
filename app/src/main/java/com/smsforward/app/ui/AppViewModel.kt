package com.smsforward.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsforward.app.data.Graph
import com.smsforward.app.data.entity.ChannelEntity
import com.smsforward.app.data.entity.RuleEntity
import com.smsforward.app.data.entity.SmsEntity
import com.smsforward.app.data.sms.SmsCaptureService
import com.smsforward.app.data.sms.SmsReader
import com.smsforward.app.forward.ChannelSender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val smsRepository = Graph.smsRepository
    private val ruleRepository = Graph.ruleRepository
    private val settings = Graph.settings

    val messages: StateFlow<List<SmsEntity>> = smsRepository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rules: StateFlow<List<RuleEntity>> = ruleRepository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels: StateFlow<List<ChannelEntity>> = ruleRepository.observeChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已处理短信台账里的条数（用于设置页展示） */
    val processedCount: StateFlow<Int> = smsRepository.observeProcessedCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val serviceRunning: StateFlow<Boolean> = SmsCaptureService.isRunning
    val serviceEnabled: StateFlow<Boolean> = settings.serviceEnabled
    val bootStart: StateFlow<Boolean> = settings.bootStart
    val onlyOtp: StateFlow<Boolean> = settings.onlyOtp
    val lastSyncAt: StateFlow<Long> = settings.lastSyncAt
    val guideBrand: StateFlow<String?> = settings.guideBrand

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun refreshInbox() {
        if (_syncing.value) return
        viewModelScope.launch {
            _syncing.value = true
            val imported = runCatching { smsRepository.syncInbox(limit = 300) }.getOrElse {
                notify("读取短信失败：${it.message ?: "未知错误"}")
                0
            }
            _syncing.value = false
            notify(
                when {
                    imported > 0 -> "已导入 $imported 条短信"
                    !SmsReader(getApplication()).hasPermission() -> "请先授予短信权限"
                    else -> "收件箱没有新短信"
                },
            )
        }
    }

    fun setServiceEnabled(enabled: Boolean) {
        settings.setServiceEnabled(enabled)
        val context = getApplication<Application>()
        if (enabled) {
            if (!SmsReader(context).hasPermission()) {
                notify("请先授予短信权限，再开启后台监听")
                return
            }
            runCatching { SmsCaptureService.start(context) }
                .onFailure { notify("监听启动失败：${it.message}") }
        } else {
            SmsCaptureService.stop(context)
        }
    }

    fun setBootStart(enabled: Boolean) {
        settings.setBootStart(enabled)
    }

    fun setOnlyOtp(enabled: Boolean) {
        settings.setOnlyOtp(enabled)
    }

    fun setGuideBrand(key: String) = settings.setGuideBrand(key)

    fun saveRule(rule: RuleEntity) {
        viewModelScope.launch {
            runCatching { ruleRepository.saveRule(rule) }
                .onSuccess { notify(if (rule.id == 0L) "规则已创建" else "规则已保存") }
                .onFailure { notify("保存失败：${it.message}") }
        }
    }

    fun deleteRule(rule: RuleEntity) {
        viewModelScope.launch {
            runCatching { ruleRepository.deleteRule(rule) }
                .onSuccess { notify("已删除规则「${rule.name}」") }
                .onFailure { notify("删除失败：${it.message}") }
        }
    }

    /**
     * 上移 / 下移规则：把整张列表重排后按位置重新编号（越靠前优先级越高），
     * 这样不用关心旧数据里的 priority 是多少。
     */
    fun moveRule(rule: RuleEntity, up: Boolean) {
        viewModelScope.launch {
            val all = runCatching { ruleRepository.allRules() }.getOrNull() ?: return@launch
            val index = all.indexOfFirst { it.id == rule.id }
            if (index < 0) return@launch
            val target = if (up) index - 1 else index + 1
            if (target !in all.indices) return@launch

            val reordered = all.toMutableList().apply { add(target, removeAt(index)) }
            val changed = reordered.mapIndexedNotNull { position, item ->
                val priority = reordered.size - position
                if (item.priority == priority) null else item.copy(priority = priority)
            }
            changed.forEach { ruleRepository.saveRule(it) }
        }
    }

    fun saveChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            runCatching { ruleRepository.saveChannel(channel) }
                .onSuccess { notify(if (channel.id == 0L) "渠道已添加" else "渠道已保存") }
                .onFailure { notify("保存失败：${it.message}") }
        }
    }

    fun deleteChannel(channel: ChannelEntity) {
        viewModelScope.launch {
            runCatching { ruleRepository.deleteChannel(channel) }
                .onSuccess { notify("已删除渠道「${channel.name}」") }
                .onFailure { notify("删除失败：${it.message}") }
        }
    }

    fun retryFailed() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val count = runCatching { smsRepository.retryFailed() }.getOrElse {
                notify("重试失败：${it.message}")
                0
            }
            _busy.value = false
            notify(if (count > 0) "已重试 $count 条失败记录" else "没有需要重试的记录")
        }
    }

    /** 给渠道发一条测试消息，用来在手机上直接验证 Webhook / Token 配置是否正确。 */
    fun testChannel(channel: ChannelEntity) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val sample = SmsEntity(
                sender = "10086",
                body = "这是一条来自「短信转发」的测试消息，收到说明渠道配置正确。",
                receivedAt = System.currentTimeMillis(),
                dedupeKey = "channel-test-${System.currentTimeMillis()}",
            )
            val result = runCatching {
                withContext(Dispatchers.IO) { ChannelSender.send(channel, sample) }
            }.getOrElse { ChannelSender.Result(false, it.message ?: "未知错误") }
            _busy.value = false
            notify(
                if (result.ok) {
                    "「${channel.name}」测试成功，去接收端确认一下"
                } else {
                    "「${channel.name}」测试失败：${result.detail}"
                },
            )
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            runCatching { smsRepository.delete(id) }
                .onSuccess { notify("已删除这条短信记录") }
        }
    }

    fun deleteForwarded() {
        viewModelScope.launch {
            runCatching { smsRepository.deleteForwarded() }
                .onSuccess { notify("已清理转发成功的记录") }
                .onFailure { notify("清理失败：${it.message}") }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            runCatching { smsRepository.clearAll() }
                .onSuccess { notify("短信记录已清空") }
                .onFailure { notify("清空失败：${it.message}") }
        }
    }

    /** 重置“已处理”台账：之后的重新导入会重新推送（危险，UI 有二次确认） */
    fun resetProcessed() {
        viewModelScope.launch {
            runCatching { smsRepository.resetProcessed() }
                .onSuccess { notify("已重置推送去重记录") }
                .onFailure { notify("重置失败：${it.message}") }
        }
    }

    private fun notify(message: String) {
        _events.tryEmit(message)
    }
}
