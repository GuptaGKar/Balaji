package com.balaji.provision.ui.cart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import com.balaji.provision.domain.model.QuoteBreakdown
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.ui.components.BalajiBasketMark
import com.balaji.provision.ui.components.EmptyState
import com.balaji.provision.ui.components.FreeDeliveryProgress
import com.balaji.provision.ui.components.MoneyText
import com.balaji.provision.ui.components.OutlinedQuantityStepper
import com.balaji.provision.ui.components.ProductThumb
import com.balaji.provision.ui.components.StickyBottomBar
import com.balaji.provision.ui.components.SummaryRow
import com.balaji.provision.ui.components.WeightedQuantityDialog
import com.balaji.provision.ui.home.HomeViewModel
import com.balaji.provision.ui.theme.appColors

@Composable
fun CartScreen(
    viewModel: CartViewModel = hiltViewModel(),
    onNavigateToCheckout: () -> Unit,
    onNavigateToCatalog: () -> Unit = {},
    onBack: () -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val cart by viewModel.cart.collectAsState()
    val quote by viewModel.quote.collectAsState()
    val error by viewModel.error.collectAsState()
    val couponInput by viewModel.couponInput.collectAsState()
    val homeState by homeViewModel.state.collectAsState()
    val unavailableLines = quote?.lines?.filter { !it.available }.orEmpty()

    var showClearDialog by remember { mutableStateOf(false) }
    var weightedProductDialog by remember { mutableStateOf<Pair<Product, Long>?>(null) }

    weightedProductDialog?.let { (prod, currentGrams) ->
        WeightedQuantityDialog(
            product = prod,
            currentQtyBaseGrams = currentGrams,
            onDismiss = { weightedProductDialog = null },
            onConfirmQtyGrams = { newGrams ->
                viewModel.updateQuantity(prod.id, newGrams)
                weightedProductDialog = null
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear your cart?") },
            text = { Text("All items will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCart()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear cart")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Keep items")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: back, title, clear action.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "My Cart",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (cart.items.isEmpty()) {
                        "Your basket is empty"
                    } else {
                        "${cart.items.size} ${if (cart.items.size == 1) "item" else "items"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { showClearDialog = true },
                enabled = cart.items.isNotEmpty()
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Clear cart",
                    tint = if (cart.items.isEmpty()) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }

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
                    title = "Your cart is empty",
                    message = "Browse the store and add the groceries you need. They will be waiting here for checkout.",
                    actionLabel = "Start shopping",
                    onAction = onNavigateToCatalog
                )
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 112.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "store") {
                        StoreCard(
                            estimatedWindow = homeState.estimatedWindow,
                            storeOpen = homeState.storeOpen
                        )
                    }

                    quote?.let { q ->
                        item(key = "free_delivery") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (q.amountToFreeDelivery <= 0L) {
                                    MaterialTheme.appColors.successContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = if (q.amountToFreeDelivery <= 0L) {
                                    null
                                } else {
                                    androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FreeDeliveryProgress(
                                    subtotalPaise = q.itemSubtotalPaise,
                                    amountToFreePaise = q.amountToFreeDelivery,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    item(key = "coupon") {
                        CouponCard(
                            couponInput = couponInput,
                            appliedCode = quote?.couponCode,
                            couponApplied = quote?.couponApplied == true,
                            couponError = quote?.couponError,
                            onInputChange = viewModel::onCouponInputChange,
                            onApply = viewModel::applyCoupon,
                            onRemove = viewModel::removeCoupon
                        )
                    }

                    items(cart.items, key = { it.productId }) { item ->
                        val lineInfo = quote?.lines?.find { it.productId == item.productId }
                        val product = viewModel.findProduct(item.productId)
                        CartItemRow(
                            item = item,
                            lineInfo = lineInfo,
                            product = product,
                            onEditWeight = { prod ->
                                weightedProductDialog = Pair(prod, item.qtyBase)
                            },
                            onQuantityChange = { newQty ->
                                viewModel.updateQuantity(item.productId, newQty)
                            },
                            onRemove = { viewModel.removeItem(item.productId) }
                        )
                    }

                    item(key = "add_more") {
                        TextButton(
                            onClick = onNavigateToCatalog,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "+ Add more items",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (unavailableLines.isNotEmpty()) {
                        item(key = "quote_attention") {
                            UnavailableItemsCard(unavailableLines)
                        }
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
                Button(
                    onClick = onNavigateToCheckout,
                    enabled = quote != null && unavailableLines.isEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "Proceed to Checkout",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreCard(estimatedWindow: String?, storeOpen: Boolean) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BalajiBasketMark(
                modifier = Modifier.size(34.dp),
                basketColor = MaterialTheme.colorScheme.primary,
                leafColor = MaterialTheme.appColors.success
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Balaji Provision Store",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = when {
                        !storeOpen -> "Store closed — orders will be reviewed later"
                        !estimatedWindow.isNullOrBlank() -> "Delivery in $estimatedWindow"
                        else -> "Cash on Delivery available"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (storeOpen) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = "COD",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    lineInfo: QuoteBreakdown.Line?,
    product: Product?,
    onEditWeight: (Product) -> Unit,
    onQuantityChange: (Long) -> Unit,
    onRemove: () -> Unit
) {
    val isWeighted = product?.quantityType == QuantityType.WEIGHTED
    val name = lineInfo?.name ?: product?.name ?: "Item #${item.productId}"
    val step = product?.stepQty ?: 1L

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductThumb(name = name, size = 54, imageUrl = product?.imagePath)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (lineInfo?.available == false) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (lineInfo?.available == false) {
                        TextDecoration.LineThrough
                    } else {
                        null
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val pack = product?.packLabel?.takeIf { it.isNotBlank() }
                    ?: lineInfo?.displayQty
                if (!pack.isNullOrBlank()) {
                    Text(
                        text = pack,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = lineInfo?.let { formatPaise(it.unitPricePaise) }
                        ?: product?.let { formatPaise(it.pricePaise) }
                        ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (lineInfo?.available == false) {
                    Text(
                        text = lineInfo.error ?: "Currently unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(
                        text = lineInfo?.let { formatPaise(it.lineTotalPaise) }
                            ?: product?.let { formatPaise(it.pricePaise) }
                            ?: "—",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Remove $name from cart",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (isWeighted && product != null) {
                    OutlinedButton(
                        onClick = { onEditWeight(product) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Scale,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (isWeighted) {
                    Text(
                        text = lineInfo?.displayQty ?: "${item.qtyBase} g",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedQuantityStepper(
                        value = item.qtyBase,
                        onDecrease = {
                            if (item.qtyBase - step <= 0L) onRemove()
                            else onQuantityChange(item.qtyBase - step)
                        },
                        onIncrease = {
                            val max = product?.maxOrderQty ?: Long.MAX_VALUE
                            onQuantityChange((item.qtyBase + step).coerceAtMost(max))
                        },
                        decreaseContentDescription = "Decrease quantity of $name",
                        increaseContentDescription = "Increase quantity of $name",
                        canIncrease = product == null ||
                            product.maxOrderQty <= 0L ||
                            item.qtyBase < product.maxOrderQty
                    )
                }
            }
        }
    }
}

@Composable
private fun UnavailableItemsCard(unavailableLines: List<QuoteBreakdown.Line>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Some items need attention",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            unavailableLines.forEach { line ->
                Text(
                    "${line.name}: ${line.error ?: "currently unavailable"}",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Reduce the quantity or remove these items to continue to checkout.",
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun OrderSummaryCard(quote: QuoteBreakdown) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Bill details",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            SummaryRow(label = "Item subtotal", value = formatPaise(quote.itemSubtotalPaise))

            when {
                // Coupon applied — show its code and saving amount
                quote.couponApplied && quote.couponCode != null -> {
                    val isFreeDelivery = quote.discountPaise == 0L
                    SummaryRow(
                        label = "Coupon (${quote.couponCode})",
                        value = if (isFreeDelivery) "FREE delivery" else "-${formatPaise(quote.discountPaise)}",
                        valueColor = MaterialTheme.appColors.success
                    )
                }
                // No coupon — show generic item discount if any (e.g. future bulk/promo)
                quote.discountPaise > 0L -> SummaryRow(
                    label = "Discount",
                    value = "-${formatPaise(quote.discountPaise)}",
                    valueColor = MaterialTheme.appColors.success
                )
            }

            SummaryRow(
                label = "Delivery",
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
                label = "Total payable",
                value = formatPaise(quote.grandTotalPaise),
                emphasise = true
            )

            Text(
                "Cash on Delivery (COD)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Coupon entry card. When no coupon is applied it shows an input + Apply button.
 * When a coupon is successfully applied it shows a success chip with a remove button.
 * Errors from the backend are displayed inline.
 */
@Composable
private fun CouponCard(
    couponInput: String,
    appliedCode: String?,
    couponApplied: Boolean,
    couponError: String?,
    onInputChange: (String) -> Unit,
    onApply: () -> Unit,
    onRemove: () -> Unit
) {
    val focusManager = LocalFocusManager.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Promo / Coupon code",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Applied state
            AnimatedVisibility(
                visible = couponApplied && appliedCode != null,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.appColors.successContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.appColors.success,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = appliedCode.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.appColors.success,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove coupon",
                                tint = MaterialTheme.appColors.success,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Input state (shown when no valid coupon applied)
            AnimatedVisibility(
                visible = !couponApplied,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = couponInput,
                            onValueChange = onInputChange,
                            placeholder = {
                                Text(
                                    "Enter code",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    onApply()
                                }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                onApply()
                            },
                            enabled = couponInput.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(56.dp)
                        ) {
                            Text("Apply", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Inline error
                    if (!couponError.isNullOrBlank()) {
                        Text(
                            text = couponError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
