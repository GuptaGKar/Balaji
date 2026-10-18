package com.balaji.provision.ui.catalog

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.navigation.compose.NavController
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.TextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun CatalogScreen(
    viewModel: ProductViewModel = viewModel(),
    onNavigateToCart: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val products by viewModel.products.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search bar
        androidx.compose.material3.TextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            label = { Text("Search products...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            leadingIcon = { Icon(androidx.compose.material.icons.outlined.Search, contentDescription = "Search") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        
        // Categories horizontal scroll
        if (categories.isNotEmpty()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        CategoryChip(
                            name = category.name,
                            isSelected = selectedCategory == category.id,
                            onClick = { viewModel.selectCategory(category.id) }
                        )
                    }
                    CategoryChip(
                        name = "All",
                        isSelected = selectedCategory == null,
                        onClick = { viewModel.selectCategory(null) }
                    )
                }
            }
        }
        
        // Products grid
        if (isLoading) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
        } else if (products.isEmpty()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No products found", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(products) { product ->
                    ProductCard(
                        product = product,
                        onAddToCart = { qty ->
                            // Add to cart logic would go here
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(name: String, isSelected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(name) },
        modifier = Modifier.padding(vertical = 4.dp),
        selectedColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
        colors = androidx.compose.material3.FilterChipDefaults.colors(
            selectedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer,
            unselectedContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
        )
    )
}

@Composable
fun ProductCard(
    product: com.balaji.provision.domain.model.Product,
    onAddToCart: (Long) -> Unit
) {
    var qty by remember { mutableStateOf(product.minQty) }
    val step = product.stepQty
    val minQty = product.minQty
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Product image
                Box(
                    modifier = Modifier.size(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Placeholder for image
                    androidx.compose.foundation.Background(
                        modifier = Modifier.fillMaxSize(),
                        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    Text(product.name.substring(0, 1).uppercase(), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                
                // Product details
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 2)
                    Text(product.description ?: "", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 2)
                    Text(product.displayPrice, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    Text(product.displayStep, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    
                    if (!product.isInStock) {
                        Text("Out of stock", color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    } else if (product.isLowStock) {
                        Text("Low stock", color = androidx.compose.material3.MaterialTheme.colorScheme.warning, fontSize = 12.sp)
                    }
                }
            }
            
            // Quantity selector & Add to cart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuantitySelector(
                    qty = qty,
                    onIncrement = { if (qty + product.stepQty <= product.maxOrderQty) qty += product.stepQty },
                    onDecrement = { if (qty - product.stepQty >= minQty) qty -= product.stepQty },
                    step = product.stepQty,
                    minQty = minQty
                )
                
                Button(
                    onClick = { /* onAddToCart(qty) */ },
                    enabled = product.isInStock,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = if (product.isInStock) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                ) {
                    Text("Add to Cart", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun QuantitySelector(
    qty: Long,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    step: Long,
    minQty: Long
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDecrement) {
            Icon(androidx.compose.material.icons.outlined.Remove, contentDescription = "Decrease quantity")
        }
        Text(qty.toString(), fontSize = 18.sp, fontWeight = FontWeight.Medium)
        IconButton(onClick = onIncrement) {
            Icon(androidx.compose.material.icons.outlined.Add, contentDescription = "Increase quantity")
        }
    }
}