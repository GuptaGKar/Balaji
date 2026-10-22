package com.balaji.provision.ui.checkout

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
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
                            .clip(CircleShape)
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF16A34A),
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Order Placed Successfully", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "Order #${confirmedOrderNo}",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Payment:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Cash on Delivery (COD)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Delivery:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Timing confirmed by the store", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
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
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Track Order in My Orders", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Checkout",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                            Text("Delivery Address", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                fontSize = 13.sp
                            )
                            Button(onClick = onNavigateToSignIn) {
                                Text("Sign In")
                            }
                        } else if (accountState.isLoading && accountState.addresses.isEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            }
                        } else if (accountState.addresses.isEmpty()) {
                            Text(
                                "Add a delivery address once and reuse it for every order.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
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
                            if (accountState.error != null) {
                                Text(
                                    accountState.error.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp
                                )
                                TextButton(onClick = { accountViewModel.refresh() }) {
                                    Text("Retry")
                                }
                            }
                        } else {
                            accountState.addresses.forEach { address ->
                                CheckoutAddressOption(
                                    address = address,
                                    selected = address.id == selectedAddress?.id,
                                    onSelect = { selectedAddressId = address.id }
                                )
                            }
                            if (accountState.error != null) {
                                Text(
                                    accountState.error.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.sp
                                )
                                TextButton(onClick = { accountViewModel.refresh() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Payment Method", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Divider(color = MaterialTheme.colorScheme.outlineVariant)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text("Cash on Delivery (COD)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Pay in cash when groceries arrive at your doorstep",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                quote?.let { q ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Amount To Pay", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Items Total", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                Text(formatPaise(q.itemSubtotalPaise), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Delivery Fee", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                Text(
                                    if (q.deliveryFeePaise == 0L) "FREE" else formatPaise(q.deliveryFeePaise),
                                    fontWeight = FontWeight.Bold,
                                    color = if (q.deliveryFeePaise == 0L) Color(0xFF16A34A) else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 13.sp
                                )
                            }

                            Divider(color = MaterialTheme.colorScheme.outlineVariant)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Grand Total (COD)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    formatPaise(q.grandTotalPaise),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Delivery Instructions (Optional)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.size(8.dp))

                        OutlinedTextField(
                            value = customerNote,
                            onValueChange = { customerNote = it },
                            placeholder = { Text("e.g., Leave at door, call on arrival...") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                if (quote == null && cart.items.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "Delivery charge and total could not be loaded. Check your connection and try again.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = !isLoading && cart.items.isNotEmpty() && quote != null &&
                        session != null && selectedAddress != null,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Confirm Order (Cash on Delivery)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                if (session != null && selectedAddress == null && !accountState.isLoading) {
                    Text(
                        "Select a saved address or add a new one to continue.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (address.isDefault) {
                        Text(
                            "Default",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Text(
                    "${address.recipientName} · ${address.phone}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    address.fullAddress(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
