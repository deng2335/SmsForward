package com.smsforward.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.smsforward.app.data.model.ForwardState
import com.smsforward.app.data.model.IncomingSms
import com.smsforward.app.data.model.SmsSource
import com.smsforward.app.data.model.Dedupe
import com.smsforward.app.util.OtpParser

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["dedupeKey"], unique = true),
        Index(value = ["receivedAt"]),
        Index(value = ["forwardState"]),
    ],
)
data class SmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 发件人号码或名称 */
    val sender: String,
    /** 短信正文（多段长短信已拼接） */
    val body: String,
    /** 收到时间（毫秒） */
    val receivedAt: Long,
    /** 从正文里识别出的验证码，没有则为空 */
    val code: String? = null,
    /** 去重指纹 */
    val dedupeKey: String,
    /** 系统短信库 _id，用于增量同步时跳过已有记录 */
    val systemId: Long? = null,
    /** 系统 subscriptionId（双卡时用来区分来源 SIM） */
    val subscriptionId: Int? = null,
    val forwardState: Int = ForwardState.PENDING,
    /** 转发结果说明，例如“已推送到 2 个渠道”或失败原因 */
    val forwardNote: String? = null,
    /** 来源：broadcast / observer / import */
    val source: String = SmsSource.BROADCAST,
    val isRead: Boolean = true,
)

fun IncomingSms.toEntity(source: String): SmsEntity {
    val ts = if (timestamp > 0) timestamp else System.currentTimeMillis()
    val text = body.trim()
    return SmsEntity(
        sender = sender.ifBlank { "未知号码" },
        body = text,
        receivedAt = ts,
        code = OtpParser.extract(text),
        dedupeKey = Dedupe.key(sender, text, ts),
        systemId = systemId,
        subscriptionId = subscriptionId,
        forwardState = ForwardState.PENDING,
        source = source,
        isRead = read,
    )
}
