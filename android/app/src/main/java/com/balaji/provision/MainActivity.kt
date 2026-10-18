package com.balaji.provision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.balaji.provision.ui.cart.CartScreen
import com.balaji.provision.ui.catalog.CatalogScreen
import com.balaji.provision.ui.checkout.CheckoutScreen
import com.balaji.provision.ui.home.HomeScreen
import com.balaji.provision.ui.orders.OrdersScreen
import com.balaji.provision.ui.profile.ProfileScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
fun AppNavHost(navController: NavController) {
    NavHost(navController, startDestination = "home") {
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
fun BottomNavigationBar(navController: NavController) {
    val destinations = listOf(
        NavItem("home", "Home", Home),
        NavItem("catalog", "Catalog", Store),
        NavItem("cart", "Cart", ShoppingCart),
        NavItem("orders", "Orders", ListAlt),
        NavItem("profile", "Profile", Person)
    )
    
    NavigationBar {
        destinations.forEach { item ->
            val selected = navController.currentBackStackEntryAsState().value?.destination?.route?.startsWith(item.route) == true
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
                },
                selected = selected
            )
        }
    }
}

data class NavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.res.Painter
)