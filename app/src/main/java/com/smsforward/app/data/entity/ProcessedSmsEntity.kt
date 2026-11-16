package com.smsforward.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已处理短信台账：只记指纹和落库时间，不存内容。
 *
 * 作用：清空短信记录后，重新导入收件箱里的旧短信时，用这张表判断“这条以前见过”，
 * 从而只入库、不再推送（无论当时是转发成功还是没命中规则）。
 * 该表不会被“清空短信记录”清除，只能在设置里显式重置。
 */
@Entity(tableName = "processed_sms")
data class ProcessedSmsEntity(
    @PrimaryKey val fingerprint: String,
    val processedAt: Long = System.currentTimeMillis(),
)
