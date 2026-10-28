package com.balaji.provision.ui.manager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.balaji.provision.BuildConfig
import com.balaji.provision.domain.model.LowStockProduct
import com.balaji.provision.domain.model.ManagerOrder
import com.balaji.provision.domain.model.ManagerOrderAction
import com.balaji.provision.domain.model.ManagerOrderLine
import com.balaji.provision.domain.model.formatPaise
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun ManagerScreen(
    role: String,
    onSignOut: () -> Unit,
    viewModel: ManagerViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var reasonAction by remember { mutableStateOf<ReasonAction?>(null) }
    var showRiderPicker by remember { mutableStateOf(false) }
    var adjustingProduct by remember { mutableStateOf<LowStockProduct?>(null) }
    var editingPriceProduct by remember { mutableStateOf<com.balaji.provision.domain.model.Product?>(null) }
    var removingItem by remember { mutableStateOf<ManagerOrderLine?>(null) }

    // Dispatch/delivery proof. The server rejects the transition without it; the UI
    // blocks it first so staff get an explanation instead of an error.
    val proofViewModel: com.balaji.provision.ui.proof.OrderProofViewModel = hiltViewModel()
    val proofState by proofViewModel.state.collectAsState()
    var proofStage by remember { mutableStateOf("dispatch") }
    val managerScope = rememberCoroutineScope()
    val proofPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val orderId = state.selectedOrderId
        if (uri != null && orderId != null) {
            proofViewModel.upload(orderId, proofStage, uri)
        }
    }

    LaunchedEffect(state.selectedOrderId) {
        state.selectedOrderId?.let { proofViewModel.load(it) }
    }
    LaunchedEffect(proofState.message) {
        proofState.message?.let {
            snackbarHostState.showSnackbar(it)
            proofViewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.actionError) {
        state.actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionError()
        }
    }

    Scaffold(
        // MainAppScreen owns the system-bar insets; asking Scaffold for them
        // again would double the gap above the header.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ManagerHeader(
                role = role,
                refreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                onSignOut = onSignOut
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                listOf("Overview", "Orders", "Stock", "Requests").forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            when {
                state.isInitialLoading -> Box(Modifier.fillMaxSize()) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.error != null && state.orders.isEmpty() -> ManagerErrorState(
                    message = state.error!!,
                    onRetry = viewModel::refresh
                )
                selectedTab == 0 -> OverviewTab(
                    state = state,
                    isManager = role == "manager",
                    onOpenOrders = { selectedTab = 1 },
                    onOpenOrder = { order ->
                        selectedTab = 1
                        viewModel.openOrder(order.id)
                    }
                )
                selectedTab == 1 -> OrdersTab(
                    state = state,
                    onOpenOrder = viewModel::openOrder
                )
                selectedTab == 2 -> StockTab(
                    state = state,
                    onAdjust = { adjustingProduct = it },
                    onEditPrice = { editingPriceProduct = it }
                )
                else -> StaffRequestsTab()
            }
        }
    }

    if (state.selectedOrderId != null) {
        ManagerOrderSheet(
            state = state,
            onDismiss = viewModel::closeOrder,
            onRetryItems = viewModel::reloadOrderItems,
            onRemoveItem = { removingItem = it },
            onAction = { action ->
                when {
                    action.toStatus == "out_for_delivery" && !proofState.proofs.containsKey("dispatch") -> {
                        managerScope.launch {
                            snackbarHostState.showSnackbar(
                                "Upload the dispatch photo or video first."
                            )
                        }
                    }
                    action.toStatus == "delivered" && !proofState.proofs.containsKey("delivery") -> {
                        managerScope.launch {
                            snackbarHostState.showSnackbar(
                                "Upload the delivery photo or video first."
                            )
                        }
                    }
                    action.requiresReason -> reasonAction = ReasonAction(action.toStatus, action.label)
                    else -> viewModel.transitionOrder(action.toStatus)
                }
            },
            proofs = proofState.proofs,
            uploadingStage = proofState.uploadingStage,
            onUploadProof = { stage ->
                proofStage = stage
                proofPicker.launch(arrayOf("image/*", "video/*"))
            },
            onAssignRider = {
                viewModel.loadRiders()
                showRiderPicker = true
            }
        )
    }

    reasonAction?.let { action ->
        ReasonDialog(
            title = action.label,
            onDismiss = { reasonAction = null },
            onConfirm = { reason ->
                viewModel.transitionOrder(action.toStatus, reason)
                reasonAction = null
            }
        )
    }

    removingItem?.let { item ->
        ReasonDialog(
            title = "Remove ${item.name}",
            onDismiss = { removingItem = null },
            onConfirm = { reason ->
                viewModel.removeOrderItem(item, reason)
                removingItem = null
            }
        )
    }

    if (showRiderPicker) {
        RiderPickerDialog(
            riders = state.riders,
            loading = state.isRiderListLoading,
            busy = state.isActionInProgress,
            onDismiss = { showRiderPicker = false },
            onSelect = { riderId ->
                viewModel.assignRider(riderId)
                showRiderPicker = false
            }
        )
    }

    adjustingProduct?.let { product ->
        AdjustStockDialog(
            product = product,
            stockBusy = state.adjustingProductId == product.id,
            priceBusy = state.updatingPriceProductId == product.id,
            onDismiss = { adjustingProduct = null },
            onConfirmStock = { delta, note ->
                viewModel.adjustStock(product, delta, note)
                adjustingProduct = null
            },
            onConfirmPrice = { selling, mrp ->
                viewModel.updateProductPriceById(product.id, product.name, selling, mrp)
                adjustingProduct = null
            }
        )
    }

    editingPriceProduct?.let { product ->
        EditPriceDialog(
            product = product,
            busy = state.updatingPriceProductId == product.id,
            onDismiss = { editingPriceProduct = null },
            onConfirm = { selling, mrp ->
                viewModel.updateProductPrice(product, selling, mrp)
                editingPriceProduct = null
            }
        )
    }
}

@Composable
private fun ManagerHeader(
    role: String,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Store management",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${role.replaceFirstChar { it.uppercase() }} account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
            IconButton(onClick = onSignOut) {
                Icon(Icons.Default.Logout, contentDescription = "Sign out")
            }
        }
    }
}

@Composable
private fun OverviewTab(
    state: ManagerUiState,
    isManager: Boolean,
    onOpenOrders: () -> Unit,
    onOpenOrder: (ManagerOrder) -> Unit
) {
    val dashboardUrl = BuildConfig.STAFF_WEB_URL.trim().trimEnd('/')
    val context = LocalContext.current
    val recent = state.orders.take(5)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Active orders",
                    value = state.activeOrderCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Waiting review",
                    value = state.pendingReviewCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    label = "Ready to dispatch",
                    value = state.readyForDispatchCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Low stock items",
                    value = state.lowStock.size.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent orders",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenOrders) { Text("View all") }
            }
        }
        if (recent.isEmpty()) {
            item {
                Text(
                    "No orders yet. New orders will appear here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(recent, key = { it.id }) { order ->
                ManagerOrderCard(order = order, onClick = { onOpenOrder(order) })
            }
        }
        item {
            WebToolsCard(
                dashboardUrl = dashboardUrl,
                isManager = isManager,
                onOpen = { openInBrowser(context, it) }
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WebToolsCard(
    dashboardUrl: String,
    isManager: Boolean,
    onOpen: (String) -> Unit
) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Store,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Store administration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                if (isManager) {
                    "Add staff or riders, reset passwords, edit the catalogue, review reports " +
                        "and change store settings in the manager dashboard."
                } else {
                    "Catalogue, reports and store settings are available in the manager dashboard."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (dashboardUrl.isBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "The web dashboard address is not set in this build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else {
                Button(
                    onClick = { onOpen(dashboardUrl) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Open web dashboard")
                }
                if (isManager) {
                    OutlinedButton(
                        onClick = { onOpen("$dashboardUrl/staff") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add staff or rider")
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersTab(
    state: ManagerUiState,
    onOpenOrder: (String) -> Unit
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    val filters = listOf(
        "all" to "All",
        "pending_review" to "Pending",
        "accepted" to "Accepted",
        "preparing" to "Preparing",
        "ready_for_dispatch" to "Ready",
        "out_for_delivery" to "On the way",
        "delivered" to "Delivered",
        "cancelled" to "Cancelled",
        "rejected" to "Rejected"
    )
    val visible = state.orders.filter { order ->
        val matchesFilter = filter == "all" || order.status == filter
        val term = search.trim()
        val matchesSearch = term.isEmpty() ||
            order.orderNumber.contains(term, ignoreCase = true) ||
            order.recipientName.contains(term, ignoreCase = true) ||
            order.phone.contains(term, ignoreCase = true)
        matchesFilter && matchesSearch
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search order, name or phone") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { (value, label) ->
                FilterChip(
                    selected = filter == value,
                    onClick = { filter = value },
                    label = { Text(label) }
                )
            }
        }
        if (state.error != null) {
            ErrorBanner(message = state.error)
        }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    if (state.orders.isEmpty()) "No orders yet." else "No orders match this filter.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visible, key = { it.id }) { order ->
                    ManagerOrderCard(order = order, onClick = { onOpenOrder(order.id) })
                }
            }
        }
    }
}

@Composable
private fun ManagerOrderCard(order: ManagerOrder, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${order.orderNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(order.status)
            }
            Text(
                order.recipientName.ifBlank { "Customer" },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                order.addressLines.ifBlank { "Address not recorded" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatPaise(order.grandTotalPaise),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${formatWhen(order.createdAt)} · ${paymentLabel(order)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StockTab(
    state: ManagerUiState,
    onAdjust: (LowStockProduct) -> Unit,
    onEditPrice: (com.balaji.provision.domain.model.Product) -> Unit
) {
    var searchProduct by rememberSaveable { mutableStateOf("") }
    val filteredProducts = state.products.filter { p ->
        val query = searchProduct.trim()
        query.isEmpty() ||
            p.name.contains(query, ignoreCase = true) ||
            p.sku.contains(query, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.lowStock.isNotEmpty()) {
            item {
                Text(
                    "Low stock alert",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Update stock after a physical count. Every change is recorded in the audit trail.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(state.lowStock, key = { "low_${it.id}" }) { product ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${product.sku} · ${product.quantityLabel} left (threshold ${product.threshold})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { onAdjust(product) },
                            enabled = state.adjustingProductId == null
                        ) {
                            Text("Adjust")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Product Pricing & Catalogue",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Staff members can update selling prices and MRPs for all products.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = searchProduct,
                onValueChange = { searchProduct = it },
                label = { Text("Search product by name or SKU") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            )
        }

        if (filteredProducts.isEmpty()) {
            item {
                Text(
                    if (state.products.isEmpty()) "No products loaded." else "No products match this search.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filteredProducts, key = { "prod_${it.id}" }) { product ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${product.sku} · ${product.packLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Selling: ${formatPaise(product.pricePaise)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (product.hasDiscount) {
                                    Text(
                                        "MRP: ${formatPaise(product.mrpPaise!!)}",
                                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { onEditPrice(product) },
                            enabled = state.updatingPriceProductId == null
                        ) {
                            Text("Edit Price")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagerOrderSheet(
    state: ManagerUiState,
    onDismiss: () -> Unit,
    onRetryItems: () -> Unit,
    onRemoveItem: (ManagerOrderLine) -> Unit,
    onAction: (ManagerOrderAction) -> Unit,
    onAssignRider: () -> Unit,
    proofs: Map<String, String> = emptyMap(),
    uploadingStage: String? = null,
    onUploadProof: (String) -> Unit = {}
) {
    val order = state.selectedOrder ?: return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Order #${order.orderNumber}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${formatWhen(order.createdAt)} · ${paymentLabel(order)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(order.status)
            }

            ProofSection(
                proofs = proofs,
                uploadingStage = uploadingStage,
                onUpload = onUploadProof
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        order.recipientName.ifBlank { "Customer" },
                        fontWeight = FontWeight.SemiBold
                    )
                    if (order.phone.isNotBlank()) Text(order.phone)
                    Text(
                        order.addressLines.ifBlank { "Address not recorded" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    order.customerNote?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "Note: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text("Items", fontWeight = FontWeight.SemiBold)
            when {
                state.isDetailLoading -> CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(8.dp)
                )
                state.detailError != null -> {
                    Text(state.detailError, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetryItems) { Text("Retry") }
                }
                state.selectedItems.isEmpty() -> Text(
                    "No items recorded.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> state.selectedItems.forEach { item ->
                    OrderItemRow(
                        item = item,
                        canRemove = !item.removed &&
                            order.status in setOf("pending_review", "accepted", "preparing") &&
                            !state.isActionInProgress,
                        onRemove = { onRemoveItem(item) }
                    )
                }
            }

            Divider()
            TotalRow("Item subtotal", order.itemSubtotalPaise)
            TotalRow("Delivery fee", order.deliveryFeePaise)
            if (order.taxPaise > 0) TotalRow("Tax", order.taxPaise)
            TotalRow("Total", order.grandTotalPaise, emphasized = true)
            if (order.isCod) {
                Text(
                    if (order.paymentStatus == "paid") {
                        "Cash collected: ${formatPaise(order.codCollectedPaise ?: 0)}"
                    } else {
                        "Cash on delivery · ${formatPaise(order.grandTotalPaise)} due"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (order.canAssignRider) {
                OutlinedButton(
                    onClick = onAssignRider,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isActionInProgress
                ) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Assign delivery rider")
                }
            }

            if (order.actions.isNotEmpty()) {
                Text("Update order", fontWeight = FontWeight.SemiBold)
                order.actions.forEach { action ->
                    Button(
                        onClick = { onAction(action) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isActionInProgress,
                        colors = if (action.destructive) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        if (state.isActionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(action.label)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderItemRow(
    item: ManagerOrderLine,
    canRemove: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (item.removed) FontWeight.Normal else FontWeight.Medium
            )
            Text(
                if (item.removed) {
                    "${item.quantityLabel} · removed${item.removedReason?.let { ": $it" }.orEmpty()}"
                } else {
                    item.quantityLabel
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (canRemove) {
                TextButton(
                    onClick = onRemove,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                ) {
                    Text("Remove unavailable item", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(formatPaise(item.lineTotalPaise), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TotalRow(label: String, paise: Long, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            formatPaise(paise),
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        "pending_review" -> MaterialTheme.colorScheme.tertiaryContainer
        "accepted", "preparing" -> MaterialTheme.colorScheme.secondaryContainer
        "ready_for_dispatch", "out_for_delivery" -> MaterialTheme.colorScheme.primaryContainer
        "delivered" -> MaterialTheme.colorScheme.primaryContainer
        "rejected", "cancelled", "delivery_failed" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = RoundedCornerShape(50)) {
        Text(
            status.replace('_', ' ').uppercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ReasonDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val trimmed = reason.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A reason is required and will be saved in the audit trail.")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                enabled = trimmed.isNotEmpty()
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun RiderPickerDialog(
    riders: List<com.balaji.provision.domain.model.RiderOption>,
    loading: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign delivery rider") },
        text = {
            when {
                loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                riders.isEmpty() -> Text(
                    "No active delivery riders. Add or activate a rider from the web dashboard."
                )
                else -> Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    riders.forEach { rider ->
                        TextButton(
                            onClick = { onSelect(rider.id) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !busy
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(rider.name, fontWeight = FontWeight.SemiBold)
                                rider.phone?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

private enum class AdjustMode { STOCK, PRICE }

@Composable
private fun AdjustStockDialog(
    product: LowStockProduct,
    stockBusy: Boolean,
    priceBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirmStock: (Long, String?) -> Unit,
    onConfirmPrice: (Long, Long?) -> Unit
) {
    var mode by remember { mutableStateOf(AdjustMode.STOCK) }

    // Stock state
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(true) }
    val quantity = amount.trim().toLongOrNull()
    val delta = quantity?.takeIf { it > 0 }?.let { if (adding) it else -it }
    val unit = if (product.quantityType == "weighted") "grams" else "packs"
    val newStock = delta?.let { product.stockQty + it }

    // Price state
    var sellingPriceText by remember { mutableStateOf(if (product.pricePaise > 0) (product.pricePaise / 100.0).toString().removeSuffix(".0") else "") }
    var mrpText by remember { mutableStateOf(product.mrpPaise?.let { (it / 100.0).toString().removeSuffix(".0") }.orEmpty()) }

    val sellingPriceRupees = sellingPriceText.toDoubleOrNull()
    val mrpRupees = mrpText.toDoubleOrNull()

    val validSelling = sellingPriceRupees != null && sellingPriceRupees >= 0
    val validMrp = mrpText.isBlank() || (mrpRupees != null && mrpRupees >= 0)

    val sellingPaise = sellingPriceRupees?.let { Math.round(it * 100) } ?: 0L
    val mrpPaise = mrpRupees?.let { Math.round(it * 100) }

    val busy = stockBusy || priceBusy

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${product.sku} · Current stock: ${product.quantityLabel}")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == AdjustMode.STOCK,
                        onClick = { mode = AdjustMode.STOCK },
                        label = { Text("Adjust Stock") }
                    )
                    FilterChip(
                        selected = mode == AdjustMode.PRICE,
                        onClick = { mode = AdjustMode.PRICE },
                        label = { Text("Adjust Price & MRP") }
                    )
                }

                if (mode == AdjustMode.STOCK) {
                    Text("Choose whether to add or remove stock, then enter the quantity in $unit.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = adding,
                            onClick = { adding = true },
                            label = { Text("Add stock") }
                        )
                        FilterChip(
                            selected = !adding,
                            onClick = { adding = false },
                            label = { Text("Remove stock") }
                        )
                    }
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { input -> amount = input.filter(Char::isDigit) },
                        label = { Text("Quantity in $unit") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (delta != null && delta != 0L) {
                        Text(
                            if (newStock != null && newStock < 0) {
                                "Cannot remove more than the current stock."
                            } else {
                                "New stock: $newStock"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (newStock != null && newStock < 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                } else {
                    Text("Enter the new selling price and optional printed MRP in Rupees (₹).")
                    OutlinedTextField(
                        value = sellingPriceText,
                        onValueChange = { sellingPriceText = it },
                        label = { Text("Selling Price (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = mrpText,
                        onValueChange = { mrpText = it },
                        label = { Text("MRP (₹) (Optional)") },
                        singleLine = true,
                        placeholder = { Text("e.g. 100") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mrpPaise != null && sellingPaise > 0 && mrpPaise > sellingPaise) {
                        val discount = (((mrpPaise - sellingPaise).toDouble() / mrpPaise) * 100).toInt()
                        Text(
                            "MRP is higher than selling price. Customer app will show $discount% OFF with strikethrough MRP.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (mode == AdjustMode.STOCK) {
                TextButton(
                    onClick = { onConfirmStock(delta ?: 0L, note.trim().ifBlank { null }) },
                    enabled = !busy && delta != null && delta != 0L && (newStock ?: 0L) >= 0L
                ) { Text(if (stockBusy) "Saving..." else "Save Stock") }
            } else {
                TextButton(
                    onClick = { onConfirmPrice(sellingPaise, mrpPaise) },
                    enabled = !busy && validSelling && validMrp
                ) { Text(if (priceBusy) "Saving..." else "Save Price") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditPriceDialog(
    product: com.balaji.provision.domain.model.Product,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long?) -> Unit
) {
    var sellingPriceText by remember { mutableStateOf((product.pricePaise / 100.0).toString().removeSuffix(".0")) }
    var mrpText by remember { mutableStateOf(product.mrpPaise?.let { (it / 100.0).toString().removeSuffix(".0") }.orEmpty()) }

    val sellingPriceRupees = sellingPriceText.toDoubleOrNull()
    val mrpRupees = mrpText.toDoubleOrNull()

    val validSelling = sellingPriceRupees != null && sellingPriceRupees >= 0
    val validMrp = mrpText.isBlank() || (mrpRupees != null && mrpRupees >= 0)

    val sellingPaise = sellingPriceRupees?.let { Math.round(it * 100) } ?: 0L
    val mrpPaise = mrpRupees?.let { Math.round(it * 100) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Price: ${product.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "${product.sku} · ${product.packLabel}\n" +
                        "Enter the new selling price and optional printed MRP in Rupees (₹)."
                )
                OutlinedTextField(
                    value = sellingPriceText,
                    onValueChange = { sellingPriceText = it },
                    label = { Text("Selling Price (₹) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = mrpText,
                    onValueChange = { mrpText = it },
                    label = { Text("MRP (₹) (Optional)") },
                    singleLine = true,
                    placeholder = { Text("e.g. 100") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (mrpPaise != null && sellingPaise > 0 && mrpPaise > sellingPaise) {
                    val discount = (((mrpPaise - sellingPaise).toDouble() / mrpPaise) * 100).toInt()
                    Text(
                        "MRP is higher than selling price. Customer app will show $discount% OFF with strikethrough MRP.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(sellingPaise, mrpPaise) },
                enabled = !busy && validSelling && validMrp
            ) { Text(if (busy) "Saving..." else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(
            message,
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ManagerErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
            Text(message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

private data class ReasonAction(val toStatus: String, val label: String)

private fun paymentLabel(order: ManagerOrder): String = when {
    order.paymentMethod == "cod" && order.paymentStatus == "paid" -> "COD paid"
    order.paymentMethod == "cod" -> "COD"
    order.paymentMethod == "online" -> "Online"
    else -> order.paymentMethod?.replaceFirstChar { it.uppercase() } ?: "Payment"
}

private fun formatWhen(raw: String): String {
    if (raw.length < 19) return raw
    return runCatching {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = parser.parse(raw.take(19)) ?: return raw
        SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(date)
    }.getOrDefault(raw)
}

private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No browser app found", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Dispatch and delivery proof. Photos and videos both count; the transition itself is
 * blocked by the database until the matching proof exists.
 */
@Composable
private fun ProofSection(
    proofs: Map<String, String>,
    uploadingStage: String?,
    onUpload: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dispatch & delivery proof", fontWeight = FontWeight.SemiBold)
            ProofRow(
                label = "Dispatch",
                detail = proofs["dispatch"]?.let { "Uploaded ($it)" }
                    ?: "Required before Out for delivery",
                present = proofs.containsKey("dispatch"),
                busy = uploadingStage == "dispatch",
                onUpload = { onUpload("dispatch") }
            )
            ProofRow(
                label = "Delivery",
                detail = proofs["delivery"]?.let { "Uploaded ($it)" }
                    ?: "Required before Delivered",
                present = proofs.containsKey("delivery"),
                busy = uploadingStage == "delivery",
                onUpload = { onUpload("delivery") }
            )
        }
    }
}

@Composable
private fun ProofRow(
    label: String,
    detail: String,
    present: Boolean,
    busy: Boolean,
    onUpload: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Medium)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = if (present) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            OutlinedButton(onClick = onUpload) {
                Text(if (present) "Replace" else "Upload")
            }
        }
    }
}

/**
 * Staff inbox for "please stock this" requests from customers. The app can accept or
 * reject a request; closing it as "added" needs the catalogue product, so that step
 * stays on the web dashboard where the product is created.
 */
@Composable
private fun StaffRequestsTab(
    viewModel: com.balaji.provision.ui.requests.ProductRequestsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load(staffView = true) }

    when {
        state.loading && state.requests.isEmpty() -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }

        state.loadError != null && state.requests.isEmpty() -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = state.loadError.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Button(onClick = { viewModel.load(staffView = true) }) { Text("Retry") }
        }

        state.requests.isEmpty() -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No product requests", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Customers can ask for a new item from the customer app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.requests, key = { it.id }) { request ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = request.itemName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = request.status.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = listOfNotNull(
                                request.customerName ?: "Customer",
                                request.customerPhone
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        request.quantityText?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        request.note?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        if (request.status == "new") {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.update(request.id, "accepted", null) },
                                    enabled = state.actionId == null
                                ) { Text("Accept") }
                                OutlinedButton(
                                    onClick = { viewModel.update(request.id, "rejected", null) },
                                    enabled = state.actionId == null
                                ) { Text("Reject") }
                            }
                        } else if (request.status == "accepted") {
                            Text(
                                text = "Add it in the catalogue, then mark it added on the web dashboard.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        request.staffReply?.let {
                            Text(
                                text = "Reply: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
