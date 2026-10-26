package com.balaji.provision.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.ProductVariantGroup
import com.balaji.provision.ui.account.AccountViewModel
import com.balaji.provision.ui.cart.CartViewModel
import com.balaji.provision.ui.catalog.ProductViewModel
import com.balaji.provision.ui.components.BalajiBasketMark
import com.balaji.provision.ui.components.CategoryShortcut
import com.balaji.provision.ui.components.ErrorState
import com.balaji.provision.ui.components.HorizontalProductCard
import com.balaji.provision.ui.components.InlineLoader
import com.balaji.provision.ui.components.SectionHeader
import com.balaji.provision.ui.components.StatusPill
import com.balaji.provision.ui.components.StoreSearchBar
import com.balaji.provision.ui.components.WeightedQuantityDialog
import com.balaji.provision.ui.components.categoryColors
import com.balaji.provision.ui.theme.appColors
import kotlinx.coroutines.delay

/**
 * Home: delivery location, search, a real promo banner (or a branded fallback),
 * category shortcuts and catalogue rails. Everything shown is sourced from the
 * live catalogue/store settings — no invented discounts, ratings or sales data.
 */
@Composable
fun HomeScreen(
    productViewModel: ProductViewModel,
    accountViewModel: AccountViewModel,
    cartViewModel: CartViewModel,
    onNavigateToCatalog: () -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToCategory: (categoryId: String) -> Unit = {},
    onNavigateToProduct: (productId: String) -> Unit = {},
    onNavigateToCart: () -> Unit = {},
    onNavigateToOrders: () -> Unit = {},
    onNavigateToAccount: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val accountState by accountViewModel.state.collectAsState()
    val categories by productViewModel.categories.collectAsState()
    val groups by productViewModel.variantGroups.collectAsState()
    val catalogueLoading by productViewModel.isLoading.collectAsState()
    val catalogueError by productViewModel.error.collectAsState()
    val cart by cartViewModel.cart.collectAsState()

    val cartItems = cart.items.associateBy { it.productId }
    val address = accountState.addresses.firstOrNull { it.isDefault }
        ?: accountState.addresses.firstOrNull()
    val initials = (accountState.profile?.displayName?.trim()?.firstOrNull()
        ?: 'B').uppercaseChar().toString()

    val categoryIds = groups.map { it.categoryId }.toSet()
    val visibleCategories = categories.filter { it.id in categoryIds }
    val inStockGroups = groups.filter { group -> group.variants.any { it.isInStock } }
    val freshPicks = inStockGroups.take(10)
    val moreToExplore = inStockGroups.drop(10).take(10)

    var weightProduct by remember { mutableStateOf<Product?>(null) }
    weightProduct?.let { product ->
        val existing = cartItems[product.id]
        WeightedQuantityDialog(
            product = product,
            currentQtyBaseGrams = existing?.qtyBase ?: product.minQty,
            onDismiss = { weightProduct = null },
            onConfirmQtyGrams = { grams ->
                if (existing == null) {
                    cartViewModel.addItem(product.id, grams)
                } else {
                    cartViewModel.updateQuantity(product.id, grams)
                }
                weightProduct = null
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onNavigateToAccount)
                ) {
                    Text(
                        text = "DELIVER TO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = address?.let { addr ->
                                listOfNotNull(addr.label, addr.area ?: addr.city)
                                    .joinToString(" · ")
                            } ?: "Set your delivery address",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(38.dp)
                        .clickable(onClick = onNavigateToAccount)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        item(key = "search") {
            StoreSearchBar(
                onClick = onNavigateToSearch,
                placeholder = "Search for atta, milk, snacks…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        item(key = "offer") {
            HomeOfferCarousel(
                banners = state.banners,
                storeOpen = state.storeOpen,
                estimatedWindow = state.estimatedWindow,
                onOpenCatalog = onNavigateToCatalog
            )
        }

        if (visibleCategories.isNotEmpty()) {
            item(key = "categories_header") {
                SectionHeader(
                    title = "Shop by category",
                    subtitle = "Everyday essentials from the store",
                    actionLabel = "See all",
                    onAction = onNavigateToCategories,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "categories_grid") {
                HomeCategoryGrid(
                    categories = visibleCategories.take(7),
                    allCategories = visibleCategories,
                    onOpenCategory = onNavigateToCategory,
                    onMore = onNavigateToCategories
                )
            }
        }

        if (state.error != null) {
            item(key = "store_error") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    ErrorState(
                        message = state.error.orEmpty(),
                        actionLabel = "Reload store details",
                        onAction = { viewModel.loadStoreInfo() }
                    )
                }
            }
        }

        when {
            groups.isEmpty() && catalogueLoading -> item(key = "catalogue_loading") {
                InlineLoader(modifier = Modifier.padding(vertical = 32.dp))
            }

            groups.isEmpty() && catalogueError != null -> item(key = "catalogue_error") {
                Box(modifier = Modifier.padding(vertical = 12.dp)) {
                    ErrorState(
                        message = catalogueError.orEmpty(),
                        onAction = { productViewModel.loadData() }
                    )
                }
            }

            else -> {
                if (freshPicks.isNotEmpty()) {
                    item(key = "fresh_header") {
                        SectionHeader(
                            title = "Fresh picks",
                            subtitle = "Available in today's catalogue",
                            actionLabel = "View all",
                            onAction = onNavigateToCatalog,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    item(key = "fresh_row") {
                        ProductRail(
                            groups = freshPicks,
                            cartItems = cartItems,
                            onOpenProduct = onNavigateToProduct,
                            onAdd = { product ->
                                cartViewModel.addItem(product.id, product.minQty)
                            },
                            onIncrease = { product, qty ->
                                cartViewModel.updateQuantity(
                                    product.id,
                                    (qty + product.stepQty).coerceAtMost(product.maxOrderQty)
                                )
                            },
                            onDecrease = { product, qty ->
                                cartViewModel.updateQuantity(product.id, qty - product.stepQty)
                            },
                            onWeight = { product -> weightProduct = product }
                        )
                    }
                }

                if (moreToExplore.isNotEmpty()) {
                    item(key = "more_header") {
                        SectionHeader(
                            title = "More to explore",
                            subtitle = "Pantry and daily needs",
                            actionLabel = "View all",
                            onAction = onNavigateToCatalog,
                            modifier = Modifier.padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 18.dp,
                                bottom = 8.dp
                            )
                        )
                    }
                    item(key = "more_row") {
                        ProductRail(
                            groups = moreToExplore,
                            cartItems = cartItems,
                            onOpenProduct = onNavigateToProduct,
                            onAdd = { product ->
                                cartViewModel.addItem(product.id, product.minQty)
                            },
                            onIncrease = { product, qty ->
                                cartViewModel.updateQuantity(
                                    product.id,
                                    (qty + product.stepQty).coerceAtMost(product.maxOrderQty)
                                )
                            },
                            onDecrease = { product, qty ->
                                cartViewModel.updateQuantity(product.id, qty - product.stepQty)
                            },
                            onWeight = { product -> weightProduct = product }
                        )
                    }
                }
            }
        }

        item(key = "store_strip") {
            StoreStrip(
                storeOpen = state.storeOpen,
                estimatedWindow = state.estimatedWindow,
                deliveryText = state.deliveryText,
                onlinePaymentEnabled = state.onlinePaymentEnabled,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            )
        }

        item(key = "legal") {
            Text(
                text = "Prices, availability and delivery timing are confirmed by the store. Invoices are issued from the store's billing system.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HomeCategoryGrid(
    categories: List<com.balaji.provision.domain.model.Category>,
    allCategories: List<com.balaji.provision.domain.model.Category>,
    onOpenCategory: (String) -> Unit,
    onMore: () -> Unit
) {
    val tiles = categories.map { it as com.balaji.provision.domain.model.Category? } +
        listOf(null)
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        tiles.chunked(4).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { category ->
                    if (category == null) {
                        MoreShortcut(
                            onClick = onMore,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val index = allCategories.indexOf(category)
                        val (container, content) = categoryColors(index)
                        CategoryShortcut(
                            category = category,
                            containerColor = container,
                            contentColor = content,
                            onClick = { onOpenCategory(category.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MoreShortcut(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.GridView,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
        Text(
            text = "More",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProductRail(
    groups: List<ProductVariantGroup>,
    cartItems: Map<String, com.balaji.provision.domain.model.CartItem>,
    onOpenProduct: (String) -> Unit,
    onAdd: (Product) -> Unit,
    onIncrease: (Product, Long) -> Unit,
    onDecrease: (Product, Long) -> Unit,
    onWeight: (Product) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups, key = { it.groupId }) { group ->
            val product = group.defaultVariant
            val cartItem = cartItems[product.id]
            HorizontalProductCard(
                group = group,
                product = product,
                cartItem = cartItem,
                onOpenDetails = { onOpenProduct(product.id) },
                onAdd = { onAdd(product) },
                onIncrease = { cartItem?.let { onIncrease(product, it.qtyBase) } },
                onDecrease = { cartItem?.let { onDecrease(product, it.qtyBase) } },
                onWeight = { onWeight(product) }
            )
        }
    }
}

private const val OFFER_ROTATION_MILLIS = 4_000L

private data class OfferSlide(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeOfferCarousel(
    banners: List<HomeBanner>,
    storeOpen: Boolean,
    estimatedWindow: String?,
    onOpenCatalog: () -> Unit
) {
    val slides = remember(banners, storeOpen, estimatedWindow) {
        if (banners.isNotEmpty()) {
            banners.map { banner ->
                OfferSlide(
                    id = banner.id,
                    title = banner.title,
                    subtitle = banner.subtitle,
                    imageUrl = banner.imageUrl
                )
            }
        } else {
            listOf(
                OfferSlide(
                    id = "store",
                    title = if (storeOpen) "Fresh groceries, delivered" else "We are closed right now",
                    subtitle = when {
                        !storeOpen -> "You can still browse; orders are reviewed when we reopen."
                        !estimatedWindow.isNullOrBlank() -> "Delivery in $estimatedWindow · Cash on Delivery"
                        else -> "Shop the full range · Cash on Delivery"
                    }
                ),
                OfferSlide(
                    id = "cod",
                    title = "Cash on Delivery",
                    subtitle = "Pay at your door when the order arrives"
                ),
                OfferSlide(
                    id = "essentials",
                    title = "Everyday essentials",
                    subtitle = "Browse the full range from the store"
                )
            )
        }
    }

    if (slides.isEmpty()) return

    key(slides.size) {
        val pagerState = rememberPagerState(
            initialPage = if (slides.size > 1) Int.MAX_VALUE / 2 else 0,
            pageCount = { if (slides.size > 1) Int.MAX_VALUE else 1 }
        )

        LaunchedEffect(pagerState, slides.size) {
            if (slides.size < 2) return@LaunchedEffect
            while (true) {
                delay(OFFER_ROTATION_MILLIS)
                if (!pagerState.isScrollInProgress) {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }

        Column(
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
        ) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                OfferSlideCard(
                    slide = slides[page % slides.size],
                    onClick = onOpenCatalog
                )
            }

            if (slides.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    slides.indices.forEach { index ->
                        val selected = pagerState.currentPage % slides.size == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(if (selected) 18.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferSlideCard(
    slide: OfferSlide,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    if (slide.imageUrl != null) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = slide.imageUrl,
                    contentDescription = slide.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.10f),
                                    Color.Black.copy(alpha = 0.65f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        slide.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 2
                    )
                    if (slide.subtitle.isNotBlank()) {
                        Text(
                            slide.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 2
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ShopNowPill(containerColor = Color.White)
                }
            }
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = shape,
            color = MaterialTheme.appColors.successContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.appColors.onSuccessContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = slide.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.appColors.onSuccessContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(10.dp))
                    ShopNowPill(containerColor = MaterialTheme.colorScheme.surface)
                }
                BalajiBasketMark(
                    modifier = Modifier.size(82.dp),
                    basketColor = MaterialTheme.appColors.onSuccessContainer,
                    leafColor = MaterialTheme.appColors.onSuccessContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ShopNowPill(containerColor: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Shop now",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                Icons.Default.ShoppingBag,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun StoreStrip(
    storeOpen: Boolean,
    estimatedWindow: String?,
    deliveryText: String?,
    onlinePaymentEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BalajiBasketMark(
                    modifier = Modifier.size(28.dp),
                    basketColor = MaterialTheme.colorScheme.primary,
                    leafColor = MaterialTheme.appColors.success
                )
                Text(
                    text = "Balaji Provision Store",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    text = if (storeOpen) "OPEN" else "CLOSED",
                    icon = if (storeOpen) Icons.Default.CheckCircle else null,
                    containerColor = if (storeOpen) {
                        MaterialTheme.appColors.successContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                    contentColor = if (storeOpen) {
                        MaterialTheme.appColors.onSuccessContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }
            val details = buildList {
                if (!estimatedWindow.isNullOrBlank()) add("Delivery in $estimatedWindow")
                deliveryText?.let { add(it) }
                add(if (onlinePaymentEnabled) "Cash on Delivery / online" else "Cash on Delivery only")
            }
            details.forEach { line ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
