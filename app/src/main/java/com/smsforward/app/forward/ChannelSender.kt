package com.smsforward.app.forward

import com.smsforward.app.data.entity.ChannelEntity
import com.smsforward.app.data.entity.SmsEntity
import com.smsforward.app.data.model.ChannelType
import com.smsforward.app.util.TextFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 各平台转发实现。只用 HttpURLConnection，不引第三方网络库，APK 更小、离线编译更容易。
 * 返回 ok=true 表示平台明确确认收到；失败原因会落到短信卡片上，方便排查。
 */
object ChannelSender {

    data class Result(val ok: Boolean, val detail: String)

    suspend fun send(channel: ChannelEntity, sms: SmsEntity): Result = withContext(Dispatchers.IO) {
        runCatching {
            when (channel.type) {
                ChannelType.WEBHOOK -> sendWebhook(channel, sms)
                ChannelType.DINGTALK -> sendDingTalk(channel, sms)
                ChannelType.WECOM -> sendWeCom(channel, sms)
                ChannelType.TELEGRAM -> sendTelegram(channel, sms)
                ChannelType.BARK -> sendBark(channel, sms)
                ChannelType.SERVERCHAN -> sendServerChan(channel, sms)
                else -> Result(false, "暂不支持的渠道类型")
            }
        }.getOrElse { error ->
            Result(false, humanError(error))
        }
    }

    // ---------------- 各平台实现 ----------------

    private fun sendWebhook(channel: ChannelEntity, sms: SmsEntity): Result {
        val url = channel.url()
        if (url.isBlank()) return Result(false, "缺少 Webhook 地址")
        val payload = JSONObject()
            .put("title", titleOf(sms))
            .put("from", sms.sender)
            .put("code", sms.code.orEmpty())
            .put("text", sms.body)
            .put("receivedAt", sms.receivedAt)
            .put("content", plainText(sms))
        val response = httpPostJson(url, payload.toString())
        return if (response.code in 200..299) Result(true, "HTTP ${response.code}") else fail(response)
    }

    private fun sendDingTalk(channel: ChannelEntity, sms: SmsEntity): Result {
        val url = channel.url()
        if (url.isBlank()) return Result(false, "缺少钉钉机器人地址")
        val payload = JSONObject()
            .put("msgtype", "text")
            .put("text", JSONObject().put("content", plainText(sms)))
        val response = httpPostJson(url, payload.toString())
        if (response.code !in 200..299) return fail(response)
        val errcode = runCatching { JSONObject(response.body).optInt("errcode", 0) }.getOrDefault(0)
        return if (errcode == 0) Result(true, "ok") else Result(false, "钉钉返回 errcode=$errcode")
    }

    private fun sendWeCom(channel: ChannelEntity, sms: SmsEntity): Result {
        val url = channel.url()
        if (url.isBlank()) return Result(false, "缺少企业微信机器人地址")
        val payload = JSONObject()
            .put("msgtype", "text")
            .put("text", JSONObject().put("content", plainText(sms)))
        val response = httpPostJson(url, payload.toString())
        if (response.code !in 200..299) return fail(response)
        val errcode = runCatching { JSONObject(response.body).optInt("errcode", 0) }.getOrDefault(0)
        return if (errcode == 0) Result(true, "ok") else Result(false, "企业微信返回 errcode=$errcode")
    }

    private fun sendTelegram(channel: ChannelEntity, sms: SmsEntity): Result {
        val token = channel.token()
        val chatId = channel.chatId()
        if (token.isBlank()) return Result(false, "缺少 Bot Token")
        if (chatId.isBlank()) return Result(false, "缺少 Chat ID")
        val payload = JSONObject()
            .put("chat_id", chatId)
            .put("text", plainText(sms))
            .put("disable_web_page_preview", true)
        val response = httpPostJson("https://api.telegram.org/bot$token/sendMessage", payload.toString())
        if (response.code !in 200..299) return fail(response)
        val ok = runCatching { JSONObject(response.body).optBoolean("ok", false) }.getOrDefault(false)
        return if (ok) Result(true, "ok") else Result(false, "Telegram 拒绝了消息")
    }

    private fun sendBark(channel: ChannelEntity, sms: SmsEntity): Result {
        val base = channel.url().trimEnd('/')
        if (base.isBlank()) return Result(false, "缺少 Bark 推送地址")
        val payload = JSONObject()
            .put("title", titleOf(sms))
            .put("body", plainText(sms))
            .put("group", "短信转发")
            .put("level", "active")
        val response = httpPostJson(base, payload.toString())
        if (response.code !in 200..299) return fail(response)
        val code = runCatching { JSONObject(response.body).optInt("code", 200) }.getOrDefault(200)
        return if (code == 200) Result(true, "ok") else Result(false, "Bark 返回 code=$code")
    }

    private fun sendServerChan(channel: ChannelEntity, sms: SmsEntity): Result {
        val raw = channel.url().ifBlank { channel.token() }
        if (raw.isBlank()) return Result(false, "缺少 SendKey")
        val url = when {
            raw.startsWith("http") -> raw
            raw.endsWith(".send") -> "https://sctapi.ftqq.com/$raw"
            else -> "https://sctapi.ftqq.com/$raw.send"
        }
        val form = "title=" + encode(titleOf(sms)) + "&desp=" + encode(plainText(sms))
        val response = httpPostForm(url, form)
        if (response.code !in 200..299) return fail(response)
        val code = runCatching { JSONObject(response.body).optInt("code", 0) }.getOrDefault(0)
        return if (code == 0) Result(true, "ok") else Result(false, "Server酱返回 code=$code")
    }

    // ---------------- 文本与网络 ----------------

    fun titleOf(sms: SmsEntity): String = "短信 · " + TextFormat.senderLabel(sms.sender)

    fun plainText(sms: SmsEntity): String = buildString {
        append("【短信转发】")
        append(TextFormat.senderLabel(sms.sender))
        append('\n')
        if (!sms.code.isNullOrBlank()) {
            append("验证码：").append(sms.code).append('\n')
        }
        append("时间：").append(TextFormat.messageTime(sms.receivedAt)).append('\n')
        append('\n')
        append(sms.body)
    }

    private data class HttpResponse(val code: Int, val body: String)

    private fun httpPostJson(url: String, json: String): HttpResponse {
        val connection = openConnection(url)
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.doOutput = true
        connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return connection.finish()
    }

    private fun httpPostForm(url: String, form: String): HttpResponse {
        val connection = openConnection(url)
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        connection.doOutput = true
        connection.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
        return connection.finish()
    }

    private fun openConnection(url: String): HttpURLConnection {
        val parsed = URL(url)
        require(parsed.protocol == "http" || parsed.protocol == "https") { "只支持 http/https 地址" }
        return (parsed.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty("User-Agent", "SmsForward/0.1 (Android)")
        }
    }

    private fun HttpURLConnection.finish(): HttpResponse {
        val code = responseCode
        val stream = if (code in 200..299) inputStream else errorStream
        val body = runCatching {
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        }.getOrDefault("")
        disconnect()
        return HttpResponse(code, body.take(2000))
    }

    private fun fail(response: HttpResponse): Result =
        Result(false, "HTTP ${response.code} " + response.body.take(100))

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun humanError(error: Throwable): String = when (error) {
        is IOException -> "网络异常：${error.message ?: error.javaClass.simpleName}"
        else -> error.message ?: error.javaClass.simpleName
    }
}
