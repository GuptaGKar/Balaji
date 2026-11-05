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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.balaji.provision.ui.components.BalajiGlassSurface
import com.balaji.provision.ui.components.ClosedPanelPlace
import com.balaji.provision.ui.components.ClosedStorePanel
import com.balaji.provision.ui.theme.BalajiDesign
import com.balaji.provision.ui.theme.BalajiText
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.ProductVariantGroup
import com.balaji.provision.domain.model.toVariantGroups
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.ui.components.MiniCartBar
import com.balaji.provision.ui.components.NAV_PILL_RESERVE_DP
import com.balaji.provision.ui.components.navPillReserve
import com.balaji.provision.ui.components.deliveryWindowLabel
import androidx.compose.ui.platform.LocalConfiguration
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
    val cartQuote by cartViewModel.quote.collectAsState()
    val address = accountState.addresses.firstOrNull { it.isDefault }
        ?: accountState.addresses.firstOrNull()
    val initials = (accountState.profile?.displayName?.trim()?.firstOrNull()
        ?: 'B').uppercaseChar().toString()

    // The basket quote must use the same PIN rule the cart and checkout use, so
    // the free-delivery hint below never disagrees with them.
    LaunchedEffect(address?.postalCode) { cartViewModel.setDeliveryPin(address?.postalCode) }

    // Composed content is re-read whenever Home is entered. The repository decides whether that
    // is actually a request based on how long ago it last fetched, so coming back from a product
    // costs nothing - but a section a manager published while the app was open now shows up on
    // the next visit instead of after a process restart. Without this call nothing ever re-read
    // the storefront: init() ran once per ViewModel and the ViewModel survives tab changes.
    LaunchedEffect(Unit) { viewModel.refreshStorefront() }

    val categoryIds = groups.map { it.categoryId }.toSet()
    val visibleCategories = categories.filter { it.id in categoryIds }
    val inStockGroups = groups.filter { group -> group.variants.any { it.isInStock } }
    // Catalogue order is alphabetical, so the first ten groups were four sauces and
    // two atta packs. Taking one group per category in turn shows the whole store.
    val variedGroups = remember(inStockGroups) { roundRobinBy(inStockGroups) { it.categoryId } }
    val freshPicks = variedGroups.take(10)
    val moreToExplore = variedGroups.drop(10).take(10)

    // Composed content replaces the default shelves entirely once a manager has made any. With
    // none - un-migrated database, network failure, or simply nothing composed - this is empty
    // and every block below renders exactly as it did before the feature existed. Prices and
    // stock come from the catalogue objects, never from the section payload, which is ids only.
    val composedSections = remember(state.sections, groups, categories) {
        resolveStorefrontSections(
            sections = state.sections,
            products = groups.flatMap { group -> group.variants },
            categories = categories
        )
    }

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

    // Only describe delivery when the server quote matches what is in the basket.
    val basketHint = cartQuote
        ?.takeIf { q -> q.lines.count { !it.isFreeItem } == cart.items.size }
        ?.let { q ->
            when {
                q.deliveryFeePaise <= 0L -> "Free delivery unlocked"
                q.amountToFreeDelivery > 0L ->
                    "Add ${formatPaise(q.amountToFreeDelivery)} more for free delivery"
                else -> null
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // The navigation pill and the basket bar both float over this list, so the list has
            // to be scrollable past them or its last row can never be reached.
            contentPadding = PaddingValues(bottom = navPillReserve(basketBarVisible = cart.items.isNotEmpty()))
        ) {
            item(key = "header") {
                HomeBrandHeader(
                    addressLine = address?.let { addr ->
                        listOfNotNull(addr.label, addr.area ?: addr.city)
                            .joinToString(" · ")
                            .ifBlank { addr.postalCode }
                    } ?: "Set your delivery address",
                    initials = initials,
                    storeOpen = state.storeOpen,
                    estimatedWindow = state.estimatedWindow,
                    onOpenAccount = onNavigateToAccount,
                    onOpenAddress = onNavigateToAccount
                )
            }

            item(key = "search") {
                StoreSearchBar(
                    onClick = onNavigateToSearch,
                    placeholder = "Search for atta, milk, snacks…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            if (composedSections.isEmpty()) {
                item(key = "service_tiles") {
                    HomeServiceTiles(
                        onlinePaymentEnabled = state.onlinePaymentEnabled,
                        hasDeliveryPin = !address?.postalCode.isNullOrBlank(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }

            item(key = "offer") {
                // Closed: the status panel leads, because "can I order?" is the only question on
                // the screen for a customer who has just found the shop shut. The manager's
                // banners stay underneath rather than being hidden - they are real store content
                // and there is no reason for them to vanish because of the hour.
                ClosedStorePanel(
                    storeOpen = state.storeOpen,
                    place = ClosedPanelPlace.Home,
                    modifier = Modifier.padding(
                        start = BalajiDesign.ScreenPadding,
                        end = BalajiDesign.ScreenPadding,
                        top = 12.dp
                    )
                )
                HomeOfferCarousel(
                    banners = state.banners,
                    storeOpen = state.storeOpen,
                    estimatedWindow = state.estimatedWindow,
                    onOpenCatalog = onNavigateToCatalog,
                    onOpenBanner = { banner ->
                        // Only follow a destination that still exists in the live
                        // catalogue; otherwise fall back to the catalogue, never a dead end.
                        val id = banner.destinationId
                        when {
                            banner.destinationType == "category" && id != null &&
                                categories.any { it.id == id } -> onNavigateToCategory(id)
                            banner.destinationType == "product" && id != null &&
                                groups.any { g -> g.groupId == id || g.variants.any { it.id == id } } ->
                                onNavigateToProduct(id)
                            else -> onNavigateToCatalog()
                        }
                    }
                )
            }

            if (composedSections.isNotEmpty()) {
                composedSections.forEach { section ->
                    item(key = "composed_${section.id}") {
                        ComposedSectionBlock(
                            section = section,
                            cartItems = cartItems,
                            allCategories = visibleCategories,
                            onOpenCategory = onNavigateToCategory,
                            onOpenProduct = onNavigateToProduct,
                            onAdd = { product -> cartViewModel.addItem(product.id, product.minQty) },
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

            if (composedSections.isEmpty() && visibleCategories.isNotEmpty()) {
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
                    if (composedSections.isEmpty() && freshPicks.isNotEmpty()) {
                        item(key = "fresh_header") {
                            SectionHeader(
                                title = "From every aisle",
                                subtitle = "A pick from each category, in stock today",
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

                    if (composedSections.isEmpty() && moreToExplore.isNotEmpty()) {
                        item(key = "more_header") {
                            SectionHeader(
                                title = "More to explore",
                                subtitle = "More from the store",
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


        MiniCartBar(
            itemCount = cart.items.size,
            detail = basketHint,
            onClick = onNavigateToCart,
            // Stacked on top of the floating pill rather than behind it: the bar is the only
            // place the customer sees the running total, and the pill is never scrolled away.
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = NAV_PILL_RESERVE_DP.dp)
        )
    }
}

/**
 * One manager-composed section.
 *
 * Column count follows the window, not the device type, so one build lays out correctly on a
 * phone, a foldable and a tablet. Tiles keep the palette index from the full category list
 * rather than from the section's own slice, so the same category is the same colour wherever a
 * manager places it.
 *
 * There is deliberately no "See all" action here: the manager chose these exact items in this
 * order, and a link that jumps to the whole catalogue would undercut that choice.
 */
@Composable
private fun ComposedSectionBlock(
    section: ResolvedSection,
    cartItems: Map<String, com.balaji.provision.domain.model.CartItem>,
    allCategories: List<com.balaji.provision.domain.model.Category>,
    onOpenCategory: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onAdd: (Product) -> Unit,
    onIncrease: (Product, Long) -> Unit,
    onDecrease: (Product, Long) -> Unit,
    onWeight: (Product) -> Unit
) {
    Column {
        SectionHeader(
            title = section.title,
            subtitle = section.subtitle,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        when (section) {
            is ResolvedProductRail -> ProductRail(
                groups = section.products.toVariantGroups(),
                cartItems = cartItems,
                onOpenProduct = onOpenProduct,
                onAdd = onAdd,
                onIncrease = onIncrease,
                onDecrease = onDecrease,
                onWeight = onWeight,
                // A manager-composed rail is one of the new surfaces the brief asked to read
                // "₹60" instead of "₹60.00". The automatic rails above keep the exact amount.
                wholeRupees = true
            )

            is ResolvedCategoryGrid -> {
                val columns = (LocalConfiguration.current.screenWidthDp / 96)
                    .toInt()
                    .coerceIn(4, 6)
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    section.categories.chunked(columns).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { category ->
                                val (container, content) = categoryColors(
                                    allCategories.indexOf(category).coerceAtLeast(0)
                                )
                                CategoryShortcut(
                                    category = category,
                                    containerColor = container,
                                    contentColor = content,
                                    onClick = { onOpenCategory(category.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Short rows are padded so the last row keeps the same tile width
                            // instead of stretching three tiles across four slots.
                            repeat((columns - row.size).coerceAtLeast(0)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
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
internal fun ProductRail(
    groups: List<ProductVariantGroup>,
    cartItems: Map<String, com.balaji.provision.domain.model.CartItem>,
    onOpenProduct: (String) -> Unit,
    onAdd: (Product) -> Unit,
    onIncrease: (Product, Long) -> Unit,
    onDecrease: (Product, Long) -> Unit,
    onWeight: (Product) -> Unit,
    wholeRupees: Boolean = false
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
                onWeight = { onWeight(product) },
                wholeRupees = wholeRupees
            )
        }
    }
}

private const val OFFER_ROTATION_MILLIS = 4_000L

private data class OfferSlide(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val banner: HomeBanner? = null,
    val overlay: Boolean = true
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeOfferCarousel(
    banners: List<HomeBanner>,
    storeOpen: Boolean,
    estimatedWindow: String?,
    onOpenCatalog: () -> Unit,
    onOpenBanner: (HomeBanner) -> Unit
) {
    val slides = remember(banners, storeOpen, estimatedWindow) {
        if (banners.isNotEmpty()) {
            banners.map { banner ->
                OfferSlide(
                    id = banner.id,
                    title = banner.title,
                    subtitle = banner.subtitle,
                    imageUrl = banner.imageUrl,
                    banner = banner,
                    overlay = banner.overlay
                )
            }
        } else {
            listOf(
                OfferSlide(
                    id = "store",
                    title = if (storeOpen) "Fresh groceries, delivered" else "We are closed right now",
                    subtitle = when {
                        !storeOpen -> "You can still browse; orders are reviewed when we reopen."
                        !estimatedWindow.isNullOrBlank() -> "${deliveryWindowLabel(estimatedWindow)} · Cash on Delivery"
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
                    onClick = {
                        val banner = slides[page % slides.size].banner
                        if (banner != null) onOpenBanner(banner) else onOpenCatalog()
                    }
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
        // A fixed 140 dp crop turned wide posters into a thin strip on tablets.
        val bannerHeight = (LocalConfiguration.current.screenWidthDp / 2.4f).coerceIn(140f, 220f).dp
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight),
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
                if (slide.overlay) {
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

/**
 * What the header badge says.
 *
 * Pure because every alternative here is a lie waiting to happen: the mockups show "30-60 min
 * Delivery" and "Tomorrow, 7-11 AM", and neither is a Balaji fact. Only the store's own
 * configured window is ever quoted, and only while the store is open; a closed store gets a
 * status, never a promise about the future, because nothing in this build schedules deliveries.
 */
internal fun storeStatusBadgeLabel(storeOpen: Boolean, estimatedWindow: String?): String = when {
    !storeOpen -> "Store Closed"
    !estimatedWindow.isNullOrBlank() -> deliveryWindowLabel(estimatedWindow)
    else -> "Store Open"
}

/**
 * The orange brand header: store identity, the delivery location the order will actually use, and
 * the store's real status.
 *
 * The account entry point stays here rather than in the navigation pill. The reference board
 * shows an `Account` tab, but this build has no top-level account route in the pill's four
 * destinations, and the header is the only place the profile was reachable from - dropping the
 * avatar to match the mockup would strand the screen.
 */
@Composable
private fun HomeBrandHeader(
    addressLine: String,
    initials: String,
    storeOpen: Boolean,
    estimatedWindow: String?,
    onOpenAccount: () -> Unit,
    onOpenAddress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(BalajiDesign.Primary, BalajiDesign.PrimaryDeep)
                )
            )
            .padding(
                horizontal = BalajiDesign.ScreenPadding,
                vertical = 14.dp
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Balaji",
                    style = BalajiText.Brand,
                    color = Color.White
                )
                Text(
                    text = "Provision Store",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier
                    .size(44.dp)
                    .clickable(onClick = onOpenAccount)
                    .semantics {
                        contentDescription = "Your account and saved addresses"
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = initials,
                        style = BalajiText.SectionTitle,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        BalajiGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenAddress),
            cornerRadius = 14.dp,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.60f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BalajiDesign.InnerGap)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Delivering to",
                        style = BalajiText.Metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = addressLine,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    text = storeStatusBadgeLabel(storeOpen, estimatedWindow),
                    icon = if (storeOpen) Icons.Default.CheckCircle else Icons.Default.Store,
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
        }
    }
}

/**
 * A capability this build genuinely has, shown in the Home strip.
 *
 * The reference board's tiles read "30-60 min", "100% Safe & Hygienic" and "Easy Reorder". Two of
 * those are claims this app cannot back up - there is no hygiene promise in the data model and no
 * reorder RPC - so the strip lists what the customer can verify by tapping: how they pay, whether
 * the address is serviceable, that orders have a trackable history, and that the store confirms
 * prices. Nothing here is marketing copy about speed.
 */
internal data class ServiceTile(val label: String, val detail: String)

internal fun homeServiceTiles(
    onlinePaymentEnabled: Boolean,
    hasDeliveryPin: Boolean
): List<ServiceTile> = buildList {
    add(
        if (onlinePaymentEnabled) {
            ServiceTile("UPI & cards", "Pay online at checkout")
        } else {
            ServiceTile("Cash on Delivery", "Pay when it arrives")
        }
    )
    add(
        if (hasDeliveryPin) {
            ServiceTile("Delivers here", "Checked against your PIN code")
        } else {
            ServiceTile("Set your address", "Charges depend on your PIN")
        }
    )
    add(ServiceTile("Order history", "Every order has a status"))
    add(ServiceTile("Store-priced", "Prices set by the store, not the app"))
}

/** The capability strip under search. Hidden entirely once a manager composes the page. */
@Composable
private fun HomeServiceTiles(
    onlinePaymentEnabled: Boolean,
    hasDeliveryPin: Boolean,
    modifier: Modifier = Modifier
) {
    val tiles = homeServiceTiles(onlinePaymentEnabled, hasDeliveryPin)
    val icons = listOf(
        Icons.Default.Wallet,
        Icons.Default.VerifiedUser,
        Icons.Default.History,
        Icons.Default.Store
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(BalajiDesign.InnerGap)
    ) {
        tiles.forEachIndexed { index, tile ->
            BalajiGlassSurface(
                modifier = Modifier.weight(1f),
                cornerRadius = BalajiDesign.RadiusCard,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        icons[index],
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = tile.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = tile.detail,
                        style = BalajiText.Metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
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
                if (!estimatedWindow.isNullOrBlank()) add(deliveryWindowLabel(estimatedWindow))
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

/** Interleaves items so each key appears in turn: A1, B1, C1, A2, B2, ... */
private fun <T> roundRobinBy(items: List<T>, key: (T) -> String): List<T> {
    val queues = items.groupBy(key).values.map { ArrayDeque(it) }
    val result = ArrayList<T>(items.size)
    while (queues.any { it.isNotEmpty() }) {
        queues.forEach { queue -> queue.removeFirstOrNull()?.let(result::add) }
    }
    return result
}
