package com.smsforward.app.data.model

import java.security.MessageDigest

/** 一条短信的转发状态。PENDING 只在处理过程中短暂存在。 */
object ForwardState {
    const val PENDING = 0
    const val SENT = 1
    const val FAILED = 2
    const val SKIPPED = 3
    const val PARTIAL = 4

    fun label(state: Int): String = when (state) {
        PENDING -> "待转发"
        SENT -> "已转发"
        FAILED -> "转发失败"
        SKIPPED -> "未转发"
        PARTIAL -> "部分成功"
        else -> "未知"
    }
}

/** 目标渠道类型。目前实现 5 种，后续加邮件/飞书只需扩这里 + ChannelSender。 */
object ChannelType {
    const val WEBHOOK = 0
    const val DINGTALK = 1
    const val WECOM = 2
    const val BARK = 3
    const val TELEGRAM = 4
    const val SERVERCHAN = 5

    val all = listOf(WEBHOOK, TELEGRAM, DINGTALK, WECOM, SERVERCHAN, BARK)

    fun label(type: Int): String = when (type) {
        WEBHOOK -> "自定义 Webhook"
        DINGTALK -> "钉钉机器人"
        WECOM -> "企业微信机器人"
        BARK -> "Bark (iOS)"
        TELEGRAM -> "Telegram Bot"
        SERVERCHAN -> "Server酱"
        else -> "未知渠道"
    }

    fun shortLabel(type: Int): String = when (type) {
        WEBHOOK -> "Webhook"
        DINGTALK -> "钉钉"
        WECOM -> "企业微信"
        BARK -> "Bark"
        TELEGRAM -> "Telegram"
        SERVERCHAN -> "Server酱"
        else -> "渠道"
    }

    fun needsToken(type: Int): Boolean = type == TELEGRAM

    fun needsChatId(type: Int): Boolean = type == TELEGRAM
}

/** 规则匹配方式。 */
object MatchType {
    const val ALL = 0
    const val KEYWORD = 1
    const val SENDER = 2
    const val REGEX = 3

    val all = listOf(ALL, KEYWORD, SENDER, REGEX)

    fun label(type: Int): String = when (type) {
        ALL -> "全部短信"
        KEYWORD -> "含关键词"
        SENDER -> "指定发件人"
        REGEX -> "正则匹配"
        else -> "未知"
    }

    fun hint(type: Int): String = when (type) {
        ALL -> "所有收到的短信都会命中"
        KEYWORD -> "多个关键词用逗号分隔，命中任意一个即可"
        SENDER -> "发件人号码/名称，多个用逗号分隔，支持前缀"
        REGEX -> "对短信正文做正则匹配，例如 ^【银行】"
        else -> ""
    }
}

/** 短信入口来源，用于调试排查漏收。 */
object SmsSource {
    const val BROADCAST = "broadcast"
    const val OBSERVER = "observer"
    const val IMPORT = "import"
}

/** 从广播/数据库读出的一条短信。 */
data class IncomingSms(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val systemId: Long? = null,
    /** 系统 subscriptionId（双卡时用于区分来源 SIM） */
    val subscriptionId: Int? = null,
    val read: Boolean = true,
)

/** 去重键：同一条短信可能同时从广播和短信库到达。 */
object Dedupe {
    fun key(sender: String, body: String, timestamp: Long): String {
        val bucket = timestamp / 60_000
        return sha1("${sender.trim()}|${body.trim()}|$bucket")
    }

    private fun sha1(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append("0123456789abcdef"[v shr 4])
                append("0123456789abcdef"[v and 0x0F])
            }
        }
    }
}
