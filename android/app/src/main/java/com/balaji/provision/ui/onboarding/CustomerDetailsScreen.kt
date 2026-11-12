package com.balaji.provision.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.domain.model.AddressDraft
import com.balaji.provision.ui.account.AddressEditorDialog
import com.balaji.provision.ui.account.AddressLocationViewModel
import com.balaji.provision.ui.components.liquidGlass

@Composable
fun CustomerDetailsScreen(
    displayName: String?,
    phone: String?,
    hasAddress: Boolean,
    isSaving: Boolean,
    accountError: String?,
    onSaveName: (String, (Boolean, String?) -> Unit) -> Unit,
    onSaveAddress: (AddressDraft, (Boolean, String?) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    locationViewModel: AddressLocationViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf(displayName.orEmpty()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var addressError by remember { mutableStateOf<String?>(null) }
    var showAddressEditor by remember { mutableStateOf(false) }
    val savedName = displayName?.takeIf { it.isNotBlank() }
    val locationState by locationViewModel.state.collectAsState()
    val context = LocalContext.current

    // The first sign-in shows "Complete your profile" and nothing else. The location prompt is
    // started by the "Add delivery address" button, not on entry, so the customer sees the page
    // and knows what the permission is for before the system dialog appears.
    var awaitFixForDialog by rememberSaveable { mutableStateOf(false) }
    // Built from the fix; handed to the editor so the customer corrects instead of retyping.
    var prefill by remember { mutableStateOf<AddressDraft?>(null) }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            locationViewModel.useCurrentLocation()
        } else {
            locationViewModel.onPermissionDenied()
        }
    }

    fun fetchLocationForAddress() {
        awaitFixForDialog = true
        if (hasLocationPermission()) {
            locationViewModel.useCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    LaunchedEffect(savedName) {
        if (!savedName.isNullOrBlank() && name.isBlank()) name = savedName
    }

    // The button fetched a fix; it arrives with its reverse-geocoded place in one state update.
    // Open the editor on the map with every field we could fill already filled; validation still
    // runs on save, so an incomplete reverse geocode shows as a normal field error. Keyed on the
    // request id, not the point, so asking twice from the same spot still opens the form.
    LaunchedEffect(locationState.requestId) {
        val fix = locationState.fix ?: return@LaunchedEffect
        if (!awaitFixForDialog || showAddressEditor || hasAddress) return@LaunchedEffect
        val place = locationState.place
        prefill = AddressDraft(
            recipientName = savedName ?: name.trim(),
            phone = phone.orEmpty(),
            addressLines = place?.addressLine.orEmpty(),
            area = place?.area.orEmpty(),
            city = place?.city.orEmpty(),
            state = place?.state.orEmpty(),
            postalCode = place?.postalCode.orEmpty(),
            lat = fix.latitude,
            lng = fix.longitude
        )
        awaitFixForDialog = false
        showAddressEditor = true
    }

    if (showAddressEditor) {
        AddressEditorDialog(
            initial = null,
            defaultName = savedName ?: name.trim().takeIf { it.isNotBlank() },
            defaultPhone = phone,
            isSaving = isSaving,
            serverError = addressError,
            prefill = prefill,
            startWithMap = prefill != null,
            onDismiss = {
                if (!isSaving) {
                    showAddressEditor = false
                    addressError = null
                    // A dismissal ends this attempt; a fix that lands later must not reopen it.
                    awaitFixForDialog = false
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
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(16.dp),
                        fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
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
                modifier = Modifier
                    .fillMaxWidth()
                    .liquidGlass(
                        shape = RoundedCornerShape(16.dp),
                        fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
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
                            onClick = { fetchLocationForAddress() },
                            enabled = !isSaving && !savedName.isNullOrBlank() &&
                                !locationState.isLocating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .semantics {
                                    contentDescription = if (locationState.isLocating) {
                                        "Finding your location"
                                    } else {
                                        "Add delivery address. Asks for location access first"
                                    }
                                },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (locationState.isLocating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "Finding your location…",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Add delivery address",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                        if (savedName.isNullOrBlank()) {
                            Text(
                                "Save your name first.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            OutlinedButton(
                                onClick = {
                                    // Manual entry skips the fix: clear any pending fetch so a
                                    // late GPS answer cannot reopen this form behind the customer.
                                    awaitFixForDialog = false
                                    prefill = null
                                    showAddressEditor = true
                                },
                                enabled = !isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Enter address manually")
                            }
                            Text(
                                "We ask for location access to fill the street, area and PIN. Check them, then save.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            locationState.error?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
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
