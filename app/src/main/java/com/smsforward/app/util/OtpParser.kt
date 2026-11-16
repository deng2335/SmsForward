package com.smsforward.app.util

/**
 * 验证码识别：不是“见数字就取”，而是按上下文打分 + 排除。
 *
 * 正向条件（任一条即可成为候选）：
 *  - 数字前面 24 字内出现强关键词：验证码 / 校验码 / 动态码 / 确认码 / 动态密码 / 短信密码 / 登录码 / 授权码 /
 *    verification code / otp / passcode / one-time code，且离数字越近分越高
 *  - 数字后面 16 字内出现强关键词（“123456 是您的验证码”这种倒装句）
 *  - 没有强关键词时，前面出现弱语境（验证 / 登录 / 注册 / 激活 / 绑定 / 确认 / 授权 / 身份）
 *
 * 负向条件（直接排除，或压低分数）：
 *  - 金额语境：余额 / 金额 / 转账 / 汇款 / 消费 / 支付 / 付款 / 收款 / 收入 / 支出 / 额度 / 积分 / 订单 /
 *    流水 / 尾号 / 卡号 / 账号 / 手机号 / 电话 / 热线 / 费用 / 手续费 / 利息 / 还款 / 账单 / 到账
 *  - 数字后面紧跟 元 / 块 / 万 / 美元 / 人民币 / % / 折 / 年 / 月 / 日 等量词
 *  - 千分位/小数点（1,200.00）、打码号码（****8000）、URL 里的数字
 *  - 金额词比验证码词更靠近数字时，判定为金额而不是验证码
 *
 * 整条短信只有一个 4-8 位数字时（很多银行的纯数字验证码短信），直接采纳。
 */
object OtpParser {

    private val BARE_NUMBER = Regex("^\\d{4,8}$")
    private val NUMBER = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")

    // 强关键词：出现即高度可信
    private val STRONG_KEYWORDS = listOf(
        "验证码", "校验码", "动态码", "确认码", "动态密码", "短信密码",
        "登录码", "授权码", "手机验证码", "验证代码",
        "verification code", "verification pin", "otp", "passcode",
        "one-time code", "auth code", "confirm code",
    )

    // 弱语境：只能配合其他条件，单独不足以判定
    private val WEAK_KEYWORDS = listOf(
        "验证", "校验", "动态", "确认", "登录", "注册", "绑定", "激活", "授权", "身份",
    )

    // 金额/账号/单据语境：出现时强烈提示“这不是验证码”
    private val MONEY_KEYWORDS = listOf(
        "余额", "金额", "转账", "汇款", "消费", "支付", "付款", "收款", "收入", "支出",
        "额度", "积分", "订单", "流水", "尾号", "卡号", "账号", "手机号", "电话", "热线",
        "费用", "手续费", "利息", "还款", "账单", "到账", "汇入", "汇出",
    )

    private val UNIT_AFTER = listOf(
        "元", "块", "万", "美元", "人民币", "港币", "欧元", "%", "％", "折", "年", "月", "日",
    )

    private const val TERMINATORS = "。！？!?；;\n"
    private const val PREV_WINDOW = 24
    private const val NEXT_WINDOW = 16
    private const val MIN_SCORE = 6

    fun extract(body: String): String? {
        if (body.isBlank()) return null
        val text = normalize(body)
        val trimmed = text.trim()
        if (BARE_NUMBER.matches(trimmed)) return trimmed
        // “【银行】654321” 这种只有签名前缀 + 纯数字的短信
        val afterBracket = stripBracketPrefix(trimmed).trim().trim(' ', '，', ',', '。', '.', '：', ':')
        if (BARE_NUMBER.matches(afterBracket)) return afterBracket

        var bestCode: String? = null
        var bestScore = 0

        for (match in NUMBER.findAll(text)) {
            val code = match.value
            val start = match.range.first
            val end = match.range.last + 1

            if (isPartOfDecimal(text, start, end)) continue
            if (isMasked(text, start)) continue
            if (isInsideUrl(text, start)) continue

            val prev = prevSegment(text, start)
            val next = nextSegment(text, end)

            val strongDist = lastDistance(prev, STRONG_KEYWORDS)
            val moneyDist = lastDistance(prev, MONEY_KEYWORDS)
            val nextStrongDist = firstDistance(next, STRONG_KEYWORDS)
            val nextMoneyDist = firstDistance(next, MONEY_KEYWORDS)

            // 金额 / 账号语境：没有验证码词，或金额词更靠近数字 → 不是验证码
            if (strongDist < 0 && nextStrongDist < 0) {
                if (moneyDist >= 0 || nextMoneyDist in 0..8) continue
            }
            if (strongDist >= 0 && moneyDist >= 0 && moneyDist < strongDist) continue
            if (strongDist < 0 && nextStrongDist >= 0 && moneyDist >= 0 && moneyDist < nextStrongDist) continue

            var score = when {
                strongDist >= 0 -> 12 - minOf(strongDist, 8)
                nextStrongDist >= 0 -> 9
                else -> {
                    val weakDist = lastDistance(prev, WEAK_KEYWORDS)
                    if (weakDist < 0) continue
                    5
                }
            }

            score += lengthBonus(code)
            if (followedByUnit(text, end)) score -= 5
            if (score < MIN_SCORE) continue

            if (score > bestScore) {
                bestScore = score
                bestCode = code
            }
        }
        return bestCode
    }

    fun containsOtp(body: String): Boolean = extract(body) != null

    // ---------------- 内部工具 ----------------

    private fun lengthBonus(code: String): Int = when (code.length) {
        6 -> 3
        8 -> 2
        else -> 1
    }

    /** 去掉开头的【签名】/ [签名] / （签名） */
    private fun stripBracketPrefix(text: String): String {
        val pairs = listOf('【' to '】', '[' to ']', '（' to '）', '(' to ')')
        for ((open, close) in pairs) {
            if (text.startsWith(open)) {
                val end = text.indexOf(close)
                if (end > 0) return text.substring(end + 1)
            }
        }
        return text
    }

    private fun isPartOfDecimal(text: String, start: Int, end: Int): Boolean {
        val before = text.getOrNull(start - 1)
        val before2 = text.getOrNull(start - 2)
        if ((before == ',' || before == '.') && before2?.isDigit() == true) return true
        val after = text.getOrNull(end)
        val after2 = text.getOrNull(end + 1)
        if ((after == ',' || after == '.') && after2?.isDigit() == true) return true
        return false
    }

    private fun isMasked(text: String, start: Int): Boolean =
        text.getOrNull(start - 1) == '*'

    private fun isInsideUrl(text: String, start: Int): Boolean {
        val from = maxOf(0, start - 20)
        val prev = text.substring(from, start).lowercase()
        return prev.contains("http") || prev.contains("www.") || prev.contains("://")
    }

    private fun followedByUnit(text: String, end: Int): Boolean {
        val tail = text.substring(end, minOf(text.length, end + 4))
        return UNIT_AFTER.any { tail.startsWith(it) }
    }

    /** 取数字前面同一句里最多 24 个字（遇到句号/问号/换行就截断）。 */
    private fun prevSegment(text: String, start: Int): String {
        val raw = text.substring(maxOf(0, start - PREV_WINDOW), start)
        val cut = raw.lastIndexOfAny(TERMINATORS.toCharArray())
        return if (cut >= 0) raw.substring(cut + 1) else raw
    }

    private fun nextSegment(text: String, end: Int): String {
        val raw = text.substring(end, minOf(text.length, end + NEXT_WINDOW))
        val cut = raw.indexOfFirst { it in TERMINATORS }
        return if (cut >= 0) raw.substring(0, cut) else raw
    }

    /** 关键词离数字的最近距离；没有则 -1。英文关键词要求词边界，避免 shipping 里的 pin。 */
    private fun lastDistance(text: String, keywords: List<String>): Int {
        val lower = text.lowercase()
        var best = -1
        for (keyword in keywords) {
            val idx = lastIndexOfKeyword(lower, keyword)
            if (idx < 0) continue
            val distance = text.length - (idx + keyword.length)
            if (best < 0 || distance < best) best = distance
        }
        return best
    }

    private fun firstDistance(text: String, keywords: List<String>): Int {
        val lower = text.lowercase()
        var best = -1
        for (keyword in keywords) {
            val idx = firstIndexOfKeyword(lower, keyword)
            if (idx < 0) continue
            if (best < 0 || idx < best) best = idx
        }
        return best
    }

    private fun lastIndexOfKeyword(text: String, keyword: String): Int {
        var idx = text.lastIndexOf(keyword)
        while (idx >= 0) {
            if (hasBoundary(text, idx, keyword)) return idx
            idx = text.lastIndexOf(keyword, idx - 1)
        }
        return -1
    }

    private fun firstIndexOfKeyword(text: String, keyword: String): Int {
        var idx = text.indexOf(keyword)
        while (idx >= 0) {
            if (hasBoundary(text, idx, keyword)) return idx
            idx = text.indexOf(keyword, idx + 1)
        }
        return -1
    }

    private fun hasBoundary(text: String, idx: Int, keyword: String): Boolean {
        if (keyword.any { it.code >= 128 }) return true // 中文关键词不需要词边界
        val before = text.getOrNull(idx - 1)
        val after = text.getOrNull(idx + keyword.length)
        return (before == null || !before.isLetterOrDigit()) &&
            (after == null || !after.isLetterOrDigit())
    }

    /** 全角数字转半角，去掉零宽字符，统一空格。 */
    private fun normalize(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            when {
                ch in '０'..'９' -> append('0' + (ch - '０'))
                ch == '\u00A0' || ch == '\u3000' -> append(' ')
                ch == '\u200B' || ch == '\uFEFF' -> Unit
                else -> append(ch)
            }
        }
    }
}
