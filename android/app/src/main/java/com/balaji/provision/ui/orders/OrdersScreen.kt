package com.balaji.provision.ui.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.domain.model.formatWeightGrams
import com.balaji.provision.ui.components.EmptyState
import com.balaji.provision.ui.components.ErrorState
import com.balaji.provision.ui.components.LoadingState
import com.balaji.provision.ui.components.MoneyText
import com.balaji.provision.ui.components.StatusPill
import com.balaji.provision.ui.components.StoreTopBar
import com.balaji.provision.ui.theme.appColors
import kotlinx.coroutines.launch

enum class OrdersTab {
    ALL,
    CURRENT,
    PREVIOUS
}

@Composable
fun OrdersScreen(
    onNavigateToSignIn: () -> Unit = {},
    onOpenOrder: (orderId: String) -> Unit = {},
    viewModel: OrdersViewModel = hiltViewModel()
) {
    val orders by viewModel.orders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCancelling by viewModel.isCancelling.collectAsState()
    val error by viewModel.error.collectAsState()
    val refreshError by viewModel.refreshError.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var cancelTarget by remember { mutableStateOf<OrderView?>(null) }
    var cancelReason by remember { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf(OrdersTab.ALL) }

    // Reviews are submitted from the previous-orders list; one review per delivered order.
    val requestsViewModel: com.balaji.provision.ui.requests.ProductRequestsViewModel = hiltViewModel()
    var reviewTarget by remember { mutableStateOf<OrderView?>(null) }
    var reviewRating by remember { mutableStateOf(5) }
    var reviewComment by remember { mutableStateOf("") }
    var reviewSaving by remember { mutableStateOf(false) }

    val activeStatuses = remember {
        setOf("pending_review", "accepted", "preparing", "ready_for_dispatch", "out_for_delivery")
    }
    val currentOrders = remember(orders) {
        orders.filter { it.status.lowercase() in activeStatuses }
    }
    val previousOrders = remember(orders) {
        orders.filter { it.status.lowercase() !in activeStatuses }
    }

    LaunchedEffect(refreshError) {
        refreshError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeRefreshError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            StoreTopBar(
                title = "Orders",
                subtitle = when {
                    orders.isEmpty() -> "Your order history"
                    currentOrders.isNotEmpty() -> "${currentOrders.size} active · ${previousOrders.size} past"
                    else -> "${orders.size} ${if (orders.size == 1) "order" else "orders"}"
                },
                leadingIcon = Icons.Default.ReceiptLong,
                trailing = {
                    IconButton(onClick = { viewModel.fetchOrders() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh orders",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            when {
                isLoading && orders.isEmpty() -> LoadingState(message = "Loading your orders...")

                error != null && orders.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (error?.contains("Sign in", ignoreCase = true) == true ||
                        error?.contains("session expired", ignoreCase = true) == true
                    ) {
                        ErrorState(
                            message = error.orEmpty(),
                            actionLabel = "Sign in",
                            onAction = onNavigateToSignIn
                        )
                    } else {
                        ErrorState(
                            message = error.orEmpty(),
                            onAction = { viewModel.fetchOrders() }
                        )
                    }
                }

                orders.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.ReceiptLong,
                        title = "No orders yet",
                        message = "Your placed orders will appear here with live status updates."
                    )
                }

                else -> {
                    // Filter Chips Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTab == OrdersTab.ALL,
                            onClick = { selectedTab = OrdersTab.ALL },
                            label = { Text("All (${orders.size})") },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            selected = selectedTab == OrdersTab.CURRENT,
                            onClick = { selectedTab = OrdersTab.CURRENT },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (currentOrders.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (selectedTab == OrdersTab.CURRENT)
                                                        MaterialTheme.colorScheme.onPrimary
                                                    else
                                                        MaterialTheme.appColors.success
                                                )
                                        )
                                    }
                                    Text("Current (${currentOrders.size})")
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        FilterChip(
                            selected = selectedTab == OrdersTab.PREVIOUS,
                            onClick = { selectedTab = OrdersTab.PREVIOUS },
                            label = { Text("Previous (${previousOrders.size})") },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (selectedTab) {
                            OrdersTab.ALL -> {
                                if (currentOrders.isNotEmpty()) {
                                    item(key = "hdr_current") {
                                        OrdersSectionHeader(
                                            title = "Current Orders",
                                            count = currentOrders.size,
                                            isLive = true
                                        )
                                    }
                                    items(currentOrders, key = { it.id }) { order ->
                                        OrderCard(
                                            order = order,
                                            isCurrent = true,
                                            onOpen = { onOpenOrder(order.id) },
                                            onCancel = {
                                                cancelReason = ""
                                                cancelTarget = order
                                            }
                                        )
                                    }
                                }

                                if (previousOrders.isNotEmpty()) {
                                    item(key = "hdr_previous") {
                                        OrdersSectionHeader(
                                            title = "Previous Orders",
                                            count = previousOrders.size,
                                            isLive = false
                                        )
                                    }
                                    items(previousOrders, key = { it.id }) { order ->
                                        OrderCard(
                                            order = order,
                                            isCurrent = false,
                                            onOpen = { onOpenOrder(order.id) },
                                            onReview = { reviewTarget = order },
                                            onCancel = {
                                                cancelReason = ""
                                                cancelTarget = order
                                            }
                                        )
                                    }
                                }
                            }

                            OrdersTab.CURRENT -> {
                                if (currentOrders.isEmpty()) {
                                    item(key = "empty_current") {
                                        EmptyOrdersSubState(
                                            icon = Icons.Default.LocalShipping,
                                            title = "No active orders",
                                            message = "You don't have any in-progress orders right now."
                                        )
                                    }
                                } else {
                                    items(currentOrders, key = { it.id }) { order ->
                                        OrderCard(
                                            order = order,
                                            isCurrent = true,
                                            onOpen = { onOpenOrder(order.id) },
                                            onCancel = {
                                                cancelReason = ""
                                                cancelTarget = order
                                            }
                                        )
                                    }
                                }
                            }

                            OrdersTab.PREVIOUS -> {
                                if (previousOrders.isEmpty()) {
                                    item(key = "empty_previous") {
                                        EmptyOrdersSubState(
                                            icon = Icons.Default.ReceiptLong,
                                            title = "No previous orders",
                                            message = "Your completed and cancelled orders will be saved here."
                                        )
                                    }
                                } else {
                                    items(previousOrders, key = { it.id }) { order ->
                                        OrderCard(
                                            order = order,
                                            isCurrent = false,
                                            onOpen = { onOpenOrder(order.id) },
                                            onReview = { reviewTarget = order },
                                            onCancel = {
                                                cancelReason = ""
                                                cancelTarget = order
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    cancelTarget?.let { order ->
        AlertDialog(
            onDismissRequest = { if (!isCancelling) cancelTarget = null },
            title = { Text("Cancel order #${order.publicOrderNumber}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("This cannot be undone. The store will see the cancellation immediately.")
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Reason (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isCancelling,
                    onClick = {
                        scope.launch {
                            val result = viewModel.cancelOrder(order.id, cancelReason)
                            when (result) {
                                is CancelOrderResult.Success -> {
                                    cancelTarget = null
                                    snackbarHostState.showSnackbar("Order cancelled")
                                }
                                is CancelOrderResult.Failure -> {
                                    snackbarHostState.showSnackbar(result.message)
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isCancelling) "Cancelling..." else "Cancel order")
                }
            },
            dismissButton = {
                TextButton(enabled = !isCancelling, onClick = { cancelTarget = null }) {
                    Text("Keep order")
                }
            }
        )
    }

    reviewTarget?.let { order ->
        AlertDialog(
            onDismissRequest = { if (!reviewSaving) reviewTarget = null },
            title = { Text("Rate order #${order.publicOrderNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("How was this order?")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..5).forEach { star ->
                            TextButton(onClick = { reviewRating = star }) {
                                Text(
                                    text = if (star <= reviewRating) "★" else "☆",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = reviewComment,
                        onValueChange = { if (it.length <= 1000) reviewComment = it },
                        label = { Text("Comment (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !reviewSaving,
                    onClick = {
                        reviewSaving = true
                        requestsViewModel.reviewOrder(
                            orderId = order.id,
                            rating = reviewRating,
                            comment = reviewComment.trim().ifBlank { null }
                        ) { success, message ->
                            reviewSaving = false
                            if (success) {
                                reviewTarget = null
                                reviewComment = ""
                                reviewRating = 5
                            }
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    }
                ) {
                    Text(if (reviewSaving) "Sending..." else "Send review")
                }
            },
            dismissButton = {
                TextButton(enabled = !reviewSaving, onClick = { reviewTarget = null }) {
                    Text("Not now")
                }
            }
        )
    }
}

@Composable
fun OrderCard(
    order: OrderView,
    isCurrent: Boolean = false,
    onCancel: () -> Unit,
    onOpen: () -> Unit = {},
    onReview: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        border = if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isCurrent) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.appColors.success)
                            )
                        }
                        Text(
                            "#${order.publicOrderNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        order.createdAt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${order.items.size} ${if (order.items.size == 1) "item" else "items"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OrderStatusChip(status = order.status)
            }

            if (order.hasRemovedItems) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    Text(
                        "Some items were unavailable and removed by the store. Your bill has been updated.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (item.removed) TextDecoration.LineThrough else null,
                                color = if (item.removed) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                itemQuantity(item),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (item.removed) {
                                Text(
                                    "Not available",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        MoneyText(
                            text = if (item.removed) "Removed" else formatPaise(item.lineTotalPaise),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.removed) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Grand total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        text = formatPaise(order.grandTotalPaise),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                StatusPill(
                    text = "Cash on Delivery",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (order.canCancel) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (!order.canCancel && order.status.equals("delivered", ignoreCase = true) &&
                    onReview != null
                ) {
                    OutlinedButton(
                        onClick = { onReview?.invoke() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Rate order", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(if (order.canCancel) 1.5f else 1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCurrent)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isCurrent)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        imageVector = if (isCurrent) Icons.Default.LocalShipping else Icons.Default.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCurrent) "Track Order" else "View Details",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun OrdersSectionHeader(
    title: String,
    count: Int,
    isLive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isLive) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.appColors.success)
            )
        }
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun EmptyOrdersSubState(
    icon: ImageVector,
    title: String,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun itemQuantity(item: OrderItemView): String {
    return if (item.quantityType.equals("weighted", ignoreCase = true)) {
        formatWeightGrams(item.qty)
    } else {
        "${item.qty} × ${item.unitLabel ?: "pack"}"
    }
}

@Composable
fun OrderStatusChip(status: String) {
    val containerColor: Color
    val contentColor: Color
    val label: String
    val icon: ImageVector

    when (status.lowercase()) {
        "pending_review" -> {
            containerColor = MaterialTheme.appColors.warningContainer
            contentColor = MaterialTheme.appColors.onWarningContainer
            label = "Pending review"
            icon = Icons.Default.PendingActions
        }
        "accepted" -> {
            containerColor = MaterialTheme.appColors.infoContainer
            contentColor = MaterialTheme.appColors.onInfoContainer
            label = "Accepted"
            icon = Icons.Default.CheckCircle
        }
        "preparing" -> {
            containerColor = MaterialTheme.appColors.infoContainer
            contentColor = MaterialTheme.appColors.onInfoContainer
            label = "Preparing"
            icon = Icons.Default.LocalShipping
        }
        "ready_for_dispatch" -> {
            containerColor = MaterialTheme.appColors.infoContainer
            contentColor = MaterialTheme.appColors.onInfoContainer
            label = "Ready for dispatch"
            icon = Icons.Default.LocalShipping
        }
        "out_for_delivery" -> {
            containerColor = MaterialTheme.appColors.successContainer
            contentColor = MaterialTheme.appColors.onSuccessContainer
            label = "Out for delivery"
            icon = Icons.Default.LocalShipping
        }
        "delivered" -> {
            containerColor = MaterialTheme.appColors.successContainer
            contentColor = MaterialTheme.appColors.onSuccessContainer
            label = "Delivered"
            icon = Icons.Default.CheckCircle
        }
        "cancelled", "rejected", "delivery_failed" -> {
            containerColor = MaterialTheme.colorScheme.errorContainer
            contentColor = MaterialTheme.colorScheme.onErrorContainer
            label = status.replace('_', ' ').replaceFirstChar { it.uppercase() }
            icon = Icons.Default.Close
        }
        else -> {
            containerColor = MaterialTheme.colorScheme.surfaceVariant
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            label = status.replace('_', ' ').replaceFirstChar { it.uppercase() }
            icon = Icons.Default.PendingActions
        }
    }

    StatusPill(
        text = label,
        icon = icon,
        containerColor = containerColor,
        contentColor = contentColor
    )
}
