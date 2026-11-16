package com.smsforward.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.smsforward.app.data.entity.ChannelEntity
import com.smsforward.app.data.entity.ProcessedSmsEntity
import com.smsforward.app.data.entity.RuleEntity
import com.smsforward.app.data.entity.SmsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {

    @Query("SELECT * FROM messages ORDER BY receivedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<SmsEntity>>

    @Query("SELECT * FROM messages WHERE forwardState = :state ORDER BY receivedAt DESC LIMIT :limit")
    suspend fun byState(state: Int, limit: Int = 30): List<SmsEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SmsEntity>): List<Long>

    @Query("SELECT MAX(receivedAt) FROM messages")
    suspend fun maxReceivedAt(): Long?

    /** 同一发件人 + 同一正文在时间窗内是否已存在，用于跨分钟边界的重复判定。 */
    @Query(
        "SELECT COUNT(*) FROM messages " +
            "WHERE sender = :sender AND body = :body AND receivedAt BETWEEN :from AND :to",
    )
    suspend fun countSimilar(sender: String, body: String, from: Long, to: Long): Int

    @Query("SELECT systemId FROM messages WHERE systemId IS NOT NULL")
    suspend fun knownSystemIds(): List<Long>

    @Query("SELECT dedupeKey FROM messages")
    suspend fun allDedupeKeys(): List<String>

    @Query("UPDATE messages SET forwardState = :state, forwardNote = :note WHERE id = :id")
    suspend fun updateForward(id: Long, state: Int, note: String?)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages")
    suspend fun clear()

    @Query("DELETE FROM messages WHERE forwardState = :state")
    suspend fun deleteByState(state: Int)

    @Query("SELECT COUNT(*) FROM messages")
    fun observeCount(): Flow<Int>
}

@Dao
interface RuleDao {

    @Query("SELECT * FROM rules ORDER BY priority DESC, id ASC")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun enabledRules(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY priority DESC, id ASC")
    suspend fun allRules(): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun byId(id: Long): RuleEntity?

    @Upsert
    suspend fun upsert(rule: RuleEntity)

    @Delete
    suspend fun delete(rule: RuleEntity)
}

@Dao
interface ChannelDao {
    @Query("SELECT * FROM channels ORDER BY id ASC")
    fun observeAll(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels ORDER BY id ASC")
    suspend fun all(): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun byId(id: Long): ChannelEntity?

    @Upsert
    suspend fun upsert(channel: ChannelEntity)

    @Delete
    suspend fun delete(channel: ChannelEntity)
}

/**
 * 已处理短信台账：指纹 + 处理时间。
 * 只在“首次见到”时写入；清空短信记录不会动它。
 */
@Dao
interface ProcessedDao {

    @Query("SELECT fingerprint FROM processed_sms WHERE fingerprint IN (:fingerprints)")
    suspend fun existing(fingerprints: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ProcessedSmsEntity>)

    @Query("SELECT COUNT(*) FROM processed_sms")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM processed_sms")
    suspend fun count(): Int

    @Query("DELETE FROM processed_sms WHERE processedAt < :before")
    suspend fun pruneBefore(before: Long)

    @Query("DELETE FROM processed_sms")
    suspend fun clear()
}
