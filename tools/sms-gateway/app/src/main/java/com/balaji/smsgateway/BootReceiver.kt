package com.balaji.smsgateway

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        try {
            if (SettingsStore(context).getApiKey() == null) return
            ContextCompat.startForegroundService(
                context,
                Intent(context, GatewayService::class.java),
            )
        } catch (_: Exception) {
            // Do not crash the boot broadcast. The user can start the gateway manually.
        }
    }
}
