package com.balaji.provision.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.balaji.provision.data.location.AreaSuggestion
import com.balaji.provision.data.location.GeoPoint
import com.balaji.provision.data.location.chipLabel
import com.balaji.provision.data.location.patchOnto
import com.balaji.provision.domain.model.AddressDraft
import com.balaji.provision.domain.model.SavedAddress
import java.util.Locale
import android.Manifest
import android.content.pm.PackageManager

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddressEditorDialog(
    initial: SavedAddress?,
    defaultName: String?,
    defaultPhone: String?,
    isSaving: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSave: (AddressDraft) -> Unit,
    locationViewModel: AddressLocationViewModel = hiltViewModel(),
    mapPickerViewModel: AddressMapPickerViewModel = hiltViewModel(),
    suggestionViewModel: AreaSuggestionViewModel = hiltViewModel()
) {
    val startingDraft = remember(initial?.id) {
        initial?.toDraft() ?: AddressDraft(
            recipientName = defaultName.orEmpty(),
            phone = defaultPhone.orEmpty()
        )
    }
    var label by remember(startingDraft) { mutableStateOf(startingDraft.label) }
    var recipientName by remember(startingDraft) { mutableStateOf(startingDraft.recipientName) }
    var phone by remember(startingDraft) { mutableStateOf(startingDraft.phone) }
    var addressLines by remember(startingDraft) { mutableStateOf(startingDraft.addressLines) }
    var landmark by remember(startingDraft) { mutableStateOf(startingDraft.landmark) }
    var area by remember(startingDraft) { mutableStateOf(startingDraft.area) }
    var city by remember(startingDraft) { mutableStateOf(startingDraft.city) }
    var state by remember(startingDraft) { mutableStateOf(startingDraft.state) }
    var postalCode by remember(startingDraft) { mutableStateOf(startingDraft.postalCode) }
    var isDefault by remember(startingDraft) { mutableStateOf(startingDraft.isDefault) }
    var lat by remember(startingDraft) { mutableStateOf(startingDraft.lat) }
    var lng by remember(startingDraft) { mutableStateOf(startingDraft.lng) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showMapPicker by remember { mutableStateOf(false) }

    val locationState by locationViewModel.state.collectAsState()
    val suggestionState by suggestionViewModel.state.collectAsState()

    /**
     * Folds a tapped completion into the form. The decisions about what a completion may and may
     * not overwrite live in `patchOnto`, which is unit-tested; this only moves values between the
     * field state and that rule, so the two cannot drift apart.
     */
    fun applySuggestion(item: AreaSuggestion) {
        val patched = item.patchOnto(
            AddressDraft(
                label = label,
                recipientName = recipientName,
                phone = phone,
                addressLines = addressLines,
                landmark = landmark,
                area = area,
                city = city,
                state = state,
                postalCode = postalCode,
                isDefault = isDefault,
                lat = lat,
                lng = lng
            )
        )
        recipientName = patched.recipientName
        phone = patched.phone
        addressLines = patched.addressLines
        landmark = patched.landmark
        area = patched.area
        city = patched.city
        state = patched.state
        postalCode = patched.postalCode
        lat = patched.lat
        lng = patched.lng
        // Ask the server about the spot right away, so the pre-order banner answers "do we
        // deliver here and at what fee" before anything is saved.
        item.point?.let {
            mapPickerViewModel.openAt(point = GeoPoint(it.latitude, it.longitude), pin = patched.postalCode)
        }
    }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            locationViewModel.useCurrentLocation()
            // The same permission also powers the map's recenter button, so a first-time
            // grant has to feed both paths or the map stays centred on nothing.
            if (showMapPicker) mapPickerViewModel.useMyLocation(pin = postalCode)
        } else {
            locationViewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(initial?.id) {
        locationViewModel.reset()
        mapPickerViewModel.reset()
        showMapPicker = false
    }

    LaunchedEffect(locationState.fix, locationState.place) {
        val fix = locationState.fix ?: return@LaunchedEffect
        lat = fix.latitude
        lng = fix.longitude
        val place = locationState.place ?: return@LaunchedEffect
        if (addressLines.isBlank()) addressLines = place.addressLine.orEmpty()
        if (area.isBlank()) area = place.area.orEmpty()
        if (city.isBlank()) city = place.city.orEmpty()
        if (state.isBlank()) state = place.state.orEmpty()
        if (postalCode.isBlank()) postalCode = place.postalCode.orEmpty()
    }

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

    fun currentPoint(): GeoPoint? {
        val latitude = lat ?: return null
        val longitude = lng ?: return null
        return GeoPoint(latitude, longitude)
    }

    // Opening the picker centres it on whatever the form already holds and asks the
    // server about that spot straight away.
    LaunchedEffect(showMapPicker) {
        if (!showMapPicker) return@LaunchedEffect
        mapPickerViewModel.openAt(point = currentPoint(), pin = postalCode)
    }

    // The PIN in the form is what gates delivery, so editing it must re-ask about the
    // same spot instead of leaving a stale verdict on screen.
    LaunchedEffect(postalCode) {
        if (showMapPicker) mapPickerViewModel.recheck(pin = postalCode)
    }

    fun save() {
        val draft = AddressDraft(
            label = label,
            recipientName = recipientName,
            phone = phone,
            addressLines = addressLines,
            landmark = landmark,
            area = area,
            city = city,
            state = state,
            postalCode = postalCode,
            isDefault = isDefault,
            lat = lat,
            lng = lng
        )
        val error = draft.validate()
        if (error != null) {
            localError = error
        } else {
            localError = null
            onSave(draft)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = if (initial == null) "Add delivery address" else "Edit delivery address",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label (Home, Work)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Recipient name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact mobile number *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedButton(
                    onClick = {
                        if (hasLocationPermission()) {
                            locationViewModel.useCurrentLocation()
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    enabled = !locationState.isLocating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (locationState.isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (locationState.isLocating) {
                            "Finding your location..."
                        } else {
                            "Use my current location"
                        }
                    )
                }
                OutlinedButton(
                    onClick = { showMapPicker = !showMapPicker },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = if (showMapPicker) {
                                "Hide the delivery area map"
                            } else {
                                "Choose the exact delivery spot on a map"
                            }
                        }
                ) {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showMapPicker) "Hide the map" else "Choose the exact spot on a map")
                }
                if (showMapPicker) {
                    DeliveryMapPicker(
                        viewModel = mapPickerViewModel,
                        pin = postalCode,
                        onPointChanged = { point ->
                            lat = point.latitude
                            lng = point.longitude
                        },
                        onRecenter = {
                            if (hasLocationPermission()) {
                                mapPickerViewModel.useMyLocation(pin = postalCode)
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (lat != null && lng != null) {
                    Text(
                        text = "GPS location attached (${String.format(Locale.US, "%.5f", lat)}, ${String.format(Locale.US, "%.5f", lng)}). Check the address fields before saving.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                locationState.error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedTextField(
                    value = addressLines,
                    onValueChange = { addressLines = it },
                    label = { Text("Flat / house no. / street *") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                OutlinedTextField(
                    value = landmark,
                    onValueChange = { landmark = it },
                    label = { Text("Landmark (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                // ── Completions, from the store's own areas and this customer's own addresses ──
                OutlinedTextField(
                    value = suggestionState.query,
                    onValueChange = { suggestionViewModel.onQueryChange(it) },
                    label = { Text("Search an area or PIN code") },
                    supportingText = {
                        // Honest about the limit of a no-cost, no-tracking autocomplete: it can
                        // only complete what this store already serves.
                        Text("Completes the areas and PIN codes we serve - not street addresses.")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (suggestionState.searching) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Text("Searching...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (suggestionState.suggestions.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestionState.suggestions.forEach { item ->
                            AssistChip(
                                onClick = { applySuggestion(item) },
                                label = { Text(item.chipLabel()) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = area,
                        onValueChange = { area = it },
                        label = { Text("Area (optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = state,
                        onValueChange = { state = it },
                        label = { Text("State *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = postalCode,
                        onValueChange = { postalCode = it.filter(Char::isDigit).take(6) },
                        label = { Text("PIN code *") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = isDefault,
                            role = Role.Checkbox,
                            onValueChange = { isDefault = it }
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isDefault,
                        onCheckedChange = null
                    )
                    Text(
                        text = "Make this my default delivery address",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                val error = localError ?: serverError
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::save,
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (initial == null) "Save address" else "Save changes")
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
