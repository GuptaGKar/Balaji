package com.balaji.provision.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Wallet
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.balaji.provision.domain.model.SavedAddress
import com.balaji.provision.ui.components.StatusPill
import com.balaji.provision.ui.components.SummaryRow
import com.balaji.provision.ui.components.liquidGlass
import com.balaji.provision.ui.theme.BalajiDesign
import com.balaji.provision.ui.theme.BalajiText
import com.balaji.provision.ui.theme.appColors

/**
 * The three checkout sections, moved off the separate checkout screen and into the basket.
 *
 * The basket is now the one page the customer checks out from: the items, then the delivery
 * address, the delivery instructions and finally the payment method, with the place-order slide
 * pinned below. None of these cards computes an amount - the fee and total always come from the
 * server quote in the bill.
 */

/** A glass card with the 18 dp corner used by every checkout section. */
@Composable
private fun CheckoutSectionCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(
                shape = RoundedCornerShape(18.dp),
                fill = MaterialTheme.colorScheme.surface
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        // Elevation 0: under a transparent glass fill the shadow renders as a grey halo
        // around every card on device, which is what made checkout look dirty.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/**
 * Address chooser. Selection is what the quote is priced against, so the card owns the radio
 * list and the "Add new" entry; the editor itself is hosted by the basket screen.
 */
@Composable
fun DeliveryAddressCard(
    isLoading: Boolean,
    addresses: List<SavedAddress>,
    selectedAddress: SavedAddress?,
    error: String?,
    isSaving: Boolean,
    onSelectAddress: (String) -> Unit,
    onAddAddress: () -> Unit,
    onRetry: () -> Unit
) {
    CheckoutSectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Delivery address",
                style = BalajiText.SectionTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onAddAddress, enabled = !isSaving) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(" Add new")
            }
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        when {
            isLoading && addresses.isEmpty() -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp
                )
            }

            addresses.isEmpty() -> {
                Text(
                    "Add a delivery address once and reuse it for every order.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = onAddAddress, enabled = !isSaving) {
                    Text("Add delivery address")
                }
            }

            else -> addresses.forEach { address ->
                CheckoutAddressOption(
                    address = address,
                    selected = address.id == selectedAddress?.id,
                    onSelect = { onSelectAddress(address.id) }
                )
            }
        }

        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

/** Free-text note carried on the order ("leave at door", "call on arrival"). */
@Composable
fun DeliveryInstructionsCard(
    note: String,
    onNoteChange: (String) -> Unit
) {
    CheckoutSectionCard {
        Text(
            text = "Delivery instructions (optional)",
            style = BalajiText.SectionTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            placeholder = { Text("e.g., Leave at door, call on arrival…") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

/**
 * Payment method. Only Cash on Delivery is offered: online payments stay disabled until the
 * M11 payment gate passes, so the card says so instead of showing a control that cannot work.
 */
@Composable
fun PaymentMethodCard() {
    CheckoutSectionCard {
        Text(
            text = "Payment method",
            style = BalajiText.SectionTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Divider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = true,
                    role = Role.RadioButton,
                    onClick = {}
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = true, onClick = {})
            Icon(
                Icons.Default.Wallet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cash on Delivery (COD)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Pay in cash when groceries arrive at your doorstep.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(14.dp),
                    fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Online payment",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Not available yet. Use Cash on Delivery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(text = "Coming soon")
            }
        }
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
            )
            .liquidGlass(
                shape = RoundedCornerShape(14.dp),
                // Selection is carried by the fill and the radio, not by a border, so the
                // glass rim is decoration here and the tint stays the state signal.
                fill = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        // Selection is the fill and the radio; a shadow here drew a grey ring around the
        // chosen address on device.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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

/**
 * Confirmation after the store accepts the order. Not dismissible: the basket is already empty
 * by then, so the only meaningful next step is the order itself.
 */
@Composable
fun OrderPlacedDialog(
    orderNumber: String,
    onTrack: () -> Unit
) {
    Dialog(onDismissRequest = { }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = RoundedCornerShape(24.dp),
                    fill = MaterialTheme.colorScheme.surface
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
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
                        "Order placed!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Order #$orderNumber",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(14.dp),
                            fill = MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SummaryRow(label = "Payment", value = "Cash on Delivery (COD)")
                        SummaryRow(label = "Delivery", value = "Timing confirmed by the store")
                        SummaryRow(label = "Final bill", value = "Issued by the store at billing")
                    }
                }

                Button(
                    onClick = onTrack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BalajiDesign.PrimaryCtaHeight),
                    shape = RoundedCornerShape(BalajiDesign.RadiusPill),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Track your order",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
