package com.balaji.provision.ui.orders

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OrdersScreen() {
    val orders by remember { mutableStateOf<List<OrderView>>(emptyList()) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "My Orders",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        
        if (orders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ListAlt,
                        contentDescription = "No orders",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Text("No orders yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Your placed orders will appear here", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Order #${order.publicOrderNumber}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                OrderStatusChip(status = order.status)
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total: ${formatPaise(order.grandTotalPaise)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${order.items.size} items", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatDate(order.createdAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = "COD",
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun OrderStatusChip(status: String) {
    val (color, icon) = when (status) {
        "pending_review" -> MaterialTheme.colorScheme.tertiary to Icons.Default.PendingActions
        "accepted" -> MaterialTheme.colorScheme.primary to Icons.Default.CheckCircle
        "preparing" -> MaterialTheme.colorScheme.secondary to Icons.Default.LocalShipping
        "ready_for_dispatch" -> MaterialTheme.colorScheme.tertiary to Icons.Default.LocalShipping
        "out_for_delivery" -> MaterialTheme.colorScheme.primary to Icons.Default.LocalShipping
        "delivered" -> MaterialTheme.colorScheme.primary to Icons.Default.CheckCircle
        "cancelled", "rejected" -> MaterialTheme.colorScheme.error to Icons.Default.Close
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Default.PendingActions
    }
    
    AssistChip(
        onClick = { },
        label = { Text(status.replace("_", " ").uppercase()) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = color) }
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
    return if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
}