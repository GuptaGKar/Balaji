package com.balaji.smsgateway

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class GatewayService : Service() {
    private var server: GatewayServer? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var settings: SettingsStore? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWifiLock()
        acquireWakeLock()
        settings = try {
            SettingsStore(this)
        } catch (_: Exception) {
            null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            startAsForeground(getString(R.string.notification_starting))
        } catch (_: Exception) {
            GatewayRuntime.failed("foreground_start_failed")
            stopSelf()
            return START_NOT_STICKY
        }

        val store = settings
        if (store == null) {
            GatewayRuntime.failed("settings_unavailable")
            updateNotification(getString(R.string.notification_failed))
            return START_STICKY
        }

        if (server != null) {
            updateNotification(getString(R.string.notification_running, PORT))
            return START_STICKY
        }

        val apiKey = try {
            store.ensureApiKey()
        } catch (_: Exception) {
            GatewayRuntime.failed("settings_unavailable")
            updateNotification(getString(R.string.notification_failed))
            return START_STICKY
        }
        if (apiKey.isBlank()) {
            GatewayRuntime.failed("api_key_missing")
            updateNotification(getString(R.string.notification_failed))
            return START_STICKY
        }

        val handler = GatewayRequestHandler(
            apiKeyProvider = { store.getApiKey() },
            rateLimiter = SlidingWindowRateLimiter(perMinute = 10, perHour = 60),
            smsTransport = SmsSender(this),
        )
        val newServer = GatewayServer(PORT, handler)

        return try {
            newServer.start(SOCKET_READ_TIMEOUT_MILLIS, false)
            server = newServer
            GatewayRuntime.running(PORT)
            updateNotification(getString(R.string.notification_running, PORT))
            START_STICKY
        } catch (_: Exception) {
            GatewayRuntime.failed("port_unavailable")
            updateNotification(getString(R.string.notification_failed))
            START_STICKY
        }
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        releaseWifiLock()
        releaseWakeLock()
        GatewayRuntime.stopped()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), type)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun acquireWifiLock() {
        try {
            val wifiManager = applicationContext.getSystemService(WifiManager::class.java) ?: return
            @Suppress("DEPRECATION")
            val lock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                WIFI_LOCK_TAG,
            )
            lock.setReferenceCounted(false)
            lock.acquire()
            wifiLock = lock
        } catch (_: Exception) {
            wifiLock = null
        }
    }

    private fun releaseWifiLock() {
        try {
            val lock = wifiLock ?: return
            if (lock.isHeld) lock.release()
        } catch (_: Exception) {
            // Ignore release failures; the OS drops the lock with the process.
        } finally {
            wifiLock = null
        }
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(PowerManager::class.java) ?: return
            val lock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG,
            )
            lock.setReferenceCounted(false)
            lock.acquire()
            wakeLock = lock
        } catch (_: Exception) {
            wakeLock = null
        }
    }

    private fun releaseWakeLock() {
        try {
            val lock = wakeLock ?: return
            if (lock.isHeld) lock.release()
        } catch (_: Exception) {
            // Ignore release failures; the OS drops the lock with the process.
        } finally {
            wakeLock = null
        }
    }

    private companion object {
        const val PORT = 8080
        const val SOCKET_READ_TIMEOUT_MILLIS = 5_000
        const val NOTIFICATION_ID = 8080
        const val CHANNEL_ID = "sms_gateway"
        const val WIFI_LOCK_TAG = "balaji:sms-gateway"
        const val WAKE_LOCK_TAG = "balaji:sms-gateway:wake"
    }
}
