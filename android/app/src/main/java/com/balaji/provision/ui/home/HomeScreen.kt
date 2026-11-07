package com.balaji.provision.ui.home

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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalShipping
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.balaji.provision.ui.components.liquidGlass
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

    // The board's "Buy Again" shelf: the customer's own order history, matched back to products
    // that are still in the catalogue. Ids come from HomeViewModel (RLS-scoped to this account)
    // and are kept only where they resolve to a live product here, so the shelf can never show a
    // discontinued item or somebody else's basket. Empty while signed out or with no orders -
    // the shelf then does not render at all, instead of showing a heading with nothing under it.
    val buyAgainGroups = remember(state.orderedProductIds, groups) {
        state.orderedProductIds
            .mapNotNull { id -> groups.firstOrNull { group -> group.variants.any { it.id == id } } }
            .take(10)
    }

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
                // The reference board reads the selector as a bold "Deliver to <name>" with the
                // locality underneath, so the two are passed separately rather than joined into
                // one " · " string. Everything here is the customer's own saved address.
                val addressLabel = address?.label?.takeIf { it.isNotBlank() }
                HomeBrandHeader(
                    addressLabel = addressLabel?.let { "Deliver to $it" }
                        ?: "Set your delivery address",
                    addressArea = if (addressLabel == null) {
                        null
                    } else {
                        address?.area?.takeIf { it.isNotBlank() }
                            ?: address?.city?.takeIf { it.isNotBlank() }
                            ?: address?.postalCode
                    },
                    initials = initials,
                    storeOpen = state.storeOpen,
                    estimatedWindow = state.estimatedWindow,
                    onOpenAccount = onNavigateToAccount,
                    onOpenAddress = onNavigateToAccount
                )
            }

            // Closed: the status panel leads, directly under the address and above the search
            // field, because "can I order?" is the only question on the screen for a customer who
            // has just found the shop shut - and that is where the closed board puts it. It
            // renders nothing while the store is open, so the open layout is untouched.
            item(key = "closed_panel") {
                ClosedStorePanel(
                    storeOpen = state.storeOpen,
                    place = ClosedPanelPlace.Home,
                    modifier = Modifier.padding(
                        start = BalajiDesign.ScreenPadding,
                        end = BalajiDesign.ScreenPadding,
                        top = 12.dp
                    )
                )
            }

            item(key = "search") {
                StoreSearchBar(
                    onClick = onNavigateToSearch,
                    placeholder = "Search for milk, atta, snacks…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }

            item(key = "offer") {
                // The manager's banners sit under search, open or closed: they are real store
                // content and there is no reason for them to vanish because of the hour.
                HomeOfferCarousel(
                    banners = state.banners,
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

            // The strip and the category mini-grid are chrome, not shelves, and the reference puts
            // them under the hero on every Home. A manager composing a product rail has said
            // nothing about either, so only the two default product rails further down step aside
            // for composed content - which is what the storefront feature actually promises.
            item(key = "service_tiles") {
                HomeServiceTiles(
                    onlinePaymentEnabled = state.onlinePaymentEnabled,
                    hasDeliveryPin = !address?.postalCode.isNullOrBlank(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
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

            if (visibleCategories.isNotEmpty()) {
                item(key = "categories_header") {
                    SectionHeader(
                        title = "Shop by Category",
                        subtitle = null,
                        actionLabel = "See All",
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
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .liquidGlass(
                                shape = RoundedCornerShape(16.dp),
                                fill = MaterialTheme.colorScheme.errorContainer
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.Transparent
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
                    if (composedSections.isEmpty() && buyAgainGroups.isNotEmpty()) {
                        item(key = "buy_again_header") {
                            SectionHeader(
                                title = "Buy Again",
                                subtitle = null,
                                actionLabel = "See All",
                                onAction = onNavigateToOrders,
                                modifier = Modifier.padding(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 8.dp
                                )
                            )
                        }
                        item(key = "buy_again_row") {
                            ProductRail(
                                groups = buyAgainGroups,
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
    onOpenCatalog: () -> Unit,
    onOpenBanner: (HomeBanner) -> Unit
) {
    val slides = remember(banners) {
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
                    // The board's headline, verbatim, and it is the same on both boards: the
                    // open and the closed reference show the identical banner. The closed message
                    // lives in the status panel above it, where "can I order?" is actually
                    // answered - the carousel advertises the catalogue, which is browsable
                    // either way.
                    title = "Daily Essentials at Great Prices",
                    subtitle = "Your local store for everyday needs"
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
        // The counter starts mid-stream so the pager can wrap forever, but the page it lands on
        // has to be a whole multiple of the slide count: otherwise `currentPage % size` opens on
        // some middle slide, and both boards show the headline slide with the first dot active.
        val startPage = (Int.MAX_VALUE / 2 / slides.size) * slides.size
        val pagerState = rememberPagerState(
            initialPage = if (slides.size > 1) startPage else 0,
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
                        // Round dots, not a sliding capsule: the board's indicator is a filled
                        // circle for the live slide and a faint one for the rest.
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (selected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
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
                .height(bannerHeight)
                // Rim only: the photograph is the fill, so there is nothing to paint behind it.
                .liquidGlass(shape),
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
        // The reference board's banner is warm white with a red headline, a dark sub-line and a
        // solid orange circle holding a white arrow - not the green "success" panel this used to
        // be. The cream is the board's own fill, so the card reads as part of the peach page
        // behind it, and the red is [BalajiDesign.BannerRed], sampled from the board's artwork:
        // a headline colour only, never a state colour.
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .liquidGlass(shape, Color(0xFFFFF7F0)),
            shape = shape,
            color = Color.Transparent
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
                        color = BalajiDesign.BannerRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = slide.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    // The board's action is a solid orange circle with a white arrow, sized for a
                    // thumb. The whole card is already the click target, so this is the signpost
                    // rather than a second, competing one.
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                BalajiBasketMark(
                    modifier = Modifier.size(82.dp),
                    basketColor = MaterialTheme.colorScheme.primary,
                    leafColor = MaterialTheme.appColors.success
                )
            }
        }
    }
}

@Composable
private fun ShopNowPill(containerColor: Color) {
    Surface(
        modifier = Modifier.liquidGlass(RoundedCornerShape(50), containerColor),
        shape = RoundedCornerShape(50),
        color = Color.Transparent
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
 * Delivery" and "Tomorrow, 7-11 AM", and neither is a Balaji fact. A closed store gets a status,
 * never a promise about the future, because nothing in this build schedules deliveries.
 *
 * It deliberately does NOT quote the delivery window. The first device pass caught that: this
 * store's `estimated_window_text` is the sentence "Same-day delivery; confirmed upon acceptance",
 * and putting 43 characters in a compact pill made the pill 230 dp wide, squeezed the address to
 * 53 dp and grew the header card to 270 dp. A badge has to be a badge; the sentence belongs on a
 * line of its own, where it can be ellipsised. See [headerDeliveryLine].
 */
internal fun storeStatusBadgeLabel(storeOpen: Boolean): String =
    if (storeOpen) "Store Open" else "Store Closed"

/**
 * The store's own delivery-window text, for the line under the address. Null while the store is
 * closed: how long a live order takes is not information a customer can use when no order can be
 * placed, and quoting it invites them to wait for a delivery that will never be created.
 */
internal fun headerDeliveryLine(storeOpen: Boolean, estimatedWindow: String?): String? =
    if (storeOpen) estimatedWindow?.trim()?.takeIf { it.isNotEmpty() } else null

/**
 * The short form quoted in the header's right-hand delivery chip.
 *
 * The reference board puts a compact ETA in that slot ("30-60 min" over "Delivery"). This store's
 * configured sentence is longer than any chip can hold, so only its first clause - the promise
 * itself - goes in the chip ("Same-day delivery"), and the rest of the sentence stays on the
 * full line elsewhere in the flow. Nothing is shortened into a different meaning: the clause ends
 * where the store ended it, at the `;`.
 */
internal fun headerDeliveryChipText(storeOpen: Boolean, estimatedWindow: String?): String? =
    headerDeliveryLine(storeOpen, estimatedWindow)
        ?.substringBefore(';')
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

/**
 * The orange brand header: store identity, the delivery location the order will actually use, and
 * the store's real status.
 *
 * The account entry point stays here rather than in the navigation pill. The reference board
 * shows an `Account` tab, but this build has no top-level account route in the pill's four
 * destinations, and the header is the only place the profile was reachable from - dropping the
 * avatar to match the mockup's notification bell would strand the screen: this build has no
 * notification backend, and a bell that does nothing is a stub. The brief calls the notification
 * entry optional (section 4), so the avatar stays in the slot the mockup gives the bell.
 *
 * The fill continues the status-bar strip the shell paints above it (see `MainAppScreen`), so the
 * first stop here is [BalajiDesign.PrimaryDeep] in both places and the seam is invisible.
 */
@Composable
private fun HomeBrandHeader(
    addressLabel: String,
    addressArea: String?,
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
            .drawWithContent {
                // Order matters for contrast, not for looks. The stops are sampled off the
                // reference board (#FA3B00 → #FE7100 → #FD9D33): white on the top stop measures
                // 3.68:1, past WCAG's 3:1 floor for large text, where the old #FF6B00 midpoint
                // (2.84:1) failed. The wordmark sits at the top-left, the deep end of this
                // gradient, and the brighter end falls away below and to the right, where the
                // opaque address card covers it. The status-bar strip above the header paints the
                // same top stop, so the two meet without a seam.
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to BalajiDesign.HeaderTop,
                        0.45f to BalajiDesign.HeaderMid,
                        1.00f to BalajiDesign.HeaderBottom
                    )
                )
                // The reference board's header carries one soft lighter arc low on the right -
                // a restrained backdrop gradient, which section 3.2 allows in headers. It is one
                // radial pass, not a blur shader, and it is centred below the card so the arc
                // grazes the right edge the way the board does.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            BalajiDesign.Highlight.copy(alpha = 0.55f),
                            BalajiDesign.Highlight.copy(alpha = 0f)
                        ),
                        center = Offset(size.width * 0.92f, size.height * 1.15f),
                        radius = size.width * 0.72f
                    )
                )
                drawContent()
            }
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
                // The board sets the second wordmark line in the same heavy display face as the
                // first, only smaller - not in body text, which is what it was before.
                Text(
                    text = "Provision Store",
                    style = BalajiText.SectionTitle,
                    color = Color.White
                )
            }
            Surface(
                shape = CircleShape,
                // Near-white disc with the deep-orange initial: 4.03:1, where white-on-orange
                // would have been 2.84:1.
                color = Color.White.copy(alpha = 0.94f),
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
                        color = BalajiDesign.PrimaryDeep
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // The board draws this selector as a near-opaque white card with a soft shadow, not as a
        // glass panel: on the orange header the liquid-glass rim's grey mid stop read as a heavy
        // grey frame around the address. Section 3.2's 0.90-0.96 white band is still respected.
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.White.copy(alpha = 0.96f),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenAddress)
                // The device pass found this row unreadable: making the card clickable merges its
                // descendants into one accessibility node, and the merged node came out with an
                // empty description - TalkBack had nothing to say about where the order ships to.
                // Stating it here is explicit rather than relying on the merge to gather the text.
                .semantics {
                    contentDescription =
                        "$addressLabel. " +
                        (addressArea?.let { "$it. " } ?: "") +
                        (headerDeliveryLine(storeOpen, estimatedWindow)
                            ?.let { "$it. " } ?: "") +
                        "${storeStatusBadgeLabel(storeOpen)}. Change delivery address"
                }
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
                    // Bold label with the chevron beside it, locality underneath: the reference
                    // board's arrangement. Both lines are the customer's own saved address.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = addressLabel,
                            style = BalajiText.ProductName,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (addressArea != null) {
                        Text(
                            text = addressArea,
                            style = BalajiText.Metadata,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // The right-hand slot is the board's ETA chip while the store takes orders, and
                // the board's "Store Closed" pill when it does not. Both readings come from the
                // server: the chip quotes the configured window's own first clause, and the pill
                // appears exactly when `store_open` is false.
                val chipText = headerDeliveryChipText(storeOpen, estimatedWindow)
                if (storeOpen && chipText != null) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = chipText,
                                style = BalajiText.Metadata,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    StatusPill(
                        text = storeStatusBadgeLabel(storeOpen),
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
            // The reference tile is a near-opaque white card with an orange outline icon on top
            // and a short label under it, and nothing else. The detail line stays dropped: four
            // tiles across 352 dp leaves 82 dp each, and two lines of small grey text under a
            // label is how a strip like this turns into mush.
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(BalajiDesign.RadiusCard),
                color = Color.White.copy(alpha = 0.96f),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        icons[index],
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = tile.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
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
        modifier = modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(16.dp),
                fill = MaterialTheme.colorScheme.surface
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
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
