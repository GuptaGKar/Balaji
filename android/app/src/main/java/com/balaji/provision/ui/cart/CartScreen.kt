package com.balaji.provision.ui.cart

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import com.balaji.provision.domain.model.QuoteBreakdown
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.ui.components.EmptyState
import com.balaji.provision.ui.components.MoneyText
import com.balaji.provision.ui.components.ProductThumb
import com.balaji.provision.ui.components.QuantityStepper
import com.balaji.provision.ui.components.StatusPill
import com.balaji.provision.ui.components.StickyBottomBar
import com.balaji.provision.ui.components.StoreTopBar
import com.balaji.provision.ui.components.SummaryRow
import com.balaji.provision.ui.components.WeightedQuantityDialog
import com.balaji.provision.ui.theme.appColors

@Composable
fun CartScreen(
    viewModel: CartViewModel = hiltViewModel(),
    onNavigateToCheckout: () -> Unit,
    onNavigateToCatalog: () -> Unit = {}
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

    Column(modifier = Modifier.fillMaxSize()) {
        StoreTopBar(
            title = "Your basket",
            subtitle = if (cart.items.isEmpty()) {
                "Nothing added yet"
            } else {
                "${cart.items.size} ${if (cart.items.size == 1) "item" else "items"} ready to deliver"
            },
            leadingIcon = Icons.Default.ShoppingCart
        )

        error?.let { errorMsg ->
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (cart.items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = Icons.Default.ShoppingCart,
                    title = "Your basket is empty",
                    message = "Browse the catalogue and add the groceries you need. They will be waiting here for checkout.",
                    actionLabel = "Browse catalogue",
                    onAction = onNavigateToCatalog
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                quote?.let { q ->
                    DeliveryPromiseBanner(quote = q)
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 104.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(cart.items, key = { it.productId }) { item ->
                        val lineInfo = quote?.lines?.find { it.productId == item.productId }
                        val product = viewModel.findProduct(item.productId)

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

                    quote?.let { q ->
                        item(key = "order_summary") {
                            OrderSummaryCard(quote = q)
                        }
                    } ?: item(key = "quote_retry") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    "Could not update your total. Check your connection.",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Button(
                                    onClick = { viewModel.refreshQuote() },
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Text("Retry total")
                                }
                            }
                        }
                    }
                }
            }

            StickyBottomBar {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total payable (COD)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        text = formatPaise(quote?.grandTotalPaise ?: 0L),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = onNavigateToCheckout,
                    enabled = quote != null,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Proceed to checkout")
                }
            }
        }
    }
}

@Composable
private fun DeliveryPromiseBanner(quote: QuoteBreakdown) {
    val unlocked = !quote.belowChargeThreshold
    if (!unlocked && quote.amountToFreeDelivery <= 0L) return
    Surface(
        color = if (unlocked) {
            MaterialTheme.appColors.successContainer
        } else {
            MaterialTheme.appColors.warningContainer
        },
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Default.CheckCircle else Icons.Default.LocalShipping,
                contentDescription = null,
                tint = if (unlocked) {
                    MaterialTheme.appColors.onSuccessContainer
                } else {
                    MaterialTheme.appColors.onWarningContainer
                },
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = if (unlocked) {
                    "You unlocked FREE local delivery!"
                } else {
                    "Add ${formatPaise(quote.amountToFreeDelivery)} more for FREE local delivery"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (unlocked) {
                    MaterialTheme.appColors.onSuccessContainer
                } else {
                    MaterialTheme.appColors.onWarningContainer
                }
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProductThumb(
                    name = lineInfo?.name ?: product?.name ?: item.productId,
                    size = 52,
                    imageUrl = product?.imagePath
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        lineInfo?.name ?: product?.name ?: "Item #${item.productId}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "Unit rate ${lineInfo?.let { formatPaise(it.unitPricePaise) } ?: (product?.let { formatPaise(it.pricePaise) } ?: "-")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        text = lineInfo?.let { formatPaise(it.lineTotalPaise) }
                            ?: (product?.let { formatPaise(it.pricePaise) } ?: "-"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove ${lineInfo?.name ?: product?.name ?: "item"} from basket",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                    )
                }
            }

            Divider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusPill(
                    text = lineInfo?.displayQty ?: if (isWeighted) "${item.qtyBase} g" else "${item.qtyBase} packs",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isWeighted) {
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Scale,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set weight", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    val step = product?.stepQty ?: 1L
                    QuantityStepper(
                        value = item.qtyBase,
                        onDecrease = { onQuantityChange(item.qtyBase - step) },
                        onIncrease = { onQuantityChange(item.qtyBase + step) },
                        decreaseContentDescription = "Decrease quantity of ${lineInfo?.name ?: product?.name ?: "item"}",
                        increaseContentDescription = "Increase quantity of ${lineInfo?.name ?: product?.name ?: "item"}",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun OrderSummaryCard(quote: QuoteBreakdown) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Order summary",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            SummaryRow(label = "Item subtotal", value = formatPaise(quote.itemSubtotalPaise))

            if (quote.discountPaise > 0L) {
                SummaryRow(
                    label = "Discount",
                    value = "-${formatPaise(quote.discountPaise)}",
                    valueColor = MaterialTheme.appColors.success
                )
            }

            SummaryRow(
                label = "Delivery charge",
                value = if (quote.deliveryFeePaise == 0L) "FREE" else formatPaise(quote.deliveryFeePaise),
                valueColor = if (quote.deliveryFeePaise == 0L) {
                    MaterialTheme.appColors.success
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            if (quote.taxPaise > 0L) {
                SummaryRow(label = "Tax", value = formatPaise(quote.taxPaise))
            }

            if (quote.feeRuleNote.isNotBlank()) {
                Text(
                    quote.feeRuleNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            SummaryRow(
                label = "Grand total",
                value = formatPaise(quote.grandTotalPaise),
                emphasise = true,
                valueColor = MaterialTheme.colorScheme.primary
            )

            Text(
                "Cash on Delivery (COD)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
