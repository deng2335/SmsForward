package com.smsforward.app.forward

import com.smsforward.app.data.entity.RuleEntity
import com.smsforward.app.data.entity.SmsEntity
import com.smsforward.app.data.model.MatchType

/** 规则命中判断。多个关键词/发件人用中英文逗号或竖线分隔。 */
object RuleMatcher {

    fun matches(rule: RuleEntity, sms: SmsEntity): Boolean = when (rule.matchType) {
        MatchType.ALL -> true
        MatchType.KEYWORD -> tokens(rule.pattern).any { token ->
            sms.body.contains(token, ignoreCase = true)
        }
        MatchType.SENDER -> tokens(rule.pattern).any { token ->
            val target = normalize(token)
            normalize(sms.sender).contains(target) || sms.sender.contains(token, ignoreCase = true)
        }
        MatchType.REGEX -> runCatching {
            Regex(rule.pattern, RegexOption.IGNORE_CASE).containsMatchIn(sms.body)
        }.getOrDefault(false)
        else -> false
    }

    private fun tokens(pattern: String): List<String> =
        pattern.split(',', '，', '|', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    private fun normalize(raw: String): String =
        raw.trim().removePrefix("+86").filter { it.isDigit() || it == '+' }
}
