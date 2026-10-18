package com.balaji.provision.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavController
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.TextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.unit.dp

@Composable
fun CartScreen(
    viewModel: CartViewModel = viewModel(),
    onNavigateToCheckout: () -> Unit
) {
    val cart by viewModel.cart.collectAsState()
    val quote by viewModel.quote.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (cart.items.isEmpty()) {
            // Empty cart state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material.icons.outlined.ShoppingCart
                        .let { Icon(it, contentDescription = "Empty cart", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, size = 64.dp) }
                    Text("Your cart is empty", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Add some delicious products to get started", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { /* Navigate to catalog */ }) {
                        Text("Browse Products")
                    }
                }
            }
        } else {
            // Cart items
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
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
            
            // Order summary
            quote?.let { quote ->
                OrderSummaryCard(
                    quote = quote,
                    onCheckoutClick = onNavigateToCheckout
                )
            }
        }
        
        // Error message
        error?.let { errorMsg ->
            androidx.compose.material3.Snackbar(
                modifier = Modifier.fillMaxWidth(),
                action = { Text("Dismiss", onClick = { viewModel.clearError() }) }
            ) {
                Text(errorMsg)
            }
        }
    }
}

@Composable
fun CartItemCard(
    item: CartItemView,
    onQuantityChange: (Long) -> Unit,
    onRemove: () -> Unit
) {
    // This would map from the cart item to a display item
    // For now, showing a placeholder
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Product image placeholder
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Background(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text("📦", fontSize = 32.sp)
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Product Name", fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                    Text("₹0.00", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                }
            }
            
            // Quantity & Remove
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantitySelector(
                    qty = 1,
                    onIncrement = { },
                    onDecrement = { },
                    step = 1,
                    minQty = 1
                )
                
                IconButton(onClick = { }) {
                    Icon(androidx.compose.material.icons.outlined.Delete, contentDescription = "Remove")
                }
            }
        }
    }
}

@Composable
fun OrderSummaryCard(
    quote: QuoteBreakdownView,
    onCheckoutClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Order Summary", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            // Items subtotal
            SummaryRow("Subtotal", formatPaise(quote.itemSubtotalPaise))
            
            // Delivery fee
            if (quote.deliveryFeePaise > 0) {
                SummaryRow("Delivery Fee", formatPaise(quote.deliveryFeePaise))
            }
            
            // Tax
            if (quote.taxPaise > 0) {
                SummaryRow("Tax", formatPaise(quote.taxPaise))
            }
            
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            // Grand total
            SummaryRow("Total", formatPaise(quote.grandTotalPaise), isTotal = true)
            
            // Fee note
            quote.feeRuleNote?.let { note ->
                if (note.isNotBlank()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Text(note, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            // Below threshold message
            if (quote.belowChargeThreshold && quote.amountToFreeDelivery > 0) {
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                Text(
                    "Add ${formatPaise(quote.amountToFreeDelivery)} more for free delivery!",
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
            }
            
            // Checkout button
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
            Button(
                onClick = { /* onCheckoutClick */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
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

// View model types for UI
data class CartItemView(
    val productId: String,
    val name: String,
    val qtyBase: Long,
    val displayQty: String,
    val unitPricePaise: Long,
    val lineTotalPaise: Long
)

data class QuoteBreakdownView(
    val itemSubtotalPaise: Long = 0,
    val discountPaise: Long = 0,
    val deliveryFeePaise: Long = 0,
    val feeRuleNote: String = "",
    val taxPaise: Long = 0,
    val grandTotalPaise: Long = 0,
    val belowChargeThreshold: Boolean = false,
    val amountToFreeDelivery: Long = 0
)

fun formatPaise(paise: Long): String {
    val rupees = paise / 100.0
    return "₹${String.format("%.2f", rupees)}"
}