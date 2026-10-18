package com.balaji.provision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.balaji.provision.ui.cart.CartScreen
import com.balaji.provision.ui.catalog.CatalogScreen
import com.balaji.provision.ui.checkout.CheckoutScreen
import com.balaji.provision.ui.home.HomeScreen
import com.balaji.provision.ui.orders.OrdersScreen
import com.balaji.provision.ui.profile.ProfileScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavHost(navController)
                }
            }
        }
    }
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(onNavigateToCatalog = { navController.navigate("catalog") })
        }
        composable("catalog") {
            CatalogScreen(onNavigateToCart = { navController.navigate("cart") })
        }
        composable("cart") {
            CartScreen(onNavigateToCheckout = { navController.navigate("checkout") })
        }
        composable("checkout") {
            CheckoutScreen(onOrderPlaced = { navController.navigate("orders") })
        }
        composable("orders") {
            OrdersScreen()
        }
        composable("profile") {
            ProfileScreen()
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val destinations = listOf(
        NavItem("home", "Home", Icons.Default.Home),
        NavItem("catalog", "Catalog", Icons.Default.Store),
        NavItem("cart", "Cart", Icons.Default.ShoppingCart),
        NavItem("orders", "Orders", Icons.Default.ListAlt),
        NavItem("profile", "Profile", Icons.Default.Person)
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        destinations.forEach { item ->
            val selected = currentRoute?.startsWith(item.route) == true
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontSize = 12.sp) },
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)