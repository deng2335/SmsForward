package com.smsforward.app.data

import android.content.Context
import com.smsforward.app.data.db.AppDatabase
import com.smsforward.app.data.entity.ProcessedSmsEntity
import com.smsforward.app.data.sms.SmsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 极简手写依赖容器：数据库、仓储、设置都在这里初始化一次。
 * 之后要换成 Hilt 也不会影响业务代码结构。
 */
object Graph {

    lateinit var appContext: Context
        private set

    val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val smsRepository: SmsRepository by lazy {
        SmsRepository(
            context = appContext,
            dao = database.smsDao(),
            ruleDao = database.ruleDao(),
            channelDao = database.channelDao(),
            processedDao = database.processedDao(),
            settings = settings,
            scope = appScope,
        )
    }

    val ruleRepository: RuleRepository by lazy {
        RuleRepository(database.ruleDao(), database.channelDao())
    }

    fun init(context: Context) {
        if (this::appContext.isInitialized) return
        appContext = context.applicationContext
        // 提前触发一次数据库创建，避免首个短信到达时阻塞广播
        appScope.launch {
            runCatching { database.smsDao().maxReceivedAt() }
            backfillProcessedLedger()
        }
    }

    /**
     * 老版本升级上来时台账是空的：用库里已有的短信指纹回填一次，
     * 这样升级后立刻清空记录再重新导入，也不会重复推送。
     */
    private suspend fun backfillProcessedLedger() {
        runCatching {
            val processedDao = database.processedDao()
            if (processedDao.count() > 0) return@runCatching
            val keys = database.smsDao().allDedupeKeys()
            if (keys.isNotEmpty()) {
                processedDao.insertAll(keys.map { ProcessedSmsEntity(it) })
            }
        }
    }
}
