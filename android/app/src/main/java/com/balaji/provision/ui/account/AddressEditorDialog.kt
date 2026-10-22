package com.balaji.provision.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.balaji.provision.domain.model.AddressDraft
import com.balaji.provision.domain.model.SavedAddress

@Composable
fun AddressEditorDialog(
    initial: SavedAddress?,
    defaultName: String?,
    defaultPhone: String?,
    isSaving: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSave: (AddressDraft) -> Unit
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
    var localError by remember { mutableStateOf<String?>(null) }

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
            isDefault = isDefault
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
