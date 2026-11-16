package com.smsforward.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.smsforward.app.data.model.ChannelType
import org.json.JSONObject

/** 转发目标渠道，config 用 JSON 存各平台自己的字段。 */
@Entity(tableName = "channels", indices = [Index(value = ["type"])])
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: Int = ChannelType.WEBHOOK,
    /** JSON：{"url":"...","token":"...","chatId":"..."} */
    val config: String = "{}",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    private fun json(): JSONObject = runCatching { JSONObject(config) }.getOrDefault(JSONObject())

    fun url(): String = json().optString("url", "")

    fun token(): String = json().optString("token", "")

    fun chatId(): String = json().optString("chatId", "")

    /** 用于列表展示的脱敏摘要 */
    fun targetSummary(): String {
        val raw = when {
            type == ChannelType.TELEGRAM -> "chat ${chatId()}"
            else -> url()
        }
        if (raw.isBlank()) return "未配置"
        val noScheme = raw.substringAfter("://", raw)
        return if (noScheme.length <= 42) noScheme else noScheme.take(40) + "…"
    }

    fun typeLabel(): String = ChannelType.shortLabel(type)
}
