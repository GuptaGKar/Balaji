package com.balaji.provision.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.QuoteBreakdown

@Composable
fun CartScreen(
    viewModel: CartViewModel = viewModel(),
    onNavigateToCheckout: () -> Unit
) {
    val cart by viewModel.cart.collectAsState()
    val quote by viewModel.quote.collectAsState()
    val error by viewModel.error.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (cart.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Empty cart",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Text("Your cart is empty", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Add some items to get started", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(cart.items) { item ->
                    CartItemCard(
                        item = item,
                        onQuantityChange = { newQty ->
                            viewModel.updateQuantity(item.productId, newQty)
                        },
                        onRemove = {
                            viewModel.removeItem(item.productId)
                        }
                    )
                }
            }
            
            quote?.let { q ->
                OrderSummaryCard(
                    quote = q,
                    onCheckoutClick = onNavigateToCheckout
                )
            }
        }
        
        error?.let { errorMsg ->
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun CartItemCard(
    item: CartItem,
    onQuantityChange: (Long) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📦", fontSize = 24.sp)
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(item.productId, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                    Text("Qty: ${item.qtyBase}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                }
                
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}

@Composable
fun OrderSummaryCard(
    quote: QuoteBreakdown,
    onCheckoutClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Order Summary", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            SummaryRow("Subtotal", formatPaise(quote.itemSubtotalPaise))
            if (quote.deliveryFeePaise > 0) {
                SummaryRow("Delivery Fee", formatPaise(quote.deliveryFeePaise))
            }
            if (quote.taxPaise > 0) {
                SummaryRow("Tax", formatPaise(quote.taxPaise))
            }
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            SummaryRow("Total", formatPaise(quote.grandTotalPaise), isTotal = true)
            
            if (quote.belowChargeThreshold && quote.amountToFreeDelivery > 0) {
                Spacer(Modifier.size(8.dp))
                Text(
                    "Add ${formatPaise(quote.amountToFreeDelivery)} more for free delivery!",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(Modifier.size(16.dp))
            Button(
                onClick = onCheckoutClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Proceed to Checkout", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, isTotal: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = if (isTotal) 16.sp else 14.sp,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            value,
            fontSize = if (isTotal) 16.sp else 14.sp,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
    }
}

fun formatPaise(paise: Long): String {
    val rupees = paise / 100.0
    return "₹${String.format("%.2f", rupees)}"
}