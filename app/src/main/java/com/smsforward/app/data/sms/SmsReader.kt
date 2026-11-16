package com.smsforward.app.data.sms

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.smsforward.app.data.model.IncomingSms

/**
 * 读取系统短信库（content://sms/inbox）。
 *
 * 非默认短信应用不能写入短信库，但可以读取（需要 READ_SMS）。
 * 不同 ROM 的 provider 列不一致，所以这里准备了两套 projection，失败就降级重试。
 */
class SmsReader(private val context: Context) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    fun canReceive(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECEIVE_SMS,
    ) == PackageManager.PERMISSION_GRANTED

    fun readInbox(limit: Int = 200): List<IncomingSms> {
        if (!hasPermission()) return emptyList()

        // 不同 ROM 的短信 provider 列不一致，依次降级：
        // 1. 带 sub_id/read（AOSP、大多数国产 ROM）
        // 2. 去掉 sub_id
        // 3. 连 limit/sort 参数都不传（个别 ROM 不认 QUERY_ARG_*）
        val attempts = listOf(
            Attempt(arrayOf("_id", "address", "body", "date", "sub_id", "read"), useQueryArgs = true),
            Attempt(arrayOf("_id", "address", "body", "date", "read"), useQueryArgs = true),
            Attempt(arrayOf("_id", "address", "body", "date"), useQueryArgs = false),
        )
        for (attempt in attempts) {
            val rows = query(Uri.parse(URI_INBOX), attempt.projection, limit, attempt.useQueryArgs)
            if (rows != null) return rows
        }
        return emptyList()
    }

    private data class Attempt(val projection: Array<String>, val useQueryArgs: Boolean)

    private fun query(
        uri: Uri,
        projection: Array<String>,
        limit: Int,
        useQueryArgs: Boolean,
    ): List<IncomingSms>? {
        return try {
            val result = ArrayList<IncomingSms>(limit.coerceAtMost(200))
            val cursor = if (useQueryArgs) {
                val args = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "date DESC")
                    putInt(ContentResolver.QUERY_ARG_LIMIT, limit.coerceIn(1, 2000))
                }
                context.contentResolver.query(uri, projection, args, null)
            } else {
                context.contentResolver.query(uri, projection, null, null, "date DESC")
            }
            cursor?.use { c ->
                val idIdx = c.getColumnIndex("_id")
                val addressIdx = c.getColumnIndex("address")
                val bodyIdx = c.getColumnIndex("body")
                val dateIdx = c.getColumnIndex("date")
                val subIdIdx = c.getColumnIndex("sub_id")
                val readIdx = c.getColumnIndex("read")

                while (c.moveToNext() && result.size < limit) {
                    val body = if (bodyIdx >= 0) c.getString(bodyIdx).orEmpty() else ""
                    val sender = if (addressIdx >= 0) c.getString(addressIdx).orEmpty() else ""
                    if (body.isBlank()) continue
                    result += IncomingSms(
                        sender = sender,
                        body = body,
                        timestamp = if (dateIdx >= 0) c.getLong(dateIdx) else 0L,
                        systemId = if (idIdx >= 0) c.getLong(idIdx) else null,
                        subscriptionId = if (subIdIdx >= 0) {
                            c.getInt(subIdIdx).takeIf { it > 0 }
                        } else {
                            null
                        },
                        read = if (readIdx >= 0) c.getInt(readIdx) == 1 else true,
                    )
                }
            }
            result
        } catch (t: Throwable) {
            // 列不存在 / provider 被限制 / 权限被回收：交给调用方降级
            null
        }
    }

    private companion object {
        const val URI_INBOX = "content://sms/inbox"
    }
}
