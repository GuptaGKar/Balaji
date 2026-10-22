package com.balaji.smsgateway

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settings = runCatching { SettingsStore(this) }.getOrNull()
        val initialApiKey = settings
            ?.let { runCatching { it.ensureApiKey() }.getOrNull() }
            .orEmpty()

        setContent {
            BalajiGatewayTheme {
                var smsGranted by remember { mutableStateOf(hasSmsPermission()) }
                var notificationsGranted by remember { mutableStateOf(hasNotificationPermission()) }
                var batteryUnrestricted by remember { mutableStateOf(isBatteryUnrestricted()) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result ->
                    smsGranted = result[Manifest.permission.SEND_SMS] == true || hasSmsPermission()
                    notificationsGranted =
                        result[Manifest.permission.POST_NOTIFICATIONS] == true ||
                        hasNotificationPermission()
                }

                val batteryLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult(),
                ) {
                    batteryUnrestricted = isBatteryUnrestricted()
                }

                val requestPermissions = {
                    val permissions = buildList {
                        add(Manifest.permission.SEND_SMS)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }

                val requestBatteryExemption = {
                    try {
                        batteryLauncher.launch(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:$packageName"),
                            ),
                        )
                    } catch (_: Exception) {
                        try {
                            batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                R.string.failure_settings,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }

                GatewayScreen(
                    initialApiKey = initialApiKey,
                    smsGranted = smsGranted,
                    notificationsGranted = notificationsGranted,
                    batteryUnrestricted = batteryUnrestricted,
                    onRequestPermissions = requestPermissions,
                    onRequestBattery = requestBatteryExemption,
                    onSaveApiKey = { key ->
                        val trimmed = key.trim()
                        if (trimmed.isEmpty()) {
                            false
                        } else {
                            val store = settings
                            if (store == null) {
                                Toast.makeText(
                                    this@MainActivity,
                                    R.string.failure_settings,
                                    Toast.LENGTH_LONG,
                                ).show()
                                false
                            } else {
                                runCatching { store.setApiKey(trimmed) }.isSuccess
                            }
                        }
                    },
                    onRegenerateApiKey = {
                        val store = settings
                        if (store == null) {
                            Toast.makeText(
                                this@MainActivity,
                                R.string.failure_settings,
                                Toast.LENGTH_LONG,
                            ).show()
                            initialApiKey
                        } else {
                            runCatching { store.regenerateApiKey() }.getOrDefault(initialApiKey)
                        }
                    },
                    onStart = {
                        try {
                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                Intent(this@MainActivity, GatewayService::class.java),
                            )
                        } catch (_: Exception) {
                            Toast.makeText(
                                this@MainActivity,
                                R.string.start_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    onStop = {
                        stopService(Intent(this@MainActivity, GatewayService::class.java))
                    },
                )
            }
        }
    }

    private fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun isBatteryUnrestricted(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java) ?: return false
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }
}
