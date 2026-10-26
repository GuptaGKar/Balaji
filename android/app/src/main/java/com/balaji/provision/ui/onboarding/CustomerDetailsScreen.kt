package com.balaji.provision.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.balaji.provision.domain.model.AddressDraft
import com.balaji.provision.ui.account.AddressEditorDialog

@Composable
fun CustomerDetailsScreen(
    displayName: String?,
    phone: String?,
    hasAddress: Boolean,
    isSaving: Boolean,
    accountError: String?,
    onSaveName: (String, (Boolean, String?) -> Unit) -> Unit,
    onSaveAddress: (AddressDraft, (Boolean, String?) -> Unit) -> Unit,
    onSignOut: () -> Unit
) {
    var name by remember { mutableStateOf(displayName.orEmpty()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var showAddressEditor by remember { mutableStateOf(false) }
    val savedName = displayName?.takeIf { it.isNotBlank() }

    LaunchedEffect(savedName) {
        if (!savedName.isNullOrBlank() && name.isBlank()) name = savedName
    }

    if (showAddressEditor) {
        AddressEditorDialog(
            initial = null,
            defaultName = savedName ?: name.trim().takeIf { it.isNotBlank() },
            defaultPhone = phone,
            isSaving = isSaving,
            serverError = addressError,
            onDismiss = {
                if (!isSaving) {
                    showAddressEditor = false
                    addressError = null
                }
            },
            onSave = { draft ->
                onSaveAddress(draft) { success, message ->
                    if (success) {
                        showAddressEditor = false
                        addressError = null
                    } else {
                        addressError = message
                    }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Store,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    "Balaji Provision Store",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (phone.isNullOrBlank()) "Set up your account" else "Signed in as $phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Complete your profile",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "Add your name and one delivery address. You can change them later from Profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Text("1. Your name", style = MaterialTheme.typography.titleMedium)
                        if (!savedName.isNullOrBlank()) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Name saved",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = null
                        },
                        label = { Text("Full name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isSaving,
                        shape = RoundedCornerShape(14.dp)
                    )

                    nameError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Button(
                        onClick = {
                            onSaveName(name) { success, message ->
                                nameError = if (success) null else message
                            }
                        },
                        enabled = !isSaving && name.trim().isNotBlank() &&
                            name.trim() != savedName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(if (savedName.isNullOrBlank()) "Save name" else "Update name")
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null)
                        Text("2. Delivery address", style = MaterialTheme.typography.titleMedium)
                        if (hasAddress) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Address added",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        if (hasAddress) {
                            "Your delivery address is saved."
                        } else {
                            "Add the address where you want your groceries delivered."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (hasAddress) {
                        OutlinedButton(
                            onClick = { showAddressEditor = true },
                            enabled = !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Add another address")
                        }
                    } else {
                        Button(
                            onClick = { showAddressEditor = true },
                            enabled = !isSaving && !savedName.isNullOrBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Add delivery address")
                        }
                        if (savedName.isNullOrBlank()) {
                            Text(
                                "Save your name first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    addressError?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (isSaving) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Saving…", style = MaterialTheme.typography.bodySmall)
                }
            }

            accountError?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            TextButton(onClick = onSignOut, enabled = !isSaving) {
                Text("Use a different mobile number")
            }
        }
    }
}
