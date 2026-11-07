package com.balaji.provision

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.balaji.provision.ui.components.NavPill
import com.balaji.provision.ui.components.NAV_PILL_BOTTOM_MARGIN_DP
import com.balaji.provision.ui.account.AccountViewModel
import com.balaji.provision.ui.auth.AuthViewModel
import com.balaji.provision.ui.auth.LoginScreen
import com.balaji.provision.ui.cart.CartScreen
import com.balaji.provision.ui.cart.CartViewModel
import com.balaji.provision.ui.catalog.CatalogScreen
import com.balaji.provision.ui.catalog.ProductDetailsLoading
import com.balaji.provision.ui.catalog.ProductDetailsScreen
import com.balaji.provision.ui.catalog.ProductViewModel
import com.balaji.provision.ui.categories.CategoriesScreen
import com.balaji.provision.ui.checkout.CheckoutScreen
import com.balaji.provision.ui.home.HomeScreen
import com.balaji.provision.ui.onboarding.CustomerDetailsScreen
import com.balaji.provision.ui.orders.OrderTrackingScreen
import com.balaji.provision.ui.orders.OrdersScreen
import com.balaji.provision.ui.profile.LegalPage
import com.balaji.provision.ui.profile.LegalScreen
import com.balaji.provision.ui.profile.ProfileScreen
import com.balaji.provision.ui.requests.ProductRequestsScreen
import com.balaji.provision.ui.splash.SplashScreen
import com.balaji.provision.ui.staff.StaffApp
import com.balaji.provision.ui.theme.BalajiDesign
import com.balaji.provision.ui.theme.BalajiTheme
import com.balaji.provision.ui.theme.ThemeViewModel
import com.balaji.provision.ui.update.UpdateSurface
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.balaji.provision.push.PushChannels.ensure(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        enableEdgeToEdge()
        setContent {
            if (BuildConfig.IS_STAFF) {
                BalajiTheme {
                    MainAppScreen()
                }
            } else {
                val themeViewModel: ThemeViewModel = hiltViewModel()
                val darkTheme by themeViewModel.darkTheme.collectAsState()
                BalajiTheme(darkTheme = darkTheme) {
                    MainAppScreen(
                        isDarkTheme = darkTheme,
                        onToggleTheme = themeViewModel::setDarkTheme
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppScreen(
    isDarkTheme: Boolean = false,
    onToggleTheme: (Boolean) -> Unit = {}
) {
    // Overlay splash over the live app; startup work continues underneath so
    // this never adds an artificial delay to first interaction.
    var splashVisible by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1100)
        splashVisible = false
    }

    // The Home header paints its brand orange behind the status bar, the way the reference board
    // does. The status-bar strip above the inset content therefore has to be painted with the
    // header rather than left showing the page background, and the bar's icons have to flip to
    // white while it is showing. Only Home asks for this; every other route keeps the old look.
    var brandStatusBar by remember { mutableStateOf(false) }

    ApplySystemBarAppearance(brandStatusBar)

    // Play handles updates for Play-distributed builds; direct builds provide their own prompt.
    UpdateSurface()
    // Keeps this install registered for push once the session is available (both flavours).
    hiltViewModel<com.balaji.provision.push.PushSyncViewModel>()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // The status-bar strip that continues the Home header upwards. It is the header's own top
        // stop (see [BalajiDesign.HeaderTop]), so the seam between the strip and the header is one
        // colour and does not read as a line. Drawn before the content box, which starts below the
        // bar, so it is only ever visible in the bar itself.
        if (brandStatusBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(BalajiDesign.HeaderTop)
            )
        }

        // The app content stays inside the system bars, while the splash paints
        // over the whole window (including the bar areas) so launch is edge to
        // edge instead of letterboxed. The shell's Scaffold explicitly opts out
        // of its own system-bar padding: this is the only inset owner, which
        // prevents the doubled top/bottom gaps that made the app look shrunk.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            if (BuildConfig.IS_STAFF) {
                StaffApp()
            } else {
                CustomerApp(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onBrandStatusBarChange = { brandStatusBar = it }
                )
            }
        }

        AnimatedVisibility(
            visible = splashVisible,
            exit = fadeOut(animationSpec = tween(durationMillis = 320))
        ) {
            SplashScreen()
        }
    }
}

/**
 * Keep the status/navigation bar icons legible when the in-app theme does not
 * match the system theme. [enableEdgeToEdge] only reads the system setting at
 * launch; the customer app can switch theme independently.
 *
 * [brandStatusBar] is true while the Home header is painting the status-bar strip orange. Orange
 * is a mid-tone, so the bar's clock and battery icons have to go white there even in light theme,
 * which is what the reference board shows.
 */
@Composable
private fun ApplySystemBarAppearance(brandStatusBar: Boolean) {
    val view = LocalView.current
    val darkIcons = !brandStatusBar &&
        MaterialTheme.colorScheme.background.luminance() > 0.5f
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = darkIcons
                isAppearanceLightNavigationBars = darkIcons
            }
        }
    }
}

@Composable
private fun CustomerApp(
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onBrandStatusBarChange: (Boolean) -> Unit = {}
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val accountViewModel: AccountViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()
    val accountState by accountViewModel.state.collectAsState()
    val profile = accountState.profile

    // Every branch below the shell draws on the page background, so the orange status-bar strip
    // has to be taken back down whenever the shell is not what is on screen - sign-out, the
    // first-run details form, an error screen - or the bar would stay orange over them.
    val inShell = authState.session != null &&
        accountState.hasLoaded &&
        profile != null &&
        profile.role == "customer" &&
        !profile.displayName.isNullOrBlank() &&
        accountState.addresses.isNotEmpty()
    LaunchedEffect(inShell) {
        if (!inShell) onBrandStatusBarChange(false)
    }

    when {
        authState.session == null -> LoginScreen(viewModel = authViewModel)

        !accountState.hasLoaded -> CustomerLoadingScreen()

        profile == null -> CustomerAccountErrorScreen(
            message = accountState.error ?: "Could not load your account.",
            onRetry = { accountViewModel.refresh(force = true) },
            onSignOut = authViewModel::signOut
        )

        profile.role != "customer" -> CustomerAccountErrorScreen(
            message = "This account belongs to store staff. Use the Balaji Staff app instead.",
            onRetry = { accountViewModel.refresh(force = true) },
            onSignOut = authViewModel::signOut
        )

        profile.displayName.isNullOrBlank() || accountState.addresses.isEmpty() ->
            CustomerDetailsScreen(
                displayName = profile.displayName,
                phone = profile.phone ?: authState.session?.phone,
                hasAddress = accountState.addresses.isNotEmpty(),
                isSaving = accountState.isSaving,
                accountError = accountState.error,
                onSaveName = { name, onResult ->
                    accountViewModel.updateDisplayName(name) { success, message ->
                        onResult(success, message)
                    }
                },
                onSaveAddress = { draft, onResult ->
                    accountViewModel.saveAddress(draft, null) { success, _, message ->
                        onResult(success, message)
                    }
                },
                onSignOut = authViewModel::signOut
            )

        else -> CustomerShell(
            accountViewModel = accountViewModel,
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme,
            onBrandStatusBarChange = onBrandStatusBarChange
        )
    }
}

@Composable
private fun CustomerLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
            Text("Loading your account…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CustomerAccountErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Could not load your account",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text("Try again")
            }
            OutlinedButton(onClick = onSignOut) {
                Text("Sign out")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerShell(
    accountViewModel: AccountViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    onBrandStatusBarChange: (Boolean) -> Unit = {}
) {
    val navController = rememberNavController()
    val cartViewModel: CartViewModel = hiltViewModel()
    val productViewModel: ProductViewModel = hiltViewModel()
    val favouritesViewModel: com.balaji.provision.ui.favourites.FavouritesViewModel = hiltViewModel()
    val cart by cartViewModel.cart.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()

    // Home is the only route whose header runs behind the status bar, so it is the only one that
    // asks the shell for the orange strip and the white bar icons. Re-run on every destination
    // change, including the moment the graph first resolves.
    LaunchedEffect(currentRoute) {
        onBrandStatusBarChange(currentRoute == "home")
    }
    // The pill is drawn over the page rather than reserving a strip, so it only appears on the
    // screens that were built to clear it: the four tabs and the product listing they lead to.
    // Full-screen flows (product details, checkout, account, order tracking, requests,
    // favourites, legal) show no pill, which also means nothing there can end up hidden behind
    // one. The old strip reserved space on every route; an overlay cannot, so the set is
    // deliberately narrower.
    val showBottomBar = currentRoute == "home" || currentRoute == "categories" ||
        currentRoute == "orders" || currentRoute == "cart" ||
        currentRoute.startsWith("listing/")

    Scaffold(
        // MainAppScreen already applies safeDrawingPadding around the whole
        // app. Opting out here stops the Scaffold from adding the status/nav
        // bar insets a second time (the cause of the extra gap at the top and
        // bottom of every shell screen).
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AppNavHost(
                navController = navController,
                cartViewModel = cartViewModel,
                productViewModel = productViewModel,
                favouritesViewModel = favouritesViewModel,
                accountViewModel = accountViewModel,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )

            // The customer pill floats over the content instead of reserving a strip, so it is
            // drawn here rather than in Scaffold's bottomBar. Every screen under this scaffold
            // pays for it with com.balaji.provision.ui.components.navPillReserve() bottom
            // padding, or its last row would scroll into a region the pill covers.
            //
            // This shell is customer-only (MainAppScreen routes staff to StaffApp, which owns
            // its own navigation), so there is no staff variant of the bar to keep in sync.
            if (showBottomBar) {
                NavPill(
                    navController = navController,
                    cartCount = cart.items.size,
                    onSelect = { route -> navigateToTab(navController, route) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = NAV_PILL_BOTTOM_MARGIN_DP.dp)
                )
            }
        }
    }
}

fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    cartViewModel: CartViewModel,
    productViewModel: ProductViewModel,
    favouritesViewModel: com.balaji.provision.ui.favourites.FavouritesViewModel,
    accountViewModel: AccountViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
    val favouriteState by favouritesViewModel.state.collectAsState()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                productViewModel = productViewModel,
                accountViewModel = accountViewModel,
                cartViewModel = cartViewModel,
                onNavigateToCatalog = {
                    navController.navigate(listingRoute("all", focusSearch = false))
                },
                onNavigateToSearch = {
                    navController.navigate(listingRoute("all", focusSearch = true))
                },
                onNavigateToCategory = { categoryId ->
                    navController.navigate(listingRoute(categoryId, focusSearch = false))
                },
                onNavigateToProduct = { productId ->
                    navController.navigate("product/$productId")
                },
                onNavigateToCart = { navigateToTab(navController, "cart") },
                onNavigateToOrders = { navigateToTab(navController, "orders") },
                onNavigateToAccount = { navController.navigate("account") },
                onNavigateToCategories = { navigateToTab(navController, "categories") }
            )
        }
        composable("categories") {
            CategoriesScreen(
                productViewModel = productViewModel,
                onNavigateToSearch = { query ->
                    navController.navigate(listingRoute("all", focusSearch = true, query = query))
                },
                onOpenCategory = { categoryId ->
                    navController.navigate(listingRoute(categoryId, focusSearch = false))
                },
                // A composed rail on the Categories page shows real products, so tapping one has
                // to reach its details exactly as it does from Home.
                onOpenProduct = { productId ->
                    navController.navigate("product/$productId")
                }
            )
        }
        composable(
            route = "listing/{categoryId}?focusSearch={focusSearch}&query={query}",
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType },
                navArgument("focusSearch") {
                    type = NavType.BoolType
                    defaultValue = false
                },
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getString("categoryId").orEmpty()
            val focusSearch = backStackEntry.arguments?.getBoolean("focusSearch") ?: false
            val searchQuery = backStackEntry.arguments?.getString("query").orEmpty()

            CatalogScreen(
                viewModel = productViewModel,
                cartViewModel = cartViewModel,
                categoryId = categoryId,
                focusSearch = focusSearch,
                initialQuery = searchQuery,
                onBack = { navController.popBackStack() },
                onNavigateToCart = { navigateToTab(navController, "cart") },
                onNavigateToProduct = { productId ->
                    navController.navigate("product/$productId")
                }
            )
        }
        composable(
            route = "product/{productId}",
            arguments = listOf(navArgument("productId") { type = NavType.StringType })
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId").orEmpty()
            val products by productViewModel.products.collectAsState()
            val allGroups by productViewModel.variantGroups.collectAsState()
            val isLoading by productViewModel.isLoading.collectAsState()
            val catalogueError by productViewModel.error.collectAsState()
            val cart by cartViewModel.cart.collectAsState()

            // Resolve the pack group from the live catalogue. The id passed in is
            // either a standalone product or any member of a variant group.
            val group = remember(productId, allGroups, products) {
                productViewModel.findVariantGroup(productId)
                    ?: allGroups.firstOrNull { it.variants.any { v -> v.id == productId } }
            }

            if (group != null) {
                ProductDetailsScreen(
                    group = group,
                    cart = cart,
                    onBack = { navController.popBackStack() },
                    onAddToCart = { id, qty -> cartViewModel.addItem(id, qty) },
                    onUpdateQuantity = { id, qty ->
                        if (qty <= 0) cartViewModel.removeItem(id)
                        else cartViewModel.updateQuantity(id, qty)
                    },
                    onRemoveFromCart = { id -> cartViewModel.removeItem(id) },
                    onOpenCart = { navController.navigate("cart") },
                    favouriteIds = favouriteState.ids,
                    onToggleFavourite = { favouritesViewModel.toggle(it) }
                )
            } else {
                ProductMissing(
                    loading = isLoading || (products.isEmpty() && catalogueError == null),
                    error = catalogueError,
                    onRetry = { productViewModel.loadData() },
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable("cart") {
            CartScreen(
                viewModel = cartViewModel,
                onNavigateToCheckout = { navController.navigate("checkout") },
                onNavigateToCatalog = {
                    navController.navigate(listingRoute("all", focusSearch = false))
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navigateToTab(navController, "home")
                    }
                }
            )
        }
        composable("checkout") {
            CheckoutScreen(
                cartViewModel = cartViewModel,
                onNavigateBack = { navController.popBackStack() },
                onOrderPlaced = {
                    cartViewModel.acknowledgeOrderPlacement()
                    navigateToTab(navController, "orders")
                }
            )
        }
        composable("orders") {
            OrdersScreen(
                onOpenOrder = { orderId ->
                    navController.navigate("order/$orderId")
                }
            )
        }
        composable(
            route = "order/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getString("orderId").orEmpty()
            OrderTrackingScreen(
                orderId = orderId,
                onBack = { navController.popBackStack() }
            )
        }
        composable("account") {
            ProfileScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navigateToTab(navController, "home")
                    }
                },
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme,
                onOpenPrivacy = { navController.navigate("legal/privacy") },
                onOpenTerms = { navController.navigate("legal/terms") },
                onOpenAccountDeletion = { navController.navigate("legal/account-deletion") },
                onOpenRequests = { navController.navigate("requests") },
                onOpenFavourites = {
                    favouritesViewModel.load()
                    navController.navigate("favourites")
                }
            )
        }
        composable("requests") {
            ProductRequestsScreen(onBack = { navController.popBackStack() })
        }
        composable("favourites") {
            com.balaji.provision.ui.favourites.FavouritesScreen(
                onBack = { navController.popBackStack() },
                onOpenProduct = { productId -> navController.navigate("product/$productId") }
            )
        }
        composable(
            route = "legal/{page}",
            arguments = listOf(navArgument("page") { type = NavType.StringType })
        ) { backStackEntry ->
            val page = when (backStackEntry.arguments?.getString("page")) {
                "terms" -> LegalPage.TERMS
                "account-deletion" -> LegalPage.ACCOUNT_DELETION
                else -> LegalPage.PRIVACY
            }
            LegalScreen(page = page, onBack = { navController.popBackStack() })
        }
    }
}

@Composable
private fun ProductMissing(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    if (loading) {
        ProductDetailsLoading(onBack = onBack)
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Text("Product details", style = MaterialTheme.typography.titleMedium)
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (error != null) {
                com.balaji.provision.ui.components.ErrorState(
                    message = error,
                    onAction = onRetry
                )
            } else {
                com.balaji.provision.ui.components.EmptyState(
                    icon = androidx.compose.material.icons.Icons.Default.ShoppingCart,
                    title = "Product unavailable",
                    message = "This product may have been removed from the catalogue.",
                    actionLabel = "Back",
                    onAction = onBack
                )
            }
        }
    }
}

fun listingRoute(categoryId: String, focusSearch: Boolean, query: String = ""): String {
    val base = "listing/$categoryId?focusSearch=$focusSearch"
    return if (query.isBlank()) base else "$base&query=${Uri.encode(query)}"
}
