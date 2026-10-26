package com.balaji.provision.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.balaji.provision.domain.model.Product
import com.balaji.provision.domain.model.formatPaise
import com.balaji.provision.domain.model.formatWeightGrams
import com.balaji.provision.domain.model.lineTotalPaise

internal fun parseWeightInput(text: String, isKilograms: Boolean): Long {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0
    return if (isKilograms) {
        val parts = trimmed.split(".")
        if (parts.size > 2) return 0
        val whole = parts[0].toLongOrNull() ?: return 0
        val fraction = if (parts.size == 2) parts[1].padEnd(3, '0').take(3).toLongOrNull() ?: return 0 else 0
        whole * 1000 + fraction
    } else {
        trimmed.toLongOrNull() ?: 0
    }
}

internal fun formatWeightInput(grams: Long, isKilograms: Boolean): String {
    if (grams <= 0L) return "0"
    if (!isKilograms) return grams.toString()
    val whole = grams / 1000
    val remainder = grams % 1000
    if (remainder == 0L) return whole.toString()
    val fraction = remainder.toString().padStart(3, '0').trimEnd('0')
    return "$whole.$fraction"
}

enum class WeightUnit {
    GRAMS, KILOGRAMS
}

@Composable
fun WeightedQuantityDialog(
    product: Product,
    currentQtyBaseGrams: Long,
    onDismiss: () -> Unit,
    onConfirmQtyGrams: (Long) -> Unit
) {
    var selectedUnit by remember {
        mutableStateOf(
            if (currentQtyBaseGrams >= 1000 && currentQtyBaseGrams % 500 == 0L) WeightUnit.KILOGRAMS else WeightUnit.GRAMS
        )
    }

    var inputText by remember {
        mutableStateOf(
            formatWeightInput(currentQtyBaseGrams, selectedUnit == WeightUnit.KILOGRAMS)
        )
    }

    val isKilograms = selectedUnit == WeightUnit.KILOGRAMS
    val calculatedGrams = parseWeightInput(inputText, isKilograms)
    val calculatedPricePaise = lineTotalPaise(product.pricePaise, product.priceBasisQty, calculatedGrams)
    val stepGrams = product.stepQty.coerceAtLeast(1L)
    val isValid = calculatedGrams >= product.minQty &&
        calculatedGrams <= product.maxOrderQty &&
        calculatedGrams % stepGrams == 0L

    fun switchUnit(unit: WeightUnit) {
        if (unit == selectedUnit) return
        val grams = parseWeightInput(inputText, isKilograms)
        selectedUnit = unit
        inputText = formatWeightInput(grams, unit == WeightUnit.KILOGRAMS)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Scale,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                "Choose weight",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                product.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                // Unit Selection Segmented Control (Grams vs Kilograms)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        UnitToggleButton(
                            label = "Grams (g)",
                            isSelected = selectedUnit == WeightUnit.GRAMS,
                            onClick = { switchUnit(WeightUnit.GRAMS) },
                            modifier = Modifier.weight(1f)
                        )
                        UnitToggleButton(
                            label = "Kilograms (kg)",
                            isSelected = selectedUnit == WeightUnit.KILOGRAMS,
                            onClick = { switchUnit(WeightUnit.KILOGRAMS) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                // Quick Weight Preset Chips
                Text(
                    "Quick selection",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = if (selectedUnit == WeightUnit.GRAMS) {
                        listOf("100", "200", "500", "750")
                    } else {
                        listOf("0.5", "1", "2", "5")
                    }
                    
                    presets.forEach { preset ->
                        FilterChip(
                            selected = inputText == preset,
                            onClick = { inputText = preset },
                            label = {
                                Text(
                                    if (selectedUnit == WeightUnit.GRAMS) "${preset}g" else "${preset}kg",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            shape = RoundedCornerShape(50),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
                
                // Manual Number Input Field
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { newValue ->
                        // Allow numbers and single decimal point
                        if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                            inputText = newValue
                        }
                    },
                    label = { Text("Enter quantity in ${if (selectedUnit == WeightUnit.GRAMS) "grams" else "kg"}") },
                    suffix = { Text(if (selectedUnit == WeightUnit.GRAMS) "g" else "kg", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
                
                // Price Preview Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Total weight",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatWeightGrams(calculatedGrams),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Calculated price",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            MoneyText(
                                text = formatPaise(calculatedPricePaise),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                if (!isValid && calculatedGrams > 0) {
                    Text(
                        when {
                            calculatedGrams % stepGrams != 0L ->
                                "Enter weight in steps of ${formatWeightGrams(stepGrams)}."
                            calculatedGrams < product.minQty ->
                                "Minimum weight is ${formatWeightGrams(product.minQty)}."
                            else ->
                                "Maximum weight is ${formatWeightGrams(product.maxOrderQty)}."
                        },
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Confirm Action Button
                Button(
                    onClick = {
                        if (isValid && calculatedGrams > 0) {
                            onConfirmQtyGrams(calculatedGrams)
                            onDismiss()
                        }
                    },
                    enabled = isValid && calculatedGrams > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Confirm & add to basket", style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

@Composable
fun UnitToggleButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 12.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}