package com.smsforward.app.data

import android.content.Context
import android.util.Log
import com.smsforward.app.data.db.ChannelDao
import com.smsforward.app.data.db.ProcessedDao
import com.smsforward.app.data.db.RuleDao
import com.smsforward.app.data.db.SmsDao
import com.smsforward.app.data.entity.SmsEntity
import com.smsforward.app.data.entity.ProcessedSmsEntity
import com.smsforward.app.data.entity.toEntity
import com.smsforward.app.data.model.ForwardState
import com.smsforward.app.data.model.IncomingSms
import com.smsforward.app.data.model.SmsSource
import com.smsforward.app.data.sms.SmsReader
import com.smsforward.app.forward.ChannelSender
import com.smsforward.app.forward.RuleMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 短信入库 + 去重 + 规则匹配 + 转发，所有入口（广播/观察者/手动同步）都走这里。
 */
class SmsRepository(
    private val context: Context,
    private val dao: SmsDao,
    private val ruleDao: RuleDao,
    private val channelDao: ChannelDao,
    private val processedDao: ProcessedDao,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {

    private val reader = SmsReader(context)
    private val ingestMutex = Mutex()

    fun observeRecent(limit: Int = 500): Flow<List<SmsEntity>> = dao.observeRecent(limit)

    fun observeProcessedCount(): Flow<Int> = processedDao.observeCount()

    /**
     * 写入短信。返回真正入库（非重复）的条数，并同步完成转发。
     *
     * 去重做了三层：
     * 1. 同一批次内按“发件人 + 正文”去重；
     * 2. 与库里 ±5 分钟内同发件人同正文的记录比对（广播时间与短信库时间常差几秒，可能跨分钟边界）；
     * 3. dedupeKey 唯一索引兜底。
     * 用 Mutex 串行化，避免广播和短信库观察者同时写入时互相看不见。
     */
    suspend fun ingest(incoming: List<IncomingSms>, source: String): Int {
        if (incoming.isEmpty()) return 0
        return withContext(Dispatchers.IO) {
            // 只在“查重 + 入库”这一段持锁，网络转发放在锁外，避免一条短信把后续入口堵住
            val inserted = ingestMutex.withLock {
                val candidates = LinkedHashMap<String, IncomingSms>()
                for (item in incoming) {
                    candidates.putIfAbsent("${item.sender.trim()}|${item.body.trim()}", item)
                }
                val entities = candidates.values
                    .map { it.toEntity(source) }
                    .filterNot { entity ->
                        dao.countSimilar(
                            entity.sender,
                            entity.body,
                            entity.receivedAt - DEDUPE_WINDOW_MS,
                            entity.receivedAt + DEDUPE_WINDOW_MS,
                        ) > 0
                    }
                if (entities.isEmpty()) return@withLock emptyList()

                val rowIds = dao.insertAll(entities)
                entities.mapIndexedNotNull { index, entity ->
                    val rowId = rowIds.getOrElse(index) { -1L }
                    if (rowId > 0) entity.copy(id = rowId) else null
                }
            }
            if (inserted.isEmpty()) return@withContext 0

            Log.d(TAG, "ingest ${inserted.size} new sms from $source")

            // 台账里出现过的指纹 = 以前处理过（无论当时转发成功还是没命中规则），
            // 清空记录后重新导入时只入库、不再推送。
            val known = processedDao.existing(inserted.map { it.dedupeKey }).toHashSet()
            val repeated = inserted.filter { it.dedupeKey in known }
            val firstSeen = inserted.filter { it.dedupeKey !in known }

            for (row in repeated) {
                dao.updateForward(row.id, ForwardState.SKIPPED, "这条短信之前已处理过，不重复推送")
            }

            val now = System.currentTimeMillis()
            // 历史短信（手动导入或首次启动扫描出来的旧消息）只入库，不转发，
            // 否则第一次打开 App 会把几周的旧短信全推到渠道里。
            val stale = firstSeen.filter { now - it.receivedAt > FORWARD_WINDOW_MS }
            val fresh = firstSeen.filter { now - it.receivedAt <= FORWARD_WINDOW_MS }
            for (row in stale) {
                dao.updateForward(row.id, ForwardState.SKIPPED, "历史短信，仅入库不转发")
            }

            // 先记账再推送：即使推送失败，也不会在清空记录后被当成新短信重推
            if (firstSeen.isNotEmpty()) {
                processedDao.insertAll(firstSeen.map { ProcessedSmsEntity(it.dedupeKey) })
                processedDao.pruneBefore(now - LEDGER_RETENTION_MS)
            }

            if (fresh.isNotEmpty()) dispatch(fresh)
            settings.markSynced()
            inserted.size
        }
    }

    /** 从系统短信库拉取历史，跳过已经入库的记录（按系统 _id）。 */
    suspend fun syncInbox(limit: Int = 200, source: String = SmsSource.IMPORT): Int =
        withContext(Dispatchers.IO) {
            val known = runCatching { dao.knownSystemIds().toHashSet() }.getOrDefault(hashSetOf())
            val rows = reader.readInbox(limit)
                .filter { it.systemId == null || it.systemId !in known }
            if (rows.isEmpty()) {
                settings.markSynced()
                return@withContext 0
            }
            val inserted = ingest(rows, source)
            settings.markSynced()
            inserted
        }

    /** 重发所有失败记录，用于网络恢复后补发。 */
    suspend fun retryFailed(): Int = withContext(Dispatchers.IO) {
        val failed = dao.byState(ForwardState.FAILED, limit = 30) +
            dao.byState(ForwardState.PARTIAL, limit = 20)
        if (failed.isEmpty()) return@withContext 0
        dispatch(failed)
        failed.size
    }

    private suspend fun dispatch(rows: List<SmsEntity>) {
        val rules = ruleDao.enabledRules()
        val channels = channelDao.all().associateBy { it.id }
        val onlyOtp = settings.onlyOtp.value

        for (row in rows) {
            try {
                if (onlyOtp && row.code.isNullOrBlank()) {
                    dao.updateForward(row.id, ForwardState.SKIPPED, "未包含验证码")
                    continue
                }

                val matched = rules.filter { RuleMatcher.matches(it, row) }
                if (matched.isEmpty()) {
                    dao.updateForward(
                        row.id,
                        ForwardState.SKIPPED,
                        if (rules.isEmpty()) "还没有启用中的转发规则" else "没有规则命中这条短信",
                    )
                    continue
                }

                val targets = matched
                    .flatMap { it.targetIdList() }
                    .distinct()
                    .mapNotNull { channels[it] }
                    .filter { it.enabled }

                if (targets.isEmpty()) {
                    dao.updateForward(row.id, ForwardState.SKIPPED, "规则没有绑定可用渠道")
                    continue
                }

                var okCount = 0
                val failures = mutableListOf<String>()
                for (channel in targets) {
                    val result = ChannelSender.send(channel, row)
                    if (result.ok) {
                        okCount++
                    } else {
                        failures += "${channel.name}：${result.detail}"
                    }
                }

                val state = when {
                    okCount == targets.size -> ForwardState.SENT
                    okCount > 0 -> ForwardState.PARTIAL
                    else -> ForwardState.FAILED
                }
                val note = if (failures.isEmpty()) {
                    "已推送到 ${targets.size} 个渠道"
                } else {
                    "成功 $okCount/${targets.size}；" + failures.joinToString("；")
                }
                dao.updateForward(row.id, state, note)
            } catch (t: Throwable) {
                Log.w(TAG, "dispatch failed for ${row.id}", t)
                runCatching {
                    dao.updateForward(row.id, ForwardState.FAILED, t.message ?: "未知错误")
                }
            }
        }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }

    suspend fun clearAll() = withContext(Dispatchers.IO) { dao.clear() }

    suspend fun deleteForwarded() = withContext(Dispatchers.IO) { dao.deleteByState(ForwardState.SENT) }

    /** 显式重置“已处理”台账；重置后重新导入的历史短信会再次参与转发（危险操作，UI 需二次确认）。 */
    suspend fun resetProcessed() = withContext(Dispatchers.IO) { processedDao.clear() }

    private companion object {
        const val TAG = "SmsRepository"

        /** 只有 30 分钟内收到的短信才会被转发，避免历史短信被补发。 */
        const val FORWARD_WINDOW_MS = 30 * 60 * 1000L

        /** 与库里已有记录的重复判定时间窗（广播与短信库时间可能有几秒差异）。 */
        const val DEDUPE_WINDOW_MS = 5 * 60 * 1000L

        /** 已处理台账保留时长，超过则清理，避免无限增长。 */
        const val LEDGER_RETENTION_MS = 120L * 24 * 60 * 60 * 1000
    }
}
