package com.balaji.provision.ui.profile

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.BuildConfig
import com.balaji.provision.domain.model.SavedAddress
import com.balaji.provision.ui.account.AccountViewModel
import com.balaji.provision.ui.account.AddressEditorDialog
import com.balaji.provision.ui.account.SavedAddressCard
import com.balaji.provision.ui.auth.AuthViewModel
import com.balaji.provision.ui.home.HomeViewModel

@Composable
fun ProfileScreen(
    onNavigateToSignIn: () -> Unit = {},
    homeViewModel: HomeViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
    accountViewModel: AccountViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val state by homeViewModel.state.collectAsState()
    val authState by authViewModel.state.collectAsState()
    val accountState by accountViewModel.state.collectAsState()
    val session = authState.session
    val storePhone = state.contactPhone?.filter { it.isDigit() || it == '+' }?.takeIf { it.isNotBlank() }
    val displayName = accountState.profile?.displayName?.takeIf { it.isNotBlank() }
        ?: session?.email
        ?: "Customer"
    val verifiedPhone = accountState.profile?.phone?.takeIf { it.isNotBlank() } ?: session?.phone

    var showEditProfile by remember { mutableStateOf(false) }
    var showAddressEditor by remember { mutableStateOf(false) }
    var editingAddress by remember { mutableStateOf<SavedAddress?>(null) }
    var deletingAddress by remember { mutableStateOf<SavedAddress?>(null) }
    var profileDialogError by remember { mutableStateOf<String?>(null) }
    var addressDialogError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (session != null) accountViewModel.refresh()
    }

    val dialStorePhone = {
        if (storePhone != null) {
            val intent = Intent(Intent.ACTION_DIAL, "tel:$storePhone".toUri())
            context.startActivity(intent)
        }
    }

    if (showEditProfile) {
        EditProfileDialog(
            initialName = displayName.takeIf { it != "Customer" }.orEmpty(),
            phone = verifiedPhone,
            isSaving = accountState.isSaving,
            error = profileDialogError,
            onDismiss = {
                if (!accountState.isSaving) {
                    showEditProfile = false
                    profileDialogError = null
                }
            },
            onSave = { name ->
                accountViewModel.updateDisplayName(name) { success, message ->
                    if (success) {
                        showEditProfile = false
                        profileDialogError = null
                    } else {
                        profileDialogError = message
                    }
                }
            }
        )
    }

    if (showAddressEditor) {
        AddressEditorDialog(
            initial = editingAddress,
            defaultName = displayName.takeIf { it != "Customer" },
            defaultPhone = verifiedPhone,
            isSaving = accountState.isSaving,
            serverError = addressDialogError,
            onDismiss = {
                if (!accountState.isSaving) {
                    showAddressEditor = false
                    editingAddress = null
                    addressDialogError = null
                }
            },
            onSave = { draft ->
                accountViewModel.saveAddress(draft, editingAddress?.id) { success, _, message ->
                    if (success) {
                        showAddressEditor = false
                        editingAddress = null
                        addressDialogError = null
                    } else {
                        addressDialogError = message
                    }
                }
            }
        )
    }

    deletingAddress?.let { address ->
        AlertDialog(
            onDismissRequest = { if (!accountState.isSaving) deletingAddress = null },
            title = { Text("Delete address?") },
            text = { Text("${address.label.ifBlank { "This address" }} will be removed from your saved addresses.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        accountViewModel.deleteAddress(address.id)
                        deletingAddress = null
                    },
                    enabled = !accountState.isSaving
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingAddress = null }, enabled = !accountState.isSaving) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Text("My Profile & Account", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            displayName,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            verifiedPhone ?: "Not signed in",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            if (session != null) "Customer account" else "Sign in to place orders and view order history.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    if (session != null) {
                        OutlinedButton(onClick = { showEditProfile = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Edit", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (session == null) {
                Button(
                    onClick = onNavigateToSignIn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign In / Create Account")
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Saved Addresses", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        if (session != null) {
                            TextButton(
                                onClick = {
                                    editingAddress = null
                                    addressDialogError = null
                                    showAddressEditor = true
                                },
                                enabled = !accountState.isSaving
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(" Add")
                            }
                        }
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (session != null) {
                        if (accountState.isLoading && accountState.addresses.isEmpty()) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else if (accountState.addresses.isEmpty()) {
                            Text(
                                "No saved addresses yet. Add one so checkout is faster.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Button(
                                onClick = {
                                    editingAddress = null
                                    addressDialogError = null
                                    showAddressEditor = true
                                },
                                enabled = !accountState.isSaving
                            ) {
                                Text("Add delivery address")
                            }
                        } else {
                            accountState.addresses.forEach { address ->
                                SavedAddressCard(
                                    address = address,
                                    isBusy = accountState.isSaving,
                                    onEdit = {
                                        editingAddress = address
                                        addressDialogError = null
                                        showAddressEditor = true
                                    },
                                    onDelete = { deletingAddress = address },
                                    onSetDefault = { accountViewModel.setDefaultAddress(address.id) }
                                )
                            }
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
                        Text(
                            "Sign in to save addresses for faster checkout.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (session != null) {
                Button(
                    onClick = { authViewModel.signOut() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Store, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Balaji Provision Store", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    InfoRow("Application Version", BuildConfig.VERSION_NAME)
                    InfoRow("Fulfillment Mode", "Hyperlocal Local Delivery")
                    if (storePhone != null) {
                        InfoRow("Support Phone", storePhone, onClick = dialStorePhone)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    phone: String?,
    isSaving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Edit details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Mobile: ${phone ?: "Not available"}",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    "Your mobile number is verified and used to sign in. Change the contact number on an address instead.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
                val message = localError ?: error
                if (message != null) {
                    Text(message, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        localError = "Enter your name."
                    } else {
                        localError = null
                        onSave(name)
                    }
                },
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(
            value,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}
