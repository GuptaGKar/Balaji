package com.balaji.smsgateway

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun GatewayScreen(
    initialApiKey: String,
    smsGranted: Boolean,
    notificationsGranted: Boolean,
    batteryUnrestricted: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestBattery: () -> Unit,
    onSaveApiKey: (String) -> Boolean,
    onRegenerateApiKey: () -> String,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var showKey by remember { mutableStateOf(false) }
    var keyError by remember { mutableStateOf(false) }
    var showRegenerateDialog by remember { mutableStateOf(false) }

    val state by GatewayRuntime.state.collectAsState()
    val running = state is GatewayState.Running
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.gateway_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.gateway_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        StatusCard(state)

        SetupCard(
            smsGranted = smsGranted,
            notificationsGranted = notificationsGranted,
            batteryUnrestricted = batteryUnrestricted,
            onRequestPermissions = onRequestPermissions,
            onRequestBattery = onRequestBattery,
        )

        ApiKeyCard(
            apiKey = apiKey,
            showKey = showKey,
            keyError = keyError,
            onApiKeyChange = {
                apiKey = it
                keyError = false
            },
            onToggleShowKey = { showKey = !showKey },
            onSave = {
                if (onSaveApiKey(apiKey)) {
                    keyError = false
                    Toast.makeText(context, R.string.api_key_saved, Toast.LENGTH_SHORT).show()
                    true
                } else {
                    keyError = true
                    false
                }
            },
            onCopy = {
                clipboard.setText(AnnotatedString(apiKey))
                Toast.makeText(context, R.string.api_key_copied, Toast.LENGTH_SHORT).show()
            },
            onRegenerate = { showRegenerateDialog = true },
        )

        NetworkCard()

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = !running && smsGranted && GatewayPolicy.isValidApiKey(apiKey),
                onClick = {
                    if (onSaveApiKey(apiKey)) {
                        keyError = false
                        onStart()
                    } else {
                        keyError = true
                    }
                },
            ) {
                Text(stringResource(R.string.start_gateway))
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = running,
                onClick = onStop,
            ) {
                Text(stringResource(R.string.stop_gateway))
            }
        }

        if (!smsGranted) {
            Text(
                text = stringResource(R.string.start_requires_sms),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!GatewayPolicy.isValidApiKey(apiKey)) {
            Text(
                text = stringResource(R.string.start_requires_key),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = stringResource(R.string.security_warning),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (showRegenerateDialog) {
        AlertDialog(
            onDismissRequest = { showRegenerateDialog = false },
            title = { Text(stringResource(R.string.api_key_regenerate_title)) },
            text = { Text(stringResource(R.string.api_key_regenerate_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        apiKey = onRegenerateApiKey()
                        keyError = false
                        showKey = true
                        showRegenerateDialog = false
                        Toast.makeText(
                            context,
                            R.string.api_key_generated,
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegenerateDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun StatusCard(state: GatewayState) {
    val color = when (state) {
        is GatewayState.Running -> Color(0xFF1B7F4A)
        is GatewayState.Failed -> MaterialTheme.colorScheme.error
        GatewayState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val text = when (state) {
        is GatewayState.Running -> stringResource(R.string.status_running, state.port)
        is GatewayState.Failed -> stringResource(R.string.status_failed, failureMessage(state.reason))
        GatewayState.Stopped -> stringResource(R.string.status_stopped)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = color,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SetupCard(
    smsGranted: Boolean,
    notificationsGranted: Boolean,
    batteryUnrestricted: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestBattery: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            PermissionRow(
                label = stringResource(R.string.setup_sms),
                granted = smsGranted,
                onRequest = onRequestPermissions,
            )
            PermissionRow(
                label = stringResource(R.string.setup_notifications),
                granted = notificationsGranted,
                onRequest = onRequestPermissions,
            )
            PermissionRow(
                label = stringResource(R.string.setup_battery),
                granted = batteryUnrestricted,
                onRequest = onRequestBattery,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(
                    if (granted) R.string.setup_granted else R.string.setup_required,
                ),
                color = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!granted) {
            TextButton(onClick = onRequest) {
                Text(stringResource(R.string.setup_open_settings))
            }
        }
    }
}

@Composable
private fun ApiKeyCard(
    apiKey: String,
    showKey: Boolean,
    keyError: Boolean,
    onApiKeyChange: (String) -> Unit,
    onToggleShowKey: () -> Unit,
    onSave: () -> Boolean,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.api_key_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.api_key_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(stringResource(R.string.api_key_label)) },
                singleLine = true,
                isError = keyError,
                visualTransformation = if (showKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(onClick = onToggleShowKey) {
                        Text(
                            stringResource(
                                if (showKey) R.string.api_key_hide else R.string.api_key_show,
                            ),
                        )
                    }
                },
                supportingText = if (keyError) {
                    { Text(stringResource(R.string.api_key_invalid)) }
                } else {
                    null
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { onSave() },
                ) {
                    Text(stringResource(R.string.api_key_save))
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = apiKey.isNotBlank(),
                    onClick = onCopy,
                ) {
                    Text(stringResource(R.string.api_key_copy))
                }
            }
            TextButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onRegenerate,
            ) {
                Text(stringResource(R.string.api_key_regenerate))
            }
        }
    }
}

@Composable
private fun NetworkCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.network_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.network_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun failureMessage(reason: String): String = when (reason) {
    "port_unavailable" -> stringResource(R.string.failure_port)
    "settings_unavailable" -> stringResource(R.string.failure_settings)
    "foreground_start_failed" -> stringResource(R.string.failure_foreground)
    "api_key_missing" -> stringResource(R.string.failure_api_key)
    else -> stringResource(R.string.failure_unknown)
}
