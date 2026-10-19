package com.balaji.provision.ui.cart

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.data.repository.CatalogRepository
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import com.balaji.provision.domain.model.QuoteBreakdown
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.ui.components.WeightedQuantityDialog

@Composable
fun CartScreen(
    viewModel: CartViewModel = hiltViewModel(),
    catalogRepository: CatalogRepository = CatalogRepository(),
    onNavigateToCheckout: () -> Unit
) {
    val cart by viewModel.cart.collectAsState()
    val quote by viewModel.quote.collectAsState()
    val error by viewModel.error.collectAsState()
    
    var weightedProductDialog by remember { mutableStateOf<Pair<Product, Long>?>(null) }

    weightedProductDialog?.let { (prod, currentGrams) ->
        WeightedQuantityDialog(
            product = prod,
            currentQtyBaseGrams = currentGrams,
            onDismiss = { weightedProductDialog = null },
            onConfirmQtyGrams = { newGrams ->
                viewModel.updateQuantity(prod.id, newGrams)
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = "Cart",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Your Grocery Basket", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                
                if (cart.items.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "${cart.itemCount} items",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
        
        if (cart.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🛒", fontSize = 56.sp)
                    Text("Your cart is currently empty", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Explore our fresh catalogue and add groceries",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Free Delivery Banner
                quote?.let { q ->
                    if (q.belowChargeThreshold && q.amountToFreeDelivery > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.LocalShipping,
                                    contentDescription = "Delivery",
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    "Add ${formatPaise(q.amountToFreeDelivery)} more for FREE Local Delivery!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    } else if (!q.belowChargeThreshold) {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("🎉", fontSize = 16.sp)
                                Text(
                                    "Congratulations! You unlocked FREE Local Delivery!",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                    }
                }
                
                // Scrollable Item List + Order Summary
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cart.items, key = { it.productId }) { item ->
                        val lineInfo = quote?.lines?.find { it.productId == item.productId }
                        val product = catalogRepository.findProductById(item.productId)
                        
                        CartItemCard(
                            item = item,
                            lineInfo = lineInfo,
                            product = product,
                            onEditWeight = { prod ->
                                weightedProductDialog = Pair(prod, item.qtyBase)
                            },
                            onQuantityChange = { newQty ->
                                viewModel.updateQuantity(item.productId, newQty)
                            },
                            onRemove = {
                                viewModel.removeItem(item.productId)
                            }
                        )
                    }
                    
                    // Total Value Card dynamically positioned below the list
                    quote?.let { q ->
                        item(key = "order_summary") {
                            Spacer(modifier = Modifier.height(8.dp))
                            OrderSummaryCard(
                                quote = q,
                                onCheckoutClick = onNavigateToCheckout
                            )
                        }
                    }
                }
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
    lineInfo: QuoteBreakdown.Line?,
    product: Product?,
    onEditWeight: (Product) -> Unit,
    onQuantityChange: (Long) -> Unit,
    onRemove: () -> Unit
) {
    val isWeighted = product?.quantityType == QuantityType.WEIGHTED ||
                     (lineInfo?.displayQty?.contains("g") == true || lineInfo?.displayQty?.contains("kg") == true) ||
                     item.qtyBase >= 100

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isWeighted) "⚖️" else "🛍️", fontSize = 24.sp)
                }
                
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        lineInfo?.name ?: product?.name ?: "Item #${item.productId}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 2
                    )
                    Text(
                        "Unit Rate: ${lineInfo?.let { formatPaise(it.unitPricePaise) } ?: (product?.let { formatPaise(it.pricePaise) } ?: "-")}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        lineInfo?.let { formatPaise(it.lineTotalPaise) } ?: (product?.let { formatPaise(it.pricePaise) } ?: "-"),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Selected: ${lineInfo?.displayQty ?: if (isWeighted) "${item.qtyBase} g" else "${item.qtyBase} packs"}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (isWeighted) {
                    // Custom Weight Adjustment Button (NO +/- buttons)
                    val targetProd = product ?: Product(
                        id = item.productId,
                        sku = "WEIGHTED",
                        name = lineInfo?.name ?: "Weighted Item",
                        description = null,
                        categoryId = "",
                        imagePath = null,
                        quantityType = QuantityType.WEIGHTED,
                        pricePaise = lineInfo?.unitPricePaise ?: 6000,
                        priceBasisQty = 1000,
                        unitLabel = "kg",
                        packSizeLabel = null,
                        stepQty = 10,
                        minQty = 100,
                        maxQty = null,
                        stockQty = 50000,
                        lowStockThreshold = 5000,
                        active = true,
                        available = true
                    )
                    
                    OutlinedButton(
                        onClick = { onEditWeight(targetProd) },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Scale, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Set Weight (g / kg)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // + / - Stepper for Packed Items
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            val step = product?.stepQty ?: 1L
                            IconButton(
                                onClick = { onQuantityChange(item.qtyBase - step) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(14.dp))
                            }
                            
                            Text(
                                item.qtyBase.toString(),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            
                            IconButton(
                                onClick = { onQuantityChange(item.qtyBase + step) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
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
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Order Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Item Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPaise(quote.itemSubtotalPaise), fontWeight = FontWeight.Medium)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Delivery Charge", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (quote.deliveryFeePaise == 0L) "FREE" else formatPaise(quote.deliveryFeePaise),
                    fontWeight = FontWeight.Bold,
                    color = if (quote.deliveryFeePaise == 0L) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface
                )
            }
            
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Grand Total", fontWeight = FontWeight.Bold)
                    Text("Cash on Delivery (COD)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    formatPaise(quote.grandTotalPaise),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Button(
                onClick = onCheckoutClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Proceed to Checkout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}