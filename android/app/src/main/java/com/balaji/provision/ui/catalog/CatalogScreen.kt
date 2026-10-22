package com.balaji.provision.ui.catalog

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.domain.model.CartItem
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.QuantityType
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.domain.model.formatWeightGrams
import com.balaji.provision.ui.cart.CartViewModel
import com.balaji.provision.ui.components.EmptyState
import com.balaji.provision.ui.components.ErrorState
import com.balaji.provision.ui.components.LoadingState
import com.balaji.provision.ui.components.MoneyText
import com.balaji.provision.ui.components.ProductCardSkeleton
import com.balaji.provision.ui.components.ProductThumb
import com.balaji.provision.ui.components.QuantityStepper
import com.balaji.provision.ui.components.StatusPill
import com.balaji.provision.ui.components.StickyBottomBar
import com.balaji.provision.ui.components.WeightedQuantityDialog
import com.balaji.provision.ui.components.StoreTopBar
import com.balaji.provision.ui.theme.appColors

@Composable
fun CatalogScreen(
    viewModel: ProductViewModel = hiltViewModel(),
    cartViewModel: CartViewModel = hiltViewModel(),
    onNavigateToCart: () -> Unit = {}
) {
    val categories by viewModel.categories.collectAsState()
    val products by viewModel.products.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val cart by cartViewModel.cart.collectAsState()
    val quote by cartViewModel.quote.collectAsState()

    var weightedProductDialog by remember { mutableStateOf<Pair<Product, Long>?>(null) }

    weightedProductDialog?.let { (prod, initialGrams) ->
        WeightedQuantityDialog(
            product = prod,
            currentQtyBaseGrams = initialGrams,
            onDismiss = { weightedProductDialog = null },
            onConfirmQtyGrams = { qtyGrams ->
                cartViewModel.updateQuantity(prod.id, qtyGrams)
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StoreTopBar(
            title = "Catalogue",
            subtitle = "Fresh groceries, delivered locally",
            leadingIcon = Icons.Default.Store,
            trailing = {
                IconButton(onClick = { viewModel.loadData() }) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh catalogue",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        // Search and category filters stay pinned so browsing never hides them.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search rice, atta, oil, dal...") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            if (categories.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { viewModel.selectCategory(null) },
                            label = { Text("All", style = MaterialTheme.typography.labelLarge) },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                    items(categories, key = { it.id }) { category ->
                        FilterChip(
                            selected = selectedCategory == category.id,
                            onClick = { viewModel.selectCategory(category.id) },
                            label = {
                                Text(category.name, style = MaterialTheme.typography.labelLarge)
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading && products.isEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) { index ->
                        ProductCardSkeleton()
                    }
                }

                error != null && products.isEmpty() -> ErrorState(
                    message = error.orEmpty(),
                    onAction = { viewModel.loadData() }
                )

                products.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No items found",
                        message = if (searchQuery.isNotBlank()) {
                            "We could not find \"$searchQuery\" in the store catalogue."
                        } else {
                            "There are no items in this category right now."
                        },
                        actionLabel = if (searchQuery.isNotBlank()) "Clear search" else null,
                        onAction = if (searchQuery.isNotBlank()) {
                            { viewModel.setSearchQuery("") }
                        } else {
                            null
                        }
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = if (cart.items.isEmpty()) 16.dp else 104.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "catalogue_summary") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        "${products.size} results for \"$searchQuery\""
                                    } else {
                                        "${products.size} products available"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (error != null) {
                                    Text(
                                        text = error.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        items(products, key = { it.id }) { product ->
                            val cartItem = remember(cart.items, product.id) {
                                cart.items.find { it.productId == product.id }
                            }

                            ProductCard(
                                product = product,
                                cartItem = cartItem,
                                onOpenWeightDialog = { currentGrams ->
                                    weightedProductDialog = Pair(product, currentGrams)
                                },
                                onAddToCart = { qty ->
                                    cartViewModel.addItem(product.id, qty)
                                },
                                onUpdateQuantity = { newQty ->
                                    if (newQty <= 0) {
                                        cartViewModel.removeItem(product.id)
                                    } else {
                                        cartViewModel.updateQuantity(product.id, newQty)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (cart.items.isNotEmpty()) {
            StickyBottomBar {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${cart.items.size} ${if (cart.items.size == 1) "item" else "items"} in basket",
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
                    onClick = onNavigateToCart,
                    modifier = Modifier.height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View basket")
                }
            }
        }
    }
}

@Composable
fun ProductCard(
    product: Product,
    cartItem: CartItem?,
    onOpenWeightDialog: (Long) -> Unit,
    onAddToCart: (Long) -> Unit,
    onUpdateQuantity: (Long) -> Unit
) {
    val isWeighted = product.quantityType == QuantityType.WEIGHTED
    val inCart = cartItem != null
    val currentQty = cartItem?.qtyBase ?: 0L
    val haptics = LocalHapticFeedback.current

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
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProductThumb(name = product.name, size = 64, imageUrl = product.imagePath)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        product.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (!product.description.isNullOrBlank()) {
                        Text(
                            product.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MoneyText(
                            text = product.displayPrice,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        StatusPill(
                            text = product.displayStep,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    StockPill(product = product)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isWeighted) {
                    OutlinedButton(
                        onClick = { onOpenWeightDialog(if (inCart) currentQty else product.minQty) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Scale,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (inCart) {
                                "Weight: ${formatWeightGrams(currentQty)}"
                            } else {
                                "Choose weight"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (inCart) {
                        StatusPill(
                            text = "Added",
                            icon = Icons.Default.CheckCircle,
                            containerColor = MaterialTheme.appColors.successContainer,
                            contentColor = MaterialTheme.appColors.onSuccessContainer
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))

                    if (inCart) {
                        QuantityStepper(
                            value = currentQty,
                            onDecrease = { onUpdateQuantity(currentQty - product.stepQty) },
                            onIncrease = { onUpdateQuantity(currentQty + product.stepQty) },
                            decreaseContentDescription = "Decrease quantity of ${product.name}",
                            increaseContentDescription = "Increase quantity of ${product.name}"
                        )
                    } else {
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAddToCart(product.minQty)
                            },
                            enabled = product.isInStock,
                            modifier = Modifier.clearAndSetSemantics {
                                contentDescription = "Add ${product.name} to basket"
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ADD",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StockPill(product: Product) {
    when {
        !product.isInStock -> StatusPill(
            text = "Out of stock",
            icon = Icons.Default.Warning,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )

        product.isLowStock -> StatusPill(
            text = "Only a few left",
            icon = Icons.Default.Warning,
            containerColor = MaterialTheme.appColors.warningContainer,
            contentColor = MaterialTheme.appColors.onWarningContainer
        )

        else -> StatusPill(
            text = "In stock",
            icon = Icons.Default.CheckCircle,
            containerColor = MaterialTheme.appColors.successContainer,
            contentColor = MaterialTheme.appColors.onSuccessContainer
        )
    }
}
