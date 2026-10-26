package com.balaji.provision.ui.catalog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.ProductVariantGroup
import com.balaji.provision.ui.cart.CartViewModel
import com.balaji.provision.ui.components.EmptyState
import com.balaji.provision.ui.components.ErrorState
import com.balaji.provision.ui.components.InlineLoader
import com.balaji.provision.ui.components.ProductGridCard
import com.balaji.provision.ui.components.StoreSearchBar
import com.balaji.provision.ui.components.WeightedQuantityDialog
import com.balaji.provision.ui.theme.appColors

private enum class SortOption(val label: String) {
    DEFAULT("Default"),
    NAME("Name: A to Z"),
    PRICE_LOW("Price: Low to High"),
    PRICE_HIGH("Price: High to Low")
}

/**
 * Product listing: back navigation, category title, search, sort/filter
 * controls and a two-column grid. Data comes from the live catalogue; the only
 * local operations are sorting and hiding out-of-stock packs.
 */
@Composable
fun CatalogScreen(
    viewModel: ProductViewModel,
    cartViewModel: CartViewModel,
    categoryId: String,
    focusSearch: Boolean,
    initialQuery: String = "",
    onBack: () -> Unit,
    onNavigateToCart: () -> Unit = {},
    onNavigateToProduct: (productId: String) -> Unit = {}
) {
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val groups by viewModel.variantGroups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val cart by cartViewModel.cart.collectAsState()

    val targetCategory = categoryId.takeIf { it.isNotBlank() && it != "all" }

    var searchOpen by rememberSaveable { mutableStateOf(focusSearch) }
    var sortOption by rememberSaveable { mutableStateOf(SortOption.DEFAULT) }
    var inStockOnly by rememberSaveable { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var filterMenuOpen by remember { mutableStateOf(false) }
    var weightDialogProduct by remember { mutableStateOf<Product?>(null) }

    // A new listing destination owns its category and search state. Passing the
    // query through navigation keeps the pinned catalogue search real: results
    // are filtered from the live catalogue instead of being a decorative field.
    LaunchedEffect(targetCategory, initialQuery, focusSearch) {
        if (viewModel.selectedCategory.value != targetCategory) {
            viewModel.selectCategory(targetCategory)
        }
        viewModel.setSearchQuery(initialQuery)
        searchOpen = focusSearch || initialQuery.isNotBlank()
    }

    weightDialogProduct?.let { product ->
        val current = cart.items.find { it.productId == product.id }?.qtyBase
        WeightedQuantityDialog(
            product = product,
            currentQtyBaseGrams = current ?: product.minQty,
            onDismiss = { weightDialogProduct = null },
            onConfirmQtyGrams = { grams ->
                cartViewModel.updateQuantity(product.id, grams)
                weightDialogProduct = null
            }
        )
    }

    val categoryName = remember(categories, targetCategory) {
        categories.firstOrNull { it.id == targetCategory }?.name
    }
    val title = when {
        targetCategory == null -> "All products"
        else -> categoryName ?: "Products"
    }

    val visibleGroups = remember(groups, sortOption, inStockOnly) {
        var list = groups
        if (inStockOnly) {
            list = list.filter { group -> group.variants.any { it.isInStock } }
        }
        when (sortOption) {
            SortOption.DEFAULT -> list
            SortOption.NAME -> list.sortedBy { it.baseName.lowercase() }
            SortOption.PRICE_LOW -> list.sortedBy { it.defaultVariant.pricePaise }
            SortOption.PRICE_HIGH -> list.sortedByDescending { it.defaultVariant.pricePaise }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header: back, title, search.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = if (searchOpen) "Hide search" else "Search products",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (searchOpen || searchQuery.isNotBlank()) {
            StoreSearchBar(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                autoFocus = focusSearch && searchQuery.isBlank(),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Sort / filter / availability row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box {
                ControlPill(
                    icon = Icons.Default.SwapVert,
                    label = "Sort",
                    onClick = { sortMenuOpen = true }
                )
                DropdownMenu(
                    expanded = sortMenuOpen,
                    onDismissRequest = { sortMenuOpen = false }
                ) {
                    SortOption.values().forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    option.label,
                                    fontWeight = if (option == sortOption) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            },
                            onClick = {
                                sortOption = option
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }

            Box {
                ControlPill(
                    icon = Icons.Default.Tune,
                    label = "Filter",
                    onClick = { filterMenuOpen = true },
                    active = inStockOnly
                )
                DropdownMenu(
                    expanded = filterMenuOpen,
                    onDismissRequest = { filterMenuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("In stock only") },
                        trailingIcon = {
                            if (inStockOnly) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.appColors.success
                                )
                            }
                        },
                        onClick = {
                            inStockOnly = !inStockOnly
                            filterMenuOpen = false
                        }
                    )
                }
            }

            val inStockCount = remember(groups) {
                groups.count { group -> group.variants.any { it.isInStock } }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { inStockOnly = !inStockOnly },
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (inStockOnly) {
                        MaterialTheme.appColors.successContainer
                    } else {
                        MaterialTheme.appColors.successContainer.copy(alpha = 0.6f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .padding(0.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.appColors.success,
                                shape = CircleShape,
                                modifier = Modifier.fillMaxSize()
                            ) {}
                        }
                        Text(
                            text = "In stock ($inStockCount)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.appColors.onSuccessContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                isLoading && groups.isEmpty() -> InlineLoader()

                error != null && groups.isEmpty() -> ErrorState(
                    message = error.orEmpty(),
                    onAction = { viewModel.loadData() }
                )

                visibleGroups.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.SearchOff,
                        title = "No items found",
                        message = when {
                            searchQuery.isNotBlank() ->
                                "We could not find \"$searchQuery\" in this category."
                            inStockOnly ->
                                "Everything in this category is out of stock right now."
                            else -> "There are no items in this category right now."
                        },
                        actionLabel = if (searchQuery.isNotBlank()) "Clear search" else null,
                        onAction = if (searchQuery.isNotBlank()) {
                            { viewModel.setSearchQuery("") }
                        } else {
                            null
                        }
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(visibleGroups, key = { it.groupId }) { group ->
                        ProductGrid(
                            group = group,
                            cartItems = cart.items.associateBy { it.productId },
                            onOpenDetails = {
                                onNavigateToProduct(group.defaultVariant.id)
                            },
                            onAdd = { product ->
                                cartViewModel.addItem(product.id, product.minQty)
                            },
                            onIncrease = { product, cartQty ->
                                cartViewModel.updateQuantity(
                                    product.id,
                                    (cartQty + product.stepQty).coerceAtMost(product.maxOrderQty)
                                )
                            },
                            onDecrease = { product, cartQty ->
                                cartViewModel.updateQuantity(product.id, cartQty - product.stepQty)
                            },
                            onWeight = { product -> weightDialogProduct = product }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductGrid(
    group: ProductVariantGroup,
    cartItems: Map<String, com.balaji.provision.domain.model.CartItem>,
    onOpenDetails: () -> Unit,
    onAdd: (Product) -> Unit,
    onIncrease: (Product, Long) -> Unit,
    onDecrease: (Product, Long) -> Unit,
    onWeight: (Product) -> Unit
) {
    val product = group.defaultVariant
    val cartItem = cartItems[product.id]
    ProductGridCard(
        group = group,
        product = product,
        cartItem = cartItem,
        onOpenDetails = onOpenDetails,
        onAdd = { onAdd(product) },
        onIncrease = { cartItem?.let { onIncrease(product, it.qtyBase) } },
        onDecrease = { cartItem?.let { onDecrease(product, it.qtyBase) } },
        onWeight = { onWeight(product) }
    )
}

@Composable
private fun ControlPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
