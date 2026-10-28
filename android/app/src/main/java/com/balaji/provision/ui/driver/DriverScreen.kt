package com.balaji.provision.ui.driver

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.balaji.provision.domain.model.RiderOrder
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.ui.proof.OrderProofViewModel

@Composable
fun DriverScreen(
    onSignOut: () -> Unit,
    viewModel: DriverViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var deliveredOrder by remember { mutableStateOf<RiderOrder?>(null) }
    var failedOrder by remember { mutableStateOf<RiderOrder?>(null) }

    // Delivery proof: a photo or video is mandatory before the order can be marked delivered.
    val proofViewModel: OrderProofViewModel = hiltViewModel()
    val proofState by proofViewModel.state.collectAsState()
    var proofTarget by remember { mutableStateOf<RiderOrder?>(null) }
    val proofPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val order = proofTarget
        if (uri != null && order != null) {
            proofViewModel.upload(order.id, "delivery", uri)
        }
    }

    DisposableEffect(lifecycleOwner) {
        viewModel.startPolling()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.startPolling()
                Lifecycle.Event.ON_STOP -> viewModel.stopPolling()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPolling()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionError()
        }
    }

    Scaffold(
        // MainAppScreen owns the system-bar insets; asking Scaffold for them
        // again would double the gap above the header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            DriverHeader(
                orderCount = state.orders.count { it.isActive },
                refreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                onSignOut = onSignOut
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isInitialLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
                state.orders.isEmpty() && state.error != null -> ErrorState(
                    message = state.error!!,
                    onRetry = viewModel::refresh
                )
                state.orders.isEmpty() -> EmptyState()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.error?.let { message ->
                        item {
                            ErrorBanner(
                                message = message,
                                onRetry = viewModel::refresh,
                                onDismiss = viewModel::dismissError
                            )
                        }
                    }
                    items(state.orders, key = { it.id }) { order ->
                        RiderOrderCard(
                            order = order,
                            updating = state.updatingOrderId == order.id,
                            anyUpdating = state.updatingOrderId != null,
                            onStartDelivery = {
                                viewModel.updateStatus(order, "out_for_delivery")
                            },
                            onDelivered = { deliveredOrder = order },
                            onFailed = { failedOrder = order },
                            onCall = { dial(context, order) },
                            onNavigate = { openDirections(context, order) }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(deliveredOrder?.id) {
        deliveredOrder?.let { proofViewModel.load(it.id) }
    }

    LaunchedEffect(proofState.message) {
        proofState.message?.let {
            snackbarHostState.showSnackbar(it)
            proofViewModel.consumeMessage()
        }
    }

    deliveredOrder?.let { order ->
        val hasProof = proofState.proofs.containsKey("delivery")
        val uploading = proofState.uploadingStage != null
        AlertDialog(
            onDismissRequest = { if (!uploading) deliveredOrder = null },
            title = { Text("Confirm delivery") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (order.isCod) {
                            "Mark order ${order.orderNumber} as delivered and record " +
                                "${formatPaise(order.grandTotalPaise)} cash collected?"
                        } else {
                            "Mark order ${order.orderNumber} as delivered?"
                        }
                    )
                    Text(
                        text = if (hasProof) {
                            "Delivery proof uploaded."
                        } else {
                            "A photo or video of the delivered items is required first."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasProof) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                    OutlinedButton(
                        onClick = {
                            proofTarget = order
                            proofPicker.launch(arrayOf("image/*", "video/*"))
                        },
                        enabled = !uploading
                    ) {
                        Text(if (hasProof) "Replace photo/video" else "Upload photo/video")
                    }
                    if (uploading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Text("Uploading proof…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateStatus(order, "delivered")
                        deliveredOrder = null
                    },
                    enabled = hasProof && !uploading
                ) { Text("Yes, delivered") }
            },
            dismissButton = {
                TextButton(onClick = { deliveredOrder = null }, enabled = !uploading) {
                    Text("Cancel")
                }
            }
        )
    }

    failedOrder?.let { order ->
        FailedDeliveryDialog(
            order = order,
            onDismiss = { failedOrder = null },
            onConfirm = { reason ->
                viewModel.updateStatus(order, "delivery_failed", reason)
                failedOrder = null
            }
        )
    }
}

@Composable
private fun DriverHeader(
    orderCount: Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "My deliveries",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (orderCount == 1) "1 active delivery" else "$orderCount active deliveries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh deliveries")
                }
            }
            IconButton(onClick = onSignOut) {
                Icon(Icons.Default.Logout, contentDescription = "Sign out")
            }
        }
    }
}

@Composable
private fun RiderOrderCard(
    order: RiderOrder,
    updating: Boolean,
    anyUpdating: Boolean,
    onStartDelivery: () -> Unit,
    onDelivered: () -> Unit,
    onFailed: () -> Unit,
    onCall: () -> Unit,
    onNavigate: () -> Unit
) {
    val address = order.address
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${order.orderNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(order.status, order.statusLabel)
            }
            Text(
                formatWhen(order.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (address != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            address.recipientName.ifBlank { "Customer" },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            address.displayAddress,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (address.phone.isNotBlank()) {
                            Text(
                                address.phone,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            order.customerNote?.takeIf { it.isNotBlank() }?.let { note ->
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        "Note: $note",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${item.displayQty}  ${item.name}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            formatPaise(item.lineTotalPaise),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (order.isCod) "Collect cash" else "Prepaid",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (order.isCod) formatPaise(order.grandTotalPaise) else "—",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (order.isCod && order.status == "delivered" && order.codCollectedPaise != null) {
                Text(
                    "COD collected: ${formatPaise(order.codCollectedPaise)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCall,
                    enabled = !address?.phone.isNullOrBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Call")
                }
                OutlinedButton(
                    onClick = onNavigate,
                    enabled = address?.hasCoordinates == true,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Navigate")
                }
            }
            if (address != null && !address.hasCoordinates) {
                Text(
                    "No pinned map location. Use the address text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (order.actions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    order.actions.forEach { action ->
                        val isDelivered = action.toStatus == "delivered"
                        val isFailure = action.toStatus == "delivery_failed"
                        if (isFailure) {
                            OutlinedButton(
                                onClick = onFailed,
                                enabled = !anyUpdating,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text(if (updating) "Updating…" else action.label)
                            }
                        } else {
                            Button(
                                onClick = if (isDelivered) onDelivered else onStartDelivery,
                                enabled = !anyUpdating,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (updating) "Updating…" else action.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String, label: String) {
    val colors = when (status) {
        "ready_for_dispatch" -> MaterialTheme.colorScheme.tertiaryContainer to
            MaterialTheme.colorScheme.onTertiaryContainer
        "out_for_delivery" -> MaterialTheme.colorScheme.secondaryContainer to
            MaterialTheme.colorScheme.onSecondaryContainer
        "delivered" -> MaterialTheme.colorScheme.primaryContainer to
            MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.errorContainer to
            MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = colors.first, shape = RoundedCornerShape(50)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.second
        )
    }
}

@Composable
private fun FailedDeliveryDialog(
    order: RiderOrder,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember(order.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delivery failed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Order ${order.orderNumber} will be returned to the store.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    placeholder = { Text("Customer unavailable, wrong address…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.isNotBlank()
            ) { Text("Mark failed") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "No assigned deliveries",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            "Orders assigned to you by the store will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            TextButton(onClick = onRetry) { Text("Retry") }
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

private fun dial(context: Context, order: RiderOrder) {
    val phone = order.address?.phone?.takeIf { it.isNotBlank() } ?: return
    try {
        context.startActivity(
            Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(phone)}".toUri())
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No phone app found", Toast.LENGTH_SHORT).show()
    }
}

private fun openDirections(context: Context, order: RiderOrder) {
    val address = order.address ?: return
    val lat = address.lat ?: return
    val lng = address.lng ?: return
    val label = Uri.encode("Order ${order.orderNumber}")
    val geoUri = "geo:$lat,$lng?q=$lat,$lng($label)".toUri()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geoUri))
        return
    } catch (_: ActivityNotFoundException) {
        // Fall through to the no-key Google Maps web link.
    }
    try {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng".toUri()
            )
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No map or browser app found", Toast.LENGTH_SHORT).show()
    }
}

private fun formatWhen(raw: String): String {
    if (raw.length >= 16) {
        return raw.substring(0, 10) + " " + raw.substring(11, 16)
    }
    return raw.take(10)
}
