package com.smsforward.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 时间展示：今天只显示时分，昨天加前缀，跨年显示完整日期。 */
object TextFormat {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
    private val mmddhhmm = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val full = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun messageTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val time = Instant.ofEpochMilli(timestamp).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val date = time.toLocalDate()
        return when {
            date == today -> time.format(hhmm)
            date == today.minusDays(1) -> "昨天 " + time.format(hhmm)
            date.year == today.year -> time.format(mmddhhmm)
            else -> time.format(full)
        }
    }

    fun dateLabel(date: LocalDate): String = when (date) {
        LocalDate.now() -> "今天"
        LocalDate.now().minusDays(1) -> "昨天"
        else -> date.format(DateTimeFormatter.ofPattern("M月d日"))
    }

    fun startOfToday(): Long =
        LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

    /** 11 位手机号分段显示，服务号/短号原样返回。 */
    fun senderLabel(address: String?): String {
        val raw = address?.trim().orEmpty()
        if (raw.isEmpty()) return "未知号码"
        val digits = raw.removePrefix("+86").filter { it.isDigit() }
        return if (digits.length == 11 && digits.startsWith("1")) {
            "${digits.substring(0, 3)} ${digits.substring(3, 7)} ${digits.substring(7)}"
        } else {
            raw
        }
    }

    /** 头像文字：中文发送方取首字，号码取末两位，避免整列都是 “1”。 */
    fun senderInitial(address: String?): String {
        val raw = address?.trim().orEmpty()
        if (raw.isEmpty()) return "?"
        val chinese = raw.firstOrNull { it.code in 0x4E00..0x9FFF }
        if (chinese != null) return chinese.toString()
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.length >= 2 -> digits.takeLast(2)
            digits.isNotEmpty() -> digits
            else -> raw.take(1).uppercase()
        }
    }

    fun bodyPreview(body: String, max: Int = 200): String {
        val oneLine = body.replace(Regex("\\s+"), " ").trim()
        return if (oneLine.length <= max) oneLine else oneLine.take(max) + "…"
    }

    /** 相对时间：刚刚 / N 分钟前 / N 小时前 / 具体时间 */
    fun relativeTime(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        if (timestamp <= 0) return "还没有记录"
        val diff = now - timestamp
        return when {
            diff < 0 -> "刚刚"
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 24 * 3_600_000L -> "${diff / 3_600_000} 小时前"
            else -> messageTime(timestamp, now)
        }
    }
}
