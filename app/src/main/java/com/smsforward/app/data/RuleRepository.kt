package com.smsforward.app.data

import com.smsforward.app.data.db.ChannelDao
import com.smsforward.app.data.db.RuleDao
import com.smsforward.app.data.entity.ChannelEntity
import com.smsforward.app.data.entity.RuleEntity
import kotlinx.coroutines.flow.Flow

/** 规则与渠道的读写。匹配/发送逻辑不在这里，避免职责混在一起。 */
class RuleRepository(
    private val ruleDao: RuleDao,
    private val channelDao: ChannelDao,
) {
    fun observeRules(): Flow<List<RuleEntity>> = ruleDao.observeAll()

    fun observeChannels(): Flow<List<ChannelEntity>> = channelDao.observeAll()

    suspend fun saveRule(rule: RuleEntity) = ruleDao.upsert(rule)

    suspend fun deleteRule(rule: RuleEntity) = ruleDao.delete(rule)

    suspend fun rule(id: Long): RuleEntity? = ruleDao.byId(id)

    suspend fun allRules(): List<RuleEntity> = ruleDao.allRules()

    suspend fun saveChannel(channel: ChannelEntity) = channelDao.upsert(channel)

    suspend fun deleteChannel(channel: ChannelEntity) = channelDao.delete(channel)

    suspend fun channel(id: Long): ChannelEntity? = channelDao.byId(id)

    suspend fun allChannels(): List<ChannelEntity> = channelDao.all()
}
