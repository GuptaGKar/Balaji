package com.balaji.provision.ui.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavController
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextAlign

@Composable
fun CheckoutScreen(
    viewModel: CheckoutViewModel = viewModel(),
    onOrderPlaced: () -> Unit
) {
    val name by remember { mutableStateOf("") }
    val phone by remember { mutableStateOf("") }
    val addressLines by remember { mutableStateOf("") }
    val landmark by remember { mutableStateOf("") }
    val area by remember { mutableStateOf("") }
    val city by remember { mutableStateOf("") }
    val state by remember { mutableStateOf("") }
    val postalCode by remember { mutableStateOf("") }
    val customerNote by remember { mutableStateOf("") }
    val selectedAddressId by remember { mutableStateOf<String?>(null) }
    
    val isLoading by remember { mutableStateOf(false) }
    val error by remember { mutableStateOf<String?>(null) }
    val showSavedAddresses by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SnackbarHost(snackbarHostState)
        
        Text("Checkout", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            .padding(16.dp)
        
        // Address section - either form or saved address selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Delivery Address", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    if (showSavedAddresses || selectedAddressId != null) {
                        OutlinedButton(onClick = { showSavedAddresses = !showSavedAddresses }) {
                            Text(if (showSavedAddresses) "Use New Address" else "Saved Addresses")
                        }
                    }
                }
                
                if (showSavedAddresses) {
                    SavedAddressesList(
                        onAddressSelected = { address ->
                            name = address.recipientName
                            phone = address.phone
                            addressLines = address.addressLines
                            landmark = address.landmark ?: ""
                            area = address.area ?: ""
                            city = address.city ?: ""
                            state = address.state ?: ""
                            postalCode = address.postalCode
                            selectedAddressId = address.id
                            showSavedAddresses = false
                        }
                    )
                } else {
                    AddressForm(
                        name = name, onNameChange = { name = it },
                        phone = phone, onPhoneChange = { phone = it },
                        addressLines = addressLines, onAddressLinesChange = { addressLines = it },
                        landmark = landmark, onLandmarkChange = { landmark = it },
                        area = area, onAreaChange = { area = it },
                        city = city, onCityChange = { city = it },
                        state = state, onStateChange = { state = it },
                        postalCode = postalCode, onPostalCodeChange = { postalCode = it }
                    )
                }
            }
        }
        
        // Order notes
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Order Notes (Optional)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Divider(modifier = Modifier.padding(vertical = 12.dp))
                
                TextField(
                    value = customerNote,
                    onValueChange = { customerNote = it },
                    label = { Text("Special instructions for the store...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        }
        
        // Order summary - would be connected to ViewModel quote
        OrderSummaryPlaceholder()
        
        Spacer(Modifier.size(16.dp))
        
        // Place order button
        Button(
            onClick = {
                // Validate and place order
                if (validateForm()) {
                    placeOrder()
                } else {
                    snackbarHostState.showSnackbar("Please fill all required fields")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = !isLoading,
            colors = ButtonDefaults.buttonColors(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text("Place Order (COD)", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }
    }
    
    private fun validateForm(): Boolean {
        return name.isNotBlank() && phone.isNotBlank() && 
               addressLines.isNotBlank() && city.isNotBlank() && 
               state.isNotBlank() && postalCode.isNotBlank()
    }
    
    private fun placeOrder() {
        // TODO: Call ViewModel placeOrder with idempotency key
        isLoading = true
        // TODO: Call viewModel.placeOrder(...)
        // On success: onOrderPlaced()
        // On error: snackbarHostState.showSnackbar(error)
        // Finally: isLoading = false
    }
}

@Composable
fun AddressForm(
    name: String, onNameChange: (String) -> Unit,
    phone: String, onPhoneChange: (String) -> Unit,
    addressLines: String, onAddressLinesChange: (String) -> Unit,
    landmark: String, onLandmarkChange: (String) -> Unit,
    area: String, onAreaChange: (String) -> Unit,
    city: String, onCityChange: (String) -> Unit,
    state: String, onStateChange: (String) -> Unit,
    postalCode: String, onPostalCodeChange: (String) -> Unit
) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text("Delivery Address", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Divider(modifier = Modifier.padding(vertical = 12.dp))
        
        TextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Recipient Name *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        Spacer(Modifier.size(12.dp))
        
        TextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = { Text("Phone Number *") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
        )
        
        Spacer(Modifier.size(12.dp))
        
        TextField(
            value = addressLines,
            onValueChange = onAddressLinesChange,
            label = { Text("Address *") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2
        )
        
        Spacer(Modifier.size(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = area,
                onValueChange = onAreaChange,
                label = { Text("Area") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            TextField(
                value = city,
                onValueChange = onCityChange,
                label = { Text("City *") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
        
        Spacer(Modifier.size(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = state,
                onValueChange = onStateChange,
                label = { Text("State *") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            TextField(
                value = postalCode,
                onValueChange = onPostalCodeChange,
                label = { Text("PIN Code *") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
        
        Spacer(Modifier.size(12.dp))
        
        TextField(
            value = landmark,
            onValueChange = onLandmarkChange,
            label = { Text("Landmark (Optional)") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SavedAddressesList(onAddressSelected: (SavedAddress) -> Unit) {
    // Placeholder - would load from ViewModel
    Column(Modifier.padding(16.dp)) {
        Text("No saved addresses yet", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
    }
}

data class SavedAddress(
    val id: String,
    val recipientName: String,
    val phone: String,
    val addressLines: String,
    val landmark: String?,
    val area: String?,
    val city: String?,
    val state: String?,
    val postalCode: String
)

@Composable
fun OrderSummaryPlaceholder() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Order Summary", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            
            SummaryRow("Subtotal", "₹0.00")
            SummaryRow("Delivery Fee", "₹0.00")
            Divider(modifier = Modifier.padding(vertical = 12.dp))
            SummaryRow("Total", "₹0.00", isTotal = true)
        }
    }
}

@Composable
fun SummaryRow(label: String, value: String, isTotal: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = if (isTotal) 16.sp else 14.sp,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            value,
            fontSize = if (isTotal) 16.sp else 14.sp,
            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// Placeholder for CheckoutViewModel - will be implemented properly
class CheckoutViewModel : androidx.lifecycle.ViewModel() {
    // Implementation will be added in the next iteration
}