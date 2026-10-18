package com.balaji.provision.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.navController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.NavController
import androidx.navigation.NavGraphBuilder
import com.balaji.provision.R
import com.balaji.provision.ui.catalog.CatalogScreen
import com.balaji.provision.ui.cart.CartScreen
import com.balaji.provision.ui.checkout.CheckoutScreen
import com.balaji.provision.ui.orders.OrdersScreen
import com.balaji.provision.ui.profile.ProfileScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun AppNavHost(navController: NavController, startDestination: String = "home") {
    NavHost(navController, startDestination) {
        composable("home") { HomeScreen(onNavigateToCatalog = { navController.navigate("catalog") }) }
        composable("catalog") { CatalogScreen(onNavigateToCart = { navController.navigate("cart") }) }
        composable("cart") { CartScreen(onNavigateToCheckout = { navController.navigate("checkout") }) }
        composable("checkout") { CheckoutScreen(onOrderPlaced = { navController.navigate("orders") }) }
        composable("orders") { OrdersScreen() }
        composable("profile") { ProfileScreen() }
    }
}

@Composable
fun HomeScreen(onNavigateToCatalog: () -> Unit) {
    var showStoreClosed by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Store header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    androidx.compose.ui.res.painterResource(R.drawable.ic_store)
                        .let { Icon(it, contentDescription = "Store", tint = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer) }
                    Column {
                        Text("Balaji Provision Store", fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Text("Quality provisions at your doorstep", color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                    }
                }
            }
            if (showStoreClosed) {
                Text("Store is currently closed", color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                    .let { it }
            }
        }
        
        // Quick actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                title = "Browse Catalogue",
                subtitle = "Browse products by category",
                icon = androidx.compose.material.icons.outlined.Store,
                onClick = onNavigateToCatalog
            )
            QuickActionCard(
                title = "My Orders",
                subtitle = "Track your deliveries",
                icon = androidx.compose.material.icons.outlined.ListAlt,
                onClick = { /* Navigate to orders */ }
            )
        }
        
        // Store info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Store Information", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                InfoRow("Contact", "9837803293")
                InfoRow("Delivery", "Same-day delivery available")
                InfoRow("Hours", "8:00 AM - 10:00 PM")
            }
        }
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.res.Painter,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(8.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
            Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(subtitle, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}