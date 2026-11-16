package com.smsforward.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smsforward.app.data.model.MatchType

/** 转发规则：什么短信、发给哪些渠道。 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val matchType: Int = MatchType.ALL,
    /** 关键词/发件人/正则的匹配内容；ALL 类型时忽略 */
    val pattern: String = "",
    /** 目标渠道 id，逗号分隔 */
    val targetIds: String = "",
    /** 数字越大越先匹配 */
    val priority: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun targetIdList(): List<Long> =
        targetIds.split(",").mapNotNull { it.trim().toLongOrNull() }.distinct()

    fun summary(): String = when (matchType) {
        MatchType.ALL -> "全部短信"
        MatchType.KEYWORD -> "含关键词：$pattern"
        MatchType.SENDER -> "发件人：$pattern"
        MatchType.REGEX -> "正则：$pattern"
        else -> "自定义"
    }
}
