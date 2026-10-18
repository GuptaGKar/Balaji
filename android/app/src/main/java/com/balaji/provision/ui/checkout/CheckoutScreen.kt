package com.balaji.provision.ui.checkout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun CheckoutScreen(
    onOrderPlaced: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var addressLines by remember { mutableStateOf("") }
    var landmark by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("") }
    var postalCode by remember { mutableStateOf("") }
    var customerNote by remember { mutableStateOf("") }
    var selectedAddressId by remember { mutableStateOf<String?>(null) }
    
    var isLoading by remember { mutableStateOf(false) }
    var showSavedAddresses by remember { mutableStateOf(false) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    fun validateForm(): Boolean {
        return name.isNotBlank() && phone.isNotBlank() &&
               addressLines.isNotBlank() && city.isNotBlank() &&
               state.isNotBlank() && postalCode.isNotBlank()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SnackbarHost(snackbarHostState)
        
        Text(
            text = "Checkout",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
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
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
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
        
        OrderSummaryPlaceholder()
        
        Spacer(Modifier.size(16.dp))
        
        Button(
            onClick = {
                if (validateForm()) {
                    isLoading = true
                    onOrderPlaced()
                } else {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Please fill all required fields")
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            enabled = !isLoading,
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
                Text("Place Order (COD)", fontWeight = FontWeight.Medium, fontSize = 16.sp)
            }
        }
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
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
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = city,
                onValueChange = onCityChange,
                label = { Text("City *") },
                modifier = Modifier.weight(1f)
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
                modifier = Modifier.weight(1f)
            )
            TextField(
                value = postalCode,
                onValueChange = onPostalCodeChange,
                label = { Text("PIN Code *") },
                modifier = Modifier.weight(1f)
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
    Column(Modifier.padding(16.dp)) {
        Text("No saved addresses yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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