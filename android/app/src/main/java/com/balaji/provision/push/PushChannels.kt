package com.balaji.provision.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.balaji.provision.BuildConfig

/**
 * Notification channels and the code that actually posts a push message.
 *
 * Staff get the "orders" channel (new order), customers get "order_status"
 * (their own order moving). The server sends the channel name in the data
 * payload; anything unknown falls back to the audience's channel so a message is
 * never dropped because of a typo on the server.
 */
object PushChannels {
    const val ORDERS = "orders"
    const val ORDER_STATUS = "order_status"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val orders = NotificationChannel(
            ORDERS,
            "New orders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "A customer placed an order" }

        val status = NotificationChannel(
            ORDER_STATUS,
            "Order updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Your order status changes" }

        manager.createNotificationChannel(orders)
        manager.createNotificationChannel(status)
    }

    fun audienceChannel(): String = if (BuildConfig.IS_STAFF) ORDERS else ORDER_STATUS

    fun show(context: Context, title: String?, body: String?, data: Map<String, String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val channel = data["channel"]?.takeIf { it == ORDERS || it == ORDER_STATUS }
            ?: audienceChannel()

        // One notification per order, not one per message: a later status replaces the
        // earlier one instead of stacking, and different orders stay separate. Without
        // this every message used a random id and Android collapsed them into a single
        // "N notifications" bundle where only the newest line was readable.
        val orderKey = data["order_id"] ?: data["orderId"]
        val notificationId = orderKey?.hashCode()?.and(0x7FFFFFFF)
            ?: (System.currentTimeMillis() and 0xFFFFFF).toInt()

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title ?: "Balaji Provision Store")
            .setContentText(body ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body ?: ""))
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(channel)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and the system call.
            // Never log the notification payload or recipient information.
            Log.w("PushChannels", "Notification permission was revoked before posting")
        }
    }
}
