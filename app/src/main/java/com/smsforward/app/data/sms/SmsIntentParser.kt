package com.smsforward.app.data.sms

import android.content.Intent
import android.provider.Telephony
import com.smsforward.app.data.model.IncomingSms

/**
 * 解析 SMS_RECEIVED 广播。
 *
 * 一条长短信会被拆成多个 PDU，这里按发件人拼接成完整正文，避免转发出去是半截话。
 */
object SmsIntentParser {

    private const val EXTRA_SUBSCRIPTION = "subscription"

    fun parse(intent: Intent): List<IncomingSms> {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return emptyList()

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return emptyList()
        if (parts.isEmpty()) return emptyList()

        val builder = LinkedHashMap<String, StringBuilder>()
        var latest = 0L

        for (raw in parts) {
            val part = raw ?: continue
            val sender = part.originatingAddress?.takeIf { it.isNotBlank() } ?: "未知号码"
            val body = part.messageBody.orEmpty()
            if (body.isEmpty()) continue
            builder.getOrPut(sender) { StringBuilder() }.append(body)
            latest = maxOf(latest, part.timestampMillis)
        }

        if (builder.isEmpty()) return emptyList()

        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION, -1).takeIf { it > 0 }
        val timestamp = if (latest > 0) latest else System.currentTimeMillis()

        return builder.map { (sender, text) ->
            IncomingSms(
                sender = sender,
                body = text.toString(),
                timestamp = timestamp,
                subscriptionId = subscriptionId,
            )
        }
    }
}
