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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.balaji.provision.ui.splash.SplashScreen
import com.balaji.provision.ui.staff.StaffApp
import com.balaji.provision.ui.theme.BalajiTheme
import com.balaji.provision.ui.theme.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    ApplySystemBarAppearance()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                    onToggleTheme = onToggleTheme
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
 */
@Composable
private fun ApplySystemBarAppearance() {
    val view = LocalView.current
    val darkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
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
    onToggleTheme: (Boolean) -> Unit
) {
    val authViewModel: AuthViewModel = hiltViewModel()
    val accountViewModel: AccountViewModel = hiltViewModel()
    val authState by authViewModel.state.collectAsState()
    val accountState by accountViewModel.state.collectAsState()
    val profile = accountState.profile

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
            onToggleTheme = onToggleTheme
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
    onToggleTheme: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val cartViewModel: CartViewModel = hiltViewModel()
    val productViewModel: ProductViewModel = hiltViewModel()
    val cart by cartViewModel.cart.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val showBottomBar = !currentRoute.startsWith("product/") &&
        !currentRoute.startsWith("legal/")

    Scaffold(
        // MainAppScreen already applies safeDrawingPadding around the whole
        // app. Opting out here stops the Scaffold from adding the status/nav
        // bar insets a second time (the cause of the extra gap at the top and
        // bottom of every shell screen).
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(
                    navController = navController,
                    cartCount = cart.items.size
                )
            }
        }
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
                accountViewModel = accountViewModel,
                isDarkTheme = isDarkTheme,
                onToggleTheme = onToggleTheme
            )
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
    accountViewModel: AccountViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit
) {
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
                    onOpenCart = { navigateToTab(navController, "cart") }
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
                onOpenAccountDeletion = { navController.navigate("legal/account-deletion") }
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

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    cartCount: Int
) {
    val destinations = listOf(
        NavItem("home", "Home", Icons.Outlined.Home),
        NavItem("categories", "Categories", Icons.Filled.GridView),
        NavItem("orders", "Buy Again", Icons.Outlined.Replay),
        NavItem("cart", "Basket", Icons.Outlined.ShoppingBag)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route.orEmpty()
    val selectedColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.surface
    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Surface(color = barColor) {
        Column {
            Divider(color = dividerColor, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEach { item ->
                    // The product listing is the category browser's destination,
                    // so keep Categories highlighted while a listing is open.
                    val selected = currentRoute == item.route ||
                        (item.route == "categories" && currentRoute.startsWith("listing/"))
                    val tint = if (selected) selectedColor else inactiveColor
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = selected,
                                onClick = { navigateToTab(navController, item.route) },
                                role = Role.Tab
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (item.route == "cart" && cartCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = selectedColor) {
                                        Text(
                                            text = if (cartCount > 99) "99+" else cartCount.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            Icon(
                                item.icon,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            color = tint,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
