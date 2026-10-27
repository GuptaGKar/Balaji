package com.balaji.provision.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.ui.components.EmptyState
import com.balaji.provision.ui.components.ErrorState
import com.balaji.provision.ui.components.LoadingState
import com.balaji.provision.ui.theme.appColors
import kotlinx.coroutines.delay

private data class TrackingStep(
    val title: String,
    val subtitle: String?,
    val done: Boolean,
    val current: Boolean
)

/**
 * Order tracking: live status timeline, itemised bill, store contact and
 * periodic refresh. All statuses/timestamps come from the server.
 */
@Composable
fun OrderTrackingScreen(
    orderId: String,
    onBack: () -> Unit,
    viewModel: OrdersViewModel = hiltViewModel()
) {
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val order = remember(orders, orderId) { orders.firstOrNull { it.id == orderId } }

    LaunchedEffect(Unit) {
        viewModel.fetchOrders()
        while (true) {
            delay(30_000)
            viewModel.fetchOrders()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "Track order",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (isLoading && order != null) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(18.dp)
                        .padding(end = 4.dp),
                    strokeWidth = 2.dp
                )
            }
            IconButton(onClick = { viewModel.fetchOrders() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh order status")
            }
        }

        when {
            order == null && isLoading -> LoadingState(message = "Loading your order…")

            order == null && error != null -> ErrorState(
                message = error.orEmpty(),
                onAction = { viewModel.fetchOrders() }
            )

            order == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.ReceiptLong,
                    title = "Order not found",
                    message = "We could not find this order. Pull the latest list and try again.",
                    actionLabel = "Refresh",
                    onAction = { viewModel.fetchOrders() }
                )
            }

            else -> {
                val cancelled = order.status == "cancelled" ||
                    order.status == "rejected" ||
                    order.status == "delivery_failed"
                val terminal = order.status == "delivered" || cancelled
                val steps = trackingSteps(order)
                val headline = statusHeadline(order.status)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "summary") {
                        StatusSummaryCard(
                            headline = headline.first,
                            message = headline.second,
                            order = order,
                            cancelled = cancelled
                        )
                    }

                    if (steps.isNotEmpty() && !cancelled) {
                        item(key = "timeline") {
                            TimelineCard(steps = steps, status = order.status)
                        }
                    }

                    if (terminal) {
                        item(key = "terminal_note") {
                            Text(
                                text = "Statuses refresh automatically. You can close this screen any time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun statusHeadline(status: String): Pair<String, String> = when (status) {
    "pending_review" -> "Order placed" to
        "We have received your order. The store will confirm it shortly."
    "accepted" -> "Order confirmed" to
        "The store has accepted your order and will start packing it."
    "preparing" -> "Preparing your order" to
        "Your items are being packed right now."
    "ready_for_dispatch" -> "Ready to go" to
        "Your order is packed and waiting for a delivery partner."
    "out_for_delivery" -> "Out for delivery" to
        "Your order is on its way. Please keep your phone nearby."
    "delivered" -> "Delivered" to
        "Your order has been delivered. Thank you for shopping with us."
    "cancelled" -> "Order cancelled" to
        "This order was cancelled. Any amount due will not be collected."
    "rejected" -> "Order rejected" to
        "The store could not accept this order. Please contact the store."
    "delivery_failed" -> "Delivery failed" to
        "Delivery could not be completed. Please contact the store for next steps."
    else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() } to
        "We will update this page as your order moves ahead."
}

private fun trackingSteps(order: OrderView): List<TrackingStep> {
    val rank = when (order.status) {
        "pending_review" -> 0
        "accepted", "preparing" -> 1
        "ready_for_dispatch", "out_for_delivery" -> 2
        "delivered" -> 3
        else -> -1
    }
    data class Stage(val title: String, val subtitle: String?)

    val stages = listOf(
        Stage("Order confirmed", formatOrderTimestamp(order.createdAtRaw)),
        Stage("Preparing", formatOrderTimestamp(order.acceptedAtRaw)),
        Stage(
            "Out for delivery",
            if (rank >= 2) "On the way to you" else null
        ),
        Stage("Delivered", formatOrderTimestamp(order.deliveredAtRaw))
    )
    return stages.mapIndexed { index, stage ->
        TrackingStep(
            title = stage.title,
            subtitle = stage.subtitle,
            done = rank > index || (rank == 3 && index == 3),
            current = rank == index
        )
    }
}

@Composable
private fun StatusSummaryCard(
    headline: String,
    message: String,
    order: OrderView,
    cancelled: Boolean
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (cancelled) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.appColors.successContainer
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (cancelled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.appColors.success
                    }
                ) {
                    Icon(
                        imageVector = if (cancelled) Icons.Default.Close else Icons.Default.Check,
                        contentDescription = null,
                        tint = if (cancelled) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        },
                        modifier = Modifier
                            .padding(7.dp)
                            .size(18.dp)
                    )
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (cancelled) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.appColors.onSuccessContainer
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (cancelled) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.appColors.onSuccessContainer
                }
            )
            Spacer(Modifier.height(14.dp))
            Divider(
                color = if (cancelled) {
                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.appColors.success.copy(alpha = 0.3f)
                }
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Order #${order.publicOrderNumber}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (cancelled) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.appColors.onSuccessContainer
                        }
                    )
                    Text(
                        "Placed ${formatOrderTimestamp(order.createdAtRaw) ?: order.createdAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (cancelled) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.appColors.onSuccessContainer
                        }
                    )
                }
                OrderStatusChip(status = order.status)
            }
        }
    }
}

@Composable
private fun TimelineCard(steps: List<TrackingStep>, status: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Order progress",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(28.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when {
                                step.done -> MaterialTheme.appColors.success
                                step.current -> MaterialTheme.appColors.success
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (step.done || step.current) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                        if (index != steps.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(30.dp)
                                    .background(
                                        if (steps.getOrNull(index + 1)?.done == true ||
                                            steps.getOrNull(index + 1)?.current == true
                                        ) {
                                            MaterialTheme.appColors.success
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    )
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(start = 10.dp, bottom = 14.dp)) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (step.done || step.current) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (step.done || step.current) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        if (!step.subtitle.isNullOrBlank()) {
                            Text(
                                text = step.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (step.current) {
                            Text(
                                text = "In progress",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.appColors.success
                            )
                        }
                    }
                }
            }
            if (status == "ready_for_dispatch") {
                Text(
                    "A delivery partner will pick this up shortly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

