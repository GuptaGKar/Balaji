package com.balaji.smsgateway

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

class SmsSender(context: Context) : SmsTransport {
    private val appContext = context.applicationContext

    override fun send(phoneNumber: String, message: String): SmsSendResult {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return SmsSendResult.Failed("sms_permission_missing")
        }

        return try {
            val manager = smsManager() ?: return SmsSendResult.Failed("sms_manager_unavailable")
            val parts = manager.divideMessage(message)
            if (parts.size <= 1) {
                manager.sendTextMessage(phoneNumber, null, message, null, null)
            } else {
                manager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            }
            SmsSendResult.Sent
        } catch (_: Exception) {
            SmsSendResult.Failed("sms_send_failed")
        }
    }

    private fun smsManager(): SmsManager? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            appContext.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
}
