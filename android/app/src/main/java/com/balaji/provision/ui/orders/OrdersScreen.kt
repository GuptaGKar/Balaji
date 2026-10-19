package com.balaji.provision.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.balaji.provision.domain.model.formatPaise

@Composable
fun OrdersScreen() {
    val sampleOrders = remember {
        listOf(
            OrderView(
                id = "ord-1",
                publicOrderNumber = "BALAJI-1082",
                status = "out_for_delivery",
                grandTotalPaise = 54500,
                createdAt = "Today, 10:15 AM",
                items = listOf(
                    OrderItemView("Aashirvaad Shudh Chakki Atta (5kg)", 1, 24000),
                    OrderItemView("Fortune Sunlite Sunflower Oil (1L)", 2, 29000),
                    OrderItemView("Tata Salt Iodized Salt (1kg)", 1, 2800)
                )
            ),
            OrderView(
                id = "ord-2",
                publicOrderNumber = "BALAJI-1075",
                status = "delivered",
                grandTotalPaise = 31800,
                createdAt = "Yesterday, 04:30 PM",
                items = listOf(
                    OrderItemView("Fortune Everyday Basmati Rice (2 kg)", 1, 24000),
                    OrderItemView("Amul Pasteurised Butter (100g)", 1, 5800),
                    OrderItemView("Everest Turmeric Powder (100g)", 2, 7200)
                )
            )
        )
    }
    
    val orders by remember { mutableStateOf(sampleOrders) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.ListAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text("Your Orders & Deliveries", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        if (orders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("📦", fontSize = 56.sp)
                    Text("No orders placed yet", fontSize = 20.sp, fontWeight = FontWeight.Bold)
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
                items(orders, key = { it.id }) { order ->
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("#${order.publicOrderNumber}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(order.createdAt, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                OrderStatusChip(status = order.status)
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                order.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("• ${item.name}", fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(formatPaise(item.unitPricePaise), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Grand Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatPaise(order.grandTotalPaise), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                }
                
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "Cash on Delivery",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun OrderStatusChip(status: String) {
    val (color, label, icon) = when (status) {
        "pending_review" -> Triple(Color(0xFFD97706), "PENDING REVIEW", Icons.Default.PendingActions)
        "accepted" -> Triple(Color(0xFF2563EB), "ACCEPTED", Icons.Default.CheckCircle)
        "preparing" -> Triple(Color(0xFF7C3AED), "PREPARING", Icons.Default.LocalShipping)
        "out_for_delivery" -> Triple(Color(0xFF059669), "OUT FOR DELIVERY", Icons.Default.LocalShipping)
        "delivered" -> Triple(Color(0xFF16A34A), "DELIVERED ✓", Icons.Default.CheckCircle)
        "cancelled", "rejected" -> Triple(Color(0xFFDC2626), "CANCELLED", Icons.Default.Close)
        else -> Triple(Color(0xFF6B7280), status.uppercase(), Icons.Default.PendingActions)
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
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