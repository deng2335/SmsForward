package com.smsforward.app.data.sms

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper

/** 监听短信库变化，广播没送达时靠它兜底。 */
class SmsObserver(private val onChanged: () -> Unit) : ContentObserver(Handler(Looper.getMainLooper())) {

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onChanged()
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        onChanged()
    }
}
