package com.balaji.provision.ui.cart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
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
import com.balaji.provision.domain.model.formatWeightGrams
import com.balaji.provision.domain.model.lineTotalPaise
import com.balaji.provision.ui.account.AccountViewModel
import com.balaji.provision.ui.components.ClosedPanelPlace
import com.balaji.provision.ui.components.ClosedStorePanel
import com.balaji.provision.ui.components.EmptyState
import com.balaji.provision.ui.components.liquidGlass
import com.balaji.provision.ui.components.MoneyText
import com.balaji.provision.ui.components.NAV_PILL_RESERVE_DP
import com.balaji.provision.ui.components.OutlinedQuantityStepper
import com.balaji.provision.ui.components.ProductThumb
import com.balaji.provision.ui.components.SlideToConfirm
import com.balaji.provision.ui.components.StickyBottomBar
import com.balaji.provision.ui.components.WeightedQuantityDialog
import com.balaji.provision.ui.home.HomeViewModel
import com.balaji.provision.ui.theme.BalajiDesign
import com.balaji.provision.ui.theme.BalajiText
import com.balaji.provision.ui.theme.appColors

@Composable
fun CartScreen(
    viewModel: CartViewModel = hiltViewModel(),
    onNavigateToCheckout: () -> Unit,
    onNavigateToCatalog: () -> Unit = {},
    onBack: () -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel()
) {
    val cart by viewModel.cart.collectAsState()
    val quote by viewModel.quote.collectAsState()
    val error by viewModel.error.collectAsState()
    val couponInput by viewModel.couponInput.collectAsState()
    val homeState by homeViewModel.state.collectAsState()
    val accountState by accountViewModel.state.collectAsState()
    val unavailableLines = quote?.lines?.filter { !it.available }.orEmpty()

    // Per-PIN delivery pricing must apply in the cart too. Only checkout used to
    // pass the PIN, so the cart quoted the store-wide fee and disagreed with the
    // values the store configured for the customer's PIN code.
    val deliveryPin = accountState.addresses.firstOrNull { it.isDefault }?.postalCode
        ?: accountState.addresses.firstOrNull()?.postalCode
    LaunchedEffect(Unit) { accountViewModel.refresh() }
    LaunchedEffect(deliveryPin) { viewModel.setDeliveryPin(deliveryPin) }

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
            // The reference cart names the count in the title and puts the destructive action in
            // words on the right ("Clear"); a lone red bin read as an icon to guess at, and the
            // separate "3 items" line was a second place to look for the same number.
            Text(
                text = if (cart.items.isEmpty()) {
                    "Your Cart"
                } else {
                    "Your Cart (${cart.items.size})"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            TextButton(
                onClick = { showClearDialog = true },
                enabled = cart.items.isNotEmpty()
            ) {
                Text(
                    text = "Clear",
                    fontWeight = FontWeight.SemiBold,
                    color = if (cart.items.isEmpty()) {
                        MaterialTheme.colorScheme.outline
                    } else {
                        MaterialTheme.colorScheme.primary
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
                        // The action bar is a sibling below the list, not an overlay, so only a
                        // breathing gap is needed here; 112 dp left a dead white band under the
                        // bill on device.
                        bottom = 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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

                    // The reference puts the coupon between the lines and the bill: it is part
                    // of the basket, not a promotion banner above it.
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

                    // Free-item coupon: the customer names which gift they want.
                    quote?.let { q ->
                        if (q.freeItemOptions.isNotEmpty()) {
                            item(key = "free_item_picker") {
                                FreeItemPickerCard(
                                    options = q.freeItemOptions,
                                    selectedId = q.freeItemSelectedId,
                                    couponCode = q.couponCode,
                                    error = q.freeItemError,
                                    onSelect = viewModel::selectFreeItem
                                )
                            }
                        }
                    }

                    // The coupon gift is server-added, so it is not a cart item.
                    quote?.let { q ->
                        q.lines.firstOrNull { it.isFreeItem }?.let { freeLine ->
                            val gift = q.freeItemOptions.firstOrNull { it.productId == freeLine.productId }
                            item(key = "free_item_line") {
                                FreeItemRow(
                                    line = freeLine,
                                    couponCode = q.couponCode,
                                    imagePath = gift?.imagePath,
                                    regularPricePaise = gift?.regularPricePaise ?: 0L
                                )
                            }
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
                                containerColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.liquidGlass(
                                shape = RoundedCornerShape(16.dp),
                                fill = MaterialTheme.colorScheme.errorContainer
                            )
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

            StickyBottomBar(
                // The navigation pill is drawn over the bottom of the window, and this bar holds
                // the only route out of the basket, so it is lifted clear of the pill rather than
                // allowed to sit behind it.
                modifier = Modifier.padding(bottom = NAV_PILL_RESERVE_DP.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ClosedStorePanel(
                        storeOpen = homeState.storeOpen,
                        place = ClosedPanelPlace.Cart
                    )
                    // Slide, not tap: this is the one place a thumb at the bottom of a scrolling
                    // page can hit an expensive action by accident. It says "Checkout" rather
                    // than "Place order" because address, payment and review still have to happen
                    // - the label has to describe the gesture it actually performs.
                    SlideToConfirm(
                        label = "Slide to Checkout",
                        // The total is in the bill and in the sticky bar; repeating it inside
                        // the slide label crowded the text and the reference has no amount here.
                        sublabel = null,
                        enabled = quote != null && unavailableLines.isEmpty() && homeState.storeOpen,
                        onConfirm = onNavigateToCheckout
                    )
                }
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
    val unitPaise = lineInfo?.unitPricePaise ?: product?.pricePaise ?: 0L
    val totalPaise = lineInfo?.lineTotalPaise
        ?: product?.let { lineTotalPaise(it.pricePaise, it.priceBasisQty, item.qtyBase) }
        ?: 0L
    val multiplePacks = !isWeighted && item.qtyBase > step
    // The pack-size line describes the pack, not the quantity; a weighted line has to name
    // the weight the customer actually chose, which is server data on the quote line.
    val pack = if (isWeighted) {
        lineInfo?.displayQty ?: formatWeightGrams(item.qtyBase)
    } else {
        product?.packLabel?.takeIf { it.isNotBlank() } ?: lineInfo?.displayQty
    }
    // A reference amount only. Orders and quotes never read it; the server price is the
    // line total below, and the struck value is the catalogue MRP scaled to this line.
    val lineMrpPaise = product?.mrpPaise
        ?.takeIf { it > product.pricePaise }
        ?.let { mrp -> if (multiplePacks) mrp * item.qtyBase else mrp }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(label = "Remove $name from cart") {
                        onRemove()
                        true
                    }
                )
            }
            .liquidGlass(
                shape = RoundedCornerShape(BalajiDesign.RadiusCard),
                fill = MaterialTheme.colorScheme.surface
            ),
        shape = RoundedCornerShape(BalajiDesign.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        // Elevation 0: a shadow under this transparent glass card renders as a grey band
        // around every cart row on device, so the rim alone defines the edge.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductThumb(name = name, size = 60, imageUrl = product?.imagePath)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = name,
                    style = BalajiText.ProductName,
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
                if (!pack.isNullOrBlank()) {
                    Text(
                        text = pack,
                        style = BalajiText.Metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                MoneyText(
                    text = formatPaise(totalPaise),
                    style = BalajiText.Price,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (lineMrpPaise != null) {
                    // MRP on its own line. Inline three amounts in a 100 dp column clipped
                    // the ADD/stepper column on device, and a struck price that wraps mid-number
                    // is worse than a taller card.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = formatPaise(lineMrpPaise),
                            style = BalajiText.Mrp.copy(textDecoration = TextDecoration.LineThrough),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (product.hasVisibleDiscount) {
                            Text(
                                text = "${product.discountPercent}% OFF",
                                style = BalajiText.Metadata.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.appColors.success
                            )
                        }
                    }
                }
                // When a line is more than one pack, the bold amount is the line total and the
                // unit rate has to stay visible, otherwise "₹60" next to a "1" stepper reads as
                // a price change. Weighted lines always need their rate for the same reason.
                val rateText = when {
                    isWeighted && unitPaise > 0L ->
                        "${formatPaise(unitPaise)} / ${product?.priceUnitLabel ?: "kg"}"
                    multiplePacks -> "${formatPaise(unitPaise)} each"
                    else -> null
                }
                if (rateText != null) {
                    Text(
                        text = rateText,
                        style = BalajiText.Metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (lineInfo?.available == false) {
                    Text(
                        text = lineInfo.error ?: "Currently unavailable",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (isWeighted && product != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    OutlinedButton(
                        onClick = { onEditWeight(product) },
                        shape = RoundedCornerShape(BalajiDesign.RadiusPill),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(BalajiDesign.ControlHeight)
                    ) {
                        Icon(
                            Icons.Default.Scale,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = onRemove,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            "Remove",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (isWeighted) {
                Text(
                    text = lineInfo?.displayQty ?: formatWeightGrams(item.qtyBase),
                    style = BalajiText.Metadata,
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

@Composable
private fun UnavailableItemsCard(unavailableLines: List<QuoteBreakdown.Line>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                fill = MaterialTheme.colorScheme.errorContainer
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
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
        shape = RoundedCornerShape(BalajiDesign.RadiusHero),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(BalajiDesign.RadiusHero),
                fill = MaterialTheme.colorScheme.surface
            )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BillRow(label = "Item Total", value = formatPaise(quote.itemSubtotalPaise))

            // Inset and rounded, like the reference. A full-bleed strip touched the card
            // edges and read as a banner inside the bill rather than a saving on it.
            if (quote.discountPaise > 0L) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.appColors.successContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "You Save",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.appColors.onSuccessContainer
                    )
                    MoneyText(
                        text = "-${formatPaise(quote.discountPaise)}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.appColors.onSuccessContainer
                    )
                }
            }

            BillRow(
                label = "Delivery Charges",
                value = formatPaise(quote.deliveryFeePaise),
                valueColor = if (quote.deliveryFeePaise == 0L) {
                    MaterialTheme.appColors.success
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            // The old basket dedicated a whole card to the free-delivery nudge. One sentence
            // under the fee it belongs to says the same thing without pushing the bill down.
            if (quote.deliveryFeePaise > 0L && quote.amountToFreeDelivery > 0L) {
                Text(
                    text = "Add ${formatPaise(quote.amountToFreeDelivery)} more for free delivery",
                    style = BalajiText.Metadata,
                    color = MaterialTheme.appColors.success
                )
            }

            // A coupon gift costs nothing, so it is listed rather than discounted.
            if (quote.lines.any { it.isFreeItem }) {
                BillRow(
                    label = "Free item" + (quote.couponCode?.let { " ($it)" } ?: ""),
                    value = "FREE",
                    valueColor = MaterialTheme.appColors.success
                )
            }

            if (quote.taxPaise > 0L) {
                BillRow(label = "Tax", value = formatPaise(quote.taxPaise))
            }

            if (quote.feeRuleNote.isNotBlank()) {
                Text(
                    quote.feeRuleNote,
                    style = BalajiText.Metadata,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Divider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Payable",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                MoneyText(
                    text = formatPaise(quote.grandTotalPaise),
                    style = BalajiText.Total,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** One line of the bill: label on the left, tabular amount on the right. */
@Composable
private fun BillRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        MoneyText(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

/**
 * Coupon entry card. When no coupon is applied it shows an input + Apply button.
 * When a coupon is successfully applied it shows a success chip with a remove button.
 * Errors from the backend are displayed inline.
 */
/**
 * Gift chooser for a free-item coupon. The server owns the pool, the free quantity
 * and the price; the customer only names which product they want, and the re-quote
 * answers with the ₹0 line.
 */
@Composable
private fun FreeItemPickerCard(
    options: List<QuoteBreakdown.FreeItemOption>,
    selectedId: String?,
    couponCode: String?,
    error: String?,
    onSelect: (String?) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        // Elevation 0: a shadow under a transparent glass fill read as a grey halo on device.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                fill = MaterialTheme.colorScheme.surface
            )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.appColors.success,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Choose your free item",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedId == null) {
                            "Pick one gift" + (couponCode?.let { " for $it" } ?: "")
                        } else {
                            "Free item added to your order"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedId == null) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.appColors.success
                        }
                    )
                }
            }

            options.forEach { option ->
                val selected = option.productId == selectedId
                Card(
                    onClick = { onSelect(if (selected) null else option.productId) },
                    enabled = option.claimable,
                    shape = RoundedCornerShape(14.dp),
                    border = if (selected) {
                        androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.appColors.success)
                    } else {
                        androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.appColors.successContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProductThumb(name = option.name, imageUrl = option.imagePath, size = 44)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (option.claimable) option.displayQty else "Currently unavailable",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (option.claimable) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                        if (option.regularPricePaise > 0L) {
                            Text(
                                text = formatPaise(option.regularPricePaise),
                                style = MaterialTheme.typography.labelSmall,
                                textDecoration = TextDecoration.LineThrough,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "FREE",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.appColors.success
                        )
                        if (selected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Selected as your free item",
                                tint = MaterialTheme.appColors.success,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            error?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * The gift line: regular price struck through, charged nothing, labelled FREE.
 * The amount comes from the server quote, never from a client-side guess.
 */
@Composable
private fun FreeItemRow(
    line: QuoteBreakdown.Line,
    couponCode: String?,
    imagePath: String?,
    regularPricePaise: Long
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.appColors.success.copy(alpha = 0.6f)
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        // The gift row is a glass surface like every other card in the basket; the green fill
        // and border carry the "free" state, the rim keeps it in the same material family.
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                fill = MaterialTheme.appColors.successContainer
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProductThumb(name = line.name, imageUrl = imagePath, size = 54)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = line.displayQty + (couponCode?.let { " · via $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                if (regularPricePaise > 0L) {
                    Text(
                        text = formatPaise(regularPricePaise),
                        style = MaterialTheme.typography.labelSmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "FREE",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.appColors.success
                )
            }
        }
    }
}

/**
 * Coupon row. Collapsed it is a single line, like the reference: the code field is not worth
 * a permanent card in a basket most customers never apply a coupon to. Tapping the row opens
 * the field; a successful apply collapses it back to the applied state.
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
    val applied = couponApplied && !appliedCode.isNullOrBlank()
    var expanded by rememberSaveable { mutableStateOf(false) }

    // A failed apply has to show its reason; leaving the row collapsed would hide the error.
    LaunchedEffect(couponError) {
        if (!couponError.isNullOrBlank()) expanded = true
    }
    LaunchedEffect(applied) {
        if (applied) expanded = false
    }

    Card(
        shape = RoundedCornerShape(BalajiDesign.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(BalajiDesign.RadiusCard),
                fill = MaterialTheme.colorScheme.surface
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = !applied,
                        onClickLabel = if (expanded) "Close coupon entry" else "Enter a coupon code"
                    ) { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = if (applied) {
                        MaterialTheme.appColors.success
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = if (applied) "Coupon $appliedCode applied" else "Apply Coupon",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (applied) {
                        MaterialTheme.appColors.success
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (applied) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(BalajiDesign.MinTouchTarget)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove coupon $appliedCode",
                            tint = MaterialTheme.appColors.success,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = if (expanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowRight
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && !applied,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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
