package com.balaji.provision.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PendingActions
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun OrdersScreen(
    viewModel: OrdersViewModel = viewModel()
) {
    val orders by remember { mutableStateOf<List<OrderView>>(emptyList()) }
    val isLoading by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("My Orders", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            .padding(16.dp)
        
        if (orders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material.icons.outlined.ListAlt
                        .let { Icon(it, contentDescription = "No orders", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, size = 64.dp) }
                    Text("No orders yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Your placed orders will appear here", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(orders) { order ->
                    OrderCard(order = order)
                }
            }
        }
    }
}

@Composable
fun OrderCard(order: OrderView) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Order #${order.publicOrderNumber}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OrderStatusChip(status = order.status)
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            // Order details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total: ${formatPaise(order.grandTotalPaise)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${order.items.size} items", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(formatDate(order.createdAt), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text("COD", fontSize = 12.sp)
                        .padding(8.dp, 4.dp)
                        .background(
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        )
                }
            }
        }
    }
}

@Composable
fun OrderStatusChip(status: String) {
    val (color, icon) = when (status) {
        "pending_review" -> androidx.compose.material3.MaterialTheme.colorScheme.warning to androidx.compose.material.icons.outlined.PendingActions
        "accepted" -> androidx.compose.material3.MaterialTheme.colorScheme.primary to androidx.compose.material.icons.outlined.CheckCircle
        "preparing" -> androidx.compose.material3.MaterialTheme.colorScheme.secondary to androidx.compose.material.icons.outlined.LocalShipping
        "ready_for_dispatch" -> androidx.compose.material3.MaterialTheme.colorScheme.tertiary to androidx.compose.material.icons.outlined.LocalShipping
        "out_for_delivery" -> androidx.compose.material3.MaterialTheme.colorScheme.primary to androidx.compose.material.icons.outlined.LocalShipping
        "delivered" -> androidx.compose.material3.MaterialTheme.colorScheme.success to androidx.compose.material.icons.outlined.CheckCircle
        "cancelled", "rejected" -> androidx.compose.material3.MaterialTheme.colorScheme.error to androidx.compose.material.icons.outlined.Close
        else -> androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant to androidx.compose.material.icons.outlined.PendingActions
    }
    
    androidx.compose.material3.Chip(
        onClick = { },
        label = { Text(status.replace("_", " ").uppercase(), color = color.copy(alpha = 0.9f)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) },
        colors = androidx.compose.material3.ChipDefaults.colors(
            containerColor = color.copy(alpha = 0.15f)
        )
    )
}

data class OrderView(
    val id: String,
    val publicOrderNumber: String,
    val status: String,
    val grandTotalPaise: Long,
    val items: List<OrderItemView> = emptyList(),
    val createdAt: String
)

data class OrderItemView(
    val name: String,
    val qty: Long,
    val unitPricePaise: Long
)

fun formatPaise(paise: Long): String {
    val rupees = paise / 100.0
    return "₹${String.format("%.2f", rupees)}"
}

fun formatDate(dateStr: String): String {
    // Simplified date formatting
    return dateStr.substring(0, 10)
}

class OrdersViewModel : androidx.lifecycle.ViewModel() {
    // Implementation would go here
}