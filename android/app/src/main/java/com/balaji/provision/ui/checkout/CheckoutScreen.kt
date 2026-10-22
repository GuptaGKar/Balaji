package com.balaji.provision.ui.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.data.network.AddressRequest
import com.balaji.provision.data.repository.PlaceOrderResult
import com.balaji.provision.domain.model.SavedAddress
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.ui.account.AccountViewModel
import com.balaji.provision.ui.account.AddressEditorDialog
import com.balaji.provision.ui.auth.AuthViewModel
import com.balaji.provision.ui.cart.CartViewModel
import com.balaji.provision.ui.components.MoneyText
import com.balaji.provision.ui.components.StatusPill
import com.balaji.provision.ui.components.StickyBottomBar
import com.balaji.provision.ui.components.StoreTopBar
import com.balaji.provision.ui.components.SummaryRow
import com.balaji.provision.ui.theme.appColors
import kotlinx.coroutines.launch

@Composable
fun CheckoutScreen(
    cartViewModel: CartViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToSignIn: () -> Unit = {},
    onOrderPlaced: () -> Unit = {}
) {
    var customerNote by remember { mutableStateOf("") }
    var selectedAddressId by remember { mutableStateOf<String?>(null) }
    var showAddressEditor by remember { mutableStateOf(false) }
    var addressDialogError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var confirmedOrderNo by remember { mutableStateOf("") }

    val cart by cartViewModel.cart.collectAsState()
    val quote by cartViewModel.quote.collectAsState()
    val authState by authViewModel.state.collectAsState()
    val accountState by accountViewModel.state.collectAsState()
    val session = authState.session
    val selectedAddress = accountState.addresses.firstOrNull { it.id == selectedAddressId }
        ?: accountState.addresses.firstOrNull { it.isDefault }
        ?: accountState.addresses.firstOrNull()

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        if (session != null) accountViewModel.refresh()
    }

    LaunchedEffect(accountState.addresses) {
        if (accountState.addresses.none { it.id == selectedAddressId }) {
            selectedAddressId = accountState.addresses.firstOrNull { it.isDefault }?.id
                ?: accountState.addresses.firstOrNull()?.id
        }
    }

    if (showAddressEditor) {
        AddressEditorDialog(
            initial = null,
            defaultName = accountState.profile?.displayName?.takeIf { it.isNotBlank() },
            defaultPhone = accountState.profile?.phone ?: session?.phone,
            isSaving = accountState.isSaving,
            serverError = addressDialogError,
            onDismiss = {
                if (!accountState.isSaving) {
                    showAddressEditor = false
                    addressDialogError = null
                }
            },
            onSave = { draft ->
                accountViewModel.saveAddress(draft, null) { success, saved, message ->
                    if (success && saved != null) {
                        selectedAddressId = saved.id
                        showAddressEditor = false
                        addressDialogError = null
                    } else {
                        addressDialogError = message
                    }
                }
            }
        )
    }

    if (showSuccessDialog) {
        Dialog(onDismissRequest = { }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.appColors.successContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.appColors.success,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Order placed successfully",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Order #${confirmedOrderNo}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SummaryRow(label = "Payment", value = "Cash on Delivery (COD)")
                            SummaryRow(label = "Delivery", value = "Timing confirmed by the store")
                        }
                    }

                    Button(
                        onClick = {
                            showSuccessDialog = false
                            cartViewModel.clearCart()
                            onOrderPlaced()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Track order in My Orders",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            StoreTopBar(
                title = "Checkout",
                subtitle = "Review and confirm your order",
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Delivery address
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Delivery address",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (session != null) {
                                TextButton(
                                    onClick = {
                                        addressDialogError = null
                                        showAddressEditor = true
                                    },
                                    enabled = !accountState.isSaving
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(" Add")
                                }
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        if (session == null) {
                            Text(
                                "Sign in to place your order. Guest checkout is not available.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(onClick = onNavigateToSignIn) {
                                Text("Sign in")
                            }
                        } else if (accountState.isLoading && accountState.addresses.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 3.dp
                                )
                            }
                        } else if (accountState.addresses.isEmpty()) {
                            Text(
                                "Add a delivery address once and reuse it for every order.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    addressDialogError = null
                                    showAddressEditor = true
                                },
                                enabled = !accountState.isSaving
                            ) {
                                Text("Add delivery address")
                            }
                        } else {
                            accountState.addresses.forEach { address ->
                                CheckoutAddressOption(
                                    address = address,
                                    selected = address.id == selectedAddress?.id,
                                    onSelect = { selectedAddressId = address.id }
                                )
                            }
                        }

                        if (accountState.error != null) {
                            Text(
                                accountState.error.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(onClick = { accountViewModel.refresh() }) {
                                Text("Retry")
                            }
                        }
                    }
                }

                // Payment method
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "Payment method",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        "Cash on Delivery (COD)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Pay in cash when groceries arrive at your doorstep",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Order summary
                quote?.let { q ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                "Amount to pay",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            SummaryRow(label = "Items total", value = formatPaise(q.itemSubtotalPaise))

                            SummaryRow(
                                label = "Delivery fee",
                                value = if (q.deliveryFeePaise == 0L) "FREE" else formatPaise(q.deliveryFeePaise),
                                valueColor = if (q.deliveryFeePaise == 0L) {
                                    MaterialTheme.appColors.success
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            SummaryRow(
                                label = "Grand total (COD)",
                                value = formatPaise(q.grandTotalPaise),
                                emphasise = true,
                                valueColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Delivery instructions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Delivery instructions (optional)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = customerNote,
                            onValueChange = { customerNote = it },
                            placeholder = { Text("e.g., Leave at door, call on arrival...") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }

                if (quote == null && cart.items.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Delivery charge and total could not be loaded. Check your connection and try again.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Button(
                                onClick = { cartViewModel.refreshQuote() },
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text("Retry total")
                            }
                        }
                    }
                }
            }

            StickyBottomBar {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total payable (COD)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MoneyText(
                        text = formatPaise(quote?.grandTotalPaise ?: 0L),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = {
                        val address = selectedAddress
                        if (address == null) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Add or select a delivery address.")
                            }
                            return@Button
                        }
                        isLoading = true
                        coroutineScope.launch {
                            val addressReq = AddressRequest(
                                recipient_name = address.recipientName,
                                phone = address.phone,
                                address_lines = address.addressLines,
                                landmark = address.landmark,
                                area = address.area,
                                city = address.city,
                                state = address.state,
                                postal_code = address.postalCode
                            )
                            when (val result = cartViewModel.placeOrderRemote(
                                addressReq,
                                customerNote.ifBlank { null }
                            )) {
                                is PlaceOrderResult.Success -> {
                                    isLoading = false
                                    confirmedOrderNo = result.response.public_order_number.orEmpty()
                                    showSuccessDialog = true
                                }
                                is PlaceOrderResult.Failure -> {
                                    isLoading = false
                                    snackbarHostState.showSnackbar(result.message)
                                }
                            }
                        }
                    },
                    enabled = !isLoading && cart.items.isNotEmpty() && quote != null &&
                        session != null && selectedAddress != null,
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Place order",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
        )
    }
}

@Composable
private fun CheckoutAddressOption(
    address: SavedAddress,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(18.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        address.label.ifBlank { "Address" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (address.isDefault) {
                        StatusPill(
                            text = "Default",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                Text(
                    "${address.recipientName} · ${address.phone}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    address.fullAddress(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
